package com.arthou.smputilities.ttsbridge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Persists the chosen output device next to the jar, so it survives restarts. */
final class Config {
    private static final Path FILE = Path.of("smputilities-voice-bridge.properties");
    private static final String KEY_SINK = "output.sink";
    private static final String KEY_MONITOR = "monitor.sink";
    private static final String KEY_SYSTEM_SINK = "system.sink";
    private static final String KEY_SYSTEM_SOURCE = "system.source";
    private static final Properties PROPS = new Properties();

    static {
        if (Files.isReadable(FILE)) {
            try (var in = Files.newInputStream(FILE)) {
                PROPS.load(in);
            } catch (IOException ignored) {
                // start from defaults when the file is unreadable
            }
        }
    }

    private Config() {
    }

    /** Empty means "system default device". */
    static String outputSink() {
        return PROPS.getProperty(KEY_SINK, "");
    }

    /** Blank is stored too, so "system default" stays a choice and not "never asked". */
    static void setOutputSink(String sink) {
        PROPS.setProperty(KEY_SINK, sink == null ? "" : sink.trim());
        save();
    }

    static boolean hasOutputSink() {
        return PROPS.containsKey(KEY_SINK);
    }

    /** Empty means "do not play it on my speakers". */
    static String monitorSink() {
        return PROPS.getProperty(KEY_MONITOR, "");
    }

    static void setMonitorSink(String sink) {
        PROPS.setProperty(KEY_MONITOR, sink == null ? "" : sink.trim());
        save();
    }

    static boolean hasMonitorSink() {
        return PROPS.containsKey(KEY_MONITOR);
    }

    /** Linux: the desktop's default output before the app added its cable; put back on close. */
    static String systemSink() {
        return PROPS.getProperty(KEY_SYSTEM_SINK, "");
    }

    static void setSystemSink(String sink) {
        PROPS.setProperty(KEY_SYSTEM_SINK, sink.trim());
        save();
    }

    /** Linux: the desktop's default microphone before the app added its own. */
    static String systemSource() {
        return PROPS.getProperty(KEY_SYSTEM_SOURCE, "");
    }

    static void setSystemSource(String source) {
        PROPS.setProperty(KEY_SYSTEM_SOURCE, source.trim());
        save();
    }

    private static void save() {
        try (var out = Files.newOutputStream(FILE)) {
            PROPS.store(out, "SMPutilities Voice Bridge settings");
        } catch (IOException ignored) {
            // a failed save only means the choice is not remembered
        }
    }
}
