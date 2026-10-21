package com.arthou.smputilities.nick;

import com.arthou.smputilities.core.chat.StyledText;
import com.arthou.smputilities.core.nick.NameAnimation;
import com.arthou.smputilities.core.perm.Perm;
import com.arthou.smputilities.mc.Mc;
import com.arthou.smputilities.network.SuNetwork;
import com.arthou.smputilities.server.Msg;
import com.arthou.smputilities.server.Names;
import com.arthou.smputilities.server.Perms;
import com.arthou.smputilities.server.SuServer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ColorArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * The HiddenNames command tree, so servers that used that mod keep their
 * commands: /hiddennames setName [players] (color animation name | name |
 * color | animation | reset), nameplateVisible, defaultVisible and
 * blocksHideName. Names take {@code &}-codes instead of JSON text, and
 * animations are plain words, so players without the mod can still join.
 */
public final class HiddenNamesCommands {
    private static final SuggestionProvider<CommandSourceStack> ANIMATIONS = (context, builder) ->
        SharedSuggestionProvider.suggest(Arrays.stream(NameAnimation.values()).map(NameAnimation::id), builder);

    private interface Targets {
        Collection<ServerPlayer> get(CommandContext<CommandSourceStack> context) throws CommandSyntaxException;
    }

    private interface Change {
        /** @return null on success, otherwise a translation key */
        String apply(CommandContext<CommandSourceStack> context, ServerPlayer target, boolean self) throws CommandSyntaxException;
    }

    private HiddenNamesCommands() {
    }

    private static boolean enabled() {
        return SuServer.config().features.nicknames;
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("hiddennames")
            .requires(source -> enabled() && Perms.has(source, Perm.NICKNAME_CHANGE))
            .then(setName(Commands.literal("setName"), context -> List.of(context.getSource().getPlayerOrException()), true)
                .then(setName(Commands.argument("players1", EntityArgument.players()).requires(Perms.require(Perm.NICKNAME_OTHERS)),
                    context -> EntityArgument.getPlayers(context, "players1"), false)))
            .then(Commands.literal("nameplateVisible").requires(Perms.require(Perm.NICKNAME_OTHERS))
                .then(Commands.argument("players", EntityArgument.players())
                    .then(Commands.argument("visible", BoolArgumentType.bool()).executes(context -> {
                        boolean visible = BoolArgumentType.getBool(context, "visible");
                        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "players");
                        for (ServerPlayer player : players) {
                            NameAnimation current = NicknameService.animation(player.getUUID());
                            if (!visible) {
                                NicknameService.setAnimation(context.getSource().getServer(), player.getUUID(), NameAnimation.HIDDEN, Mc.player(context.getSource()));
                            } else if (current == NameAnimation.HIDDEN) {
                                NicknameService.setAnimation(context.getSource().getServer(), player.getUUID(), NameAnimation.NONE, Mc.player(context.getSource()));
                            }
                        }
                        return Msg.ok(context.getSource(), visible ? "nick.nameplate_shown" : "nick.nameplate_hidden", players.size());
                    }))))
            .then(Commands.literal("defaultVisible").requires(Perms.require(Perm.NICKNAME_OTHERS))
                .then(Commands.argument("visible", BoolArgumentType.bool()).executes(context -> {
                    boolean visible = BoolArgumentType.getBool(context, "visible");
                    SuServer.config().nicknames.defaultNameplateVisible = visible;
                    saveAndSync(context);
                    return Msg.ok(context.getSource(), visible ? "nick.default_visible_on" : "nick.default_visible_off");
                })))
            .then(Commands.literal("blocksHideName").requires(Perms.require(Perm.NICKNAME_OTHERS))
                .then(Commands.argument("hide", BoolArgumentType.bool()).executes(context -> {
                    boolean hide = BoolArgumentType.getBool(context, "hide");
                    SuServer.config().nicknames.blocksHideNameplates = hide;
                    saveAndSync(context);
                    return Msg.ok(context.getSource(), hide ? "nick.blocks_hide_on" : "nick.blocks_hide_off");
                }))));
    }

    private static void saveAndSync(CommandContext<CommandSourceStack> context) {
        SuServer.configManager().save();
        SuServer.get().log(Mc.player(context.getSource()), "nick.settings", null, "", context.getInput());
        SuNetwork.syncNicknamesToAll(context.getSource().getServer());
    }

    /** The subcommands of setName, for oneself or for the given players. */
    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T setName(T builder, Targets targets, boolean self) {
        return builder
            .then(Commands.argument("color1", ColorArgument.color())
                .then(Commands.argument("animation1", StringArgumentType.word()).suggests(ANIMATIONS)
                    .then(Commands.argument("name1", StringArgumentType.greedyString()).executes(context -> run(context, targets, self, (ctx, target, own) -> {
                        String error = checkColor(ctx, own);
                        if (error == null) {
                            error = checkAnimation(ctx, own);
                        }
                        if (error != null) {
                            return error;
                        }
                        ChatFormatting color = ColorArgument.getColor(ctx, "color1");
                        String code = color.isColor() ? "&" + color.getChar() : "";
                        boolean colors = !own || Perms.has(Mc.player(ctx.getSource()), Perm.NICKNAME_COLOR);
                        String text = StringArgumentType.getString(ctx, "name1");
                        error = NicknameService.set(ctx.getSource().getServer(), target.getUUID(), code + (colors ? text : StyledText.strip(text)),
                            true, Mc.player(ctx.getSource()));
                        if (error == null) {
                            NicknameService.setAnimation(ctx.getSource().getServer(), target.getUUID(), animation(ctx), Mc.player(ctx.getSource()));
                        }
                        return error;
                    })))))
            .then(Commands.literal("name")
                .then(Commands.argument("name1", StringArgumentType.greedyString()).executes(context -> run(context, targets, self, (ctx, target, own) -> {
                    boolean colors = !own || Perms.has(Mc.player(ctx.getSource()), Perm.NICKNAME_COLOR);
                    return NicknameService.setText(ctx.getSource().getServer(), target.getUUID(), StringArgumentType.getString(ctx, "name1"), colors,
                        Mc.player(ctx.getSource()));
                }))))
            .then(Commands.literal("color")
                .then(Commands.argument("color1", ColorArgument.color()).executes(context -> run(context, targets, self, (ctx, target, own) -> {
                    String error = checkColor(ctx, own);
                    return error != null ? error
                        : NicknameService.setColor(ctx.getSource().getServer(), target.getUUID(), ColorArgument.getColor(ctx, "color1"), Mc.player(ctx.getSource()));
                }))))
            .then(Commands.literal("animation")
                .then(Commands.argument("animation1", StringArgumentType.word()).suggests(ANIMATIONS).executes(context -> run(context, targets, self, (ctx, target, own) -> {
                    String error = checkAnimation(ctx, own);
                    if (error == null) {
                        NicknameService.setAnimation(ctx.getSource().getServer(), target.getUUID(), animation(ctx), Mc.player(ctx.getSource()));
                    }
                    return error;
                }))))
            .then(Commands.literal("reset").executes(context -> run(context, targets, self, (ctx, target, own) -> {
                NicknameService.reset(ctx.getSource().getServer(), target.getUUID(), Mc.player(ctx.getSource()));
                return null;
            })));
    }

    private static NameAnimation animation(CommandContext<CommandSourceStack> context) {
        return NameAnimation.parse(StringArgumentType.getString(context, "animation1"));
    }

    private static String checkColor(CommandContext<CommandSourceStack> context, boolean own) {
        ServerPlayer player = Mc.player(context.getSource());
        return own && player != null && !Perms.has(player, Perm.NICKNAME_COLOR) ? "nick.no_colors" : null;
    }

    private static String checkAnimation(CommandContext<CommandSourceStack> context, boolean own) {
        if (animation(context) == null) {
            return "nick.unknown_animation";
        }
        ServerPlayer player = Mc.player(context.getSource());
        return own && player != null && !Perms.has(player, Perm.NICKNAME_ANIMATION) ? "nick.no_animation" : null;
    }

    private static int run(CommandContext<CommandSourceStack> context, Targets targets, boolean self, Change change) throws CommandSyntaxException {
        Collection<ServerPlayer> players = targets.get(context);
        int done = 0;
        for (ServerPlayer target : players) {
            String error = change.apply(context, target, self);
            if (error != null) {
                Msg.error(context.getSource(), error);
                if (error.equals("nick.unknown_animation")) {
                    Msg.info(context.getSource(), "nick.animations", Arrays.toString(Arrays.stream(NameAnimation.values()).map(NameAnimation::id).toArray()));
                }
                return done;
            }
            done++;
        }
        if (self) {
            return Msg.ok(context.getSource(), "nick.updated_self", StyledText.strip(Names.display(context.getSource().getPlayerOrException()).getString()));
        }
        return Msg.ok(context.getSource(), "nick.updated", done);
    }
}
