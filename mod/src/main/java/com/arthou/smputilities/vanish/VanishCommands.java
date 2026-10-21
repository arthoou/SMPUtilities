package com.arthou.smputilities.vanish;

import com.arthou.smputilities.mc.Mc;
import com.arthou.smputilities.mc.Texts;
import com.arthou.smputilities.server.Lang;
import com.arthou.smputilities.server.Names;
import com.arthou.smputilities.server.SuServer;
import com.arthou.smputilities.util.CommandTrees;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Vanilla's /list with vanished players left out (and counted out) for
 * those who may not see them; staff who may see them get them tagged.
 */
public final class VanishCommands {
    private VanishCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        if (!SuServer.config().features.vanish) {
            return;
        }
        CommandTrees.remove(dispatcher, "list");
        dispatcher.register(Commands.literal("list")
            .executes(context -> list(context.getSource(), false))
            .then(Commands.literal("uuids").executes(context -> list(context.getSource(), true))));
    }

    private static int list(CommandSourceStack source, boolean uuids) {
        MinecraftServer server = source.getServer();
        ServerPlayer viewer = Mc.player(source);
        List<Component> names = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (viewer != null && !VanishService.canSee(viewer, player)) {
                continue;
            }
            MutableComponent name = Names.seesRealNames(viewer) ? Texts.literal(Names.realName(player)) : Names.display(player);
            if (uuids) {
                name = Texts.translatable("commands.list.nameAndId", name, player.getUUID().toString());
            }
            if (VanishService.isVanished(player.getUUID())) {
                name.append(Texts.literal(" " + Lang.tr(viewer, "vanish.presence_tag")).withStyle(ChatFormatting.GRAY));
            }
            names.add(name);
        }
        MutableComponent joined = Texts.empty();
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) {
                joined.append(Texts.literal(", "));
            }
            joined.append(names.get(i));
        }
        Mc.reply(source, Texts.translatable("commands.list.players", names.size(), server.getMaxPlayers(), joined));
        return names.size();
    }
}
