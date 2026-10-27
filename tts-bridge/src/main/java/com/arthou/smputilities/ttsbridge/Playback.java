package com.arthou.smputilities.ttsbridge;

import javax.sound.sampled.SourceDataLine;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What is playing right now: the players and speech engines we started and the
 * Java Sound lines we opened. Closing the app stops them at once; a phrase left
 * playing would outlive the app, and on Linux move to the speakers once the
 * cable it plays into is removed.
 */
final class Playback {
    private static final Set<Process> PROCESSES = ConcurrentHashMap.newKeySet();
    private static final Set<SourceDataLine> LINES = ConcurrentHashMap.newKeySet();

    private Playback() {
    }

    static Process track(Process process) {
        PROCESSES.add(process);
        process.onExit().thenRun(() -> PROCESSES.remove(process));
        return process;
    }

    static SourceDataLine track(SourceDataLine line) {
        LINES.add(line);
        return line;
    }

    static void untrack(SourceDataLine line) {
        LINES.remove(line);
    }

    static void stopAll() {
        for (Process process : PROCESSES) {
            process.destroy();
        }
        for (SourceDataLine line : LINES) {
            try {
                line.stop();
                line.flush();
                line.close();
            } catch (Exception ignored) {
                // the line goes away with the process anyway
            }
        }
    }
}
