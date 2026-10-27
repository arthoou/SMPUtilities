package com.arthou.smputilities.ttsbridge;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Plays MP3 bytes on a chosen output device.
 *
 * The audio is piped through the player's stdin instead of a temporary file:
 * inside a Flatpak the sandbox and the host do not share /tmp, but
 * flatpak-spawn does forward stdin, so piping works in both cases.
 */
final class Player {
    private Player() {
    }

    /**
     * Windows and macOS playback. Mirrors what the original bridge did: send the
     * audio to the chosen device (VB-Cable, BlackHole) and to the monitor device
     * at the same time, so the game receives it and you still hear yourself.
     */
    private static void playWithJavaSound(byte[] mp3, String device) throws IOException {
        String monitor = Config.monitorSink();
        boolean separateMonitor = !monitor.isBlank() && !monitor.equals(device);

        Thread second = null;
        if (separateMonitor) {
            String target = monitor;
            second = new Thread(() -> {
                try {
                    JavaSoundPlayer.play(mp3, target);
                } catch (IOException ignored) {
                    // monitoring is best effort; the game feed matters more
                }
            }, "smputilities-voice-bridge-monitor");
            second.setDaemon(true);
            second.start();
        }

        JavaSoundPlayer.play(mp3, device);

        if (second != null) {
            try {
                second.join(15_000L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static boolean hasHost(String binary) {
        try {
            List<String> command = new ArrayList<>();
            if (Tts.FLATPAK) {
                command.add("flatpak-spawn");
                command.add("--host");
            }
            command.add("which");
            command.add(binary);
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            return process.waitFor() == 0;
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    /** Name of the player that will be used, or "none". */
    static String backend() {
        if (Tts.MAC) {
            // Java Sound can pick the device (BlackHole); afplay only plays on the default output.
            if (!JavaSoundPlayer.outputs().isEmpty()) {
                return "javasound";
            }
            return hasHost("afplay") ? "afplay" : "none";
        }
        if (Tts.WINDOWS) {
            return "javasound";
        }
        if (hasHost("mpv")) {
            return "mpv";
        }
        if (hasHost("ffplay")) {
            return "ffplay";
        }
        return "none";
    }

    static void play(byte[] mp3, String sink) throws IOException {
        String backend = backend();

        if ("javasound".equals(backend)) {
            playWithJavaSound(mp3, sink);
            return;
        }

        List<String> command = new ArrayList<>();

        if (Tts.FLATPAK) {
            command.add("flatpak-spawn");
            command.add("--host");
            if (sink != null && !sink.isBlank()) {
                command.add("--env=PULSE_SINK=" + sink);
            }
        }

        switch (backend) {
            case "mpv" -> {
                command.add("mpv");
                command.add("--no-video");
                command.add("--really-quiet");
                if (sink != null && !sink.isBlank()) {
                    command.add("--audio-device=pulse/" + sink);
                }
                command.add("-");
            }
            case "ffplay" -> {
                command.add("ffplay");
                command.add("-nodisp");
                command.add("-autoexit");
                command.add("-loglevel");
                command.add("quiet");
                command.add("-");
            }
            case "afplay" -> {
                command.add("afplay");
                command.add("-");
            }
            case "javasound" -> {
                // Handled before we get here.
                throw new IOException("unreachable");
            }
            default -> throw new IOException("No audio player found (install mpv or ffmpeg)");
        }

        ProcessBuilder builder = new ProcessBuilder(command)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD);
        if (!Tts.FLATPAK && sink != null && !sink.isBlank()) {
            builder.environment().put("PULSE_SINK", sink);
        }

        Process process = Playback.track(builder.start());
        try (OutputStream stdin = process.getOutputStream()) {
            stdin.write(mp3);
            stdin.flush();
        } catch (IOException ignored) {
            // player exited early; nothing useful to add
        }
    }
}
