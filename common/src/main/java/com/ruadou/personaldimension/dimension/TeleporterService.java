package com.ruadou.personaldimension.dimension;

import com.ruadou.personaldimension.attachment.TravelPoint;
import com.ruadou.personaldimension.block.entity.TeleporterBlockEntity;
import com.ruadou.personaldimension.platform.Platform;
import com.ruadou.personaldimension.friend.FriendService;
import com.ruadou.personaldimension.friend.Permission;

import java.util.List;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * 维度传送器服务：提供传送器列表查询与跨维度传送逻辑。
 *
 * <p>传送为服务端权威：客户端仅发送目标索引，服务端校验索引有效性、
 * 传送器方块是否仍存在后执行传送，并同步更新 TravelMemory。
 */
public final class TeleporterService {

    private TeleporterService() {}

    /** 获取玩家的传送器列表（不可变副本）。 */
    public static List<TeleporterEntry> getList(MinecraftServer server, UUID owner) {
        return TeleporterData.get(server).get(owner);
    }

    public static List<TeleporterEntry> accessibleList(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return List.of();
        java.util.ArrayList<TeleporterEntry> result = new java.util.ArrayList<>(getList(server, player.getUUID()));
        var owner = Platform.ownerOf(player.level().dimension());
        if (owner.isPresent() && !owner.get().equals(player.getUUID())
            && FriendService.hasPermission(server, owner.get(), player.getUUID(), Permission.ENTER)
            && FriendService.hasPermission(server, owner.get(), player.getUUID(), Permission.USE_PRIVATE_TELEPORTER)) {
            for (TeleporterEntry e : getList(server, owner.get())) {
                if (e.dimension().equals(player.level().dimension())) {
                    result.add(new TeleporterEntry(e.dimension(), e.x(), e.y(), e.z(),
                        "[Shared] " + e.name(), e.note()));
                }
            }
        }
        return List.copyOf(result);
    }

    /** 重命名传送器：更新 name 和 note，并回传刷新后的列表。 */
    public static void rename(MinecraftServer server, UUID owner, int index, String name, String note) {
        TeleporterData.get(server).rename(owner, index, name, note);
        var player = server.getPlayerList().getPlayer(owner);
        if (player != null) {
            Platform.sendTeleporterList(player, TeleporterData.get(server).get(owner));
        }
    }

    /**
     * 传送到指定索引的传送器位置。
     *
     * <p>若玩家当前在自己的个人维度内，先记录 own 返回点再传送，
     * 使后续控制器"进入"按钮可回到此位置。
     * 传送前校验目标方块是否仍存在，不存在则从列表中移除并提示。
     *
     * @return 传送成功返回 true
     */
    public static boolean teleportTo(ServerPlayer player, int index) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }
        List<TeleporterEntry> list = accessibleList(player);
        if (index < 0 || index >= list.size()) {
            player.displayClientMessage(
                Component.translatable("personaldimension.message.teleporter_not_found"), true);
            return false;
        }

        TeleporterEntry target = list.get(index);
        boolean shared = index >= getList(server, player.getUUID()).size();
        ServerLevel dstLevel = server.getLevel(target.dimension());
        if (dstLevel == null) {
            player.displayClientMessage(
                Component.translatable("personaldimension.message.teleporter_dim_unloaded"), true);
            return false;
        }

        // 校验传送器方块是否仍存在
        if (!(dstLevel.getBlockEntity(target.pos()) instanceof TeleporterBlockEntity)) {
            // 方块已被破坏：从列表中移除并提示
            if (!shared) TeleporterData.get(server).remove(player.getUUID(), target.dimension(), target.pos());
            player.displayClientMessage(
                Component.translatable("personaldimension.message.teleporter_missing"), true);
            return false;
        }

        if (shared) {
            var owner = Platform.ownerOf(dstLevel.dimension());
            if (!dstLevel.dimension().equals(player.level().dimension()) || owner.isEmpty()
                || !(dstLevel.getBlockEntity(target.pos()) instanceof TeleporterBlockEntity be)
                || !owner.get().equals(be.getOwner())
                || !FriendService.hasPermission(server, owner.get(), player.getUUID(), Permission.USE_PRIVATE_TELEPORTER)) return false;
        }

        // 若在自己的个人维度内，记录 own 返回点以便"进入"按钮回到此位置
        ResourceKey<Level> playerDim = player.level().dimension();
        if (Platform.isPersonalDimension(playerDim)
            && playerDim.equals(Platform.levelKeyFor(player.getUUID()))) {
            Platform.updateTravelMemory(player, mem -> mem.withOwn(TravelPoint.of(player)));
        }

        // 传送到传送器方块上方
        player.teleportTo(dstLevel,
            target.x() + 0.5D, target.y() + 1.0D, target.z() + 0.5D,
            player.getYRot(), player.getXRot());
        String displayName = target.name().isEmpty()
            ? dimensionDisplayName(target.dimension())
            : target.name();
        player.sendSystemMessage(
            Component.translatable("personaldimension.message.teleported_to", displayName));
        return true;
    }

    /** 维度显示名称：原版维度硬编码中文，其余用路径名。 */
    private static String dimensionDisplayName(ResourceKey<Level> dim) {
        if ("minecraft".equals(dim.location().getNamespace())) {
            return switch (dim.location().getPath()) {
                case "overworld" -> "主世界";
                case "the_nether" -> "下界";
                case "the_end" -> "末地";
                default -> dim.location().getPath();
            };
        }
        return dim.location().getPath();
    }
}
