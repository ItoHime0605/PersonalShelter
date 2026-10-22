package com.ruadou.personaldimension.client;

/**
 * 客户端侧个人维度信息缓存。
 * 保存服务端回传的个人维度快照（天气、时刻、昼夜循环等），供控制器界面读取展示；
 * 数据为纯记录，不引用客户端专用类。
 */
public final class ClientDimensionInfo {

    private ClientDimensionInfo() {}

    // volatile 保证网络线程写入对渲染读取可见
    private static volatile DimensionInfoSnapshot info;

    public static void update(DimensionInfoSnapshot snapshot) {
        info = snapshot;
    }

    public static DimensionInfoSnapshot info() {
        return info;
    }

    public static void clear() {
        info = null;
    }
}
