package com.arthou.smputilities.platform;

/**
 * The language a client reported, for game versions (before 1.20.2) where
 * the server did not keep it. Implemented on ServerPlayer by a mixin.
 */
public interface ClientLanguage {
    String smputilities$language();
}
