package com.arthou.smputilities.ttsbridge;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Local text-to-speech bridge for the SMPutilities Minecraft mod.
 *
 * Runs on Windows, Linux and macOS from a single jar: the HTTP layer is
 * com.sun.net.httpserver and the UI is Swing, both part of the JDK, so the
 * build has no third-party dependencies.
 *
 * HTTP contract (unchanged from the original bridge, so the mod keeps working):
 *   GET  /health    -> 200 when alive
 *   POST /speak     -> {"text":"...","voiceName":"...","pitch":"..."}
 *   POST /shutdown  -> 200, then exits
 *
 * Usage: java -jar smputilities-voice-bridge.jar [--background] [--parent-pid <pid>]
 */
public final class VoiceBridge {
    public static final int PORT = 43125;

    private static final AtomicInteger SERVED = new AtomicInteger();
    private static volatile BridgeWindow window;
    private static volatile HttpServer server;

    private VoiceBridge() {
    }

    public static void main(String[] args) throws Exception {
        boolean background = false;
        long parentPid = -1L;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--background" -> background = true;
                case "--parent-pid" -> {
                    if (i + 1 < args.length) {
                        try {
                            parentPid = Long.parseLong(args[++i]);
                        } catch (NumberFormatException ignored) {
                            // leave the watchdog disabled
                        }
                    }
                }
                default -> {
                    // ignore unknown flags so older mod builds keep working
                }
            }
        }

        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);
        } catch (IOException bindFailure) {
            // Another bridge already owns the port: nothing to do, stay out of the way.
            System.err.println("smputilities-voice-bridge: port " + PORT + " already in use, exiting.");
            return;
        }

        server.createContext("/health", VoiceBridge::handleHealth);
        server.createContext("/speak", VoiceBridge::handleSpeak);
        server.createContext("/shutdown", VoiceBridge::handleShutdown);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();

        restoreAudioRouting();

        if (!GraphicsEnvironment.isHeadless()) {
            startUi(background);
        }

        if (parentPid > 0) {
            watchParent(parentPid);
        }

        log("Bridge listening on http://127.0.0.1:" + PORT);
        log("Voice source: ttstool.com (" + RemoteTts.voiceCount() + " cloud voices)");
        log("Offline fallback: " + Tts.engine().label + "   player: " + Player.backend());
    }

    /**
     * PulseAudio modules do not survive a reboot, so re-create the routing the
     * user already chose instead of making them redo it every session.
     */
    private static void restoreAudioRouting() {
        if (!Audio.available()) {
            return;
        }

        // First run on Linux: the whole point is to speak through the microphone,
        // so start on the cable instead of the speakers, and keep a copy on the
        // speakers so you still hear yourself (what the Windows bridge does).
        if (Audio.canCreateCable() && !Config.hasOutputSink()) {
            Config.setOutputSink(Audio.CABLE_SINK);
            if (!Config.hasMonitorSink()) {
                String speakers = Audio.defaultSink();
                Config.setMonitorSink(speakers == null ? "" : speakers);
            }
            log("First run: voice goes to " + Audio.MIC_DESCRIPTION);
        }

        if (Audio.CABLE_SINK.equals(Config.outputSink()) && (!Audio.cableExists() || !Audio.micExists())) {
            String error = Audio.createCable();
            log(error == null ? "Virtual microphone ready: " + Audio.MIC_DESCRIPTION : "Could not create the virtual microphone: " + error);
        }

        String monitor = Config.monitorSink();
        if (monitor.isBlank() || Audio.loopbackDestination() != null) {
            return;
        }

        // The monitor source appears a moment after the sink; creating the
        // loopback too early makes it latch onto the default device instead.
        for (int attempt = 0; attempt < 20 && !Audio.monitorExists(); attempt++) {
            try {
                Thread.sleep(100L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        if (!Audio.monitorExists()) {
            log("Monitoring not restored: virtual cable never appeared");
            return;
        }

        String error = Audio.createLoopback(monitor);
        log(error == null ? "Monitoring restored on " + monitor : "Could not restore monitoring: " + error);
    }

    // ---------------------------------------------------------------- HTTP

    private static void handleHealth(HttpExchange exchange) throws IOException {
        String body = "{\"status\":\"ok\""
            + ",\"voiceSource\":\"ttstool.com\""
            + ",\"cloudVoices\":" + RemoteTts.voiceCount()
            + ",\"fallbackEngine\":\"" + Json.escape(Tts.engine().label) + "\""
            + ",\"player\":\"" + Json.escape(Player.backend()) + "\""
            + ",\"served\":" + SERVED.get() + "}";
        respond(exchange, 200, body);
    }

    private static void handleSpeak(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            respond(exchange, 405, "{\"error\":\"method not allowed\"}");
            return;
        }

        String payload;
        try (InputStream in = exchange.getRequestBody()) {
            payload = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        String text = Json.string(payload, "text");
        String voice = Json.string(payload, "voiceName");
        String pitch = Json.string(payload, "pitch");

        if (text.isBlank()) {
            respond(exchange, 400, "{\"error\":\"empty text\"}");
            return;
        }

        try {
            Tts.speak(text, voice, pitch);
            int served = SERVED.incrementAndGet();
            if (window != null) {
                window.setServed(served);
            }
            log("Spoke: " + preview(text));
            respond(exchange, 200, "{\"status\":\"ok\"}");
        } catch (IOException failure) {
            log("Speech failed: " + failure.getMessage());
            respond(exchange, 500, "{\"error\":\"" + Json.escape(String.valueOf(failure.getMessage())) + "\"}");
        }
    }

    private static void handleShutdown(HttpExchange exchange) throws IOException {
        respond(exchange, 200, "{\"status\":\"stopping\"}");
        log("Shutdown requested");
        shutdown();
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static String preview(String text) {
        String single = text.replaceAll("\\s+", " ").trim();
        return single.length() <= 60 ? single : single.substring(0, 57) + "…";
    }

    // ------------------------------------------------------------- lifecycle

    static void shutdown() {
        new Thread(() -> {
            try {
                Thread.sleep(250L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            if (server != null) {
                server.stop(0);
            }
            System.exit(0);
        }, "smputilities-voice-bridge-shutdown").start();
    }

    /** Exits when Minecraft goes away, so no stray process is left behind. */
    private static void watchParent(long parentPid) {
        Thread watchdog = new Thread(() -> {
            ProcessHandle.of(parentPid).ifPresentOrElse(handle -> {
                try {
                    handle.onExit().get();
                } catch (Exception ignored) {
                    Thread.currentThread().interrupt();
                }
                log("Parent process ended, shutting down");
                shutdown();
            }, VoiceBridge::shutdown);
        }, "smputilities-voice-bridge-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    private static void log(String line) {
        System.out.println("[smputilities-voice-bridge] " + line);
        if (window != null) {
            window.append(line);
        }
    }

    // -------------------------------------------------------------------- UI

    private static void startUi(boolean background) {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {
            // the painted widgets do not depend on the look and feel
        }

        SwingUtilities.invokeLater(() -> {
            BridgeWindow created = new BridgeWindow(PORT, VoiceBridge::shutdown);
            created.setRunning(true);
            created.setServed(SERVED.get());
            created.append("Bridge listening on http://127.0.0.1:" + PORT);
            created.append("Voice source: ttstool.com (" + RemoteTts.voiceCount() + " cloud voices)");
            created.append("Offline fallback: " + Tts.engine().label);
            window = created;

            boolean tray = installTray(created);
            created.setVisible(!background || !tray);
            if (background && tray) {
                created.append("Started in the background — reopen from the tray icon.");
            }
        });
    }

    private static boolean installTray(BridgeWindow frame) {
        if (!SystemTray.isSupported()) {
            return false;
        }
        try {
            int size = 16;
            BufferedImage icon = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = icon.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Theme.ACCENT);
            g.fillRoundRect(0, 0, size, size, 5, 5);
            g.setColor(Theme.BG);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
            g.drawString("D", 4, 12);
            g.dispose();

            PopupMenu menu = new PopupMenu();
            MenuItem open = new MenuItem("Open SMPutilities Voice Bridge");
            open.addActionListener(e -> {
                frame.setVisible(true);
                frame.toFront();
            });
            MenuItem quit = new MenuItem("Stop bridge");
            quit.addActionListener(e -> shutdown());
            menu.add(open);
            menu.addSeparator();
            menu.add(quit);

            TrayIcon trayIcon = new TrayIcon(icon, "SMPutilities Voice Bridge", menu);
            trayIcon.setImageAutoSize(true);
            trayIcon.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent event) {
                    if (event.getClickCount() == 2) {
                        frame.setVisible(true);
                        frame.toFront();
                    }
                }
            });
            SystemTray.getSystemTray().add(trayIcon);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
