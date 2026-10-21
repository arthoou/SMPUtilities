package com.arthou.smputilities.core.modsync;

/**
 * One file the server wants clients to have. {@code url} empty means the file
 * is served by the server's own host; otherwise it is an external download.
 */
public record ModEntry(String fileName, String sha512, long size, String url, String credential, boolean required) {
    public boolean hosted() {
        return url == null || url.isEmpty();
    }
}
