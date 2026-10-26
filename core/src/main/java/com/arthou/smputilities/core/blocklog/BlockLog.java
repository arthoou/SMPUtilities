package com.arthou.smputilities.core.blocklog;

import com.arthou.smputilities.core.util.SuLog;

import java.io.BufferedWriter;
import java.io.IOException;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Who changed which block and what went in and out of which container, on
 * disk: one file per region (512 x 512 blocks) and day, in
 * {@code <world>/smputilities/blocklog/<dimension>/<rx>.<rz>/YYYY-MM-DD.log},
 * a tab-separated line per change. A background thread appends lines and
 * answers lookups; nothing is kept in memory.
 */
public final class BlockLog {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final int REGION_SHIFT = 9;
    private static final int MAX_OPEN = 64;

    private final Path root;
    private final ZoneId zone;
    private final LinkedBlockingQueue<Entry> pending = new LinkedBlockingQueue<>();
    private final Thread writer;
    private final ExecutorService reader = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "SMPutilities-block-log-reader");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean closed;
    private final java.util.concurrent.atomic.AtomicLong added = new java.util.concurrent.atomic.AtomicLong();
    private volatile long written;
    // Writer thread only.
    private final Map<Path, BufferedWriter> open = new LinkedHashMap<>(16, 0.75F, true);

    public BlockLog(Path root, ZoneId zone) {
        this.root = root;
        this.zone = zone;
        writer = new Thread(this::writeLoop, "SMPutilities-block-log");
        writer.setDaemon(true);
        writer.start();
    }

    public void add(Entry entry) {
        if (!closed) {
            added.incrementAndGet();
            pending.add(entry);
        }
    }

    // ------------------------------------------------------------- writing

    private void writeLoop() {
        List<Entry> batch = new ArrayList<>();
        while (true) {
            try {
                Entry first = pending.poll(1, TimeUnit.SECONDS);
                if (first == null) {
                    if (closed) {
                        break;
                    }
                    continue;
                }
                batch.add(first);
                pending.drainTo(batch);
                for (Entry entry : batch) {
                    write(entry);
                }
                flushAll();
                written += batch.size();
                batch.clear();
            } catch (InterruptedException e) {
                break;
            } catch (RuntimeException e) {
                SuLog.warn("Block log could not be written", e);
                batch.clear();
            }
        }
        List<Entry> rest = new ArrayList<>();
        pending.drainTo(rest);
        rest.forEach(this::write);
        closeAll();
    }

    private void write(Entry entry) {
        Path file = file(entry.dimension(), entry.x() >> REGION_SHIFT, entry.z() >> REGION_SHIFT, day(entry.time()));
        try {
            BufferedWriter out = open.get(file);
            if (out == null) {
                Files.createDirectories(file.getParent());
                out = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                open.put(file, out);
                if (open.size() > MAX_OPEN) {
                    Map.Entry<Path, BufferedWriter> eldest = open.entrySet().iterator().next();
                    eldest.getValue().close();
                    open.remove(eldest.getKey());
                }
            }
            out.write(entry.toLine());
            out.write('\n');
        } catch (IOException e) {
            SuLog.warn("Block log could not write " + file + ": " + e.getMessage());
        }
    }

    private void flushAll() {
        for (BufferedWriter out : open.values()) {
            try {
                out.flush();
            } catch (IOException ignored) {
                // Reported when the line is written next time.
            }
        }
    }

    private void closeAll() {
        for (BufferedWriter out : open.values()) {
            try {
                out.close();
            } catch (IOException ignored) {
                // The server is stopping.
            }
        }
        open.clear();
    }

    /** Writes what is queued and stops the threads. */
    public void close() {
        closed = true;
        writer.interrupt();
        try {
            writer.join(10_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        reader.shutdown();
    }

    // ------------------------------------------------------------- reading

    /** Runs a lookup on the reader thread; {@code done} receives the matches, newest first. */
    public void lookup(Query query, Consumer<List<Entry>> done) {
        try {
            reader.execute(() -> done.accept(find(query)));
        } catch (RejectedExecutionException e) {
            done.accept(List.of());
        }
    }

    /** The matches of a query, newest first (call off the server thread). */
    public List<Entry> find(Query query) {
        // Wait until what was logged before the query is on disk.
        long target = added.get();
        long waitUntil = System.currentTimeMillis() + 2000;
        while (written < target && System.currentTimeMillis() < waitUntil) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        List<Entry> found = new ArrayList<>();
        Path dimensionDir = root.resolve(safe(query.dimension()));
        if (!Files.isDirectory(dimensionDir)) {
            return found;
        }
        for (Path regionDir : regions(dimensionDir, query)) {
            for (Path file : days(regionDir, query.since())) {
                try {
                    for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                        Entry entry = Entry.parse(line, query.dimension());
                        if (entry != null && query.matches(entry)) {
                            found.add(entry);
                        }
                    }
                } catch (IOException e) {
                    SuLog.warn("Block log could not read " + file + ": " + e.getMessage());
                }
            }
        }
        found.sort(Comparator.comparingLong(Entry::time).reversed());
        return query.limit() > 0 && found.size() > query.limit() ? new ArrayList<>(found.subList(0, query.limit())) : found;
    }

    private List<Path> regions(Path dimensionDir, Query query) {
        List<Path> result = new ArrayList<>();
        if (query.radius() >= 0) {
            int minX = (query.x() - query.radius()) >> REGION_SHIFT;
            int maxX = (query.x() + query.radius()) >> REGION_SHIFT;
            int minZ = (query.z() - query.radius()) >> REGION_SHIFT;
            int maxZ = (query.z() + query.radius()) >> REGION_SHIFT;
            for (int rx = minX; rx <= maxX; rx++) {
                for (int rz = minZ; rz <= maxZ; rz++) {
                    Path dir = dimensionDir.resolve(rx + "." + rz);
                    if (Files.isDirectory(dir)) {
                        result.add(dir);
                    }
                }
            }
            return result;
        }
        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(dimensionDir)) {
            for (Path dir : dirs) {
                if (Files.isDirectory(dir)) {
                    result.add(dir);
                }
            }
        } catch (IOException e) {
            SuLog.warn("Block log could not list " + dimensionDir + ": " + e.getMessage());
        }
        return result;
    }

    private List<Path> days(Path regionDir, long since) {
        List<Path> result = new ArrayList<>();
        LocalDate first = since <= 0 ? LocalDate.MIN : Instant.ofEpochMilli(since).atZone(zone).toLocalDate();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(regionDir, "*.log")) {
            for (Path file : files) {
                LocalDate date = parseDay(file);
                if (date != null && !date.isBefore(first)) {
                    result.add(file);
                }
            }
        } catch (IOException e) {
            SuLog.warn("Block log could not list " + regionDir + ": " + e.getMessage());
        }
        return result;
    }

    /** Deletes the days older than {@code keepDays}. */
    public void prune(int keepDays) {
        if (keepDays <= 0 || !Files.isDirectory(root)) {
            return;
        }
        LocalDate oldest = LocalDate.now(zone).minusDays(keepDays);
        try (DirectoryStream<Path> dimensions = Files.newDirectoryStream(root)) {
            for (Path dimension : dimensions) {
                if (!Files.isDirectory(dimension)) {
                    continue;
                }
                try (DirectoryStream<Path> regions = Files.newDirectoryStream(dimension)) {
                    for (Path region : regions) {
                        if (!Files.isDirectory(region)) {
                            continue;
                        }
                        try (DirectoryStream<Path> files = Files.newDirectoryStream(region, "*.log")) {
                            for (Path file : files) {
                                LocalDate date = parseDay(file);
                                if (date != null && date.isBefore(oldest)) {
                                    Files.deleteIfExists(file);
                                }
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            SuLog.warn("Block log could not remove old days: " + e.getMessage());
        }
    }

    private static LocalDate parseDay(Path file) {
        String name = file.getFileName().toString();
        try {
            return LocalDate.parse(name.substring(0, name.length() - ".log".length()), DAY);
        } catch (DateTimeParseException | StringIndexOutOfBoundsException e) {
            return null;
        }
    }

    private String day(long time) {
        return DAY.format(Instant.ofEpochMilli(time).atZone(zone).toLocalDate());
    }

    private Path file(String dimension, int rx, int rz, String day) {
        return root.resolve(safe(dimension)).resolve(rx + "." + rz).resolve(day + ".log");
    }

    /** "minecraft:the_nether" -> "minecraft_the_nether". */
    static String safe(String dimension) {
        StringBuilder out = new StringBuilder();
        for (char c : dimension.toLowerCase(Locale.ROOT).toCharArray()) {
            out.append(c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_' || c == '-' ? c : '_');
        }
        return out.toString();
    }

    // -------------------------------------------------------------- records

    public enum Action {
        /** A player broke a block. */
        BREAK,
        /** A player placed a block or emptied a bucket. */
        PLACE,
        /** An explosion destroyed a block. */
        EXPLODE,
        /** A player put items into a container. */
        ADD,
        /** A player took items out of a container. */
        TAKE;

        public boolean block() {
            return this == BREAK || this == PLACE || this == EXPLODE;
        }
    }

    /**
     * One change. For blocks {@code before} and {@code after} are block states
     * as text and {@code data} the block entity before the change; for
     * containers {@code before} is the item and {@code count} how many.
     * {@code actor} is a player's UUID, or "#explosion" and the like.
     */
    public record Entry(long time, Action action, String actor, String actorName, String dimension, int x, int y, int z,
                        String before, String after, String data, int count) {

        String toLine() {
            return time + "\t" + action.name() + "\t" + escape(actor) + "\t" + escape(actorName) + "\t" + x + "\t" + y + "\t" + z
                + "\t" + escape(before) + "\t" + escape(after) + "\t" + escape(data) + "\t" + count;
        }

        static Entry parse(String line, String dimension) {
            String[] parts = line.split("\t", -1);
            if (parts.length < 11) {
                return null;
            }
            try {
                return new Entry(Long.parseLong(parts[0]), Action.valueOf(parts[1]), unescape(parts[2]), unescape(parts[3]), dimension,
                    Integer.parseInt(parts[4]), Integer.parseInt(parts[5]), Integer.parseInt(parts[6]),
                    unescape(parts[7]), unescape(parts[8]), unescape(parts[9]), Integer.parseInt(parts[10]));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    /**
     * What to look for: one dimension, around a point ({@code radius} -1 for
     * the whole dimension, 0 for one block), since a time, by some actors and
     * of some actions (null for all).
     */
    public record Query(String dimension, int x, int y, int z, int radius, long since, java.util.Set<String> actors,
                        java.util.Set<Action> actions, int limit) {

        boolean matches(Entry entry) {
            if (entry.time() < since) {
                return false;
            }
            if (radius >= 0 && (Math.abs(entry.x() - x) > radius || Math.abs(entry.y() - y) > radius || Math.abs(entry.z() - z) > radius)) {
                return false;
            }
            if (actors != null && !actors.contains(entry.actor())) {
                return false;
            }
            return actions == null || actions.contains(entry.action());
        }
    }

    // ------------------------------------------------------------- rollback

    /** Setting one block back: to {@code state} with {@code data}, if it still is {@code expected}. */
    public record BlockChange(int x, int y, int z, String state, String data, String expected) {
    }

    /** Putting items back into a container ({@code add}) or taking them out again. */
    public record ItemChange(int x, int y, int z, String item, int count, boolean add) {
    }

    /**
     * Undoes the changes found: every block goes back to how it was before
     * the first change found at it, if it still is as the last one left it;
     * items taken are put back and items added are taken out.
     */
    public static Plan rollback(List<Entry> entries) {
        return plan(entries, true);
    }

    /** Redoes the changes found (after a rollback). */
    public static Plan restore(List<Entry> entries) {
        return plan(entries, false);
    }

    private static Plan plan(List<Entry> entries, boolean undo) {
        Map<Long, Entry> first = new HashMap<>();
        Map<Long, Entry> last = new HashMap<>();
        List<ItemChange> items = new ArrayList<>();
        List<Entry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparingLong(Entry::time));
        for (Entry entry : sorted) {
            if (entry.action().block()) {
                long key = key(entry.x(), entry.y(), entry.z());
                first.putIfAbsent(key, entry);
                last.put(key, entry);
            } else {
                boolean add = entry.action() == Action.TAKE == undo;
                items.add(new ItemChange(entry.x(), entry.y(), entry.z(), entry.before(), entry.count(), add));
            }
        }
        List<BlockChange> blocks = new ArrayList<>();
        for (Map.Entry<Long, Entry> position : first.entrySet()) {
            Entry oldest = position.getValue();
            Entry newest = last.get(position.getKey());
            blocks.add(undo
                ? new BlockChange(oldest.x(), oldest.y(), oldest.z(), oldest.before(), oldest.data(), newest.after())
                : new BlockChange(newest.x(), newest.y(), newest.z(), newest.after(), "", oldest.before()));
        }
        // Lowest first, so blocks that need support (sand, torches) find it.
        blocks.sort(Comparator.comparingInt(BlockChange::y).thenComparingInt(BlockChange::x).thenComparingInt(BlockChange::z));
        return new Plan(blocks, items);
    }

    public record Plan(List<BlockChange> blocks, List<ItemChange> items) {
        public boolean isEmpty() {
            return blocks.isEmpty() && items.isEmpty();
        }
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    // ---------------------------------------------------------------- text

    static String escape(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '\t' -> out.append("\\t");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    static String unescape(String text) {
        if (text.indexOf('\\') < 0) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length()) {
                char next = text.charAt(++i);
                out.append(switch (next) {
                    case 't' -> '\t';
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    default -> next;
                });
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
