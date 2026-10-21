package com.arthou.smputilities.core.modsync;

import java.util.List;

/**
 * What the server sends a joining client.
 *
 * @param hostBase base URL of the server's file host ("http://host:port"), empty when hosting is off
 * @param token    per-session token for the file host; meaningless anywhere else
 */
public record ModManifest(String hostBase, String token, List<ModEntry> entries) {
}
