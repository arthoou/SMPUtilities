package com.arthou.smputilities.ttsbridge;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * PulseAudio / PipeWire routing.
 *
 * On Windows people use VB-Cable to feed synthesized speech into a game's
 * microphone. The Linux equivalent is a null sink: audio written to the sink
 * shows up on its ".monitor" source, which the game can pick as its mic.
 */
final class Audio {
    /** Sink name we create. Its monitor is what the game selects as microphone. */
    static final String CABLE_SINK = "DuTTS_Cable";
    static final String CABLE_MONITOR = CABLE_SINK + ".monitor";
    /**
     * A real input device fed by the cable's monitor. Many apps hide monitor
     * sources from their microphone list (or show them as "Monitor of ..."),
     * so this is the name the voice chat should pick.
     */
    static final String MIC_SOURCE = "DuTTS_Mic";
    static final String MIC_DESCRIPTION = "DuTTS Microphone";

    private Audio() {
    }

    record Device(String name, String description) {
        @Override
        public String toString() {
            return description == null || description.isBlank() ? name : description;
        }
    }

    /** Runs a command on the host (through flatpak-spawn when sandboxed). */
    private static List<String> run(List<String> command) throws IOException {
        List<String> full = new ArrayList<>();
        if (Tts.FLATPAK) {
            full.add("flatpak-spawn");
            full.add("--host");
        }
        full.addAll(command);

        Process process = new ProcessBuilder(full).redirectErrorStream(true).start();
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        try {
            process.waitFor();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        return lines;
    }

    /** True when the user can pick an output device on this platform. */
    static boolean available() {
        if (Tts.WINDOWS) {
            return !JavaSoundPlayer.outputs().isEmpty();
        }
        if (Tts.MAC) {
            return false;
        }
        try {
            return !run(List.of("pactl", "--version")).isEmpty();
        } catch (IOException exception) {
            return false;
        }
    }

    /** Windows has no user-space virtual device: VB-Cable is a kernel driver. */
    static boolean canCreateCable() {
        return !Tts.WINDOWS && !Tts.MAC;
    }

    /** Playback devices the speech can be sent to. */
    static List<Device> sinks() {
        if (Tts.WINDOWS) {
            return JavaSoundPlayer.outputs();
        }

        List<Device> devices = new ArrayList<>();
        try {
            for (String line : run(List.of("pactl", "list", "short", "sinks"))) {
                String[] parts = line.split("\t");
                if (parts.length >= 2) {
                    devices.add(new Device(parts[1], prettify(parts[1])));
                }
            }
        } catch (IOException ignored) {
            // leave the list empty; the UI shows the failure state
        }
        return devices;
    }

    static boolean cableExists() {
        return sinks().stream().anyMatch(d -> d.name().equals(CABLE_SINK));
    }

    /**
     * Creates the null sink and the microphone on top of it. Returns null on
     * success, or the error output.
     */
    static String createCable() {
        if (!cableExists()) {
            try {
                List<String> out = run(List.of(
                    "pactl", "load-module", "module-null-sink",
                    "sink_name=" + CABLE_SINK,
                    "sink_properties=device.description=DuTTS_Virtual_Cable"));
                if (!cableExists()) {
                    return String.join(" ", out);
                }
            } catch (IOException exception) {
                return exception.getMessage();
            }
        }
        return createMic();
    }

    /** Creates {@link #MIC_SOURCE} from the cable's monitor, if it is missing. */
    static String createMic() {
        if (micExists()) {
            return null;
        }

        // The monitor appears a moment after the sink; remapping it too early fails.
        for (int attempt = 0; attempt < 20 && !monitorExists(); attempt++) {
            try {
                Thread.sleep(100L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (!monitorExists()) {
            return "virtual cable monitor never appeared";
        }

        try {
            List<String> out = run(List.of(
                "pactl", "load-module", "module-remap-source",
                "master=" + CABLE_MONITOR,
                "source_name=" + MIC_SOURCE,
                // This exact quoting is the only form both PulseAudio and
                // pipewire-pulse read as a description containing a space.
                "source_properties=\"device.description=\\\"" + MIC_DESCRIPTION + "\\\"\""));
            return micExists() ? null : String.join(" ", out);
        } catch (IOException exception) {
            return exception.getMessage();
        }
    }

    static boolean micExists() {
        return sourceExists(MIC_SOURCE);
    }

    /** Unloads the microphone and every null sink we created. */
    static void removeCable() {
        try {
            for (String line : run(List.of("pactl", "list", "short", "modules"))) {
                if (line.contains("module-remap-source") && line.contains("source_name=" + MIC_SOURCE)) {
                    run(List.of("pactl", "unload-module", line.split("\t")[0]));
                }
            }
            for (String line : run(List.of("pactl", "list", "short", "modules"))) {
                if (line.contains("module-null-sink") && line.contains(CABLE_SINK)) {
                    String id = line.split("\t")[0];
                    run(List.of("pactl", "unload-module", id));
                }
            }
        } catch (IOException ignored) {
            // nothing else to do; the UI refresh will show it is still there
        }
    }

    /** The sink the desktop currently plays on, or null when it cannot be read. */
    static String defaultSink() {
        try {
            for (String line : run(List.of("pactl", "get-default-sink"))) {
                String name = line.trim();
                if (!name.isEmpty() && !name.equals(CABLE_SINK) && !name.contains(" ")) {
                    return name;
                }
            }
        } catch (IOException ignored) {
            // treated as unknown
        }
        return null;
    }

    // ---------------------------------------------------------- monitoring

    /**
     * A loopback copies whatever is written to the cable onto a real output, so
     * you hear your own TTS while the game still receives it as a microphone.
     */
    static String createLoopback(String destinationSink) {
        removeLoopback();
        if (destinationSink == null || destinationSink.isBlank()) {
            return null;
        }

        // Without the cable, PulseAudio silently falls back to the DEFAULT source,
        // which turns the loopback into "speakers into speakers" - and can leak
        // everything you hear into the microphone. Refuse instead.
        if (!monitorExists()) {
            return "virtual cable is missing - create it first";
        }

        try {
            List<String> out = run(List.of(
                "pactl", "load-module", "module-loopback",
                "source=" + CABLE_MONITOR,
                "sink=" + destinationSink,
                "latency_msec=50",
                // never let the session manager re-route this stream elsewhere
                "source_dont_move=true",
                "sink_dont_move=true"));

            String destination = loopbackDestination();
            if (destination == null) {
                return String.join(" ", out);
            }
            if (!destination.equals(destinationSink)) {
                removeLoopback();
                return "loopback attached to the wrong device, removed";
            }
            return null;
        } catch (IOException exception) {
            return exception.getMessage();
        }
    }

    /** True when the cable's monitor source is actually present. */
    static boolean monitorExists() {
        return sourceExists(CABLE_MONITOR);
    }

    private static boolean sourceExists(String sourceName) {
        try {
            for (String line : run(List.of("pactl", "list", "short", "sources"))) {
                String[] parts = line.split("\t");
                if (parts.length >= 2 && parts[1].equals(sourceName)) {
                    return true;
                }
            }
        } catch (IOException ignored) {
            // treated as missing
        }
        return false;
    }

    static void removeLoopback() {
        try {
            for (String line : run(List.of("pactl", "list", "short", "modules"))) {
                if (line.contains("module-loopback") && line.contains(CABLE_MONITOR)) {
                    run(List.of("pactl", "unload-module", line.split("\t")[0]));
                }
            }
        } catch (IOException ignored) {
            // the UI refresh will still show the real state
        }
    }

    /** Sink currently receiving the monitor copy, or null when off. */
    static String loopbackDestination() {
        try {
            for (String line : run(List.of("pactl", "list", "short", "modules"))) {
                if (line.contains("module-loopback") && line.contains(CABLE_MONITOR)) {
                    for (String token : line.split("\\s+")) {
                        if (token.startsWith("sink=")) {
                            return token.substring(5);
                        }
                    }
                }
            }
        } catch (IOException ignored) {
            // treated as "off"
        }
        return null;
    }

    private static String prettify(String name) {
        if (name.equals(CABLE_SINK)) {
            return "DuTTS Virtual Cable  (use this one)";
        }
        String pretty = name
            .replaceFirst("^alsa_output\\.", "")
            .replaceFirst("^alsa_input\\.", "")
            .replace('_', ' ')
            .replace('-', ' ');
        return pretty.length() > 60 ? pretty.substring(0, 57) + "..." : pretty;
    }
}
