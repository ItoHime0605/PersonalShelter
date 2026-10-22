package com.ruadou.personaldimension.client;

import java.util.List;

import net.minecraft.resources.ResourceLocation;

/**
 * 客户端侧"当前区块群系"缓存。
 * 保存服务端回传的、收录器方块所在区块内出现的群系 id 列表，供群系收录器界面展示待选项。
 * 与 {@link ClientCollectedBiomes} 同构：纯数据缓存，不引用客户端专用类。
 */
public final class ClientChunkBiomes {

    private ClientChunkBiomes() {}

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
