package com.ruadou.personaldimension.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import com.ruadou.personaldimension.friend.Permission;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

/**
 * 配置系统的通用缓存逻辑（平台无关）。
 * 两端的 Config 类通过实现 ConfigProvider 接口提供配置值，
 * 本类负责缓存和判断逻辑。
 */
public final class ConfigManager {

    private ConfigManager() {}

    public enum BlockListMode {
        WHITELIST("personaldimension.configuration.blockListMode.whitelist"),
        BLACKLIST("personaldimension.configuration.blockListMode.blacklist");

        private final String translationKey;

        BlockListMode(String translationKey) {
            this.translationKey = translationKey;
        }

        public net.minecraft.network.chat.Component getTranslatedName() {
            return net.minecraft.network.chat.Component.translatable(translationKey);
        }
    }

    private static final class CacheEntry {
        final Set<String> set;
        final BlockListMode mode;
        final List<? extends String> sourceRef;

        CacheEntry(Set<String> set, BlockListMode mode, List<? extends String> sourceRef) {
            this.set = set;
            this.mode = mode;
            this.sourceRef = sourceRef;
        }
    }

    private static final CacheEntry EMPTY = new CacheEntry(Set.of(), BlockListMode.BLACKLIST, List.of());
    private static final AtomicReference<CacheEntry> CACHE = new AtomicReference<>(EMPTY);

    private static volatile Supplier<List<? extends String>> blockListSupplier;
    private static volatile Supplier<BlockListMode> modeSupplier;

    // ——— 权限配置（控制权限项是否可用、新建组时是否默认开启）———

    /** 单个权限的配置：是否可用、新建组时是否默认开启。 */
    public static final class PermissionConfig {
        private final boolean enabled;
        private final boolean defaultOn;

        public PermissionConfig(boolean enabled, boolean defaultOn) {
            this.enabled = enabled;
            this.defaultOn = defaultOn;
        }

        public boolean enabled() { return this.enabled; }
        public boolean defaultOn() { return this.defaultOn; }
    }

    /** 权限配置提供者：返回每个权限的配置项（未包含的权限回退到"启用+默认关闭"）。 */
    private static volatile Supplier<Map<Permission, PermissionConfig>> permissionConfigSupplier;

    /**
     * 初始化配置提供者。
     * @param modeSupplier 方块名单模式提供者
     * @param blockListSupplier 方块名单提供者
     */
    public static void init(Supplier<BlockListMode> modeSupplier, Supplier<List<? extends String>> blockListSupplier) {
        ConfigManager.modeSupplier = modeSupplier;
        ConfigManager.blockListSupplier = blockListSupplier;
        CACHE.set(EMPTY);
    }

    /**
     * 初始化权限配置提供者。
     * 各平台 Config 在初始化时传入，未传入时回退到"全部启用、默认关闭"。
     */
    public static void initPermissionConfig(Supplier<Map<Permission, PermissionConfig>> supplier) {
        ConfigManager.permissionConfigSupplier = supplier;
    }

    private static CacheEntry currentEntry() {
        CacheEntry entry = CACHE.get();
        List<? extends String> currentList = blockListSupplier.get();
        BlockListMode currentMode = modeSupplier.get();
        if (entry.sourceRef == currentList && entry.mode == currentMode) {
            return entry;
        }
        Set<String> snapshot = new HashSet<>(currentList.size());
        for (String s : currentList) {
            if (s != null) {
                snapshot.add(s);
            }
        }
        CacheEntry fresh = new CacheEntry(Collections.unmodifiableSet(snapshot), currentMode, currentList);
        if (CACHE.compareAndSet(entry, fresh)) {
            return fresh;
        }
        return CACHE.get();
    }

    public static boolean isBlockAllowed(Block block) {
        String id = BuiltInRegistries.BLOCK.getKey(block).toString();
        CacheEntry entry = currentEntry();
        boolean inList = entry.set.contains(id);
        return entry.mode == BlockListMode.WHITELIST ? inList : !inList;
    }

    public static boolean validateBlockName(Object obj) {
        if (!(obj instanceof String name)) {
            return false;
        }
        ResourceLocation id = ResourceLocation.tryParse(name);
        return id != null && BuiltInRegistries.BLOCK.containsKey(id);
    }

    // ——— 权限配置查询 ———

    /**
     * 权限是否可用（管理员可在配置中关闭某权限项，使其不在权限组中可选、解析时也被过滤）。
     * 未配置时回退到 true（默认启用），保证旧配置升级后行为不变。
     */
    public static boolean isPermissionEnabled(Permission permission) {
        Supplier<Map<Permission, PermissionConfig>> supplier = permissionConfigSupplier;
        if (supplier == null) {
            return true;
        }
        Map<Permission, PermissionConfig> map = supplier.get();
        if (map == null) {
            return true;
        }
        PermissionConfig cfg = map.get(permission);
        return cfg == null || cfg.enabled();
    }

    /**
     * 新建权限组时该权限是否默认开启。
     * 未配置时回退到 false（默认关闭），保证旧配置升级后行为不变。
     */
    public static boolean isPermissionDefaultOn(Permission permission) {
        Supplier<Map<Permission, PermissionConfig>> supplier = permissionConfigSupplier;
        if (supplier == null) {
            return false;
        }
        Map<Permission, PermissionConfig> map = supplier.get();
        if (map == null) {
            return false;
        }
        PermissionConfig cfg = map.get(permission);
        return cfg != null && cfg.defaultOn();
    }

    /**
     * 返回当前可用的权限列表（按枚举顺序，过滤被禁用的项）。
     * 供 UI 渲染与新建组初始化使用。
     */
    public static List<Permission> availablePermissions() {
        List<Permission> list = new ArrayList<>();
        for (Permission p : Permission.values()) {
            if (isPermissionEnabled(p)) {
                list.add(p);
            }
        }
        return list;
    }

    /**
     * 构建新建权限组时应默认开启的权限集合（可用且 defaultOn=true 的权限）。
     */
    public static java.util.EnumSet<Permission> defaultEnabledPermissions() {
        java.util.EnumSet<Permission> set = java.util.EnumSet.noneOf(Permission.class);
        for (Permission p : Permission.values()) {
            if (isPermissionEnabled(p) && isPermissionDefaultOn(p)) {
                set.add(p);
            }
        }
        return set;
    }
}
