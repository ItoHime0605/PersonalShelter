package com.ruadou.personaldimension.biome;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 玩家已收录群系的全局持久化载体。
 *
 * <p>群系收录属"个人数据"：按<b>玩家 UUID</b> 索引一份群系 id 集合。存于主世界的
 * {@code DimensionDataStorage}（全局 {@link SavedData}），使收录数据脱离具体维度、随存档持久化，
 * 与 {@link com.ruadou.personaldimension.friend.FriendPermissionData} 相同思路。
 *
 * <p><b>线程约束</b>：仅服务端主线程访问，使用普通 {@link LinkedHashMap}/{@link LinkedHashSet}
 * 保持插入顺序以便界面稳定展示。
 */
public class CollectedBiomeData extends SavedData {

    public static final String ID = "personal_dimension_collected_biomes";

    private static final String TAG_ENTRIES = "Entries";
    private static final String TAG_OWNER = "Owner";
    private static final String TAG_BIOMES = "Biomes";

    // 第三参 DataFixTypes 传 null：本数据无需 Mojang 数据修复器（用原版 3 参构造，跨加载器通用）。
    public static final SavedData.Factory<CollectedBiomeData> FACTORY =
        new SavedData.Factory<>(CollectedBiomeData::new, CollectedBiomeData::load, null);

    private final Map<UUID, Set<ResourceLocation>> collected = new LinkedHashMap<>();

    public CollectedBiomeData() {}

    /** 从主世界存档获取（不存在则创建）全局收录数据。 */
    public static CollectedBiomeData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, ID);
    }

    /** 只读获取某玩家已收录的群系 id 集合（未收录返回空集合）。 */
    public Set<ResourceLocation> getBiomes(UUID owner) {
        Set<ResourceLocation> set = this.collected.get(owner);
        return set != null ? Collections.unmodifiableSet(set) : Set.of();
    }

    /** 判断某玩家是否已收录指定群系（改造前的服务端复检）。 */
    public boolean hasBiome(UUID owner, ResourceLocation biome) {
        Set<ResourceLocation> set = this.collected.get(owner);
        return set != null && set.contains(biome);
    }

    /**
     * 为玩家收录一批群系 id，返回本次<b>新增</b>的数量（已收录的不重复计入）。
     * 有实际新增才标脏，避免无意义的存档写入。
     */
    public int addBiomes(UUID owner, Set<ResourceLocation> biomes) {
        Set<ResourceLocation> set = this.collected.computeIfAbsent(owner, k -> new LinkedHashSet<>());
        int before = set.size();
        set.addAll(biomes);
        int added = set.size() - before;
        if (added > 0) {
            this.setDirty();
        }
        return added;
    }

    private static CollectedBiomeData load(CompoundTag tag, HolderLookup.Provider registries) {
        CollectedBiomeData data = new CollectedBiomeData();
        ListTag entries = tag.getList(TAG_ENTRIES, Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            UUID owner = entry.getUUID(TAG_OWNER);
            Set<ResourceLocation> set = new LinkedHashSet<>();
            ListTag biomes = entry.getList(TAG_BIOMES, Tag.TAG_STRING);
            for (int j = 0; j < biomes.size(); j++) {
                ResourceLocation id = ResourceLocation.tryParse(biomes.getString(j));
                if (id != null) {
                    set.add(id);
                }
            }
            data.collected.put(owner, set);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        for (Map.Entry<UUID, Set<ResourceLocation>> e : this.collected.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID(TAG_OWNER, e.getKey());
            ListTag biomes = new ListTag();
            for (ResourceLocation id : e.getValue()) {
                biomes.add(StringTag.valueOf(id.toString()));
            }
            entry.put(TAG_BIOMES, biomes);
            entries.add(entry);
        }
        tag.put(TAG_ENTRIES, entries);
        return tag;
    }
}
