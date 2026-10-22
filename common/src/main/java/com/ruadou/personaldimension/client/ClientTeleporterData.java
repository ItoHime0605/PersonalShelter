package com.ruadou.personaldimension.client;

import java.util.List;

import com.ruadou.personaldimension.dimension.TeleporterEntry;

/**
 * 客户端侧传送器数据缓存。
 * 保存服务端回传的玩家传送器列表，供控制器传送标签页读取展示。
 * 纯数据缓存，不引用客户端专用类。
 */
public final class ClientTeleporterData {

    private ClientTeleporterData() {}

    // volatile 保证网络线程写入对渲染读取可见
    private static volatile List<TeleporterEntry> teleporters = List.of();

    public static void update(List<TeleporterEntry> list) {
        teleporters = List.copyOf(list);
    }

    public static List<TeleporterEntry> teleporters() {
        return teleporters;
    }

    public static void clear() {
        teleporters = List.of();
    }
}
