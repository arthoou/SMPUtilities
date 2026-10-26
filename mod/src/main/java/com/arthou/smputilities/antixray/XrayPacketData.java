package com.arthou.smputilities.antixray;

/** Added to chunk packet data by the anti-xray mixin. */
public interface XrayPacketData {
    /** The block data as the game made it. */
    byte[] smputilities$rawBuffer();

    /** The block data as it is sent, with the hidden blocks replaced. */
    byte[] smputilities$sentBuffer();
}
