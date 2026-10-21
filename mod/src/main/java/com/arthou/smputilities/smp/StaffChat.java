package com.arthou.smputilities.smp;

import com.arthou.smputilities.core.chat.Placeholders;
import com.arthou.smputilities.core.perm.Perm;
import com.arthou.smputilities.mc.Mc;
import com.arthou.smputilities.server.Msg;
import com.arthou.smputilities.server.Names;
import com.arthou.smputilities.server.Perms;
import com.arthou.smputilities.server.SuServer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;

/** /sc: a channel only staff reads. "/sc" alone turns it on for normal chat lines. */
public final class StaffChat {
    private StaffChat() {
    }

    private static boolean enabled() {
        return SuServer.config().features.staffChat;
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralCommandNode<CommandSourceStack> node = dispatcher.register(Commands.literal("staffchat")
            .requires(source -> enabled() && Perms.has(source, Perm.STAFF_CHAT))
            .executes(StaffChat::toggle)
            .then(Commands.argument("message", StringArgumentType.greedyString()).executes(context -> {
                send(context.getSource().getServer(), Mc.player(context.getSource()), StringArgumentType.getString(context, "message"));
                return 1;
            })));
        dispatcher.register(Commands.literal("sc")
            .requires(source -> enabled() && Perms.has(source, Perm.STAFF_CHAT))
            .executes(StaffChat::toggle)
            .redirect(node));
    }

    private static int toggle(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        boolean[] on = new boolean[1];
        SuServer.get().players().update(player.getUUID(), record -> on[0] = record.staffChatToggled = !record.staffChatToggled);
        return Msg.ok(context.getSource(), on[0] ? "staffchat.on" : "staffchat.off");
    }

    /** A chat line from a player who turned the channel on. @return true when it went to staff chat */
    public static boolean handleChat(ServerPlayer player, String text) {
        SuServer context = SuServer.get();
        if (context == null || !enabled() || !context.players().get(player.getUUID()).staffChatToggled || !Perms.has(player, Perm.STAFF_CHAT)) {
            return false;
        }
        send(Mc.server(player), player, text);
        return true;
    }

    /** @param sender null for the console */
    public static void send(MinecraftServer server, ServerPlayer sender, String text) {
        boolean colors = sender == null || Perms.has(sender, Perm.CHAT_COLOR);
        Map<String, String> values = new HashMap<>();
        values.put("name", sender == null ? "Console" : Placeholders.escapeCodes(Names.plainDisplay(sender)));
        values.put("realname", sender == null ? "Console" : Placeholders.escapeCodes(Names.realName(sender)));
        values.put("message", colors ? text : Placeholders.escapeCodes(text));
        Component line = Msg.styled(Placeholders.apply(SuServer.config().staffChat.format, values));
        for (ServerPlayer staff : server.getPlayerList().getPlayers()) {
            if (Perms.has(staff, Perm.STAFF_CHAT)) {
                Mc.send(staff, line);
            }
        }
        Mc.console(server, line);
    }
}
