package com.arthou.smputilities.mixin;

import com.arthou.smputilities.vanish.SuTrackedEntity;
import com.arthou.smputilities.vanish.VanishService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

/**
 * Server-side vanish: a vanished player is never added to the tracking set
 * of viewers who may not see them, so their client never receives the
 * entity at all (no position, equipment, sounds or particles from it).
 */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class TrackedEntityMixin implements SuTrackedEntity {
    @Shadow
    @Final
    Entity entity;

    @Shadow
    @Final
    private Set<ServerPlayerConnection> seenBy;

    @Shadow
    public abstract void removePlayer(ServerPlayer player);

    @Shadow
    public abstract void updatePlayer(ServerPlayer player);

    @Inject(method = "updatePlayer", at = @At("HEAD"), cancellable = true)
    private void smputilities$hideVanished(ServerPlayer viewer, CallbackInfo ci) {
        if (entity instanceof ServerPlayer target && target != viewer && VanishService.isHiddenFrom(viewer, target)) {
            if (seenBy.contains(viewer.connection)) {
                removePlayer(viewer);
            }
            ci.cancel();
        }
    }

    @Override
    public void smputilities$update(ServerPlayer viewer) {
        updatePlayer(viewer);
    }
}
