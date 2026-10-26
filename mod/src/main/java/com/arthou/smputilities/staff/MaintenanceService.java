package com.arthou.smputilities.staff;

import com.arthou.smputilities.core.config.SuConfig;
import com.arthou.smputilities.core.perm.Perm;
import com.arthou.smputilities.core.perm.PermissionRules;
import com.arthou.smputilities.mc.Mc;
import com.arthou.smputilities.server.Msg;
import com.arthou.smputilities.server.Perms;
import com.arthou.smputilities.server.SuServer;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.UUID;

/** Maintenance mode: only players with smputilities.maintenance.bypass may join. */
public final class MaintenanceService {
    private MaintenanceService() {
    }

    /** At login, before the player is placed: the reason to refuse them, or null. */
    public static Component refuse(MinecraftServer server, UUID id, String name) {
        SuConfig config = SuServer.config();
        if (!config.maintenance.enabled || SuServer.get() == null) {
            return null;
        }
        // No ServerPlayer exists yet: decide by config grants and op level.
        if (PermissionRules.fallback(config, Perm.MAINTENANCE_BYPASS, id, name, Mc.opLevel(server, id, name))) {
            return null;
        }
        return Msg.styled(config.maintenance.message);
    }

    public static LiteralArgumentBuilder<CommandSourceStack> branch() {
        return Commands.literal("maintenance")
            .requires(Perms.require(Perm.ADMIN_MAINTENANCE))
            .executes(context -> Msg.info(context.getSource(), SuServer.config().maintenance.enabled ? "maintenance.status.on" : "maintenance.status.off"))
            .then(Commands.literal("on").executes(context -> set(context, true, null))
                .then(Commands.argument("message", StringArgumentType.greedyString())
                    .executes(context -> set(context, true, StringArgumentType.getString(context, "message")))))
            .then(Commands.literal("off").executes(context -> set(context, false, null)));
    }

    private static int set(CommandContext<CommandSourceStack> context, boolean on, String message) {
        set(context.getSource().getServer(), Mc.player(context.getSource()), on, message);
        return Msg.ok(context.getSource(), on ? "maintenance.on" : "maintenance.off");
    }

    public static void set(MinecraftServer server, ServerPlayer actor, boolean on, String message) {
        SuServer.configManager().edit(config -> {
            config.maintenance.enabled = on;
            if (message != null && !message.isBlank()) {
                config.maintenance.message = message;
            }
        });
        if (SuServer.get() != null) {
            SuServer.get().log(actor, on ? "maintenance.on" : "maintenance.off", null, "", message == null ? "" : message);
        }
        if (on && SuServer.config().maintenance.kickOnline) {
            Component reason = Msg.styled(SuServer.config().maintenance.message);
            for (ServerPlayer player : new ArrayList<>(server.getPlayerList().getPlayers())) {
                if (!Perms.has(player, Perm.MAINTENANCE_BYPASS)) {
                    player.connection.disconnect(reason);
                }
            }
        }
    }
}
