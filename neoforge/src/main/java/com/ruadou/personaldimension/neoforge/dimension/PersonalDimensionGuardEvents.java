package com.ruadou.personaldimension.neoforge.dimension;

import java.util.Optional;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.ruadou.personaldimension.shelter.ShelterUpgradeRules;
import java.util.UUID;

import com.ruadou.personaldimension.dimension.FriendVisitorTeleporterService;
import com.ruadou.personaldimension.dimension.GuardService;
import com.ruadou.personaldimension.dimension.ModDimensions;
import com.ruadou.personaldimension.dimension.PersonalDimensionService;
import com.ruadou.personaldimension.friend.FriendService;
import com.ruadou.personaldimension.friend.Permission;
import com.ruadou.personaldimension.dimension.PersonalDimensionData;
import com.ruadou.personaldimension.shelter.ShelterBoundary;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.event.level.BlockEvent;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NeoForge 端的守卫事件（服务端）：
 *  - 维度切换前置检查（EntityTravelToDimensionEvent）
 *  - 容器关闭后同步背包（防止视觉残留）
 *
 * <p>物品丢弃拦截已迁移到客户端 ScreenEvent（ClientDropGuard），
 * 在发包前就取消操作，避免"丢出去再消失"的视觉不同步。
 */
@SuppressWarnings("null")
public final class PersonalDimensionGuardEvents {

    private static final Logger LOGGER = LoggerFactory.getLogger("Personaldimension");

    private static final Map<UUID, String> LAST_DIMENSION = new ConcurrentHashMap<>();

    private PersonalDimensionGuardEvents() {}

    /**
     * 维度切换前置检查：NeoForge 提供 EntityTravelToDimensionEvent 可取消，
     * Architectury 暂无前置维度切换事件，故此逻辑保留在 NeoForge 端。
     */
    @SubscribeEvent
    public static void onTravelToDimension(EntityTravelToDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) {
            return;
        }
        Optional<UUID> owner = ModDimensions.ownerOf(event.getDimension());
        if (owner.isEmpty() || owner.get().equals(sp.getUUID())) {
            return;
        }
        MinecraftServer server = sp.getServer();
        if (server == null) {
            return;
        }
        if (!FriendService.hasPermission(server, owner.get(), sp.getUUID(), Permission.ENTER)) {
            event.setCanceled(true);
            sp.displayClientMessage(
                Component.translatable("personaldimension.message.visit_denied"), true);
            return;
        }
        if (PersonalDimensionService.consumeControllerVisit(sp.getUUID())) {
            return;
        }
        FriendVisitorTeleporterService.VisitorTarget target =
            FriendVisitorTeleporterService.getVisitorTarget(server, owner.get(), sp.getUUID());
        if (target == null) {
            return;
        }
        if (target.controllerOnly()) {
            return;
        }
        GuardService.markPendingRedirect(sp.getUUID());
    }

    /**
     * Server-side square land boundary. Applies to players and automation alike.
     * Never infer a dimension's level from the client or a packet.
     */
    private static boolean outsideShelter(net.minecraft.world.level.LevelAccessor level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) return false;
        if (ModDimensions.ownerOf(serverLevel.dimension()).isEmpty()) return false;
        PersonalDimensionData data = serverLevel.getDataStorage().computeIfAbsent(
            PersonalDimensionData.FACTORY, PersonalDimensionData.ID);
        return !ShelterBoundary.canModifyBlock(data.getShelterLevel(), pos.getX(), pos.getZ());
    }

    @SubscribeEvent
    public static void onShelterBreak(BlockEvent.BreakEvent event) {
        if (outsideShelter(event.getLevel(), event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onShelterPlace(BlockEvent.EntityPlaceEvent event) {
        if (outsideShelter(event.getLevel(), event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onShelterMultiPlace(BlockEvent.EntityMultiPlaceEvent event) {
        if (outsideShelter(event.getLevel(), event.getPos()) ||
            event.getReplacedBlockSnapshots().stream().anyMatch(
                snapshot -> outsideShelter(event.getLevel(), snapshot.getPos()))) {
            event.setCanceled(true);
        }
    }

    /**
     * Server-authoritative movement guard. Dynamic dimensions can share vanilla world-border
     * state, so a per-player movement check is needed in addition to the visual border.
     * Creative flight and visitors are restricted exactly like survival movement.
     */
    @SubscribeEvent
    public static void onShelterPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        if (ModDimensions.ownerOf(level.dimension()).isEmpty()) {
            LAST_DIMENSION.remove(player.getUUID());
            return;
        }

        // Test mode: execute vanilla /worldborder commands exactly once per dimension entry.
        // Commands run as a level-4 server command source at the player's current dimension.
        // Vanilla may still share the border across dimensions; this experiment does not
        // guarantee a per-dimension border and must be tested in a disposable world.
        String dimensionKey = level.dimension().location().toString();
        if (!dimensionKey.equals(LAST_DIMENSION.get(player.getUUID()))) {
            LAST_DIMENSION.put(player.getUUID(), dimensionKey);
            PersonalDimensionData entryData = level.getDataStorage().computeIfAbsent(
                PersonalDimensionData.FACTORY, PersonalDimensionData.ID);
            int borderSize = ShelterUpgradeRules.size(entryData.getShelterLevel());
            var source = player.createCommandSourceStack().withPermission(4).withSuppressedOutput();
            var commands = player.getServer().getCommands();
            commands.performPrefixedCommand(source, "worldborder center 0.5 0.5");
            commands.performPrefixedCommand(source, "worldborder set " + borderSize);
            LOGGER.info("[Personal Shelter] Applied /worldborder on entry: player={}, dimension={}, size={}",
                player.getGameProfile().getName(), dimensionKey, borderSize);
        }

        PersonalDimensionData data = level.getDataStorage().computeIfAbsent(
            PersonalDimensionData.FACTORY, PersonalDimensionData.ID);
        int shelterLevel = data.getShelterLevel();
        double x = player.getX();
        double z = player.getZ();
        if (ShelterBoundary.containsPosition(shelterLevel, x, z)) return;

        double safeX = ShelterBoundary.clampCoordinate(shelterLevel, x);
        double safeZ = ShelterBoundary.clampCoordinate(shelterLevel, z);
        player.connection.teleport(safeX, player.getY(), safeZ, player.getYRot(), player.getXRot());
        player.setDeltaMovement(0.0D, player.getDeltaMovement().y, 0.0D);
    }

    /**
     * 容器关闭后同步背包：
     * 如果玩家无丢弃权限且光标有物品，把光标物品放回背包。
     * 防止关闭容器时光标物品被原版丢弃导致消失。
     */
    @SubscribeEvent
    public static void onContainerClose(PlayerContainerEvent.Close event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            // 检查光标是否有物品
            var carried = sp.containerMenu.getCarried();
            if (carried != null && !carried.isEmpty()
                && GuardService.deny(sp, Permission.DROP_ITEM)) {
                // 把光标物品放回背包
                sp.getInventory().placeItemBackInInventory(carried);
                sp.containerMenu.setCarried(net.minecraft.world.item.ItemStack.EMPTY);
                LOGGER.info("[NeoForge-容器关闭] 光标物品已放回背包: player={}", sp.getName());
            }
            GuardService.resyncInventory(sp);
        }
    }
}
