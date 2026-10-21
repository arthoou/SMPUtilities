package com.arthou.smputilities.mixin;

import com.arthou.smputilities.smp.AfkService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.SleepStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * AFK players who are not in bed are left out of the players the sleep
 * percentage counts, so one AFK player does not keep the night going.
 */
@Mixin(SleepStatus.class)
public abstract class SleepStatusMixin {
    @Shadow
    private int activePlayers;

    @Inject(method = "update", at = @At("RETURN"))
    private void smputilities$ignoreAfk(List<ServerPlayer> players, CallbackInfoReturnable<Boolean> cir) {
        for (ServerPlayer player : players) {
            if (!player.isSpectator() && AfkService.ignoredForSleep(player)) {
                activePlayers--;
            }
        }
    }
}
