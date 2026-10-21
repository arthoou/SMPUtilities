package com.arthou.smputilities.ttsbridge;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Platform speech backend. Windows uses SAPI through PowerShell; Linux uses
 *  espeak-ng when present and falls back to speech-dispatcher; macOS uses `say`. */
final class Tts {
    enum Engine {
        WINDOWS_SAPI("Windows SAPI"),
        ESPEAK_NG("espeak-ng"),
        SPEECH_DISPATCHER("speech-dispatcher"),
        MACOS_SAY("macOS say"),
        NONE("none");

        final String label;

        Engine(String label) {
            this.label = label;
        }
    }

    static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    static final boolean MAC = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");

    /** Inside a Flatpak sandbox the host's /usr/bin is not visible, so speech
     *  engines must be probed and run on the host through flatpak-spawn. */
    static final boolean FLATPAK = java.nio.file.Files.exists(java.nio.file.Path.of("/.flatpak-info"));

    private static final Engine ENGINE = detect();

    /** Prefixes a command so it runs on the host when we are sandboxed. */
    private static List<String> hostCommand(List<String> command) {
        return hostCommand(command, null);
    }

    /** Wraps a command for the host, optionally forcing a PulseAudio sink. */
    private static List<String> hostCommand(List<String> command, String sink) {
        List<String> wrapped = new ArrayList<>();
        if (FLATPAK) {
            wrapped.add("flatpak-spawn");
            wrapped.add("--host");
            if (sink != null && !sink.isBlank()) {
                wrapped.add("--env=PULSE_SINK=" + sink);
            }
        }
        wrapped.addAll(command);
        return wrapped;
    }

    private Tts() {
    }

    static Engine engine() {
        return ENGINE;
    }

    private static Engine detect() {
        if (WINDOWS) {
            return Engine.WINDOWS_SAPI;
        }
        if (MAC) {
            return has("say") ? Engine.MACOS_SAY : Engine.NONE;
        }
        if (has("espeak-ng")) {
            return Engine.ESPEAK_NG;
        }
        if (has("espeak")) {
            return Engine.ESPEAK_NG;
        }
        if (has("spd-say")) {
            return Engine.SPEECH_DISPATCHER;
        }
        return Engine.NONE;
    }

    private static boolean has(String binary) {
        try {
            Process process = new ProcessBuilder(hostCommand(List.of("which", binary)))
                .redirectErrorStream(true)
                .start();
            return process.waitFor() == 0;
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    /** Maps the mod's free-form pitch string onto a -10..10 scale. */
    private static int pitchScale(String pitch) {
        if (pitch == null || pitch.isBlank()) {
            return 0;
        }
        String value = pitch.trim().toLowerCase(Locale.ROOT);
        switch (value) {
            case "x-low", "xlow", "verylow", "very-low" -> {
                return -8;
            }
            case "low", "baixo" -> {
                return -4;
            }
            case "default", "medium", "normal", "padrao" -> {
                return 0;
            }
            case "high", "alto" -> {
                return 4;
            }
            case "x-high", "xhigh", "veryhigh", "very-high" -> {
                return 8;
            }
            default -> {
                try {
                    return Math.max(-10, Math.min(10, (int) Math.round(Double.parseDouble(value.replace("%", "")))));
                } catch (NumberFormatException ignored) {
                    return 0;
                }
            }
        }
    }


    /** Where the last successful synthesis came from, for the UI. */
    private static volatile String lastSource = "unknown";

    static String lastSource() {
        return lastSource;
    }

    /**
     * Speaks the text.
     *
     * The DUSMP voice names ("Amazon US English (Ivy)", ...) are ttstool.com
     * cloud voices, so that is tried first. A local engine is only a fallback
     * for when the service is unreachable - and it never receives the cloud
     * voice name, which it would reject.
     */
    static void speak(String text, String voiceName, String pitch) throws IOException {
        if (text == null || text.isBlank()) {
            return;
        }

        IOException remoteFailure = null;
        try {
            byte[] mp3 = RemoteTts.synthesize(text, voiceName, pitch);
            if (mp3 != null && mp3.length > 0) {
                Player.play(mp3, Config.outputSink());
                lastSource = "ttstool.com";
                return;
            }
        } catch (IOException exception) {
            remoteFailure = exception;
        }

        if (ENGINE == Engine.NONE) {
            throw remoteFailure != null
                ? remoteFailure
                : new IOException("No cloud voice and no local speech engine available");
        }

        // Local engines do not know the cloud voice names, so drop it.
        speakLocal(text, "", pitch);
        lastSource = ENGINE.label + (remoteFailure != null ? " (cloud failed)" : "");
    }

    /** Local fallback engine. Never receives cloud voice names. */
    private static void speakLocal(String text, String voiceName, String pitch) throws IOException {
        if (text == null || text.isBlank()) {
            return;
        }

        int scale = pitchScale(pitch);
        List<String> command = new ArrayList<>();

        switch (ENGINE) {
            case WINDOWS_SAPI -> {
                // SSML carries the pitch; System.Speech has no direct pitch property.
                String ssmlPitch = (scale >= 0 ? "+" : "") + (scale * 5) + "%";
                StringBuilder script = new StringBuilder();
                script.append("Add-Type -AssemblyName System.Speech; ");
                script.append("$s = New-Object System.Speech.Synthesis.SpeechSynthesizer; ");
                if (voiceName != null && !voiceName.isBlank()) {
                    script.append("try { $s.SelectVoice('").append(voiceName.replace("'", "''")).append("') } catch {}; ");
                }
                script.append("$x = '<speak version=\"1.0\" xmlns=\"http://www.w3.org/2001/10/synthesis\" xml:lang=\"en-US\">");
                script.append("<prosody pitch=\"").append(ssmlPitch).append("\">");
                script.append(xmlEscape(text).replace("'", "''"));
                script.append("</prosody></speak>'; ");
                script.append("$s.SpeakSsml($x); $s.Dispose();");
                command.add("powershell");
                command.add("-NoProfile");
                command.add("-NonInteractive");
                command.add("-Command");
                command.add(script.toString());
            }
            case ESPEAK_NG -> {
                command.add(has("espeak-ng") ? "espeak-ng" : "espeak");
                if (voiceName != null && !voiceName.isBlank()) {
                    command.add("-v");
                    command.add(voiceName);
                }
                command.add("-p");
                command.add(Integer.toString(Math.max(0, Math.min(99, 50 + scale * 5))));
                command.add("--");
                command.add(text);
            }
            case SPEECH_DISPATCHER -> {
                command.add("spd-say");
                command.add("-w");
                if (voiceName != null && !voiceName.isBlank()) {
                    command.add("-y");
                    command.add(voiceName);
                }
                command.add("-p");
                command.add(Integer.toString(Math.max(-100, Math.min(100, scale * 10))));
                command.add("--");
                command.add(text);
            }
            case MACOS_SAY -> {
                command.add("say");
                if (voiceName != null && !voiceName.isBlank()) {
                    command.add("-v");
                    command.add(voiceName);
                }
                command.add(text);
            }
            case NONE -> throw new IOException("No speech engine available on this system");
        }

        String sink = Config.outputSink();
        ProcessBuilder builder = new ProcessBuilder(hostCommand(command, sink))
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD);
        // Outside Flatpak the environment is inherited directly.
        if (!FLATPAK && sink != null && !sink.isBlank()) {
            builder.environment().put("PULSE_SINK", sink);
        }
        builder.start();
    }

    private static String xmlEscape(String raw) {
        return raw.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }

    /** Human-readable hint shown in the UI when no engine is installed. */
    static String installHint() {
        if (WINDOWS || MAC) {
            return "";
        }
        if (FLATPAK) {
            return "Sandboxed: allow host access with"
                + "  flatpak override --user --talk-name=org.freedesktop.Flatpak org.prismlauncher.PrismLauncher";
        }
        return "Optional offline fallback:  sudo apt install espeak-ng";
    }
}
