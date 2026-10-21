package com.arthou.smputilities.troll;

import com.arthou.smputilities.mc.Ids;
import com.arthou.smputilities.mc.Mc;
import com.arthou.smputilities.mc.Texts;
import com.arthou.smputilities.SmpUtilities;
import com.arthou.smputilities.chat.ChatService;
import com.arthou.smputilities.core.config.SuConfig;
import com.arthou.smputilities.core.perm.Perm;
import com.arthou.smputilities.server.Lang;
import com.arthou.smputilities.server.Names;
import com.arthou.smputilities.server.Perms;
import com.arthou.smputilities.server.SuServer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Staff pranks, after TrollBoss' set (its code is GPL-3.0, so none of it is
 * used: every prank is written for this mod). Blocks a prank places are put
 * back when it ends, when the player leaves and when the server stops, and
 * only ever replace air; summoned mobs are removed after a few seconds and
 * cannot be hurt. Damage, kills and kicks are a category of their own that
 * the config can turn off.
 */
public final class TrollService {
    /** Each category has its own config switch. */
    public enum Category { EXPLOSIONS, SOUND, JUMPSCARE, VISUAL, MOVEMENT, CHAT, WORLD, HARM, KICK }

    public enum Action {
        EXPLOSION(Category.EXPLOSIONS), LIGHTNING(Category.EXPLOSIONS), SOUND(Category.SOUND), JUMPSCARE(Category.JUMPSCARE),
        NAUSEA(Category.VISUAL), DARKNESS(Category.VISUAL), LEVITATE(Category.MOVEMENT), SLOW(Category.MOVEMENT), GLOW(Category.VISUAL),
        TINY_JUMP(Category.MOVEMENT), SPIN(Category.MOVEMENT), LAUNCH(Category.MOVEMENT), FAKE_MESSAGE(Category.CHAT),
        FAKE_JOIN(Category.CHAT), FAKE_QUIT(Category.CHAT), TITLE_ACTION(Category.JUMPSCARE), TITLE_DEATH(Category.JUMPSCARE),
        // TrollBoss' pranks
        CREEPER(Category.EXPLOSIONS), ABDUCT(Category.JUMPSCARE), TRAMPLE(Category.VISUAL), SQUIDRAIN(Category.VISUAL),
        PUMPKINHEAD(Category.VISUAL), DRUG(Category.VISUAL), INFECT(Category.VISUAL), FREEZE(Category.VISUAL), NOMINE(Category.VISUAL),
        PUSH(Category.MOVEMENT), TURN(Category.MOVEMENT), RANDOMTP(Category.MOVEMENT), BORDER(Category.MOVEMENT), SWITCH(Category.MOVEMENT),
        SKY(Category.WORLD), BURY(Category.WORLD), TRAP(Category.WORLD), WEBTRAP(Category.WORLD),
        FAKEOP(Category.CHAT), FAKEDEOP(Category.CHAT), SPAM(Category.CHAT), GARBAGE(Category.CHAT),
        TROLLKICK(Category.KICK), CRASH(Category.KICK), FAKERESTART(Category.KICK),
        BURN(Category.HARM), BOLT(Category.HARM), BOOM(Category.HARM), HURT(Category.HARM), STARVE(Category.HARM), SPANK(Category.HARM),
        FREEFALL(Category.HARM), ANVIL(Category.HARM), GOKILL(Category.HARM);

        private final Category category;

        Action(Category category) {
            this.category = category;
        }

        public Category category() {
            return category;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Action parse(String value) {
            try {
                return valueOf(value.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    /** Something a prank does later: every tick until it is done, then once at the end (also when cut short). */
    private static final class Task {
        final UUID player;
        final String key;
        int ticksLeft;
        final Runnable eachTick;
        final Runnable atEnd;

        Task(UUID player, String key, int ticks, Runnable eachTick, Runnable atEnd) {
            this.player = player;
            this.key = key;
            this.ticksLeft = ticks;
            this.eachTick = eachTick;
            this.atEnd = atEnd;
        }
    }

    /** A block a prank put where there was air; it goes back to air unless someone changed it since. */
    private record Placed(ServerLevel level, BlockPos pos, BlockState original, BlockState placed) {
    }

    private static final String THIS_ACTION_SOUND = SmpUtilities.MOD_ID + ":action.thisaction";
    private static final String CRASH_MESSAGE = "Internal Exception: java.net.SocketException: Connection reset";
    private static final Random RANDOM = new Random();
    // Server thread only.
    private static final List<Task> TASKS = new ArrayList<>();
    private static final Set<UUID> GARBLED = new HashSet<>();

    private TrollService() {
    }

    /** @return false when this kind of prank is disabled in the config */
    public static boolean allowed(Action action) {
        SuConfig config = SuServer.config();
        if (!config.features.troll) {
            return false;
        }
        SuConfig.Troll troll = config.troll;
        return switch (action.category()) {
            case EXPLOSIONS -> troll.fakeExplosions;
            case SOUND -> troll.fakeSounds;
            case JUMPSCARE -> troll.jumpscares;
            case VISUAL -> troll.visualEffects;
            case MOVEMENT -> troll.funnyEffects;
            case CHAT -> troll.fakeMessages;
            case WORLD -> troll.worldChanges;
            case HARM -> troll.harmful;
            case KICK -> troll.kicks;
        };
    }

    /**
     * Plays a prank on the target. Players with smputilities.troll.bypass
     * can only be pranked by themselves.
     *
     * @return null when it ran, otherwise a translation key (its argument is the target's name)
     */
    public static String apply(Action action, ServerPlayer target, String argument, ServerPlayer actor) {
        if (actor != null && actor != target && Perms.has(target, Perm.TROLL_BYPASS)) {
            return "troll.bypass";
        }
        if (isBusy(target, action)) {
            return "troll.busy";
        }
        return run(action, target, argument == null ? "" : argument.trim(), actor);
    }

    /** Same title to everyone (DUSMP /thisaction and /death). */
    public static void broadcastTitle(MinecraftServer server, Action action, String argument) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            run(action, player, argument == null ? "" : argument, null);
        }
    }

    private static String run(Action action, ServerPlayer target, String argument, ServerPlayer actor) {
        int seconds = Math.max(1, SuServer.config().troll.maxEffectSeconds);
        ServerLevel level = Mc.level(target);
        Vec3 behind = target.position().subtract(target.getLookAngle().multiply(2.5, 0, 2.5));
        switch (action) {
            case EXPLOSION -> {
                Mc.particle(target, ParticleTypes.EXPLOSION_EMITTER, behind.x, behind.y + 1, behind.z);
                sound(target, "minecraft:entity.generic.explode", behind, 4.0F, 1.0F);
            }
            case LIGHTNING -> Mc.visualLightning(level, behind.x, behind.y, behind.z);
            case SOUND -> {
                String sound = !argument.isEmpty() && Ids.parse(argument) != null ? argument : "minecraft:entity.creeper.primed";
                sound(target, sound, behind, 1.0F, 1.0F);
            }
            case JUMPSCARE -> {
                Mc.particle(target, ParticleTypes.ELDER_GUARDIAN, target.getX(), target.getY(), target.getZ());
                sound(target, "minecraft:entity.elder_guardian.curse", target.position(), 1.0F, 1.0F);
                effect(target, "minecraft:darkness", 3, 0);
            }
            case NAUSEA -> effect(target, "minecraft:nausea", Math.min(seconds, 10), 0);
            case DARKNESS -> effect(target, "minecraft:darkness", Math.min(seconds, 8), 0);
            case LEVITATE -> {
                effect(target, "minecraft:levitation", Math.min(seconds, 3), 0);
                effect(target, "minecraft:slow_falling", Math.min(seconds, 3) + 8, 0);
            }
            case SLOW -> effect(target, "minecraft:slowness", seconds, 2);
            case GLOW -> effect(target, "minecraft:glowing", seconds, 0);
            case TINY_JUMP -> effect(target, "minecraft:jump_boost", seconds, 4);
            case SPIN -> later(target, "spin", 16, () -> rotate(target, 22.5F), null);
            case LAUNCH -> {
                effect(target, "minecraft:slow_falling", 12, 0);
                push(target, 0, 1.1, 0);
            }
            case FAKE_MESSAGE -> {
                String[] parts = argument.split(" ", 2);
                if (parts.length == 2) {
                    Mc.send(target, Texts.literal("<" + parts[0] + "> " + parts[1]));
                }
            }
            case FAKE_JOIN, FAKE_QUIT -> {
                String name = argument.isEmpty() ? "Herobrine" : argument;
                Mc.send(target, Texts.empty().append(Texts.translatable(
                    action == Action.FAKE_JOIN ? "multiplayer.player.joined" : "multiplayer.player.left", name).withStyle(ChatFormatting.YELLOW)));
            }
            case TITLE_ACTION -> title(target, Texts.literal("This action will have consequences...").withStyle(ChatFormatting.RED),
                argument.isEmpty() ? Texts.empty() : Texts.literal(argument), THIS_ACTION_SOUND);
            case TITLE_DEATH -> {
                String[] parts = argument.split(" ");
                String name = parts.length > 0 && !parts[0].isEmpty() ? parts[0] : target.getGameProfile().getName();
                ChatFormatting color = parts.length > 1 ? ChatFormatting.getByName(parts[1]) : ChatFormatting.RED;
                if (color == null || !color.isColor()) {
                    color = ChatFormatting.RED;
                }
                title(target, Texts.literal(name + " -1").withStyle(color), Texts.empty(), "minecraft:entity.wither.spawn");
            }
            case CREEPER -> TrollEntities.harmlessCreeper(level, behind);
            case ABDUCT -> {
                Mc.particle(target, ParticleTypes.ELDER_GUARDIAN, target.getX(), target.getY(), target.getZ());
                sound(target, "minecraft:entity.elder_guardian.curse", target.position(), 0.7F, 1.0F);
                effect(target, "minecraft:levitation", 4, 2);
                effect(target, "minecraft:darkness", 5, 0);
                later(target, "abduct", 80, () -> beam(level, target), () -> effect(target, "minecraft:slow_falling", 15, 0));
            }
            case TRAMPLE -> {
                for (int i = 0; i < 5; i++) {
                    double angle = i * Math.PI * 2 / 5;
                    TrollEntities.summon(level, TrollEntities.Kind.COW, target.getX() + Math.cos(angle) * 1.5, target.getY() + 3 + i,
                        target.getZ() + Math.sin(angle) * 1.5, 15 * 20, TASKS_SINK);
                }
                sound(target, "minecraft:entity.cow.ambient", target.position(), 1.0F, 0.8F);
            }
            case SQUIDRAIN -> later(target, "squidrain", 60, () -> {
                if (RANDOM.nextInt(4) == 0) {
                    TrollEntities.summon(level, TrollEntities.Kind.SQUID, target.getX() + RANDOM.nextGaussian() * 2, target.getY() + 15,
                        target.getZ() + RANDOM.nextGaussian() * 2, 8 * 20, TASKS_SINK);
                }
            }, null);
            case PUMPKINHEAD -> {
                ItemStack old = target.getItemBySlot(EquipmentSlot.HEAD);
                target.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
                // The old helmet goes back into the inventory, or at their feet when it is full.
                if (!old.isEmpty() && !target.getInventory().add(old) && !old.isEmpty()) {
                    target.drop(old, false);
                }
                sound(target, "minecraft:block.pumpkin.carve", target.position(), 1.0F, 1.0F);
            }
            case DRUG -> {
                effect(target, "minecraft:jump_boost", seconds, 1);
                effect(target, "minecraft:nausea", seconds, 1);
                effect(target, "minecraft:speed", seconds, 1);
            }
            case INFECT -> {
                effect(target, "minecraft:blindness", seconds, 1);
                effect(target, "minecraft:nausea", seconds, 1);
                effect(target, "minecraft:hunger", seconds, 0);
            }
            case FREEZE -> {
                Vec3 at = target.position();
                effect(target, "minecraft:blindness", Math.min(seconds, 5), 0);
                sound(target, "minecraft:block.glass.break", at, 1.0F, 0.6F);
                later(target, "freeze", seconds * 20, () -> {
                    if (target.position().distanceToSqr(at) > 0.01) {
                        target.connection.teleport(at.x, at.y, at.z, target.getYRot(), target.getXRot());
                    }
                }, null);
            }
            case NOMINE -> effect(target, "minecraft:mining_fatigue", seconds, 2);
            case PUSH -> {
                Vec3 look = target.getLookAngle();
                push(target, look.x * 2.5, 0.6, look.z * 2.5);
            }
            case TURN -> rotate(target, 180.0F);
            case RANDOMTP -> later(target, "randomtp", 6 * 20, null, null, 20, () -> {
                int x = target.getBlockX() + RANDOM.nextInt(25) - 12;
                int z = target.getBlockZ() + RANDOM.nextInt(25) - 12;
                if (Mc.hasChunk(level, x >> 4, z >> 4)) {
                    Mc.teleport(target, level, x + 0.5, TrollWorld.surface(level, x, z), z + 0.5, target.getYRot(), target.getXRot());
                    sound(target, "minecraft:entity.enderman.teleport", target.position(), 1.0F, 1.0F);
                }
            });
            case BORDER -> {
                Vec3 edge = TrollWorld.nearestEdge(level.getWorldBorder(), target.getX(), target.getZ());
                int x = (int) Math.floor(edge.x);
                int z = (int) Math.floor(edge.z);
                Mc.teleport(target, level, edge.x, TrollWorld.surface(level, x, z), edge.z, target.getYRot(), target.getXRot());
            }
            case SWITCH -> {
                ServerPlayer other = Names.resolve(Mc.server(target), argument, actor).map(Names.Target::online).orElse(null);
                if (other == null || other == target) {
                    return "troll.switch_missing";
                }
                ServerLevel otherLevel = Mc.level(other);
                Vec3 here = target.position();
                Vec3 there = other.position();
                float yaw = target.getYRot();
                float pitch = target.getXRot();
                Mc.teleport(target, otherLevel, there.x, there.y, there.z, other.getYRot(), other.getXRot());
                Mc.teleport(other, level, here.x, here.y, here.z, yaw, pitch);
            }
            case SKY -> {
                Vec3 from = target.position();
                int x = target.getBlockX();
                int z = target.getBlockZ();
                int y = Math.min(TrollWorld.maxY(level) - 3, target.getBlockY() + 60);
                List<Placed> placed = new ArrayList<>();
                place(level, new BlockPos(x, y - 1, z), Blocks.GLASS.defaultBlockState(), placed);
                Mc.teleport(target, level, x + 0.5, y, z + 0.5, target.getYRot(), target.getXRot());
                later(target, "sky", seconds * 20, null, () -> {
                    restore(placed);
                    if (!target.hasDisconnected() && Mc.level(target) == level) {
                        Mc.teleport(target, level, from.x, from.y, from.z, target.getYRot(), target.getXRot());
                    }
                });
            }
            case BURY -> {
                Vec3 from = target.position();
                // Resistance V cancels the suffocation while they are inside the ground.
                effect(target, "minecraft:resistance", seconds + 2, 4);
                Mc.teleport(target, level, from.x, from.y - 3, from.z, target.getYRot(), target.getXRot());
                later(target, "bury", seconds * 20, null, () -> {
                    if (!target.hasDisconnected() && Mc.level(target) == level) {
                        Mc.teleport(target, level, from.x, from.y, from.z, target.getYRot(), target.getXRot());
                    }
                });
            }
            case TRAP, WEBTRAP -> {
                BlockPos feet = target.blockPosition();
                List<Placed> placed = new ArrayList<>();
                if (action == Action.TRAP) {
                    BlockState glass = Blocks.GLASS.defaultBlockState();
                    for (int dy = 0; dy <= 1; dy++) {
                        place(level, feet.offset(1, dy, 0), glass, placed);
                        place(level, feet.offset(-1, dy, 0), glass, placed);
                        place(level, feet.offset(0, dy, 1), glass, placed);
                        place(level, feet.offset(0, dy, -1), glass, placed);
                    }
                    place(level, feet.offset(0, 2, 0), glass, placed);
                    place(level, feet.offset(0, -1, 0), glass, placed);
                    target.connection.teleport(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, target.getYRot(), target.getXRot());
                } else {
                    place(level, feet, Blocks.COBWEB.defaultBlockState(), placed);
                    place(level, feet.above(), Blocks.COBWEB.defaultBlockState(), placed);
                }
                later(target, action.id(), seconds * 20, null, () -> restore(placed));
            }
            case FAKEOP, FAKEDEOP -> Mc.send(target, Texts.translatable("chat.type.admin", "Server",
                Texts.translatable(action == Action.FAKEOP ? "commands.op.success" : "commands.deop.success", target.getGameProfile().getName()))
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            case SPAM -> {
                int count = Math.max(1, Math.min(50, number(argument, 20)));
                for (int i = 0; i < count; i++) {
                    Mc.send(target, gibberish(8 + RANDOM.nextInt(30)));
                }
            }
            case GARBAGE -> {
                boolean on = GARBLED.add(target.getUUID());
                if (!on) {
                    GARBLED.remove(target.getUUID());
                }
                return on ? null : "troll.garbage_off";
            }
            case TROLLKICK -> target.connection.disconnect(Texts.literal(Lang.tr(target, "troll.kick_message")));
            case CRASH -> target.connection.disconnect(Texts.literal(CRASH_MESSAGE));
            case FAKERESTART -> later(target, "fakerestart", 10 * 20, null, () -> {
                if (!target.hasDisconnected()) {
                    target.connection.disconnect(Texts.translatable("multiplayer.disconnect.server_shutdown"));
                }
            }, 20, () -> {
                int left = (remaining(target, "fakerestart") - 1) / 20 + 1;
                title(target, Texts.literal(Lang.tr(target, "troll.restart_title")).withStyle(ChatFormatting.RED),
                    Texts.literal(Lang.tr(target, "troll.restart_in", left)), "minecraft:block.note_block.bass");
            });
            case BURN -> TrollWorld.ignite(target, 5);
            case BOLT -> {
                Mc.visualLightning(level, target.getX(), target.getY(), target.getZ());
                TrollWorld.ignite(target, 8);
                wound(target, 5);
            }
            case BOOM -> TrollWorld.explode(level, target.getX(), target.getY(), target.getZ(), 2.5F);
            case HURT -> later(target, "hurt", 6 * 25, null, null, 25, () -> {
                wound(target, 2);
                sound(target, "minecraft:entity.player.hurt", target.position(), 1.0F, 1.0F);
            });
            case STARVE -> later(target, "starve", 10 * 20, null, null, 20, () -> {
                target.getFoodData().setFoodLevel(Math.max(0, target.getFoodData().getFoodLevel() - 2));
                sound(target, "minecraft:entity.player.burp", target.position(), 0.6F, 0.6F);
            });
            case SPANK -> {
                wound(target, 4);
                Vec3 look = target.getLookAngle();
                push(target, -look.x * 0.8, 0.5, -look.z * 0.8);
                sound(target, "minecraft:entity.player.attack.strong", target.position(), 1.0F, 1.0F);
                Mc.send(target, Texts.literal(Lang.tr(target, "troll.spanked")).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            }
            case FREEFALL -> {
                int y = Math.min(TrollWorld.maxY(level) - 2, target.getBlockY() + 40);
                Mc.teleport(target, level, target.getX(), y, target.getZ(), target.getYRot(), target.getXRot());
            }
            case ANVIL -> {
                BlockPos at = target.blockPosition().above(12);
                if (!level.getBlockState(at).isAir()) {
                    return "troll.no_room";
                }
                FallingBlockEntity anvil = TrollWorld.fallingAnvil(level, at);
                later(target, "anvil", 10 * 20, null, () -> TrollWorld.removeLandedAnvil(level, anvil));
            }
            case GOKILL -> later(target, "gokill", 5 * 20, null, () -> {
                if (!target.hasDisconnected() && target.isAlive()) {
                    TrollWorld.kill(target);
                }
            }, 20, () -> {
                int left = (remaining(target, "gokill") - 1) / 20 + 1;
                title(target, Texts.literal("☠ " + left).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
                    Texts.literal(Lang.tr(target, "troll.doom")), "minecraft:block.note_block.basedrum");
            });
        }
        return null;
    }

    // ------------------------------------------------------------- chat

    /** A garbled player's chat line, or null when they are not garbled. */
    public static String garble(ServerPlayer player, String text) {
        if (!GARBLED.contains(player.getUUID())) {
            return null;
        }
        return gibberish(Math.max(4, Math.min(80, text.length()))).getString();
    }

    private static MutableComponent gibberish(int length) {
        String letters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!?#%&*";
        MutableComponent line = Texts.empty();
        StringBuilder word = new StringBuilder();
        for (int i = 0; i < length; i++) {
            word.append(RANDOM.nextInt(7) == 0 ? ' ' : letters.charAt(RANDOM.nextInt(letters.length())));
        }
        return line.append(Texts.literal(word.toString()).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(RANDOM.nextInt(0xFFFFFF)))));
    }

    // ------------------------------------------------------------- tasks

    private static boolean isBusy(ServerPlayer player, Action action) {
        for (Task task : TASKS) {
            if (player.getUUID().equals(task.player) && task.key.equals(action.id())) {
                return true;
            }
        }
        return false;
    }

    private static int remaining(ServerPlayer player, String key) {
        for (Task task : TASKS) {
            if (player.getUUID().equals(task.player) && task.key.equals(key)) {
                return task.ticksLeft;
            }
        }
        return 0;
    }

    private static void later(ServerPlayer player, String key, int ticks, Runnable eachTick, Runnable atEnd) {
        TASKS.add(new Task(player.getUUID(), key, ticks, eachTick, atEnd));
    }

    /** Like {@link #later(ServerPlayer, String, int, Runnable, Runnable)}, plus {@code step} every {@code interval} ticks from the start. */
    private static void later(ServerPlayer player, String key, int ticks, Runnable eachTick, Runnable atEnd, int interval, Runnable step) {
        int[] counter = {0};
        TASKS.add(new Task(player.getUUID(), key, ticks, () -> {
            if (eachTick != null) {
                eachTick.run();
            }
            if (++counter[0] % interval == 0) {
                step.run();
            }
        }, atEnd));
        step.run();
    }

    /** Summoned mobs: removed when their time is up (no player). */
    private static final TrollEntities.Sink TASKS_SINK = (entity, ticks) -> TASKS.add(new Task(null, "summon", ticks, null, entity::discard));

    public static void tick(MinecraftServer server) {
        if (TASKS.isEmpty()) {
            return;
        }
        for (Task task : new ArrayList<>(TASKS)) {
            ServerPlayer player = task.player == null ? null : server.getPlayerList().getPlayer(task.player);
            if (task.player != null && player == null) {
                // Left the server: finish right away (blocks back, teleports skipped).
                finish(task);
                continue;
            }
            if (--task.ticksLeft <= 0) {
                finish(task);
            } else if (task.eachTick != null) {
                task.eachTick.run();
            }
        }
    }

    private static void finish(Task task) {
        TASKS.remove(task);
        if (task.atEnd != null) {
            task.atEnd.run();
        }
    }

    public static void forget(ServerPlayer player) {
        GARBLED.remove(player.getUUID());
        for (Task task : new ArrayList<>(TASKS)) {
            if (player.getUUID().equals(task.player)) {
                finish(task);
            }
        }
    }

    /** The server is stopping: everything placed goes back before the worlds are saved. */
    public static void stop(MinecraftServer server) {
        for (Task task : new ArrayList<>(TASKS)) {
            finish(task);
        }
        TASKS.clear();
        GARBLED.clear();
    }

    // ----------------------------------------------------------- helpers

    private static void place(ServerLevel level, BlockPos pos, BlockState state, List<Placed> placed) {
        BlockState original = level.getBlockState(pos);
        if (original.isAir() && TrollWorld.inside(level, pos)) {
            level.setBlock(pos, state, 3);
            placed.add(new Placed(level, pos, original, state));
        }
    }

    private static void restore(List<Placed> placed) {
        for (Placed block : placed) {
            if (block.level().getBlockState(block.pos()) == block.placed()) {
                block.level().setBlock(block.pos(), block.original(), 3);
            }
        }
        placed.clear();
    }

    /** Health down by {@code amount}, never below half a heart. */
    private static void wound(ServerPlayer player, float amount) {
        if (!player.isCreative() && !player.isSpectator()) {
            player.setHealth(Math.max(1.0F, player.getHealth() - amount));
        }
    }

    private static void push(ServerPlayer player, double x, double y, double z) {
        player.setDeltaMovement(player.getDeltaMovement().add(x, y, z));
        player.hurtMarked = true;
    }

    private static void rotate(ServerPlayer player, float degrees) {
        player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot() + degrees, player.getXRot());
    }

    /** The abduction's beam of light under the player. */
    private static void beam(ServerLevel level, ServerPlayer player) {
        for (int i = 0; i < 4; i++) {
            Mc.particle(player, ParticleTypes.END_ROD, player.getX() + RANDOM.nextGaussian() * 0.4, player.getY() - RANDOM.nextDouble() * 6,
                player.getZ() + RANDOM.nextGaussian() * 0.4);
        }
    }

    private static int number(String text, int fallback) {
        try {
            return text.isEmpty() ? fallback : Integer.parseInt(text.split(" ")[0]);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static void title(ServerPlayer player, Component title, Component subtitle, String soundId) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 12 * 20, 20));
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        player.connection.send(new ClientboundSetTitleTextPacket(title));
        Mc.playSound(player, soundId, SoundSource.MASTER, 1.0F, 1.0F);
    }

    /** Effects by id, so renamed fields (JUMP became JUMP_BOOST) and missing ones (darkness before 1.19) need no code. */
    private static void effect(ServerPlayer player, String effectId, int seconds, int amplifier) {
        MobEffectInstance effect = Mc.effect(effectId, seconds * 20, amplifier);
        if (effect != null) {
            player.addEffect(effect);
        }
    }

    private static void sound(ServerPlayer player, String soundId, Vec3 at, float volume, float pitch) {
        Mc.playSound(player, soundId, SoundSource.HOSTILE, at.x, at.y, at.z, volume, pitch);
    }

    /** Helper for chat-based fake presence lines broadcast to everyone. */
    public static void fakePresenceForAll(ServerPlayer reference, boolean join) {
        ChatService.broadcastPresence(reference, join, true);
    }
}
