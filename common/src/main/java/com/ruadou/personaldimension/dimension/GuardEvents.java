package com.ruadou.personaldimension.dimension;

import java.util.Optional;
import java.util.UUID;

import com.ruadou.personaldimension.block.FriendVisitorTeleporterBlock;
import com.ruadou.personaldimension.block.TeleporterBlock;
import com.ruadou.personaldimension.friend.Permission;

import dev.architectury.event.CompoundEventResult;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.BlockEvent;
import dev.architectury.event.events.common.EntityEvent;
import dev.architectury.event.events.common.InteractionEvent;
import dev.architectury.event.events.common.PlayerEvent;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public final class GuardEvents {

    private GuardEvents() {}

    public static void register() {
        PlayerEvent.PLAYER_JOIN.register(player -> {
            PersonalDimensionService.evictIfUnauthorized(player);
            PersonalDimensionService.sendDimensionInfoToPlayer(player);
        });

        PlayerEvent.PLAYER_QUIT.register(player -> {
            GuardService.onPlayerLoggedOut(player);
        });

        PlayerEvent.PLAYER_RESPAWN.register((player, conqueredEnd, reason) -> {
            PersonalDimensionService.evictIfUnauthorized(player);
        });

        PlayerEvent.CHANGE_DIMENSION.register((player, from, to) -> {
            if (!GuardService.takePendingRedirect(player.getUUID())) {
                return;
            }
            Optional<UUID> owner = ModDimensions.ownerOf(player.level().dimension());
            if (owner.isEmpty()) {
                return;
            }
            MinecraftServer server = player.getServer();
            if (server == null) {
                return;
            }
            FriendVisitorTeleporterService.VisitorTarget target =
                FriendVisitorTeleporterService.getVisitorTarget(server, owner.get(), player.getUUID());
            if (target == null || target.pos() == null) {
                return;
            }
            Vec3 pos = target.pos();
            BlockPos bePos = BlockPos.containing(pos.x, pos.y - 1, pos.z);
            if (!(player.level().getBlockState(bePos).getBlock() instanceof
                  FriendVisitorTeleporterBlock)) {
                return;
            }
            player.teleportTo((ServerLevel) player.level(), pos.x, pos.y, pos.z, player.getYRot(), player.getXRot());
        });

        BlockEvent.BREAK.register((level, pos, state, player, xp) -> {
            if (GuardService.deny(player, Permission.BREAK)) {
                GuardService.resyncInventory(player);
                return EventResult.interruptFalse();
            }
            return EventResult.pass();
        });

        BlockEvent.PLACE.register((level, pos, state, placer) -> {
            if (!(placer instanceof Player player)) {
                return EventResult.pass();
            }
            if (state.getBlock() instanceof FriendVisitorTeleporterBlock
                && ModDimensions.isPublicDimension(level.dimension())) {
                if (player instanceof ServerPlayer sp) {
                    sp.displayClientMessage(
                        Component.translatable("personaldimension.message.cannot_place_visitor_teleporter"), true);
                }
                GuardService.resyncInventory(player);
                return EventResult.interruptFalse();
            }
            if (state.getBlock() instanceof TeleporterBlock
                || state.getBlock() instanceof FriendVisitorTeleporterBlock) {
                Optional<UUID> dimOwner = ModDimensions.ownerOf(level.dimension());
                if (dimOwner.isPresent()
                    && !dimOwner.get().equals(player.getUUID())) {
                    if (player instanceof ServerPlayer sp) {
                        sp.displayClientMessage(
                            Component.translatable(
                                state.getBlock() instanceof FriendVisitorTeleporterBlock
                                    ? "personaldimension.message.cannot_place_visitor_teleporter"
                                    : "personaldimension.message.cannot_place_teleporter"),
                            true);
                    }
                    GuardService.resyncInventory(player);
                    return EventResult.interruptFalse();
                }
            }
            if (GuardService.deny(player, Permission.PLACE)) {
                GuardService.resyncInventory(player);
                return EventResult.interruptFalse();
            }
            return EventResult.pass();
        });

        InteractionEvent.RIGHT_CLICK_BLOCK.register((player, hand, pos, face) -> {
            if (player.level().isClientSide) {
                return EventResult.pass();
            }
            ItemStack stack = player.getItemInHand(hand);
            Permission perm = GuardService.blockInteractPerm(stack.getItem(), player.level(), pos);
            if (GuardService.deny(player, perm)) {
                GuardService.resyncInventory(player);
                return EventResult.interruptFalse();
            }
            return EventResult.pass();
        });

        InteractionEvent.INTERACT_ENTITY.register((player, entity, hand) -> {
            if (player.level().isClientSide) {
                return EventResult.pass();
            }
            if (GuardService.deny(player, Permission.INTERACT_ENTITY)) {
                GuardService.resyncInventory(player);
                return EventResult.interruptFalse();
            }
            return EventResult.pass();
        });

        InteractionEvent.RIGHT_CLICK_ITEM.register((player, hand) -> {
            if (player.level().isClientSide()) {
                return CompoundEventResult.pass();
            }
            ItemStack stack = player.getItemInHand(hand);
            Permission perm = GuardService.useItemPerm(stack.getItem());
            if (perm != null && GuardService.deny(player, perm)) {
                GuardService.resyncInventory(player);
                return CompoundEventResult.interruptFalse(stack);
            }
            return CompoundEventResult.pass();
        });

        PlayerEvent.ATTACK_ENTITY.register((player, level, target, hand, hitResult) -> {
            if (level.isClientSide) {
                return EventResult.pass();
            }
            if (GuardService.deny(player, GuardService.attackPerm(target))) {
                return EventResult.interruptFalse();
            }
            return EventResult.pass();
        });

        EntityEvent.LIVING_HURT.register((entity, source, amount) -> {
            if (source.getEntity() instanceof Player attacker
                && GuardService.deny(attacker, GuardService.attackPerm(entity))) {
                return EventResult.interruptFalse();
            }
            return EventResult.pass();
        });

        PlayerEvent.PICKUP_ITEM_PRE.register((player, entity, stack) -> {
            if (GuardService.deny(player, Permission.PICKUP)) {
                return EventResult.interruptFalse();
            }
            return EventResult.pass();
        });

    }
}
