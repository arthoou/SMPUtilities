package com.arthou.smputilities.core.util;

import java.util.function.Consumer;

/**
 * Tiny logging facade so core code can log without depending on a specific
 * logging library. The platform installs a sink backed by the game logger.
 */
public final class SuLog {
    public interface Sink {
        void info(String message);

        void warn(String message, Throwable error);

        void error(String message, Throwable error);
    }

    private static volatile Sink sink = new Sink() {
        @Override
        public void info(String message) {
            System.out.println("[SMPutilities] " + message);
        }

        @Override
        public void warn(String message, Throwable error) {
            System.err.println("[SMPutilities] WARN " + message);
            if (error != null) {
                error.printStackTrace();
            }
        }

        @Override
        public void error(String message, Throwable error) {
            System.err.println("[SMPutilities] ERROR " + message);
            if (error != null) {
                error.printStackTrace();
            }
        }
    };

    private SuLog() {
    }

    public static void install(Sink newSink) {
        sink = newSink;
    }

    public static void info(String message) {
        sink.info(message);
    }

    public static void warn(String message) {
        sink.warn(message, null);
    }

    public static void warn(String message, Throwable error) {
        sink.warn(message, error);
    }

    public static void error(String message, Throwable error) {
        sink.error(message, error);
    }

    /** Runs an action and logs instead of propagating, for listeners that must never break the caller. */
    public static <T> void guard(String what, T value, Consumer<T> action) {
        try {
            action.accept(value);
        } catch (RuntimeException e) {
            error(what + " failed", e);
        }
    }
}
