package com.ruadou.personaldimension.client;

import java.util.List;

import com.ruadou.personaldimension.friend.NamedPlayer;

/**
 * 客户端侧好友系统数据缓存。
 * 保存服务端回传的三类快照：本人维度的好友/权限配置、在线玩家列表、可访问维度列表；
 * 供好友管理 / 权限组 / 访问界面读取展示。纯数据缓存，不引用客户端专用类。
 */
public final class ClientFriendData {

    private ClientFriendData() {}

    // volatile 保证网络线程写入对渲染读取可见
    private static volatile FriendDataSnapshot ownConfig;
    private static volatile List<NamedPlayer> onlinePlayers = List.of();
    private static volatile List<NamedPlayer> visitList = List.of();

    public static void updateConfig(FriendDataSnapshot snapshot) {
        ownConfig = snapshot;
    }

    public static FriendDataSnapshot ownConfig() {
        return ownConfig;
    }

    public static void updateOnline(List<NamedPlayer> players) {
        onlinePlayers = List.copyOf(players);
    }

    public static List<NamedPlayer> onlinePlayers() {
        return onlinePlayers;
    }

    public static void updateVisitList(List<NamedPlayer> players) {
        visitList = List.copyOf(players);
    }

    public static List<NamedPlayer> visitList() {
        return visitList;
    }

    public static void clear() {
        ownConfig = null;
        onlinePlayers = List.of();
        visitList = List.of();
    }
}
