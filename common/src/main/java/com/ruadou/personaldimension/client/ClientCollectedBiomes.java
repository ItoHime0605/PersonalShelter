package com.ruadou.personaldimension.client;

import java.util.List;

import net.minecraft.resources.ResourceLocation;

/**
 * 客户端侧已收录群系缓存。
 * 保存服务端回传的群系 id 列表，供群系改造器界面展示候选。纯数据缓存，不引用客户端专用类。
 */
public final class ClientCollectedBiomes {

    private ClientCollectedBiomes() {}

    // volatile 保证网络线程写入对渲染线程读取可见
    private static volatile List<ResourceLocation> biomes = List.of();

    public static void update(List<ResourceLocation> list) {
        biomes = List.copyOf(list);
    }

    public static List<ResourceLocation> biomes() {
        return biomes;
    }

    public static void clear() {
        biomes = List.of();
    }
}
