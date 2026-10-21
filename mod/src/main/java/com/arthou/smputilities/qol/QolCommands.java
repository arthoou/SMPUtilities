package com.arthou.smputilities.qol;

import com.arthou.smputilities.core.config.SuConfig;
import com.arthou.smputilities.core.perm.Perm;
import com.arthou.smputilities.server.Msg;
import com.arthou.smputilities.server.Perms;
import com.arthou.smputilities.server.SuServer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** /hat and /sit. */
public final class QolCommands {
    public static final String SEAT_TAG = "smputilities.seat";
    /** Seat entity id -> rider. */
    private static final Map<UUID, UUID> SEATS = new ConcurrentHashMap<>();

    private QolCommands() {
    }

    private static boolean enabled(java.util.function.Predicate<SuConfig.Qol> flag) {
        SuConfig config = SuServer.config();
        return config.features.qol && flag.test(config.qol);
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("hat")
            .requires(source -> enabled(qol -> qol.hat) && Perms.has(source, Perm.HAT))
            .executes(QolCommands::hat));
        dispatcher.register(Commands.literal("sit")
            .requires(source -> enabled(qol -> qol.sit) && Perms.has(source, Perm.SIT))
            .executes(QolCommands::sit));
    }

    private static int hat(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ItemStack hand = player.getMainHandItem();
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        if (hand.isEmpty()) {
            return Msg.error(context.getSource(), "hat.empty_hand");
        }
        if (!head.isEmpty() && !player.isCreative() && EnchantmentHelper.has(head, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE)) {
            return Msg.error(context.getSource(), "hat.binding");
        }
        ItemStack newHead = hand.split(1);
        player.setItemSlot(EquipmentSlot.HEAD, newHead);
        if (!head.isEmpty()) {
            if (hand.isEmpty()) {
                player.setItemInHand(InteractionHand.MAIN_HAND, head);
            } else if (!player.getInventory().add(head)) {
                player.drop(head, false);
            }
        }
        player.inventoryMenu.broadcastChanges();
        return Msg.ok(context.getSource(), "hat.done");
    }

    private static int sit(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (player.isPassenger()) {
            player.stopRiding();
            return 1;
        }
        if (!player.onGround() || player.isFallFlying() || player.isSwimming()) {
            return Msg.error(context.getSource(), "sit.not_here");
        }
        ServerLevel level = player.serverLevel();
        Display.TextDisplay seat = new Display.TextDisplay(EntityType.TEXT_DISPLAY, level);
        seat.setPos(player.getX(), player.getY() - 0.35, player.getZ());
        seat.addTag(SEAT_TAG);
        seat.setInvulnerable(true);
        level.addFreshEntity(seat);
        if (!player.startRiding(seat, true)) {
            seat.discard();
            return Msg.error(context.getSource(), "sit.not_here");
        }
        SEATS.put(seat.getUUID(), player.getUUID());
        return Msg.info(context.getSource(), "sit.done");
    }

    /** Removes seats whose rider stood up or left. Called every tick. */
    public static void tick(net.minecraft.server.MinecraftServer server) {
        if (SEATS.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, UUID> entry : SEATS.entrySet()) {
            ServerPlayer rider = server.getPlayerList().getPlayer(entry.getValue());
            Entity vehicle = rider == null ? null : rider.getVehicle();
            if (vehicle == null || !vehicle.getUUID().equals(entry.getKey())) {
                SEATS.remove(entry.getKey());
                for (ServerLevel level : server.getAllLevels()) {
                    Entity seat = level.getEntity(entry.getKey());
                    if (seat != null) {
                        seat.discard();
                        break;
                    }
                }
            }
        }
    }

    /** Seats left behind by a crash are removed as soon as their chunk loads. */
    public static boolean isOrphanSeat(Entity entity) {
        return entity.getTags().contains(SEAT_TAG) && !SEATS.containsKey(entity.getUUID());
    }
}
