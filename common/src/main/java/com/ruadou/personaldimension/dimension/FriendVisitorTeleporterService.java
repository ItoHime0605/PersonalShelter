package com.ruadou.personaldimension.dimension;

import java.util.UUID;

import com.ruadou.personaldimension.friend.FriendPermissionData;
import com.ruadou.personaldimension.friend.OwnerFriendConfig;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public final class FriendVisitorTeleporterService {

    private FriendVisitorTeleporterService() {}

    /** 访客传送目标信息：位置 + 是否仅限控制器传送 */
    public record VisitorTarget(Vec3 pos, boolean controllerOnly) {}

    /**
     * 查找访客对应的好友访问传送器信息（位置 + 传送模式）。
     * 按访客权限组匹配传送器，未分配组则用默认组。
     *
     * @return 传送器存在且维度已加载时返回目标信息，否则 null
     */
    public static VisitorTarget getVisitorTarget(MinecraftServer server, UUID owner, UUID visitor) {
        OwnerFriendConfig config = FriendPermissionData.get(server).getConfig(owner);
        if (config == null) {
            return null;
        }
        String visitorGroup = config.friends().get(visitor);
        if (visitorGroup == null || visitorGroup.isEmpty()) {
            visitorGroup = config.defaultGroupName();
        }
        if (visitorGroup == null) {
            return null;
        }
        FriendVisitorTeleporterData.TeleporterEntry entry =
            FriendVisitorTeleporterData.get(server).findByGroup(owner, visitorGroup);
        if (entry == null) {
            return null;
        }
        ServerLevel level = server.getLevel(entry.dimension());
        if (level == null) {
            return null;
        }
        Vec3 pos = new Vec3(entry.pos().getX() + 0.5, entry.pos().getY() + 1, entry.pos().getZ() + 0.5);
        return new VisitorTarget(pos, entry.controllerOnly());
    }

    /** @deprecated 使用 {@link #getVisitorTarget} 替代 */
    @Deprecated(since = "1.0.0", forRemoval = true)
    public static Vec3 getVisitorSpawnPos(MinecraftServer server, UUID owner, UUID visitor) {
        VisitorTarget target = getVisitorTarget(server, owner, visitor);
        return target == null ? null : target.pos();
    }
}
