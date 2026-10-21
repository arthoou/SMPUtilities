package com.arthou.smputilities.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/** Client game API calls that differ between versions. */
public final class ClientCompat {
    private ClientCompat() {
    }

    public static KeyMapping keyMapping(String name, String category) {
        //#if MC>=1.21.9
        return new KeyMapping(name, InputConstants.UNKNOWN.getValue(),
            KeyMapping.Category.register(com.arthou.smputilities.mc.Ids.mod(category.substring(category.lastIndexOf('.') + 1))));
        //#else
        //$$ return new KeyMapping(name, InputConstants.UNKNOWN.getValue(), category);
        //#endif
    }

    /** Nametags cache the display name on Forge and NeoForge; Fabric computes it every frame. */
    public static void refreshDisplayName(net.minecraft.world.entity.player.Player player) {
        //#if FORGELIKE
        player.refreshDisplayName();
        //#endif
    }

    /** Runs a command as if typed in chat (without the slash). */
    public static void sendCommand(String command) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() == null) {
            return;
        }
        //#if MC>=1.19
        minecraft.getConnection().sendCommand(command);
        //#else
        //$$ minecraft.player.chat("/" + command);
        //#endif
    }
}
