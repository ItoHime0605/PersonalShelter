package com.ruadou.personaldimension.friend;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.ruadou.personaldimension.config.ConfigManager;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 好友与权限配置的全局持久化载体。
 *
 * <p>好友/权限需在维度主人<b>离线时也能被查询</b>（访客在其维度里操作要判权限），
 * 故不挂玩家 attachment，而是全局 {@link SavedData}，存于主世界的
 * {@code DimensionDataStorage}，按<b>维度主人 UUID</b>索引每份 {@link OwnerFriendConfig}。
 *
 * <p><b>线程约束</b>：仅服务端主线程访问，使用普通 {@link LinkedHashMap}。
 */
public class FriendPermissionData extends SavedData {

    public static final String ID = "personal_dimension_friends";

    private static final String TAG_ENTRIES = "Entries";
    private static final String TAG_OWNER = "Owner";
    private static final String TAG_CONFIG = "Config";

    // 第三参 DataFixTypes 传 null：本数据无需 Mojang 数据修复器（用原版 3 参构造，跨加载器通用）。
    public static final SavedData.Factory<FriendPermissionData> FACTORY =
        new SavedData.Factory<>(FriendPermissionData::new, FriendPermissionData::load, null);

    private final Map<UUID, OwnerFriendConfig> configs = new LinkedHashMap<>();

    public FriendPermissionData() {}

    /** 从主世界存档获取（不存在则创建）全局好友数据。 */
    public static FriendPermissionData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, ID);
    }

    /** 只读获取某主人的配置；未配置返回 null（表示该维度尚未开放，仅主人可用）。 */
    public OwnerFriendConfig getConfig(UUID owner) {
        return this.configs.get(owner);
    }

    /** 获取或创建某主人的配置（用于主人编辑；创建时按配置播种默认组权限并标脏）。 */
    public OwnerFriendConfig getOrCreateConfig(UUID owner) {
        OwnerFriendConfig config = this.configs.get(owner);
        if (config == null) {
            // 首次创建时按配置播种默认组的初始权限
            config = OwnerFriendConfig.createDefault(ConfigManager.defaultEnabledPermissions());
            this.configs.put(owner, config);
            this.setDirty();
        }
        return config;
    }

    public Map<UUID, OwnerFriendConfig> configs() {
        return this.configs;
    }

    private static FriendPermissionData load(CompoundTag tag, HolderLookup.Provider registries) {
        FriendPermissionData data = new FriendPermissionData();
        ListTag entries = tag.getList(TAG_ENTRIES, Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            UUID owner = entry.getUUID(TAG_OWNER);
            OwnerFriendConfig config = OwnerFriendConfig.load(entry.getCompound(TAG_CONFIG));
            data.configs.put(owner, config);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        for (Map.Entry<UUID, OwnerFriendConfig> e : this.configs.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID(TAG_OWNER, e.getKey());
            entry.put(TAG_CONFIG, e.getValue().save());
            entries.add(entry);
        }
        tag.put(TAG_ENTRIES, entries);
        return tag;
    }
}
