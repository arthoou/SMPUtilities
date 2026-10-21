package com.arthou.smputilities.fabric.mixin;

import com.arthou.smputilities.fabric.FabricEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Nicknames in chat, death messages and (client side) nametags: swaps the
 * plain name that vanilla wraps in team colors. Forge and NeoForge have the
 * NameFormat event for this.
 */
@Mixin(Player.class)
public abstract class PlayerMixin {
    @ModifyArg(method = "getDisplayName", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/scores/PlayerTeam;formatNameForTeam(Lnet/minecraft/world/scores/Team;Lnet/minecraft/network/chat/Component;)Lnet/minecraft/network/chat/MutableComponent;"),
        index = 1, require = 0)
    private Component smputilities$displayName(Component name) {
        return FabricEvents.displayName((Player) (Object) this, name);
    }
}
