package com.arthou.smputilities.antixray;

import com.arthou.smputilities.core.config.SuConfig;
import com.arthou.smputilities.core.perm.Perm;
import com.arthou.smputilities.core.teleport.Location;
import com.arthou.smputilities.mc.Ids;
import com.arthou.smputilities.mc.Mc;
import com.arthou.smputilities.mc.Texts;
import com.arthou.smputilities.server.Lang;
import com.arthou.smputilities.server.Msg;
import com.arthou.smputilities.server.Names;
import com.arthou.smputilities.server.Perms;
import com.arthou.smputilities.server.SuServer;
import com.arthou.smputilities.teleport.Teleports;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * X-ray alerts: counts the veins of valuable ores each player finds and tells
 * staff (smputilities.xray.alerts) about them, with a warning once a player
 * finds more veins in the time window than the threshold allows.
 */
public final class XrayAlerts {
    /** Ores this close to one another, broken within this time, belong to the same vein. */
    private static final int VEIN_DISTANCE = 3;
    private static final long VEIN_GAP_MILLIS = 60_000;
    private static final int KEPT = 50;

    private record Group(String key, Set<Block> blocks, int threshold) {
    }

    private record Mined(Group group, String dimension, BlockPos pos, long time, boolean veinStart) {
    }

    /** One vein a player found. */
    public record Alert(int id, UUID player, String name, String ore, String dimension, BlockPos pos, Location where,
                        int veins, boolean suspicious, long time) {
    }

    private static final Map<UUID, List<Mined>> MINED = new HashMap<>();
    private static final Deque<Alert> ALERTS = new ArrayDeque<>();
    private static List<Group> groups = List.of();
    private static int nextId = 1;

    private XrayAlerts() {
    }

    private static boolean enabled() {
        return SuServer.config().features.xrayAlerts;
    }

    static void rebuild(SuConfig config) {
        List<Group> built = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : config.xrayAlerts.thresholds.entrySet()) {
            Set<Block> blocks = XrayRuleBuilder.blocks(List.of(entry.getKey()));
            if (!blocks.isEmpty()) {
                built.add(new Group(entry.getKey(), blocks, Math.max(1, entry.getValue() == null ? 1 : entry.getValue())));
            }
        }
        groups = List.copyOf(built);
    }

    public static void onServerStop() {
        MINED.clear();
        ALERTS.clear();
        nextId = 1;
    }

    public static void forget(ServerPlayer player) {
        MINED.remove(player.getUUID());
    }

    /** A player broke a block. */
    public static void broken(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
        if (!enabled() || player.isCreative() || player.isSpectator()) {
            return;
        }
        Group group = groupOf(state.getBlock());
        if (group == null) {
            return;
        }
        SuConfig.XrayAlerts config = SuServer.config().xrayAlerts;
        long now = System.currentTimeMillis();
        long window = Math.max(1, config.windowMinutes) * 60_000L;
        String dimension = Ids.key(level.dimension());
        List<Mined> mined = MINED.computeIfAbsent(player.getUUID(), id -> new ArrayList<>());
        mined.removeIf(entry -> now - entry.time() > window);
        boolean newVein = true;
        for (Mined entry : mined) {
            if (entry.group() == group && entry.dimension().equals(dimension) && now - entry.time() <= VEIN_GAP_MILLIS
                && Math.abs(entry.pos().getX() - pos.getX()) <= VEIN_DISTANCE
                && Math.abs(entry.pos().getY() - pos.getY()) <= VEIN_DISTANCE && Math.abs(entry.pos().getZ() - pos.getZ()) <= VEIN_DISTANCE) {
                newVein = false;
                break;
            }
        }
        mined.add(new Mined(group, dimension, pos.immutable(), now, newVein));
        if (!newVein) {
            return;
        }
        int veins = 0;
        for (Mined entry : mined) {
            if (entry.group() == group && entry.veinStart()) {
                veins++;
            }
        }
        boolean suspicious = veins >= group.threshold();
        Alert alert = new Alert(nextId++, player.getUUID(), Names.realName(player), oreName(state.getBlock()), dimension, pos.immutable(),
            Teleports.locationOf(player), veins, suspicious, now);
        ALERTS.addFirst(alert);
        while (ALERTS.size() > KEPT) {
            ALERTS.removeLast();
        }
        if (suspicious || config.everyVein) {
            notifyStaff(Mc.server(player), alert, config);
        }
    }

    private static Group groupOf(Block block) {
        for (Group group : groups) {
            if (group.blocks().contains(block)) {
                return group;
            }
        }
        return null;
    }

    /** "deepslate_diamond_ore" -> "Deepslate Diamond Ore". */
    private static String oreName(Block block) {
        String id = Mc.blockId(block);
        String path = id.substring(id.indexOf(':') + 1);
        StringBuilder name = new StringBuilder();
        for (String word : path.split("_")) {
            if (!word.isEmpty()) {
                name.append(name.length() == 0 ? "" : " ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        return name.toString();
    }

    private static void notifyStaff(MinecraftServer server, Alert alert, SuConfig.XrayAlerts config) {
        String where = alert.pos().getX() + " " + alert.pos().getY() + " " + alert.pos().getZ();
        for (ServerPlayer staff : server.getPlayerList().getPlayers()) {
            if (staff.getUUID().equals(alert.player()) || !Perms.has(staff, Perm.XRAY_ALERTS)) {
                continue;
            }
            Mc.send(staff, line(staff, alert, where, config));
            if (alert.suspicious()) {
                Mc.playSound(staff, config.sound, SoundSource.MASTER, 0.9F, 0.8F);
            }
        }
        Mc.console(server, Texts.literal(text(Lang.EN, alert, where, config)));
    }

    private static String text(String language, Alert alert, String where, SuConfig.XrayAlerts config) {
        String key = alert.suspicious() ? "xray.suspicious" : "xray.vein";
        return Lang.format(language, key, alert.name(), alert.ore(), where, shortDimension(alert.dimension()), alert.veins(), config.windowMinutes);
    }

    private static MutableComponent line(ServerPlayer viewer, Alert alert, String where, SuConfig.XrayAlerts config) {
        MutableComponent line = Msg.prefix().append(Texts.literal(text(Lang.languageOf(viewer), alert, where, config))
            .withStyle(style -> Texts.color(style, alert.suspicious() ? Msg.DANGER : Msg.WARN)));
        if (Perms.has(viewer, Perm.STAFF_TELEPORT)) {
            String command = "/su xray tp " + alert.id();
            line.append(Texts.literal(" ")).append(Texts.hover(Texts.clickRun(
                Texts.literal("[" + Lang.tr(viewer, "xray.tp_button") + "]").withStyle(ChatFormatting.AQUA), command), Texts.literal(command)));
        }
        return line;
    }

    private static String shortDimension(String dimension) {
        return dimension.startsWith("minecraft:") ? dimension.substring("minecraft:".length()) : dimension;
    }

    /** Newest first. */
    public static List<Alert> recent() {
        return new ArrayList<>(ALERTS);
    }

    public static boolean teleport(ServerPlayer staff, int id) {
        for (Alert alert : ALERTS) {
            if (alert.id() == id) {
                return Teleports.teleport(staff, alert.where(), true);
            }
        }
        return false;
    }

    // -------------------------------------------------------------- command

    /** /su xray [tp <id>]. */
    public static LiteralArgumentBuilder<CommandSourceStack> branch() {
        return Commands.literal("xray")
            .requires(source -> enabled() && Perms.has(source, Perm.XRAY_ALERTS))
            .executes(XrayAlerts::list)
            .then(Commands.literal("tp").requires(Perms.require(Perm.STAFF_TELEPORT))
                .then(Commands.argument("id", IntegerArgumentType.integer(1)).executes(XrayAlerts::teleportCommand)));
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer viewer = Mc.player(source);
        if (ALERTS.isEmpty()) {
            return Msg.info(source, "xray.none");
        }
        Msg.info(source, "xray.header", Math.min(10, ALERTS.size()));
        Iterator<Alert> alerts = ALERTS.iterator();
        SuConfig.XrayAlerts config = SuServer.config().xrayAlerts;
        for (int i = 0; i < 10 && alerts.hasNext(); i++) {
            Alert alert = alerts.next();
            String where = alert.pos().getX() + " " + alert.pos().getY() + " " + alert.pos().getZ();
            String language = viewer == null ? Lang.EN : Lang.languageOf(viewer);
            String age = com.arthou.smputilities.core.util.Durations.format(Math.max(1000, System.currentTimeMillis() - alert.time()));
            MutableComponent line = Texts.literal("#" + alert.id() + " · " + age.toLowerCase(Locale.ROOT) + " · " + text(language, alert, where, config))
                .withStyle(alert.suspicious() ? ChatFormatting.RED : ChatFormatting.GRAY);
            if (viewer != null && Perms.has(viewer, Perm.STAFF_TELEPORT)) {
                String command = "/su xray tp " + alert.id();
                line.append(Texts.literal(" ")).append(Texts.hover(Texts.clickRun(
                    Texts.literal("[" + Lang.tr(viewer, "xray.tp_button") + "]").withStyle(ChatFormatting.AQUA), command), Texts.literal(command)));
            }
            Mc.reply(source, line);
        }
        return 1;
    }

    private static int teleportCommand(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int id = IntegerArgumentType.getInteger(context, "id");
        if (!teleport(context.getSource().getPlayerOrException(), id)) {
            return Msg.error(context.getSource(), "xray.not_found", id);
        }
        return 1;
    }
}
