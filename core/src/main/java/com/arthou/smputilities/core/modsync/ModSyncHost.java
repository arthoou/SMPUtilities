package com.arthou.smputilities.core.modsync;

import com.arthou.smputilities.core.util.SuLog;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * Read-only file host for mod sync. It serves exactly the files listed in the
 * server's manifest, addressed by SHA-512, and only to requests carrying a
 * token handed to a connected player. Nothing can be uploaded or executed.
 */
public final class ModSyncHost {
    private final Map<String, Path> filesByHash = new ConcurrentHashMap<>();
    private final Set<String> tokens = ConcurrentHashMap.newKeySet();
    private HttpServer server;

    public synchronized void start(int port) throws IOException {
        stop();
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newFixedThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable, "SMPutilities-modsync-host");
            thread.setDaemon(true);
            return thread;
        }));
        server.createContext("/modsync/", this::handle);
        server.start();
        SuLog.info("Mod sync host listening on port " + port);
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    public void setFiles(Map<String, Path> byHash) {
        filesByHash.clear();
        filesByHash.putAll(byHash);
    }

    public void addToken(String token) {
        tokens.add(token);
    }

    public void removeToken(String token) {
        tokens.remove(token);
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            String token = exchange.getRequestHeaders().getFirst("X-SMPutilities-Token");
            if (token == null || !tokens.contains(token)) {
                exchange.sendResponseHeaders(403, -1);
                return;
            }
            String hash = exchange.getRequestURI().getPath().substring("/modsync/".length()).toLowerCase(Locale.ROOT);
            Path file = SafeNames.isSha512(hash) ? filesByHash.get(hash) : null;
            if (file == null || !Files.isRegularFile(file)) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            exchange.getResponseHeaders().add("Content-Type", "application/java-archive");
            exchange.sendResponseHeaders(200, Files.size(file));
            try (InputStream in = Files.newInputStream(file); OutputStream out = exchange.getResponseBody()) {
                in.transferTo(out);
            }
        }
    }
}
