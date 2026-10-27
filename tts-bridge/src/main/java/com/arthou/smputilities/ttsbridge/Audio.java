package com.arthou.smputilities.ttsbridge;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Where the voice is played, per system.
 *
 * Linux: the app creates the cable itself with PulseAudio / PipeWire, a null
 * sink whose ".monitor" source feeds a microphone the game can pick.
 * Windows and macOS have no user-space virtual device, so a driver provides
 * the cable and Java Sound plays the voice into it; the voice chat then picks
 * the driver's input. Windows uses VB-Cable; macOS takes VB-Cable (the
 * default) or BlackHole, whichever is installed or chosen.
 */
final class Audio {
    /** Sink name we create. Its monitor is what the game selects as microphone. */
    static final String CABLE_SINK = "SMPutilities_TTS_Cable";
    static final String CABLE_MONITOR = CABLE_SINK + ".monitor";
    /**
     * A real input device fed by the cable's monitor. Many apps hide monitor
     * sources from their microphone list (or show them as "Monitor of ..."),
     * so this is the name the voice chat should pick.
     */
    static final String MIC_SOURCE = "SMPutilities_TTS_Mic";
    static final String MIC_DESCRIPTION = "SMPutilities TTS Microphone";
    /** What the cable and microphone were called before; removing the cable also removes these. */
    private static final String LEGACY_CABLE_SINK = "DuTTS_Cable";
    private static final String LEGACY_MIC_SOURCE = "DuTTS_Mic";

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

    /** Windows and macOS: the cable comes from a driver and the voice is played with Java Sound. */
    static boolean usesDriverCable() {
        return Tts.WINDOWS || Tts.MAC;
    }

    static final String BLACKHOLE_LINK = "existential.audio/blackhole";
    static final String BLACKHOLE_URL = "https://existential.audio/blackhole/";
    static final String VB_CABLE_LINK = "vb-audio.com/Cable";
    static final String VB_CABLE_URL = "https://vb-audio.com/Cable/";

    private static boolean isVbCable(String device) {
        String name = device == null ? "" : device.toLowerCase(Locale.ROOT);
        return name.contains("vb-cable") || name.contains("vb-audio");
    }

    private static boolean isBlackHole(String device) {
        return device != null && device.toLowerCase(Locale.ROOT).contains("blackhole");
    }

    /**
     * macOS takes either driver: the one the voice is set to, else VB-Cable when
     * it is installed (the default), else BlackHole when only that one is.
     */
    private static boolean macUsesBlackHole() {
        if (!Tts.MAC) {
            return false;
        }
        String chosen = Config.outputSink();
        if (isBlackHole(chosen) || isVbCable(chosen)) {
            return isBlackHole(chosen);
        }
        List<Device> outputs = sinks();
        return outputs.stream().noneMatch(d -> isVbCable(d.name())) && outputs.stream().anyMatch(d -> isBlackHole(d.name()));
    }

    /** The driver that provides the cable on this system. */
    static String driverName() {
        return macUsesBlackHole() ? "BlackHole" : "VB-Cable";
    }

    static String driverLink() {
        return macUsesBlackHole() ? BLACKHOLE_LINK : VB_CABLE_LINK;
    }

    static String driverUrl() {
        return macUsesBlackHole() ? BLACKHOLE_URL : VB_CABLE_URL;
    }

    /** The driver's playback device: where the voice goes. */
    static String driverOutput() {
        if (Tts.MAC) {
            return macUsesBlackHole() ? "BlackHole 2ch" : "VB-Cable";
        }
        return "CABLE Input";
    }

    /** The driver's recording device: what the voice chat picks as microphone. */
    static String driverMicrophone() {
        if (Tts.MAC) {
            return macUsesBlackHole() ? "BlackHole 2ch" : "VB-Cable";
        }
        return "CABLE Output (VB-Audio Virtual Cable)";
    }

    /** True for the device the voice should be played on: our Linux cable or the driver's input. */
    static boolean isCable(String device) {
        if (device == null || device.isBlank()) {
            return false;
        }
        if (Tts.MAC) {
            return isVbCable(device) || isBlackHole(device);
        }
        if (Tts.WINDOWS) {
            return device.contains("CABLE Input");
        }
        return device.equals(CABLE_SINK);
    }

    /** True when the user can pick an output device on this platform. */
    static boolean available() {
        if (usesDriverCable()) {
            return !JavaSoundPlayer.outputs().isEmpty();
        }
        try {
            return !run(List.of("pactl", "--version")).isEmpty();
        } catch (IOException exception) {
            return false;
        }
    }

    /** Only Linux can create the cable; Windows and macOS need the driver installed. */
    static boolean canCreateCable() {
        return !usesDriverCable();
    }

    /** Playback devices the speech can be sent to. */
    static List<Device> sinks() {
        if (usesDriverCable()) {
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
        return sinks().stream().anyMatch(d -> isCable(d.name()));
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
                    "sink_properties=device.description=SMPutilities_TTS_Cable"));
                keepSystemDefaults();
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
                // Single quotes around the list, double quotes around the value:
                // PulseAudio rejects escaped quotes ("Invalid properties"), and
                // pipewire-pulse reads this form too.
                "source_properties='device.description=\"" + MIC_DESCRIPTION + "\"'"));
            keepSystemDefaults();
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
                if (line.contains("module-remap-source")
                    && (line.contains("source_name=" + MIC_SOURCE) || line.contains("source_name=" + LEGACY_MIC_SOURCE))) {
                    run(List.of("pactl", "unload-module", line.split("\t")[0]));
                }
            }
            for (String line : run(List.of("pactl", "list", "short", "modules"))) {
                if (line.contains("module-null-sink") && (line.contains(CABLE_SINK) || line.contains(LEGACY_CABLE_SINK))) {
                    String id = line.split("\t")[0];
                    run(List.of("pactl", "unload-module", id));
                }
            }
        } catch (IOException ignored) {
            // nothing else to do; the UI refresh will show it is still there
        }
    }

    // ------------------------------------------------ the desktop's own devices

    /** The sink the desktop currently plays on (never the cable), or null when it cannot be read. */
    static String defaultSink() {
        String sink = defaultDevice("sink");
        return isOurs(sink) ? null : sink;
    }

    /** The default "sink" or "source", or null when it cannot be read. */
    private static String defaultDevice(String kind) {
        try {
            // pactl 15+ and pipewire-pulse; older pactl only has "info", in English with LC_ALL=C.
            for (String line : run(List.of("pactl", "get-default-" + kind))) {
                String name = line.trim();
                if (!name.isEmpty() && !name.contains(" ")) {
                    return name;
                }
            }
            String label = "Default " + Character.toUpperCase(kind.charAt(0)) + kind.substring(1) + ":";
            for (String line : run(List.of("env", "LC_ALL=C", "pactl", "info"))) {
                if (line.startsWith(label)) {
                    String name = line.substring(label.length()).trim();
                    return name.isEmpty() ? null : name;
                }
            }
        } catch (IOException ignored) {
            // treated as unknown
        }
        return null;
    }

    /** The cable, its monitor and the microphone, under their current and old names. */
    private static boolean isOurs(String device) {
        return device != null && (device.equals(CABLE_SINK) || device.equals(CABLE_MONITOR) || device.equals(MIC_SOURCE)
            || device.equals(LEGACY_CABLE_SINK) || device.equals(LEGACY_CABLE_SINK + ".monitor") || device.equals(LEGACY_MIC_SOURCE));
    }

    private static boolean deviceExists(String kind, String name) {
        try {
            for (String line : run(List.of("pactl", "list", "short", kind + "s"))) {
                String[] parts = line.split("\t");
                if (parts.length >= 2 && parts[1].equals(name)) {
                    return true;
                }
            }
        } catch (IOException ignored) {
            // treated as missing
        }
        return false;
    }

    /**
     * Saves the desktop's default output and input before the app adds anything,
     * so closing it can put them back. A default that is already ours was left by
     * a crash: the one saved last time stays.
     */
    static void rememberSystemDefaults() {
        String sink = defaultDevice("sink");
        if (sink != null && !isOurs(sink)) {
            Config.setSystemSink(sink);
        }
        String source = defaultDevice("source");
        if (source != null && !isOurs(source)) {
            Config.setSystemSource(source);
        }
    }

    /**
     * The cable and the microphone are only for the game: if the sound server
     * made one of them the desktop's default (PipeWire can, for a new device),
     * the saved default goes back.
     */
    static void keepSystemDefaults() {
        putBackDefault("sink", Config.systemSink(), false);
        putBackDefault("source", Config.systemSource(), false);
    }

    private static void putBackDefault(String kind, String saved, boolean wasOurs) {
        if (saved.isBlank() || !(wasOurs || isOurs(defaultDevice(kind))) || !deviceExists(kind, saved)) {
            return;
        }
        try {
            run(List.of("pactl", "set-default-" + kind, saved));
        } catch (IOException ignored) {
            // the sound server keeps the default it picked
        }
    }

    /**
     * Closing the app: everything it added to the sound system goes away (the
     * monitor copy, the microphone and the cable) and the desktop's default
     * output and input are the ones it had before.
     */
    static void restoreSystem() {
        boolean sinkWasOurs = isOurs(defaultDevice("sink"));
        boolean sourceWasOurs = isOurs(defaultDevice("source"));
        removeLoopback();
        removeCable();
        putBackDefault("sink", Config.systemSink(), sinkWasOurs);
        putBackDefault("source", Config.systemSource(), sourceWasOurs);
    }

    /**
     * True while something records the virtual microphone (a voice chat,
     * Discord). Removing it moves that recording to the default microphone:
     * the real one.
     */
    static boolean micInUse() {
        try {
            List<String> ours = new ArrayList<>();
            for (String line : run(List.of("pactl", "list", "short", "sources"))) {
                String[] parts = line.split("\t");
                if (parts.length >= 2 && (parts[1].equals(MIC_SOURCE) || parts[1].equals(LEGACY_MIC_SOURCE))) {
                    ours.add(parts[0]);
                }
            }
            for (String line : run(List.of("pactl", "list", "short", "source-outputs"))) {
                String[] parts = line.split("\t");
                if (parts.length >= 2 && ours.contains(parts[1])) {
                    return true;
                }
            }
        } catch (IOException ignored) {
            // treated as unused
        }
        return false;
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
            return "SMPutilities TTS Cable  " + I18n.t("(use this one)", "(use este)");
        }
        String pretty = name
            .replaceFirst("^alsa_output\\.", "")
            .replaceFirst("^alsa_input\\.", "")
            .replace('_', ' ')
            .replace('-', ' ');
        return pretty.length() > 60 ? pretty.substring(0, 57) + "..." : pretty;
    }
}
