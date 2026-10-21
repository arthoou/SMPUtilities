package com.arthou.smputilities.client.tts;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

public final class VoiceClient {
    private static final URI SPEAK_URI = URI.create("http://127.0.0.1:43125/speak");
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2))
        .build();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "smputilities-voice");
            thread.setDaemon(true);
            return thread;
        }
    });
    private static final AtomicBoolean helperMissingShown = new AtomicBoolean();

    private VoiceClient() {
    }

    public static void speak(String text, String voiceName, String pitch) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isBlank()) {
            return;
        }

        EXECUTOR.execute(() -> sendToHelper(trimmed, voiceName == null ? "" : voiceName.trim(), pitch == null ? "" : pitch.trim()));
    }

    private static void sendToHelper(String text, String voiceName, String pitch) {
        if (!BridgeLauncher.ensureRunning(true)) {
            return;
        }

        try {
            if (postSpeak(text, voiceName, pitch)) {
                helperMissingShown.set(false);
                return;
            }
        } catch (IOException exception) {
            if (exception instanceof ConnectException) {
                if (BridgeLauncher.ensureRunning(false)) {
                    try {
                        if (postSpeak(text, voiceName, pitch)) {
                            helperMissingShown.set(false);
                            return;
                        }
                    } catch (IOException retryException) {
                        if (!(retryException instanceof ConnectException) && helperMissingShown.compareAndSet(false, true)) {
                            showClientMessage(Component.translatable("tts.helper_io")
                                .withStyle(ChatFormatting.RED));
                        }
                    } catch (InterruptedException retryException) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }

                if (helperMissingShown.compareAndSet(false, true)) {
                    showClientMessage(Component.translatable("tts.helper_missing")
                        .withStyle(ChatFormatting.RED));
                }
            } else {
                showClientMessage(Component.translatable("tts.helper_io")
                    .withStyle(ChatFormatting.RED));
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static boolean postSpeak(String text, String voiceName, String pitch) throws IOException, InterruptedException {
        String payload = "{\"text\":\"" + escapeJson(text) + "\",\"voiceName\":\"" + escapeJson(voiceName) + "\",\"pitch\":\"" + escapeJson(pitch) + "\"}";
        HttpRequest request = HttpRequest.newBuilder(SPEAK_URI)
            .timeout(Duration.ofSeconds(8))
            .header("Content-Type", "application/json; charset=utf-8")
            .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
            .build();

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return true;
        }

        showClientMessage(Component.translatable("tts.helper_error", response.statusCode())
            .withStyle(ChatFormatting.RED));
        return false;
    }

    private static void showClientMessage(Component message) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(message, false);
            }
        });
    }

    private static String escapeJson(String value) {
        StringBuilder builder = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '\\' -> builder.append("\\\\");
                case '"' -> builder.append("\\\"");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (character < 0x20) {
                        builder.append(String.format("\\u%04x", (int) character));
                    } else {
                        builder.append(character);
                    }
                }
            }
        }
        return builder.toString();
    }
}
