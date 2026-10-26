package com.arthou.smputilities.core.discord;

import com.arthou.smputilities.core.util.SuLog;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Posts lines to a Discord webhook from a background thread. Lines that
 * arrive close together go out as one message (at most every two seconds,
 * within Discord's 2000 characters), mentions are never pinged, and when
 * Discord asks to slow down the thread waits as long as it says.
 */
public final class DiscordWebhook {
    /** Sends one message; returns the HTTP status and, for 429, the seconds to wait. */
    public interface Sender {
        Response post(String url, String json) throws IOException, InterruptedException;
    }

    public record Response(int status, double retryAfterSeconds) {
    }

    public static final int MAX_CONTENT = 2000;
    private static final long BATCH_MILLIS = 2000;
    private static final int MAX_QUEUE = 2000;
    private static final Pattern WEBHOOK = Pattern.compile("^https://(?:(?:ptb|canary)\\.)?discord(?:app)?\\.com/api/webhooks/\\d+/[\\w-]+$");
    private static final Pattern RETRY_AFTER = Pattern.compile("\"retry_after\"\\s*:\\s*([0-9.]+)");

    private final String url;
    private final String username;
    private final Sender sender;
    private final LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>(MAX_QUEUE);
    private final Thread thread;
    private volatile boolean closed;

    public DiscordWebhook(String url, String username, Sender sender) {
        if (!validUrl(url)) {
            throw new IllegalArgumentException("not a Discord webhook URL");
        }
        this.url = url.trim();
        this.username = username == null ? "" : username;
        this.sender = sender == null ? httpSender() : sender;
        thread = new Thread(this::run, "SMPutilities-discord");
        thread.setDaemon(true);
        thread.start();
    }

    /** Only https URLs of Discord's own webhook API are used, so a typo cannot send chat elsewhere. */
    public static boolean validUrl(String url) {
        return url != null && WEBHOOK.matcher(url.trim()).matches();
    }

    /** Queues a line; dropped when the queue is full (Discord is down or very slow). */
    public void send(String line) {
        if (!closed && line != null && !line.isBlank()) {
            queue.offer(line);
        }
    }

    /** Sends what is queued (waiting up to a few seconds) and stops. */
    public void close() {
        closed = true;
        thread.interrupt();
        try {
            thread.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void run() {
        List<String> lines = new ArrayList<>();
        while (true) {
            try {
                String first = queue.poll(1, TimeUnit.SECONDS);
                if (first == null) {
                    if (closed) {
                        return;
                    }
                    continue;
                }
                lines.add(first);
                if (!closed) {
                    Thread.sleep(BATCH_MILLIS);
                }
            } catch (InterruptedException e) {
                // Closing: send what is queued, once.
                closed = true;
            }
            queue.drainTo(lines);
            for (String message : batch(lines, MAX_CONTENT)) {
                if (!post(message)) {
                    break;
                }
            }
            lines.clear();
            if (closed && queue.isEmpty()) {
                return;
            }
        }
    }

    /** @return false to give up on the rest of this batch */
    private boolean post(String message) {
        String json = json(message, username);
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                Response response = sender.post(url, json);
                if (response.status() == 429) {
                    Thread.sleep((long) Math.min(30_000, Math.max(500, response.retryAfterSeconds() * 1000)));
                    continue;
                }
                if (response.status() >= 400) {
                    SuLog.warn("Discord webhook answered " + response.status() + "; check discord.*WebhookUrl");
                    return response.status() < 500;
                }
                return true;
            } catch (IOException e) {
                SuLog.warn("Discord webhook could not be reached: " + e.getMessage());
                return false;
            } catch (InterruptedException e) {
                closed = true;
                return false;
            }
        }
        return true;
    }

    /** Lines joined into messages of at most {@code max} characters; longer lines are cut. */
    public static List<String> batch(List<String> lines, int max) {
        List<String> messages = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String raw : lines) {
            String line = raw.length() > max ? raw.substring(0, max - 1) + "…" : raw;
            if (current.length() > 0 && current.length() + 1 + line.length() > max) {
                messages.add(current.toString());
                current.setLength(0);
            }
            if (current.length() > 0) {
                current.append('\n');
            }
            current.append(line);
        }
        if (current.length() > 0) {
            messages.add(current.toString());
        }
        return messages;
    }

    /** The request body; "allowed_mentions" keeps @everyone, @here and user mentions from pinging. */
    public static String json(String content, String username) {
        StringBuilder out = new StringBuilder("{\"content\":").append(quote(content));
        if (username != null && !username.isBlank()) {
            out.append(",\"username\":").append(quote(username.length() > 80 ? username.substring(0, 80) : username));
        }
        return out.append(",\"allowed_mentions\":{\"parse\":[]}}").toString();
    }

    static String quote(String text) {
        StringBuilder out = new StringBuilder(text.length() + 2).append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    /** Markdown and mention characters escaped, so a player's text shows as typed. */
    public static String escape(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ("\\*_~`|>#@[]()-".indexOf(c) >= 0) {
                out.append('\\');
            }
            out.append(c);
        }
        return out.toString();
    }

    private static Sender httpSender() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
        return (target, json) -> {
            HttpRequest request = HttpRequest.newBuilder(URI.create(target))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("User-Agent", "SMPutilities")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            double retry = 0;
            if (response.statusCode() == 429) {
                Matcher matcher = RETRY_AFTER.matcher(response.body());
                retry = matcher.find() ? Double.parseDouble(matcher.group(1)) : 2;
            }
            return new Response(response.statusCode(), retry);
        };
    }
}
