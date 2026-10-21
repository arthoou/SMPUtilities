package com.arthou.smputilities.ttsbridge;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Client for the ttstool.com backend, which is what the DUSMP voice names refer
 * to ("Amazon US English (Ivy)", "Microsoft Spanish (Pablo)", ...). These are
 * cloud voices, not anything installed locally.
 *
 * Two calls, mirroring what the site itself does:
 *   POST /ttstool/createParts   [{voiceId, ssml}]  -> ["id"]
 *   GET  /ttstool/getParts?q=id                    -> audio/mpeg
 */
final class RemoteTts {
    private static final String HOST = "https://support.readaloud.app";
    private static final URI VOICES_URI = URI.create(HOST + "/read-aloud/list-voices/premium");
    private static final URI CREATE_URI = URI.create(HOST + "/ttstool/createParts");
    private static final String DEFAULT_LANG = "en-US";

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    /** voiceName -> language code, filled lazily from the service. */
    private static volatile Map<String, String> voiceLanguages;

    private RemoteTts() {
    }

    static boolean reachable() {
        try {
            return !languages().isEmpty();
        } catch (IOException exception) {
            return false;
        }
    }

    static int voiceCount() {
        try {
            return languages().size();
        } catch (IOException exception) {
            return 0;
        }
    }

    private static Map<String, String> languages() throws IOException {
        Map<String, String> cached = voiceLanguages;
        if (cached != null) {
            return cached;
        }

        synchronized (RemoteTts.class) {
            if (voiceLanguages != null) {
                return voiceLanguages;
            }
            Map<String, String> parsed = new HashMap<>();
            String body = get(VOICES_URI);
            // Entries look like {"voiceName":"...","lang":"en-US","gender":"female"}
            int at = 0;
            while ((at = body.indexOf("\"voiceName\"", at)) >= 0) {
                String name = Json.string(body.substring(at, Math.min(body.length(), at + 400)), "voiceName");
                String lang = Json.string(body.substring(at, Math.min(body.length(), at + 400)), "lang");
                if (!name.isBlank()) {
                    parsed.put(name, lang.isBlank() ? DEFAULT_LANG : lang);
                }
                at += 11;
            }
            voiceLanguages = parsed;
            return parsed;
        }
    }

    private static final String DEFAULT_VOICE = "Amazon US English (Ivy)";

    /**
     * Language is derived from the voice name itself rather than the voice list,
     * so synthesis keeps working even if the list endpoint is unavailable. This
     * matches what the original Windows bridge did.
     */
    private static String languageOf(String voiceName) {
        String name = voiceName.toLowerCase(java.util.Locale.ROOT);
        if (name.contains("spanish")) {
            return "es-ES";
        }
        if (name.contains("portuguese")) {
            return "pt-BR";
        }
        if (name.contains("polish")) {
            return "pl-PL";
        }
        if (name.contains("french")) {
            return "fr-FR";
        }
        if (name.contains("german")) {
            return "de-DE";
        }
        if (name.contains("italian")) {
            return "it-IT";
        }
        if (name.contains("dutch")) {
            return "nl-NL";
        }
        if (name.contains("british english")) {
            return "en-GB";
        }
        return DEFAULT_LANG;
    }

    /** Returns the MP3 for this text. */
    static byte[] synthesize(String text, String voiceName, String pitch) throws IOException {
        String voice = (voiceName == null || voiceName.isBlank()) ? DEFAULT_VOICE : voiceName.trim();
        String lang = languageOf(voice);

        String ssml = "<speak version=\"1.0\" xml:lang=\"" + lang + "\">"
            + "<prosody pitch='" + normalizePitch(pitch) + "'>"
            + xmlEscape(text)
            + "</prosody></speak>";

        String payload = "[{\"voiceId\":\"" + Json.escape(voice) + "\","
            + "\"ssml\":\"" + Json.escape(ssml) + "\"}]";

        String ids = post(CREATE_URI, payload);
        String query = idsToQuery(ids);
        if (query.isBlank()) {
            throw new IOException("ttstool returned no audio parts: " + ids);
        }

        HttpRequest request = HttpRequest.newBuilder(
                URI.create(HOST + "/ttstool/getParts?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8)))
            .timeout(Duration.ofSeconds(30))
            .GET()
            .build();
        try {
            HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("ttstool getParts returned HTTP " + response.statusCode());
            }
            return response.body();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while fetching audio", exception);
        }
    }

    /** ["a","b"] -> a,b */
    private static String idsToQuery(String jsonArray) {
        StringBuilder out = new StringBuilder();
        boolean inString = false;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < jsonArray.length(); i++) {
            char c = jsonArray.charAt(i);
            if (c == '"') {
                if (inString) {
                    if (out.length() > 0) {
                        out.append(',');
                    }
                    out.append(current);
                    current.setLength(0);
                }
                inString = !inString;
            } else if (inString) {
                current.append(c);
            }
        }
        return out.toString();
    }

    /** ttstool accepts the SSML prosody vocabulary. */
    private static String normalizePitch(String pitch) {
        if (pitch == null || pitch.isBlank()) {
            return "medium";
        }
        return switch (pitch.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "x-low", "low", "medium", "high", "x-high" -> pitch.trim().toLowerCase(java.util.Locale.ROOT);
            default -> "medium";
        };
    }

    private static String get(URI uri) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15)).GET().build();
        try {
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("HTTP " + response.statusCode() + " from " + uri);
            }
            return response.body();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", exception);
        }
    }

    private static String post(URI uri, String body) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(20))
            .header("Content-Type", "application/json")
            .header("User-Agent", "SMPutilities-Voice-Bridge/1.0")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();
        try {
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("HTTP " + response.statusCode() + " from " + uri
                    + ": " + response.body().substring(0, Math.min(160, response.body().length())));
            }
            return response.body();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", exception);
        }
    }

    private static String xmlEscape(String raw) {
        return raw.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;");
    }
}
