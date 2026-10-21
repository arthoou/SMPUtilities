package com.arthou.smputilities.mixin;

import com.arthou.smputilities.vanish.VanishService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/** The server list's player count and sample leave vanished players out. */
@Mixin(MinecraftServer.class)
public abstract class ServerStatusMixin {
    //#if MC>=1.19.4
    @Redirect(method = "buildPlayerStatus", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/PlayerList;getPlayers()Ljava/util/List;"))
    private List<ServerPlayer> smputilities$listed(PlayerList list) {
        return VanishService.listed(list.getPlayers());
    }
    //#else
    //$$ // Before 1.19.4 the status is built inside tickServer, from the count and the list.
    //$$ @Redirect(method = "tickServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;getPlayerCount()I"))
    //$$ private int smputilities$listedCount(MinecraftServer server) {
    //$$     return VanishService.listed(server.getPlayerList().getPlayers()).size();
    //$$ }
    //$$
    //$$ @Redirect(method = "tickServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/PlayerList;getPlayers()Ljava/util/List;"))
    //$$ private List<ServerPlayer> smputilities$listed(PlayerList list) {
    //$$     return VanishService.listed(list.getPlayers());
    //$$ }
    //#endif
}
