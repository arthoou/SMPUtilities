package com.arthou.smputilities.core.chat;

import com.arthou.smputilities.core.util.SuLog;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * The whole public chat on disk, one file per day in
 * {@code <world>/smputilities/chat/YYYY-MM-DD.log}, a tab-separated line per
 * message. Nothing is kept in memory: a background thread appends the lines,
 * and pages are read from the end of a file only when staff ask for them.
 */
public final class ChatHistory {
    /** A message; {@code offset} is where its line starts in the day's file, the cursor for older pages. */
    public record Line(long offset, long time, String sender, String name, String text) {
    }

    private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final int CHUNK = 8192;
    /** Stops a page read at this many bytes, however long the lines are. */
    private static final int MAX_WINDOW = 1 << 20;

    private final Path directory;
    private final ZoneId zone;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "SMPutilities-chat-history");
        thread.setDaemon(true);
        return thread;
    });
    // Writer thread only.
    private BufferedWriter out;
    private String outDay;

    public ChatHistory(Path directory, ZoneId zone) {
        this.directory = directory;
        this.zone = zone;
    }

    public String today() {
        return DAY.format(LocalDate.now(zone));
    }

    public void append(long time, UUID sender, String name, String text) {
        String line = time + "\t" + (sender == null ? "" : sender) + "\t" + clean(name) + "\t" + escape(text);
        String day = DAY.format(Instant.ofEpochMilli(time).atZone(zone).toLocalDate());
        writer.execute(() -> write(day, line));
    }

    /**
     * Up to {@code limit} messages of a day, newest first, all older than the
     * line starting at {@code before} ({@code -1} for the newest).
     */
    public List<Line> read(String day, long before, int limit) {
        Path file = file(day);
        if (file == null || !Files.isRegularFile(file) || limit <= 0) {
            return List.of();
        }
        try (RandomAccessFile in = new RandomAccessFile(file.toFile(), "r")) {
            long end = before < 0 ? in.length() : Math.min(before, in.length());
            int window = Math.min(CHUNK, (int) end);
            byte[] bytes;
            long start;
            while (true) {
                start = end - window;
                bytes = new byte[window];
                in.seek(start);
                in.readFully(bytes);
                if (start == 0 || window >= MAX_WINDOW || count(bytes, (byte) '\n') > limit) {
                    break;
                }
                window = (int) Math.min(end, Math.min(MAX_WINDOW, window * 2L));
            }
            return lines(bytes, start, limit);
        } catch (IOException e) {
            SuLog.warn("Could not read chat history " + file, e);
            return List.of();
        }
    }

    /** {@link #read} on the history's thread, after every line appended so far; the callback runs there too. */
    public void readAsync(String day, long before, int limit, Consumer<List<Line>> callback) {
        try {
            writer.execute(() -> callback.accept(read(day, before, limit)));
        } catch (RejectedExecutionException e) {
            callback.accept(List.of());
        }
    }

    /** Days with a history file, newest first. */
    public List<String> days(int limit) {
        List<String> days = new ArrayList<>();
        if (!Files.isDirectory(directory)) {
            return days;
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*.log")) {
            for (Path file : files) {
                String name = file.getFileName().toString();
                String day = name.substring(0, name.length() - 4);
                if (file(day) != null) {
                    days.add(day);
                }
            }
        } catch (IOException e) {
            SuLog.warn("Could not list chat history", e);
        }
        days.sort(Collections.reverseOrder());
        return days.size() > limit ? new ArrayList<>(days.subList(0, limit)) : days;
    }

    /** Deletes days older than the retention period (0 keeps everything). */
    public void prune(int retentionDays) {
        if (retentionDays <= 0) {
            return;
        }
        String oldest = DAY.format(LocalDate.now(zone).minusDays(retentionDays));
        writer.execute(() -> {
            for (String day : days(Integer.MAX_VALUE)) {
                if (day.compareTo(oldest) < 0) {
                    try {
                        Files.deleteIfExists(directory.resolve(day + ".log"));
                    } catch (IOException e) {
                        SuLog.warn("Could not prune chat history " + day, e);
                    }
                }
            }
        });
    }

    public void close() {
        writer.execute(this::closeFile);
        writer.shutdown();
        try {
            writer.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void write(String day, String line) {
        try {
            if (!day.equals(outDay)) {
                closeFile();
                Files.createDirectories(directory);
                out = Files.newBufferedWriter(directory.resolve(day + ".log"), StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
                outDay = day;
            }
            out.write(line);
            out.write('\n');
            out.flush();
        } catch (IOException e) {
            SuLog.error("Could not write chat history", e);
            closeFile();
        }
    }

    private void closeFile() {
        if (out != null) {
            try {
                out.close();
            } catch (IOException ignored) {
                // nothing left to save
            }
        }
        out = null;
        outDay = null;
    }

    /** The day's file, or null for anything that is not a date (no paths from the client). */
    private Path file(String day) {
        try {
            return directory.resolve(DAY.format(LocalDate.parse(day, DAY)) + ".log");
        } catch (DateTimeParseException | NullPointerException e) {
            return null;
        }
    }

    /** Complete lines in a window of the file, newest first; a cut first line and an unfinished last one are skipped. */
    static List<Line> lines(byte[] bytes, long start, int limit) {
        List<Line> result = new ArrayList<>();
        int end = bytes.length;
        // Only lines that end in a newline are complete (the writer may be mid-line).
        while (end > 0 && bytes[end - 1] != '\n') {
            end--;
        }
        int lineEnd = end - 1;
        while (lineEnd >= 0 && result.size() < limit) {
            int lineStart = lineEnd;
            while (lineStart > 0 && bytes[lineStart - 1] != '\n') {
                lineStart--;
            }
            if (lineStart == 0 && start > 0) {
                break;
            }
            Line line = parse(start + lineStart, new String(bytes, lineStart, lineEnd - lineStart, StandardCharsets.UTF_8));
            if (line != null) {
                result.add(line);
            }
            lineEnd = lineStart - 1;
        }
        return result;
    }

    private static Line parse(long offset, String text) {
        String[] parts = text.split("\t", 4);
        if (parts.length < 4) {
            return null;
        }
        try {
            return new Line(offset, Long.parseLong(parts[0]), parts[1], parts[2], unescape(parts[3]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int count(byte[] bytes, byte value) {
        int count = 0;
        for (byte b : bytes) {
            if (b == value) {
                count++;
            }
        }
        return count;
    }

    private static String clean(String name) {
        return name == null ? "" : name.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    static String escape(String text) {
        StringBuilder result = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\\' -> result.append("\\\\");
                case '\t' -> result.append("\\t");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                default -> result.append(c);
            }
        }
        return result.toString();
    }

    static String unescape(String text) {
        StringBuilder result = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length()) {
                char next = text.charAt(++i);
                result.append(switch (next) {
                    case 't' -> '\t';
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    default -> next;
                });
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
}
