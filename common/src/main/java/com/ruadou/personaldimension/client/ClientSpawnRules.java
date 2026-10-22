package com.ruadou.personaldimension.client;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.ruadou.personaldimension.spawn.SpawnFilterMode;
import com.ruadou.personaldimension.spawn.SpawnSourceScope;

import net.minecraft.resources.ResourceLocation;

/**
 * 客户端侧生物生成规则缓存。
 * 保存服务端回传的名单模式 / 来源范围 / 名单实体，供生物生成控制界面展示与选中判定。
 * 纯数据缓存，仅引用 {@code spawn} 包的纯枚举（两端通用），不引用客户端专用类。
 */
public final class ClientSpawnRules {

    private ClientSpawnRules() {}

    // volatile 保证网络线程写入对渲染线程读取可见
    private static volatile SpawnFilterMode mode = SpawnFilterMode.BLACKLIST;
    private static volatile SpawnSourceScope scope = SpawnSourceScope.ALL_SOURCES;
    private static volatile Set<ResourceLocation> entities = Set.of();

    /** 由 {@code SpawnRulesPacket} 在网络线程写入（枚举以 ordinal 传输，越界回退默认）。 */
    public static void update(int modeOrdinal, int scopeOrdinal, List<ResourceLocation> list) {
        SpawnFilterMode[] modes = SpawnFilterMode.values();
        SpawnSourceScope[] scopes = SpawnSourceScope.values();
        mode = (modeOrdinal >= 0 && modeOrdinal < modes.length) ? modes[modeOrdinal] : SpawnFilterMode.BLACKLIST;
        scope = (scopeOrdinal >= 0 && scopeOrdinal < scopes.length) ? scopes[scopeOrdinal] : SpawnSourceScope.ALL_SOURCES;
        // 不可变副本，与该类其他字段保持 volatile + 不可变模式；LinkedHashSet 保持插入顺序便于稳定展示
        Set<ResourceLocation> snapshot = new LinkedHashSet<>(list);
        entities = Collections.unmodifiableSet(snapshot);
    }

    public static SpawnFilterMode mode() {
        return mode;
    }

    public static SpawnSourceScope scope() {
        return scope;
    }

    public static Set<ResourceLocation> entities() {
        return entities;
    }

    public static void clear() {
        mode = SpawnFilterMode.BLACKLIST;
        scope = SpawnSourceScope.ALL_SOURCES;
        entities = Set.of();
    }
}
