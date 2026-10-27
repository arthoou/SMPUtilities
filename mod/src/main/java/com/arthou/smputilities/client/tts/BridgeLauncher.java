package com.arthou.smputilities.client.tts;

import com.arthou.smputilities.mc.Texts;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Extracts and runs the voice bridge.
 *
 * The bridge is a plain jar rather than a Windows .exe, so the same file works
 * on Windows, Linux and macOS. It is started with the JVM Minecraft is already
 * running on, which is always present, so there is nothing extra to install.
 */
public final class BridgeLauncher {
    private static final URI HEALTH_URI = URI.create("http://127.0.0.1:43125/health");
    private static final Duration HEALTH_TIMEOUT = Duration.ofMillis(800L);
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(HEALTH_TIMEOUT)
        .build();
    private static final String RESOURCE_ROOT = "/smputilities/voice-bridge/";
    private static final String BRIDGE_JAR = "smputilities-voice-bridge.jar";
    /** Written by the voice app once it has run: its chosen output and microphone. */
    private static final String BRIDGE_SETTINGS = "smputilities-voice-bridge.properties";
    private static final boolean LINUX = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux");
    private static final AtomicBoolean launchErrorShown = new AtomicBoolean();
    private static volatile long lastLaunchAttemptMillis;

    private BridgeLauncher() {
    }

    public static boolean ensureRunning(boolean showError) {
        return ensureRunning(showError, false);
    }

    /**
     * Linux: the virtual microphone exists only while the voice app runs (it is
     * removed when the app closes), and a voice chat opens its microphone as soon
     * as you join a server. So once the app has been set up, it starts with the
     * game, in the background, and the microphone is there first. On Windows and
     * macOS the driver's device is always there, so the app waits for the first
     * TTS line as before.
     */
    public static void startWithGame() {
        if (!LINUX || !com.arthou.smputilities.client.ClientConfig.get().tts.autoStartBridge
            || !Files.exists(bridgeDirectory().resolve(BRIDGE_SETTINGS))) {
            return;
        }
        Thread thread = new Thread(() -> ensureRunning(false, true), "smputilities-voice-start");
        thread.setDaemon(true);
        thread.start();
    }

    private static boolean ensureRunning(boolean showError, boolean background) {
        if (isHealthy()) {
            launchErrorShown.set(false);
            return true;
        }

        if (!com.arthou.smputilities.client.ClientConfig.get().tts.autoStartBridge) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (now - lastLaunchAttemptMillis < 3_000L) {
            return waitForHealth(Duration.ofSeconds(3L), showError);
        }

        lastLaunchAttemptMillis = now;
        try {
            Path bridgeDirectory = extractBridgeBundle();
            startBridgeProcess(bridgeDirectory, background);
        } catch (Exception exception) {
            if (showError && launchErrorShown.compareAndSet(false, true)) {
                showClientMessage(Texts.translatable("tts.helper_missing")
                    .append(Texts.literal(" (" + exception.getClass().getSimpleName() + ")"))
                    .withStyle(ChatFormatting.RED));
            }
            return false;
        }

        boolean healthy = waitForHealth(Duration.ofSeconds(8L), showError);
        if (healthy) {
            launchErrorShown.set(false);
        }
        return healthy;
    }

    private static boolean waitForHealth(Duration duration, boolean showError) {
        long deadline = System.nanoTime() + duration.toNanos();
        while (System.nanoTime() < deadline) {
            if (isHealthy()) {
                launchErrorShown.set(false);
                return true;
            }

            try {
                Thread.sleep(200L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        if (showError && launchErrorShown.compareAndSet(false, true)) {
            showClientMessage(Texts.translatable("tts.helper_io")
                .withStyle(ChatFormatting.RED));
        }
        return false;
    }

    private static boolean isHealthy() {
        try {
            HttpRequest request = HttpRequest.newBuilder(HEALTH_URI)
                .timeout(HEALTH_TIMEOUT)
                .GET()
                .build();
            HttpResponse<Void> response = HTTP.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (ConnectException ignored) {
            return false;
        } catch (IOException | InterruptedException ignored) {
            if (ignored instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    private static Path bridgeDirectory() {
        return Minecraft.getInstance().gameDirectory.toPath()
            .resolve("config")
            .resolve("smputilities")
            .resolve("voice-bridge");
    }

    private static Path extractBridgeBundle() throws IOException {
        Path targetDirectory = bridgeDirectory();
        Files.createDirectories(targetDirectory);

        try (InputStream stream = BridgeLauncher.class.getResourceAsStream(RESOURCE_ROOT + BRIDGE_JAR)) {
            if (stream == null) {
                throw new IOException("Missing bridge resource: " + BRIDGE_JAR);
            }

            Files.copy(stream, targetDirectory.resolve(BRIDGE_JAR), StandardCopyOption.REPLACE_EXISTING);
        }

        return targetDirectory;
    }

    private static void startBridgeProcess(Path bridgeDirectory, boolean background) throws IOException {
        Path jar = bridgeDirectory.resolve(BRIDGE_JAR);
        String currentPid = Long.toString(ProcessHandle.current().pid());
        List<String> command = new ArrayList<>(List.of(javaExecutable(), "-jar", jar.toString(), "--parent-pid", currentPid));
        // Only when it starts with the game: on demand the window shows, since it
        // is where the output and microphone are chosen. In the background it goes
        // to the tray, or still shows when there is no tray.
        if (background) {
            command.add("--background");
        }
        new ProcessBuilder(command)
            .directory(bridgeDirectory.toFile())
            // Nobody reads the pipes; once they fill up the bridge blocks on its next log line.
            .redirectErrorStream(true)
            .redirectOutput(bridgeDirectory.resolve("smputilities-voice-bridge.log").toFile())
            .start();
    }

    /** Reuses the JVM Minecraft is running on, falling back to java.home. */
    private static String javaExecutable() {
        return ProcessHandle.current().info().command().orElseGet(() -> {
            String home = System.getProperty("java.home", "");
            String binary = System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java";
            return home.isBlank() ? binary : Path.of(home, "bin", binary).toString();
        });
    }

    private static void showClientMessage(Component message) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(message, false);
            }
        });
    }
}
