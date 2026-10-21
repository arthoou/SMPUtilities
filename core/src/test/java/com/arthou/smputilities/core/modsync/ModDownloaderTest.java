package com.arthou.smputilities.core.modsync;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModDownloaderTest {
    @Test
    void credentialsOnlyGoToTheOriginalHost() {
        URI start = URI.create("https://cdn.example.com/mods/a.jar");
        assertTrue(ModDownloader.sameOrigin(start, URI.create("https://cdn.example.com/other/b.jar")));
        assertFalse(ModDownloader.sameOrigin(start, URI.create("https://evil.example.net/a.jar")));
        assertFalse(ModDownloader.sameOrigin(start, URI.create("http://cdn.example.com/mods/a.jar")));
        assertFalse(ModDownloader.sameOrigin(start, URI.create("https://cdn.example.com:8443/mods/a.jar")));
        assertTrue(ModDownloader.sameOrigin(URI.create("http://10.0.0.5:25580/modsync/x"), URI.create("http://10.0.0.5:25580/modsync/y")));
    }
}
