package com.ruadou.personaldimension.neoforge;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ruadou.personaldimension.config.ConfigManager;
import com.ruadou.personaldimension.friend.Permission;

import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.TranslatableEnum;

/**
 * 模组配置（NeoForge 实现）。
 * 使用 common 的 ConfigManager 处理缓存逻辑，本类仅提供配置值。
 */
@SuppressWarnings("null")
public final class Config {

    private Config() {}

    public enum BlockListMode implements TranslatableEnum {
        WHITELIST("personaldimension.configuration.blockListMode.whitelist"),
        BLACKLIST("personaldimension.configuration.blockListMode.blacklist");

        private final String translationKey;

        BlockListMode(String translationKey) {
            this.translationKey = translationKey;
        }

        @Override
        public net.minecraft.network.chat.Component getTranslatedName() {
            return net.minecraft.network.chat.Component.translatable(translationKey);
        }

        public ConfigManager.BlockListMode toCommon() {
            return this == WHITELIST ? ConfigManager.BlockListMode.WHITELIST : ConfigManager.BlockListMode.BLACKLIST;
        }
    }

    private static final ModConfigSpec.Builder COMMON_BUILDER = new ModConfigSpec.Builder();
    private static final ModConfigSpec.Builder CLIENT_BUILDER = new ModConfigSpec.Builder();
    private static final ModConfigSpec.Builder SERVER_BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.EnumValue<BlockListMode> BLOCK_LIST_MODE;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BLOCK_LIST;

    /** 每个权限的 enabled 配置（是否在权限组中可选）。 */
    private static final Map<Permission, ModConfigSpec.BooleanValue> PERM_ENABLED = new LinkedHashMap<>();
    /** 每个权限的 defaultOn 配置（新建组时是否默认开启）。 */
    private static final Map<Permission, ModConfigSpec.BooleanValue> PERM_DEFAULT_ON = new LinkedHashMap<>();

    static {
        COMMON_BUILDER.push("dimension_terrain");
        COMMON_BUILDER.push("block_config");

        BLOCK_LIST_MODE = COMMON_BUILDER
                .comment("方块名单模式：BLACKLIST=列表内禁用、其余放行；WHITELIST=仅列表内允许")
                .translation("personaldimension.configuration.dimension_terrain.block_config.blockListMode")
                .defineEnum("blockListMode", BlockListMode.BLACKLIST);

        BLOCK_LIST = COMMON_BUILDER
                .comment("方块名单（资源 ID，如 minecraft:stone），按 blockListMode 生效")
                .translation("personaldimension.configuration.dimension_terrain.block_config.blockList")
                .defineListAllowEmpty("blockList",
                        List.of("minecraft:bedrock", "minecraft:barrier"),
                        () -> "minecraft:stone",
                        Config::validateBlockName);

        COMMON_BUILDER.pop();
        COMMON_BUILDER.pop();

        // ——— 好友权限组配置：两个子组（可用 / 默认开启），每组包含全部权限开关 ———
        COMMON_BUILDER.push("friend_permissions");

        // 子组1：可用 —— 控制每项权限是否在权限组中可选
        COMMON_BUILDER.push("enabled");
        for (Permission p : Permission.values()) {
            String id = p.id();
            PERM_ENABLED.put(p, COMMON_BUILDER
                    .translation("personaldimension.configuration.friend_permissions.enabled." + id)
                    .define(id, true));
        }
        COMMON_BUILDER.pop();

        // 子组2：默认开启 —— 控制新建权限组时各项权限的默认状态
        COMMON_BUILDER.push("defaultOn");
        for (Permission p : Permission.values()) {
            String id = p.id();
            PERM_DEFAULT_ON.put(p, COMMON_BUILDER
                    .translation("personaldimension.configuration.friend_permissions.defaultOn." + id)
                    .define(id, false));
        }
        COMMON_BUILDER.pop();

        COMMON_BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = COMMON_BUILDER.build();
    public static final ModConfigSpec CLIENT_SPEC = CLIENT_BUILDER.build();
    public static final ModConfigSpec SERVER_SPEC = SERVER_BUILDER.build();

    public static void init() {
        ConfigManager.init(() -> BLOCK_LIST_MODE.get().toCommon(), () -> BLOCK_LIST.get());
        ConfigManager.initPermissionConfig(Config::buildPermissionConfigMap);
    }

    /** 从配置值构建权限配置映射，供 ConfigManager 查询。 */
    private static Map<Permission, ConfigManager.PermissionConfig> buildPermissionConfigMap() {
        Map<Permission, ConfigManager.PermissionConfig> map = new LinkedHashMap<>();
        for (Permission p : Permission.values()) {
            boolean enabled = PERM_ENABLED.get(p).get();
            boolean defaultOn = PERM_DEFAULT_ON.get(p).get();
            map.put(p, new ConfigManager.PermissionConfig(enabled, defaultOn));
        }
        return map;
    }

    private static boolean validateBlockName(Object obj) {
        return ConfigManager.validateBlockName(obj);
    }

    public static boolean isBlockAllowed(Block block) {
        return ConfigManager.isBlockAllowed(block);
    }
}
