package com.ruadou.personaldimension.fabric;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ruadou.personaldimension.config.ConfigManager;
import com.ruadou.personaldimension.friend.Permission;

import net.minecraft.world.level.block.Block;

/**
 * 模组配置（Fabric 简化实现）。
 * 使用 common 的 ConfigManager 处理缓存逻辑，本类仅提供配置值。
 * <p>注：Fabric 端暂未接入配置文件读取，所有权限默认"启用+默认关闭"，
 * 与 NeoForge 端的默认值保持一致；后续接入配置文件后再同步。
 */
public final class Config {

    private Config() {}

    private static ConfigManager.BlockListMode blockListMode = ConfigManager.BlockListMode.BLACKLIST;
    private static List<String> blockList = List.of("minecraft:bedrock", "minecraft:barrier");

    /** Fabric 端权限配置默认值：全部启用、新建组默认关闭（与 NeoForge 默认一致）。 */
    private static final Map<Permission, ConfigManager.PermissionConfig> PERM_CONFIG;

    static {
        Map<Permission, ConfigManager.PermissionConfig> m = new LinkedHashMap<>();
        for (Permission p : Permission.values()) {
            m.put(p, new ConfigManager.PermissionConfig(true, false));
        }
        PERM_CONFIG = m;
    }

    public static void init() {
        ConfigManager.init(() -> blockListMode, () -> blockList);
        ConfigManager.initPermissionConfig(() -> PERM_CONFIG);
    }

    public static boolean isBlockAllowed(Block block) {
        return ConfigManager.isBlockAllowed(block);
    }

    public static ConfigManager.BlockListMode getBlockListMode() {
        return blockListMode;
    }

    public static List<String> getBlockList() {
        return blockList;
    }
}
