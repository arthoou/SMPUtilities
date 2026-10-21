package com.arthou.smputilities.smp;

import com.arthou.smputilities.core.chat.Placeholders;
import com.arthou.smputilities.core.config.SuConfig;
import com.arthou.smputilities.core.perm.Perm;
import com.arthou.smputilities.mc.Mc;
import com.arthou.smputilities.server.Lang;
import com.arthou.smputilities.server.Msg;
import com.arthou.smputilities.server.Names;
import com.arthou.smputilities.server.Perms;
import com.arthou.smputilities.server.SuServer;
import com.arthou.smputilities.vanish.VanishService;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** First-join announcement, the join message of the day and /rules. */
public final class WelcomeService {
    private WelcomeService() {
    }

    private static boolean enabled() {
        return SuServer.config().features.welcome;
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("rules")
            .requires(source -> enabled() && Perms.has(source, Perm.RULES))
            .executes(context -> {
                List<String> rules = SuServer.config().welcome.rules;
                if (rules == null || rules.isEmpty()) {
                    return Msg.info(context.getSource(), "rules.none");
                }
                ServerPlayer player = Mc.player(context.getSource());
                for (String line : rules) {
                    Msg.raw(context.getSource(), Msg.linked(Placeholders.apply(line, values(context.getSource().getServer(), player))));
                }
                return rules.size();
            }));
    }

    /** After the join line. */
    public static void onLogin(ServerPlayer player, boolean firstJoin) {
        SuServer context = SuServer.get();
        if (context == null || !enabled()) {
            return;
        }
        SuConfig.Welcome config = SuServer.config().welcome;
        MinecraftServer server = Mc.server(player);
        if (firstJoin && config.firstJoinBroadcast && !VanishService.isVanished(player.getUUID())) {
            int count = context.players().all().size();
            for (ServerPlayer recipient : server.getPlayerList().getPlayers()) {
                String name = Placeholders.escapeCodes(Names.seesRealNames(recipient) ? Names.realName(player) : Names.plainDisplay(player));
                String line = config.firstJoinMessage == null || config.firstJoinMessage.isEmpty()
                    ? Lang.tr(recipient, "welcome.first_join", name, count)
                    : Placeholders.apply(config.firstJoinMessage, Map.of("name", name, "count", String.valueOf(count)));
                Mc.send(recipient, Msg.linked(line));
            }
        }
        if (config.motd != null) {
            Map<String, String> values = values(server, player);
            for (String line : config.motd) {
                Mc.send(player, Msg.linked(Placeholders.apply(line, values)));
            }
        }
    }

    /** Online players minus vanished ones, so a count never gives vanished staff away. */
    static int visibleOnline(MinecraftServer server) {
        int count = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!VanishService.isVanished(player.getUUID())) {
                count++;
            }
        }
        return count;
    }

    private static Map<String, String> values(MinecraftServer server, ServerPlayer player) {
        Map<String, String> values = new HashMap<>();
        values.put("name", player == null ? "Console" : Placeholders.escapeCodes(Names.plainDisplay(player)));
        values.put("online", String.valueOf(visibleOnline(server)));
        values.put("max", String.valueOf(server.getPlayerList().getMaxPlayers()));
        return values;
    }
}
