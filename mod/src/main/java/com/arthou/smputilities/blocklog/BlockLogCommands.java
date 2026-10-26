package com.arthou.smputilities.blocklog;

import com.arthou.smputilities.core.blocklog.BlockLog;
import com.arthou.smputilities.core.perm.Perm;
import com.arthou.smputilities.core.teleport.Location;
import com.arthou.smputilities.core.util.Durations;
import com.arthou.smputilities.mc.Ids;
import com.arthou.smputilities.mc.Mc;
import com.arthou.smputilities.mc.Stacks;
import com.arthou.smputilities.mc.Texts;
import com.arthou.smputilities.server.Lang;
import com.arthou.smputilities.server.Msg;
import com.arthou.smputilities.server.Names;
import com.arthou.smputilities.server.Perms;
import com.arthou.smputilities.server.SuServer;
import com.arthou.smputilities.teleport.Teleports;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** /su log: inspect, lookup, rollback, restore and undo, after CoreProtect's commands. */
public final class BlockLogCommands {
    private static final int PAGE = 10;
    private static final int HISTORY_LINES = 8;
    private static final int DEFAULT_RADIUS = 10;
    private static final UUID CONSOLE = new UUID(0, 0);

    private static final Map<UUID, List<BlockLog.Entry>> RESULTS = new HashMap<>();
    private static final Map<UUID, Undo> UNDO = new HashMap<>();
    private static Job running;

    private record Undo(String dimension, BlockLog.Plan plan) {
    }

    private BlockLogCommands() {
    }

    private static boolean enabled() {
        return SuServer.config().features.blockLog;
    }

    static void onServerStop() {
        RESULTS.clear();
        UNDO.clear();
        running = null;
    }

    public static LiteralArgumentBuilder<CommandSourceStack> branch() {
        return Commands.literal("log")
            .requires(source -> enabled() && Perms.has(source, Perm.STAFF_BLOCKLOG))
            .then(Commands.literal("inspect").executes(BlockLogCommands::inspect))
            .then(Commands.literal("lookup").executes(context -> lookup(context, ""))
                .then(Commands.argument("params", StringArgumentType.greedyString())
                    .executes(context -> lookup(context, StringArgumentType.getString(context, "params")))))
            .then(Commands.literal("page").then(Commands.argument("page", IntegerArgumentType.integer(1)).executes(context -> {
                showPage(context.getSource(), IntegerArgumentType.getInteger(context, "page"));
                return 1;
            })))
            .then(Commands.literal("tp").requires(Perms.require(Perm.STAFF_TELEPORT))
                .then(Commands.argument("entry", IntegerArgumentType.integer(1)).executes(BlockLogCommands::teleport)))
            .then(Commands.literal("rollback").requires(Perms.require(Perm.STAFF_ROLLBACK))
                .then(Commands.argument("params", StringArgumentType.greedyString())
                    .executes(context -> change(context, true))))
            .then(Commands.literal("restore").requires(Perms.require(Perm.STAFF_ROLLBACK))
                .then(Commands.argument("params", StringArgumentType.greedyString())
                    .executes(context -> change(context, false))))
            .then(Commands.literal("undo").requires(Perms.require(Perm.STAFF_ROLLBACK)).executes(BlockLogCommands::undo));
    }

    private static UUID key(CommandSourceStack source) {
        ServerPlayer player = Mc.player(source);
        return player == null ? CONSOLE : player.getUUID();
    }

    private static String language(CommandSourceStack source) {
        ServerPlayer player = Mc.player(source);
        return player == null ? Lang.EN : Lang.languageOf(player);
    }

    private static int inspect(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        return Msg.ok(context.getSource(), BlockLogService.toggleInspect(player) ? "log.inspect.on" : "log.inspect.off");
    }

    // -------------------------------------------------------------- params

    /** Parsed "u:<players> t:<time> r:<radius|#world> a:<actions>". */
    private record Params(Set<String> actors, long since, int radius, Set<BlockLog.Action> actions, String error) {
    }

    private static Params parse(CommandSourceStack source, String text) {
        Set<String> actors = null;
        long since = 0;
        int radius = -2;
        Set<BlockLog.Action> actions = null;
        for (String token : text.trim().split("\\s+")) {
            if (token.isEmpty()) {
                continue;
            }
            int colon = token.indexOf(':');
            String name = colon < 0 ? "" : token.substring(0, colon).toLowerCase(Locale.ROOT);
            String value = colon < 0 ? token : token.substring(colon + 1);
            switch (name) {
                case "u", "user", "users", "p" -> {
                    actors = new HashSet<>();
                    for (String user : value.split(",")) {
                        if (user.startsWith("#")) {
                            actors.add(user.toLowerCase(Locale.ROOT));
                            continue;
                        }
                        Optional<Names.Target> target = Names.resolve(source.getServer(), user, Mc.player(source));
                        if (target.isEmpty()) {
                            return error(Lang.format(language(source), "player.not_found", user));
                        }
                        actors.add(target.get().id().toString());
                    }
                }
                case "t", "time" -> {
                    Long millis = Durations.parse(value);
                    if (millis == null || millis <= 0) {
                        return error(Lang.format(language(source), "log.bad_time", value));
                    }
                    since = System.currentTimeMillis() - millis;
                }
                case "r", "radius" -> {
                    if (value.equalsIgnoreCase("#world") || value.equalsIgnoreCase("#global") || value.equalsIgnoreCase("global")) {
                        radius = -1;
                    } else {
                        try {
                            radius = Math.max(0, Math.min(1000, Integer.parseInt(value)));
                        } catch (NumberFormatException e) {
                            return error(Lang.format(language(source), "log.bad_param", token));
                        }
                    }
                }
                case "a", "action" -> {
                    actions = EnumSet.noneOf(BlockLog.Action.class);
                    for (String action : value.toLowerCase(Locale.ROOT).split(",")) {
                        switch (action) {
                            case "break", "-block" -> actions.add(BlockLog.Action.BREAK);
                            case "place", "+block" -> actions.add(BlockLog.Action.PLACE);
                            case "block" -> {
                                actions.add(BlockLog.Action.BREAK);
                                actions.add(BlockLog.Action.PLACE);
                            }
                            case "explode", "explosion" -> actions.add(BlockLog.Action.EXPLODE);
                            case "container", "item", "items" -> {
                                actions.add(BlockLog.Action.ADD);
                                actions.add(BlockLog.Action.TAKE);
                            }
                            case "+container", "add" -> actions.add(BlockLog.Action.ADD);
                            case "-container", "take", "remove" -> actions.add(BlockLog.Action.TAKE);
                            default -> {
                                return error(Lang.format(language(source), "log.bad_param", token));
                            }
                        }
                    }
                }
                default -> {
                    return error(Lang.format(language(source), "log.bad_param", token));
                }
            }
        }
        return new Params(actors, since, radius, actions, null);
    }

    private static Params error(String message) {
        return new Params(null, 0, 0, null, message);
    }

    private static BlockLog.Query query(CommandSourceStack source, Params params, int defaultRadius, int limit) {
        Vec3 at = source.getPosition();
        //#if MC>=1.19.4
        BlockPos center = BlockPos.containing(at.x, at.y, at.z);
        //#else
        //$$ BlockPos center = new BlockPos(at);
        //#endif
        int radius = params.radius() == -2 ? defaultRadius : params.radius();
        return new BlockLog.Query(Ids.key(source.getLevel().dimension()), center.getX(), center.getY(), center.getZ(), radius,
            params.since(), params.actors(), params.actions(), limit);
    }

    // -------------------------------------------------------------- lookup

    private static int lookup(CommandContext<CommandSourceStack> context, String text) {
        CommandSourceStack source = context.getSource();
        BlockLog log = BlockLogService.log();
        Params params = parse(source, text);
        if (log == null) {
            return 0;
        }
        if (params.error() != null) {
            Mc.reply(source, Texts.literal(params.error()).withStyle(ChatFormatting.RED));
            return 0;
        }
        // Without a player or a radius, look around the caller.
        BlockLog.Query query = query(source, params, params.actors() == null ? DEFAULT_RADIUS : -1, 5000);
        UUID viewer = key(source);
        MinecraftServer server = source.getServer();
        log.lookup(query, found -> server.execute(() -> {
            RESULTS.put(viewer, found);
            showPage(source, 1);
        }));
        return 1;
    }

    private static void showPage(CommandSourceStack source, int page) {
        List<BlockLog.Entry> found = RESULTS.get(key(source));
        if (found == null || found.isEmpty()) {
            Msg.info(source, "log.none");
            return;
        }
        int pages = (found.size() + PAGE - 1) / PAGE;
        int shown = Math.min(Math.max(1, page), pages);
        Msg.info(source, "log.lookup.header", found.size(), shown, pages);
        ServerPlayer viewer = Mc.player(source);
        for (int i = (shown - 1) * PAGE; i < Math.min(found.size(), shown * PAGE); i++) {
            Mc.reply(source, line(source, found.get(i), i + 1, viewer != null && Perms.has(viewer, Perm.STAFF_TELEPORT)));
        }
        if (shown < pages) {
            String command = "/su log page " + (shown + 1);
            Mc.reply(source, Texts.hover(Texts.clickRun(Texts.literal("[" + Lang.format(language(source), "log.next") + "]")
                .withStyle(ChatFormatting.AQUA), command), Texts.literal(command)));
        }
    }

    /** History of one block, newest first, for the inspector. */
    static void showHistory(ServerPlayer player, String dimension, BlockPos pos) {
        BlockLog log = BlockLogService.log();
        if (log == null) {
            return;
        }
        BlockLog.Query query = new BlockLog.Query(dimension, pos.getX(), pos.getY(), pos.getZ(), 0, 0, null, null, 200);
        MinecraftServer server = Mc.server(player);
        log.lookup(query, found -> server.execute(() -> {
            if (player.isRemoved()) {
                return;
            }
            RESULTS.put(player.getUUID(), found);
            if (found.isEmpty()) {
                Msg.info(player, "log.history.none", pos.getX(), pos.getY(), pos.getZ());
                return;
            }
            Msg.info(player, "log.history.header", pos.getX(), pos.getY(), pos.getZ(), found.size());
            CommandSourceStack source = player.createCommandSourceStack();
            for (int i = 0; i < Math.min(HISTORY_LINES, found.size()); i++) {
                Mc.send(player, line(source, found.get(i), i + 1, false));
            }
            if (found.size() > HISTORY_LINES) {
                String command = "/su log page 1";
                Mc.send(player, Texts.hover(Texts.clickRun(Texts.literal("[" + Lang.tr(player, "log.more") + "]")
                    .withStyle(ChatFormatting.AQUA), command), Texts.literal(command)));
            }
        }));
    }

    private static MutableComponent line(CommandSourceStack source, BlockLog.Entry entry, int number, boolean teleport) {
        String language = language(source);
        String age = Durations.format(Math.max(1000, System.currentTimeMillis() - entry.time())).toLowerCase(Locale.ROOT);
        String what = switch (entry.action()) {
            case BREAK -> Lang.format(language, "log.line.break", entry.actorName(), BlockTexts.shortName(entry.before()));
            case PLACE -> Lang.format(language, "log.line.place", entry.actorName(), BlockTexts.shortName(entry.after()),
                BlockTexts.shortName(entry.before()));
            case EXPLODE -> Lang.format(language, "log.line.explode", BlockTexts.shortName(entry.before()));
            case ADD -> Lang.format(language, "log.line.add", entry.actorName(), entry.count(), BlockTexts.itemName(entry.before()));
            case TAKE -> Lang.format(language, "log.line.take", entry.actorName(), entry.count(), BlockTexts.itemName(entry.before()));
        };
        ChatFormatting color = switch (entry.action()) {
            case BREAK, TAKE, EXPLODE -> ChatFormatting.RED;
            case PLACE, ADD -> ChatFormatting.GREEN;
        };
        MutableComponent line = Texts.literal(Lang.format(language, "log.ago", age) + " · ").withStyle(ChatFormatting.GRAY)
            .append(Texts.literal(what).withStyle(color))
            .append(Texts.literal(" (" + entry.x() + " " + entry.y() + " " + entry.z() + ")").withStyle(ChatFormatting.DARK_GRAY));
        if (teleport) {
            String command = "/su log tp " + number;
            line.append(Texts.literal(" ")).append(Texts.hover(Texts.clickRun(Texts.literal("[TP]").withStyle(ChatFormatting.AQUA), command),
                Texts.literal(command)));
        }
        return line;
    }

    private static int teleport(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        List<BlockLog.Entry> found = RESULTS.get(player.getUUID());
        int index = IntegerArgumentType.getInteger(context, "entry") - 1;
        if (found == null || index >= found.size()) {
            return Msg.error(context.getSource(), "log.none");
        }
        BlockLog.Entry entry = found.get(index);
        return Teleports.teleport(player, new Location(entry.dimension(), entry.x() + 0.5, entry.y() + 1, entry.z() + 0.5, player.getYRot(), player.getXRot()), true) ? 1 : 0;
    }

    // ------------------------------------------------- rollback and restore

    private static int change(CommandContext<CommandSourceStack> context, boolean rollback) {
        CommandSourceStack source = context.getSource();
        BlockLog log = BlockLogService.log();
        if (log == null) {
            return 0;
        }
        if (running != null) {
            return Msg.error(source, "log.busy");
        }
        Params params = parse(source, StringArgumentType.getString(context, "params"));
        if (params.error() != null) {
            Mc.reply(source, Texts.literal(params.error()).withStyle(ChatFormatting.RED));
            return 0;
        }
        if (params.since() <= 0) {
            return Msg.error(source, "log.need_time");
        }
        int max = Math.max(1, BlockLogService.config().maxChanges);
        BlockLog.Query query = query(source, params, params.actors() == null ? DEFAULT_RADIUS : -1, max + 1);
        UUID owner = key(source);
        MinecraftServer server = source.getServer();
        String dimension = query.dimension();
        log.lookup(query, found -> server.execute(() -> {
            if (found.size() > max) {
                Msg.error(source, "log.too_many", found.size() + "+", max);
                return;
            }
            BlockLog.Plan plan = rollback ? BlockLog.rollback(found) : BlockLog.restore(found);
            if (plan.isEmpty()) {
                Msg.info(source, "log.none");
                return;
            }
            if (running != null) {
                Msg.error(source, "log.busy");
                return;
            }
            Msg.info(source, rollback ? "log.rollback.start" : "log.restore.start", plan.blocks().size(), plan.items().size());
            running = new Job(source, owner, dimension, plan, rollback ? "log.rollback.done" : "log.restore.done");
            SuServer serverContext = SuServer.get();
            if (serverContext != null) {
                ServerPlayer actor = Mc.player(source);
                serverContext.log(actor, rollback ? "blocklog.rollback" : "blocklog.restore", null, "", StringArgumentType.getString(context, "params"));
            }
        }));
        return 1;
    }

    private static int undo(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (running != null) {
            return Msg.error(source, "log.busy");
        }
        Undo undo = UNDO.remove(key(source));
        if (undo == null) {
            return Msg.error(source, "log.undo.none");
        }
        Msg.info(source, "log.undo.start", undo.plan().blocks().size(), undo.plan().items().size());
        running = new Job(source, key(source), undo.dimension(), undo.plan(), "log.undo.done");
        return 1;
    }

    /** Called every tick: applies the running rollback a slice at a time. */
    public static void tick(MinecraftServer server) {
        Job job = running;
        if (job == null) {
            return;
        }
        try {
            if (job.step(server, Math.max(1, BlockLogService.config().changesPerTick))) {
                running = null;
                UNDO.put(job.owner, new Undo(job.dimension, new BlockLog.Plan(job.undoBlocks, job.undoItems)));
                Msg.ok(job.source, job.doneKey, job.changed, job.skipped);
            }
        } catch (RuntimeException e) {
            running = null;
            Msg.error(job.source, "log.failed", e.toString());
        }
    }

    private static final class Job {
        final CommandSourceStack source;
        final UUID owner;
        final String dimension;
        final String doneKey;
        final Iterator<BlockLog.BlockChange> blocks;
        final Iterator<BlockLog.ItemChange> items;
        final List<BlockLog.BlockChange> undoBlocks = new ArrayList<>();
        final List<BlockLog.ItemChange> undoItems = new ArrayList<>();
        int changed;
        int skipped;

        Job(CommandSourceStack source, UUID owner, String dimension, BlockLog.Plan plan, String doneKey) {
            this.source = source;
            this.owner = owner;
            this.dimension = dimension;
            this.doneKey = doneKey;
            this.blocks = plan.blocks().iterator();
            this.items = plan.items().iterator();
        }

        /** @return true when done */
        boolean step(MinecraftServer server, int budget) {
            ResourceKey<Level> key = Ids.dimension(dimension);
            ServerLevel level = key == null ? null : server.getLevel(key);
            if (level == null) {
                return true;
            }
            RegistryAccess registries = server.registryAccess();
            for (int i = 0; i < budget; i++) {
                if (blocks.hasNext()) {
                    count(applyBlock(level, blocks.next(), registries));
                } else if (items.hasNext()) {
                    count(applyItems(level, items.next(), registries));
                } else {
                    return true;
                }
            }
            return !blocks.hasNext() && !items.hasNext();
        }

        private void count(boolean done) {
            if (done) {
                changed++;
            } else {
                skipped++;
            }
        }

        private boolean applyBlock(ServerLevel level, BlockLog.BlockChange change, RegistryAccess registries) {
            BlockState target = BlockTexts.state(change.state());
            if (target == null) {
                return false;
            }
            BlockPos pos = new BlockPos(change.x(), change.y(), change.z());
            BlockState current = level.getBlockState(pos);
            BlockState expected = BlockTexts.state(change.expected());
            if (expected != null && current.getBlock() != expected.getBlock()) {
                return false;
            }
            BlockEntity existing = current.hasBlockEntity() ? level.getBlockEntity(pos) : null;
            String currentData = BlockTexts.blockEntity(existing, registries);
            if (existing instanceof Container container) {
                // Nothing drops when the container goes; what it held is kept for the undo.
                container.clearContent();
            }
            level.setBlock(pos, target, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            if (!change.data().isEmpty()) {
                BlockEntity restored = BlockTexts.blockEntity(change.data(), pos, target, registries);
                if (restored != null) {
                    level.setBlockEntity(restored);
                }
            }
            undoBlocks.add(new BlockLog.BlockChange(change.x(), change.y(), change.z(), BlockTexts.state(current), currentData, change.state()));
            return true;
        }

        private boolean applyItems(ServerLevel level, BlockLog.ItemChange change, RegistryAccess registries) {
            BlockPos pos = new BlockPos(change.x(), change.y(), change.z());
            List<Container> containers = containers(level, pos);
            ItemStack stack = BlockTexts.item(change.item(), change.count(), registries);
            if (containers.isEmpty() || stack.isEmpty()) {
                return false;
            }
            int moved = change.add() ? insert(containers, stack) : remove(containers, stack, change.count());
            if (moved > 0) {
                undoItems.add(new BlockLog.ItemChange(change.x(), change.y(), change.z(), change.item(), moved, !change.add()));
            }
            return moved == change.count();
        }
    }

    /** The container at pos, and the other half of a double chest. */
    private static List<Container> containers(ServerLevel level, BlockPos pos) {
        List<Container> found = new ArrayList<>();
        if (level.getBlockEntity(pos) instanceof Container container) {
            found.add(container);
        }
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            Direction side = ChestBlock.getConnectedDirection(state);
            if (level.getBlockEntity(pos.relative(side)) instanceof Container other) {
                found.add(other);
            }
        }
        return found;
    }

    private static int insert(List<Container> containers, ItemStack stack) {
        int left = stack.getCount();
        for (Container container : containers) {
            for (int slot = 0; slot < container.getContainerSize() && left > 0; slot++) {
                ItemStack there = container.getItem(slot);
                if (!there.isEmpty() && Stacks.same(there, stack)) {
                    int room = Math.min(there.getMaxStackSize(), container.getMaxStackSize()) - there.getCount();
                    if (room > 0) {
                        int add = Math.min(room, left);
                        there.grow(add);
                        left -= add;
                    }
                }
            }
            for (int slot = 0; slot < container.getContainerSize() && left > 0; slot++) {
                if (container.getItem(slot).isEmpty() && container.canPlaceItem(slot, stack)) {
                    ItemStack put = stack.copy();
                    put.setCount(Math.min(left, Math.min(stack.getMaxStackSize(), container.getMaxStackSize())));
                    container.setItem(slot, put);
                    left -= put.getCount();
                }
            }
            container.setChanged();
        }
        return stack.getCount() - left;
    }

    private static int remove(List<Container> containers, ItemStack stack, int count) {
        int left = count;
        for (Container container : containers) {
            for (int slot = 0; slot < container.getContainerSize() && left > 0; slot++) {
                ItemStack there = container.getItem(slot);
                if (!there.isEmpty() && Stacks.same(there, stack)) {
                    int take = Math.min(there.getCount(), left);
                    there.shrink(take);
                    if (there.isEmpty()) {
                        container.setItem(slot, ItemStack.EMPTY);
                    }
                    left -= take;
                }
            }
            container.setChanged();
        }
        return count - left;
    }
}
