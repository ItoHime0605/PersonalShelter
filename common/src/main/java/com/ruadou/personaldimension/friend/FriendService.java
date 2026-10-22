package com.ruadou.personaldimension.friend;

import com.ruadou.personaldimension.config.ConfigManager;
import com.ruadou.personaldimension.platform.Platform;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * 好友与权限领域服务（服务端）。
 *
 * <p>作为网络层/执行层与 {@link FriendPermissionData} 之间的中间层，集中权限解析、配置改动、
 * 在线玩家与可访问维度列表的构建以及玩家名解析；<b>不含</b>传送逻辑。
 */
public final class FriendService {

    private FriendService() {}

    private static FriendPermissionData data(MinecraftServer server) {
        return FriendPermissionData.get(server);
    }

    // ——— 权限解析 ———

    /**
     * 解析 {@code actor} 在 {@code owner} 的个人维度里拥有的权限集合。
     * 主人本人拥有全部权限；其余访客按 {@link OwnerFriendConfig#resolve(UUID)}（含默认组）解析；
     * 主人从未配置过则访客无任何权限（维度默认私有）。
     *
     * <p>无论主人或访客，被配置禁用的权限项都会被过滤掉（管理员级禁用，全局生效）。
     */
    public static EnumSet<Permission> resolve(MinecraftServer server, UUID owner, UUID actor) {
        EnumSet<Permission> resolved;
        if (owner.equals(actor)) {
            resolved = EnumSet.allOf(Permission.class);
        } else {
            OwnerFriendConfig config = data(server).getConfig(owner);
            resolved = config != null ? config.resolve(actor) : EnumSet.noneOf(Permission.class);
        }
        // 过滤被管理员禁用的权限项（即使存档中仍有，也不再生效）
        resolved.retainAll(ConfigManager.availablePermissions());
        return resolved;
    }

    public static boolean hasPermission(MinecraftServer server, UUID owner, UUID actor, Permission perm) {
        return resolve(server, owner, actor).contains(perm);
    }

    // ——— 名称解析 ———

    /** 解析玩家显示名：在线玩家 -> 档案缓存 -> UUID 前 8 位兜底。 */
    public static String resolveName(MinecraftServer server, UUID id) {
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) {
            return online.getGameProfile().getName();
        }
        if (server.getProfileCache() != null) {
            Optional<GameProfile> profile = server.getProfileCache().get(id);
            if (profile.isPresent() && profile.get().getName() != null) {
                return profile.get().getName();
            }
        }
        return id.toString().substring(0, 8);
    }

    // ——— 列表构建 ———

    /** 在线玩家列表（可排除某人，通常排除请求者自己）。 */
    public static List<NamedPlayer> onlinePlayers(MinecraftServer server, UUID exclude) {
        List<NamedPlayer> list = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!p.getUUID().equals(exclude)) {
                list.add(new NamedPlayer(p.getUUID(), p.getGameProfile().getName()));
            }
        }
        return list;
    }

    /**
     * {@code actor} 当前可访问（拥有 {@link Permission#ENTER} 且维度已存在）的他人个人维度列表。
     * 不含 actor 自己的维度（自己的经控制器"进入"按钮往返）。
     */
    public static List<NamedPlayer> visitableFor(MinecraftServer server, UUID actor) {
        List<NamedPlayer> list = new ArrayList<>();
        for (Map.Entry<UUID, OwnerFriendConfig> e : data(server).configs().entrySet()) {
            UUID owner = e.getKey();
            if (owner.equals(actor)) {
                continue;
            }
            if (server.getLevel(Platform.levelKeyFor(owner)) == null) {
                continue;
            }
            if (e.getValue().resolve(actor).contains(Permission.ENTER)) {
                list.add(new NamedPlayer(owner, resolveName(server, owner)));
            }
        }
        return list;
    }

    // ——— 配置改动（均由维度主人自己触发，getOrCreateConfig 保证配置存在并标脏）——

    private static OwnerFriendConfig editable(MinecraftServer server, UUID owner) {
        return data(server).getOrCreateConfig(owner);
    }

    private static void markDirty(MinecraftServer server) {
        data(server).setDirty();
    }

    public static void addFriend(MinecraftServer server, UUID owner, UUID friend) {
        editable(server, owner).addFriend(friend);
        markDirty(server);
    }

    public static void removeFriend(MinecraftServer server, UUID owner, UUID friend) {
        editable(server, owner).removeFriend(friend);
        markDirty(server);
    }

    public static void assignGroup(MinecraftServer server, UUID owner, UUID friend, String groupName) {
        editable(server, owner).assignGroup(friend, groupName);
        markDirty(server);
    }

    public static void createGroup(MinecraftServer server, UUID owner, String name) {
        // 新建组时按配置播种默认权限（可用且 defaultOn=true 的权限）
        editable(server, owner).createGroup(name, ConfigManager.defaultEnabledPermissions());
        markDirty(server);
    }

    public static void renameGroup(MinecraftServer server, UUID owner, String oldName, String newName) {
        editable(server, owner).renameGroup(oldName, newName);
        markDirty(server);
    }

    public static void deleteGroup(MinecraftServer server, UUID owner, String name) {
        editable(server, owner).deleteGroup(name);
        markDirty(server);
    }

    public static void setGroupPermission(MinecraftServer server, UUID owner,
                                          String groupName, Permission permission, boolean enabled) {
        PermissionGroup group = editable(server, owner).group(groupName);
        if (group != null) {
            group.set(permission, enabled);
            markDirty(server);
        }
    }

    public static void setDefaultGroup(MinecraftServer server, UUID owner, String name) {
        editable(server, owner).setDefaultGroup(name);
        markDirty(server);
    }
}
