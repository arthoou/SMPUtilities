package com.arthou.smputilities.chat;

import com.arthou.smputilities.core.config.SuConfig;
import com.arthou.smputilities.core.perm.Perm;
import com.arthou.smputilities.core.player.PlayerRecord;
import com.arthou.smputilities.core.chat.Placeholders;
import com.arthou.smputilities.server.Msg;
import com.arthou.smputilities.server.Names;
import com.arthou.smputilities.server.Perms;
import com.arthou.smputilities.server.SuServer;
import com.arthou.smputilities.util.CommandTrees;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** /msg, /reply, /ignore and the per-player mention sound settings. */
public final class ChatCommands {
    private ChatCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        SuConfig config = SuServer.config();
        if (config.features.qol && config.qol.msg) {
            CommandTrees.remove(dispatcher, "msg", "tell", "w");
            LiteralCommandNode<CommandSourceStack> msg = dispatcher.register(Commands.literal("msg")
                .requires(Perms.require(Perm.CHAT_MSG))
                .then(Commands.argument("target", StringArgumentType.string())
                    .suggests(Names.onlineSuggestions())
                    .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(ChatCommands::message))));
            dispatcher.register(Commands.literal("tell").requires(Perms.require(Perm.CHAT_MSG)).redirect(msg));
            dispatcher.register(Commands.literal("w").requires(Perms.require(Perm.CHAT_MSG)).redirect(msg));
            LiteralCommandNode<CommandSourceStack> reply = dispatcher.register(Commands.literal("reply")
                .requires(Perms.require(Perm.CHAT_MSG))
                .then(Commands.argument("message", StringArgumentType.greedyString()).executes(ChatCommands::reply)));
            dispatcher.register(Commands.literal("r").requires(Perms.require(Perm.CHAT_MSG)).redirect(reply));
        }
        if (config.features.qol && config.qol.ignore) {
            dispatcher.register(Commands.literal("ignore")
                .requires(Perms.require(Perm.IGNORE))
                .executes(ChatCommands::listIgnored)
                .then(Commands.argument("target", StringArgumentType.string())
                    .suggests(Names.onlineSuggestions())
                    .executes(ChatCommands::toggleIgnore)));
        }
    }

    /** Branch under /su: mention sound preferences (works for vanilla clients, the sound is sent by the server). */
    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> mentionsBranch() {
        return Commands.literal("mentions")
            .executes(context -> showMentions(context.getSource().getPlayerOrException()))
            .then(Commands.literal("on").executes(context -> setMentions(context, true)))
            .then(Commands.literal("off").executes(context -> setMentions(context, false)))
            .then(Commands.literal("sound")
                .then(Commands.argument("sound", ResourceLocationArgument.id())
                    .suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggestResource(
                        net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.keySet(), builder))
                    .executes(context -> {
                        ServerPlayer player = context.getSource().getPlayerOrException();
                        String sound = ResourceLocationArgument.getId(context, "sound").toString();
                        SuServer.get().players().update(player.getUUID(), record -> record.mentionSoundId = sound);
                        ChatService.playSound(player, sound, 0.8F, 1.0F);
                        return Msg.ok(context.getSource(), "mentions.sound_set", sound);
                    })))
            .then(Commands.literal("volume")
                .then(Commands.argument("volume", FloatArgumentType.floatArg(0, 1))
                    .executes(context -> {
                        ServerPlayer player = context.getSource().getPlayerOrException();
                        float volume = FloatArgumentType.getFloat(context, "volume");
                        SuServer.get().players().update(player.getUUID(), record -> record.mentionVolume = volume);
                        return Msg.ok(context.getSource(), "mentions.volume_set", Math.round(volume * 100) + "%");
                    })))
            .then(Commands.literal("reset").executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                SuServer.get().players().update(player.getUUID(), record -> {
                    record.mentionSound = true;
                    record.mentionSoundId = "";
                    record.mentionVolume = -1;
                });
                return Msg.ok(context.getSource(), "mentions.reset");
            }));
    }

    private static int showMentions(ServerPlayer player) {
        PlayerRecord record = SuServer.get().players().get(player.getUUID());
        Msg.info(player, record.mentionSound ? "mentions.status_on" : "mentions.status_off");
        return 1;
    }

    private static int setMentions(CommandContext<CommandSourceStack> context, boolean enabled) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        SuServer.get().players().update(player.getUUID(), record -> record.mentionSound = enabled);
        return Msg.ok(context.getSource(), enabled ? "mentions.enabled" : "mentions.disabled");
    }

    private static int message(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        String targetName = StringArgumentType.getString(context, "target");
        Optional<Names.Target> target = Names.resolve(source.getServer(), targetName, source.getPlayer());
        if (target.isEmpty() || !target.get().isOnline()) {
            return Msg.error(source, "player.not_found_online", targetName);
        }
        return send(source, target.get().online(), StringArgumentType.getString(context, "message"));
    }

    private static int reply(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer sender = context.getSource().getPlayerOrException();
        UUID last = SuServer.get().players().get(sender.getUUID()).lastMessaged;
        ServerPlayer target = last == null ? null : sender.server.getPlayerList().getPlayer(last);
        if (target == null || !com.arthou.smputilities.vanish.VanishService.canSee(sender, target)) {
            return Msg.error(context.getSource(), "msg.no_reply");
        }
        return send(context.getSource(), target, StringArgumentType.getString(context, "message"));
    }

    /** Delivers a private message. Console can message players too. */
    public static int send(CommandSourceStack source, ServerPlayer target, String text) {
        ServerPlayer sender = source.getPlayer();
        SuConfig.Chat config = SuServer.config().chat;
        if (sender != null) {
            if (sender == target) {
                return Msg.error(source, "msg.self");
            }
            if (!ChatService.checkCanSpeak(sender, text, false)) {
                return 0;
            }
            if (ChatService.isIgnoring(target, sender)) {
                return Msg.error(source, "msg.ignored");
            }
        }
        String senderName = sender == null ? "Console" : name(sender, config);
        String targetName = name(target, config);
        String body = sender != null && config.playerColors && Perms.has(sender, Perm.CHAT_COLOR) ? text : Placeholders.escapeCodes(text);

        Map<String, String> outgoing = new HashMap<>();
        outgoing.put("sender", Placeholders.escapeCodes(senderName));
        outgoing.put("target", Placeholders.escapeCodes(targetName));
        outgoing.put("message", body);
        MutableComponent toSender = Msg.styled(Placeholders.apply(config.privateFormatOutgoing, outgoing));
        MutableComponent toTarget = Msg.styled(Placeholders.apply(config.privateFormatIncoming, outgoing));

        source.sendSystemMessage(toSender);
        target.sendSystemMessage(toTarget);
        target.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5F, 1.2F);
        SuServer context = SuServer.get();
        if (sender != null) {
            context.players().update(sender.getUUID(), record -> record.lastMessaged = target.getUUID());
            context.players().update(target.getUUID(), record -> record.lastMessaged = sender.getUUID());
        }
        Component spy = Component.literal("[spy] " + (sender == null ? "Console" : Names.realName(sender)) + " → " + Names.realName(target) + ": " + text)
            .withStyle(ChatFormatting.DARK_GRAY);
        for (ServerPlayer staff : target.server.getPlayerList().getPlayers()) {
            if (staff != sender && staff != target && context.players().get(staff.getUUID()).socialSpy && Perms.has(staff, Perm.CHAT_MANAGE)) {
                staff.sendSystemMessage(spy);
            }
        }
        target.server.sendSystemMessage(spy);
        return 1;
    }

    private static String name(ServerPlayer player, SuConfig.Chat config) {
        return config.privateMessagesUseNicknames ? Names.plainDisplay(player) : Names.realName(player);
    }

    private static int toggleIgnore(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String input = StringArgumentType.getString(context, "target");
        Optional<Names.Target> target = Names.resolve(player.server, input, player);
        if (target.isEmpty()) {
            return Msg.error(context.getSource(), "player.not_found", input);
        }
        if (target.get().id().equals(player.getUUID())) {
            return Msg.error(context.getSource(), "ignore.self");
        }
        UUID id = target.get().id();
        boolean[] added = new boolean[1];
        SuServer.get().players().update(player.getUUID(), record -> {
            added[0] = record.ignored.add(id);
            if (!added[0]) {
                record.ignored.remove(id);
            }
        });
        String shown = Names.plainDisplay(id, target.get().name());
        return Msg.ok(context.getSource(), added[0] ? "ignore.added" : "ignore.removed", shown);
    }

    private static int listIgnored(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        PlayerRecord record = SuServer.get().players().get(player.getUUID());
        if (record.ignored.isEmpty()) {
            return Msg.info(context.getSource(), "ignore.empty");
        }
        StringBuilder names = new StringBuilder();
        for (UUID id : record.ignored) {
            if (names.length() > 0) {
                names.append(", ");
            }
            names.append(Names.plainDisplay(id, Names.offlineName(player.server, id).orElse(id.toString().substring(0, 8))));
        }
        return Msg.info(context.getSource(), "ignore.list", names.toString());
    }
}
