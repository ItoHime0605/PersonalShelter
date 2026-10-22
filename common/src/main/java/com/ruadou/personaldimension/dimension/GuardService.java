package com.ruadou.personaldimension.dimension;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.ruadou.personaldimension.friend.FriendService;
import com.ruadou.personaldimension.friend.Permission;
import com.ruadou.personaldimension.platform.Platform;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorStandItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.BrushItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.DebugStickItem;
import net.minecraft.world.item.EndCrystalItem;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.FireChargeItem;
import net.minecraft.world.item.FireworkRocketItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.HangingEntityItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.LeadItem;
import net.minecraft.world.item.MinecartItem;
import net.minecraft.world.item.ProjectileItem;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class GuardService {

    private GuardService() {}

    private static final Map<UUID, Long> LAST_NOTIFY = new ConcurrentHashMap<>();
    private static final long NOTIFY_INTERVAL_MS = 1000L;

    private static final Set<UUID> PENDING_REDIRECT = ConcurrentHashMap.newKeySet();

    public static void markPendingRedirect(UUID playerId) {
        PENDING_REDIRECT.add(playerId);
    }

    public static boolean takePendingRedirect(UUID playerId) {
        return PENDING_REDIRECT.remove(playerId);
    }

    public static void removePendingRedirect(UUID playerId) {
        PENDING_REDIRECT.remove(playerId);
    }

    public static boolean deny(Player player, Permission perm) {
        if (!(player instanceof ServerPlayer sp)) {
            return false;
        }
        ResourceKey<Level> dim = sp.level().dimension();
        if (Platform.isPublicDimension(dim)) {
            return false;
        }
        Optional<UUID> owner = Platform.ownerOf(dim);
        if (owner.isEmpty()) {
            return false;
        }
        if (owner.get().equals(sp.getUUID())) {
            return false;
        }
        MinecraftServer server = sp.getServer();
        if (server == null) {
            return false;
        }
        if (FriendService.hasPermission(server, owner.get(), sp.getUUID(), perm)) {
            return false;
        }
        notifyDenied(sp, perm);
        return true;
    }

    public static void resyncInventory(Player player) {
        if (player instanceof ServerPlayer sp) {
            sp.containerMenu.sendAllDataToRemote();
        }
    }

    public static boolean isContainer(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof Container || be instanceof MenuProvider;
    }

    public static Permission blockInteractPerm(Item item, Level level, BlockPos pos) {
        if (isContainer(level, pos)) {
            return Permission.CONTAINER;
        }
        if (item instanceof BucketItem) {
            return Permission.USE_BUCKET;
        }
        if (isModifyBlockItem(item)) {
            return Permission.MODIFY_BLOCK;
        }
        if (isPlaceEntityItem(item)) {
            return Permission.SPAWN_ENTITY;
        }
        return Permission.INTERACT_BLOCK;
    }

    public static Permission useItemPerm(Item item) {
        if (item instanceof BucketItem) {
            return Permission.USE_BUCKET;
        }
        if (isPlaceEntityItem(item)) {
            return Permission.SPAWN_ENTITY;
        }
        if (isRangedItem(item)) {
            return Permission.USE_PROJECTILE;
        }
        return null;
    }

    private static boolean isRangedItem(Item item) {
        if (item instanceof FireChargeItem) {
            return false;
        }
        return item instanceof ProjectileWeaponItem
            || item instanceof ProjectileItem
            || item instanceof EnderpearlItem
            || item instanceof FishingRodItem;
    }

    private static boolean isModifyBlockItem(Item item) {
        return item instanceof FlintAndSteelItem
            || item instanceof FireChargeItem
            || item instanceof HoeItem
            || item instanceof ShovelItem
            || item instanceof AxeItem
            || item instanceof BoneMealItem
            || item instanceof HoneycombItem
            || item instanceof ShearsItem
            || item instanceof BrushItem
            || item instanceof DebugStickItem;
    }

    private static boolean isPlaceEntityItem(Item item) {
        return item instanceof SpawnEggItem
            || item instanceof BoatItem
            || item instanceof MinecartItem
            || item instanceof ArmorStandItem
            || item instanceof EndCrystalItem
            || item instanceof HangingEntityItem
            || item instanceof LeadItem
            || item instanceof FireworkRocketItem;
    }

    public static Permission attackPerm(Entity target) {
        return target instanceof Player ? Permission.PVP : Permission.ATTACK;
    }

    private static void notifyDenied(ServerPlayer sp, Permission perm) {
        long now = System.currentTimeMillis();
        Long last = LAST_NOTIFY.get(sp.getUUID());
        if (last != null && now - last < NOTIFY_INTERVAL_MS) {
            return;
        }
        LAST_NOTIFY.put(sp.getUUID(), now);
        sp.displayClientMessage(Component.translatable(
            "personaldimension.message.no_permission",
            Component.translatable(perm.translationKey())), true);
    }

    public static void onPlayerLoggedOut(ServerPlayer sp) {
        LAST_NOTIFY.remove(sp.getUUID());
        PENDING_REDIRECT.remove(sp.getUUID());
    }
}
