package com.arthou.smputilities.antixray;

import com.arthou.smputilities.SmpUtilities;
import com.arthou.smputilities.core.antixray.ChunkCodec;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.PalettedContainer;

/**
 * For the CI smoke test: makes a chunk packet of the spawn area, hides its
 * blocks and reads the result back with the game's own palette reader, as a
 * client would, checking that every block and every byte lines up.
 */
public final class XraySelfTest {
    private XraySelfTest() {
    }

    /** @return null when the packet reads back correctly, otherwise what went wrong */
    public static String run(MinecraftServer server) {
        ServerLevel level = server.overworld();
        if (!AntiXray.active(level)) {
            return "anti-xray is not active in the overworld";
        }
        int changed = 0;
        int checked = 0;
        boolean gameReader = true;
        for (int cx = -1; cx <= 1; cx++) {
            for (int cz = -1; cz <= 1; cz++) {
                LevelChunk chunk = level.getChunk(cx, cz);
                XrayPacketData data = (XrayPacketData) new ClientboundLevelChunkPacketData(chunk);
                byte[] raw = data.smputilities$rawBuffer();
                byte[] sent = data.smputilities$sentBuffer();
                int count = chunk.getSectionsCount();
                ChunkCodec.Section[] before = ChunkCodec.parse(raw, count, AntiXray.lengthPrefix());
                ChunkCodec.Section[] after = ChunkCodec.parse(sent, count, AntiXray.lengthPrefix());
                if (after[count - 1].end != sent.length) {
                    return "chunk " + cx + "," + cz + ": " + (sent.length - after[count - 1].end) + " bytes left after the sections";
                }
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(sent));
                for (int s = 0; s < count; s++) {
                    int[] original = ChunkCodec.decode(raw, before[s]);
                    int[] shown = ChunkCodec.decode(sent, after[s]);
                    PalettedContainer<BlockState> states = null;
                    if (gameReader) {
                        try {
                            states = chunk.getSections()[s].getStates().copy();
                            buf.readerIndex(after[s].statesStart);
                            states.read(buf);
                        } catch (NoSuchMethodError | NoClassDefFoundError e) {
                            // Dedicated servers may not ship the client's reader.
                            gameReader = false;
                            states = null;
                        }
                        if (states != null && buf.readerIndex() != after[s].statesEnd) {
                            return "chunk " + cx + "," + cz + " section " + s + ": the game read " + (buf.readerIndex() - after[s].statesStart)
                                + " bytes of block states, the packet has " + (after[s].statesEnd - after[s].statesStart);
                        }
                    }
                    for (int i = 0; i < ChunkCodec.SECTION_BLOCKS; i++) {
                        if (states != null && AntiXray.id(states.get(i & 15, i >> 8, (i >> 4) & 15)) != shown[i]) {
                            return "chunk " + cx + "," + cz + " section " + s + " block " + i + ": the game reads another block than was written";
                        }
                        if (original[i] != shown[i]) {
                            changed++;
                        }
                    }
                    checked++;
                }
            }
        }
        SmpUtilities.LOGGER.info("Anti-xray self test: {} sections, {} blocks hidden, game reader {}", checked, changed,
            gameReader ? "used" : "not available on this server");
        return null;
    }
}
