package com.ruadou.personaldimension.client;

import java.util.List;

/**
 * 客户端侧好友访问传送器数据缓存。
 * 保存服务端回传的当前传送器绑定状态和其他传送器已占用的权限组列表。
 */
public final class FriendVisitorTeleporterClientData {

    private FriendVisitorTeleporterClientData() {}

    private static volatile List<String> boundToCurrent = List.of();
    private static volatile List<String> boundToOthers = List.of();
    private static volatile boolean controllerOnly = true;

    public static void update(FriendVisitorTeleporterStatusSnapshot snapshot) {
        boundToCurrent = List.copyOf(snapshot.boundToCurrent());
        boundToOthers = List.copyOf(snapshot.boundToOthers());
        controllerOnly = snapshot.controllerOnly();
    }

    public static List<String> boundToCurrent() {
        return boundToCurrent;
    }

    public static List<String> boundToOthers() {
        return boundToOthers;
    }

    public static boolean controllerOnly() {
        return controllerOnly;
    }

    public static void clear() {
        boundToCurrent = List.of();
        boundToOthers = List.of();
        controllerOnly = true;
    }
}
