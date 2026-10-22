package com.ruadou.personaldimension.dimension;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 维度传送器全局持久化载体。
 *
 * <p>传送器位置需在任何维度都可查询（玩家从个人维度里发起传送请求），
 * 故不挂玩家 attachment，而是全局 {@link SavedData}，存于主世界的
 * {@code DimensionDataStorage}，按放置者 UUID 索引其传送器列表。
 *
 * <p><b>线程约束</b>：仅服务端主线程访问，使用普通 {@link LinkedHashMap}。
 */
public class TeleporterData extends SavedData {

    public static final String ID = "personal_dimension_teleporters";

    private static final String TAG_ENTRIES = "Entries";
    private static final String TAG_OWNER = "Owner";
    private static final String TAG_LIST = "Teleporters";

    /** 第三参 DataFixTypes 传 null：本数据无需 Mojang 数据修复器（用原版 3 参构造，跨加载器通用）。 */
    public static final SavedData.Factory<TeleporterData> FACTORY =
        new SavedData.Factory<>(TeleporterData::new, TeleporterData::load, null);

    private final Map<UUID, List<TeleporterEntry>> teleporters = new LinkedHashMap<>();

    public TeleporterData() {}

    /** 从主世界存档获取（不存在则创建）全局传送器数据。 */
    public static TeleporterData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, ID);
    }

    /** 添加一个传送器条目（放置方块时调用）。 */
    public void add(UUID owner, TeleporterEntry entry) {
        teleporters.computeIfAbsent(owner, k -> new ArrayList<>()).add(entry);
        setDirty();
    }

    /** 移除指定维度+坐标的传送器条目（破坏方块时调用）。 */
    public void remove(UUID owner, ResourceKey<Level> dim, BlockPos pos) {
        List<TeleporterEntry> list = teleporters.get(owner);
        if (list == null) {
            return;
        }
        boolean removed = list.removeIf(e ->
            e.dimension().equals(dim) && e.x() == pos.getX() && e.y() == pos.getY() && e.z() == pos.getZ());
        if (removed) {
            if (list.isEmpty()) {
                teleporters.remove(owner);
            }
            setDirty();
        }
    }

    /** 移除指定维度下所有玩家的传送器条目（删除个人维度时调用）。 */
    public void removeByDimension(ResourceKey<Level> dim) {
        boolean changed = false;
        for (List<TeleporterEntry> list : teleporters.values()) {
            changed |= list.removeIf(e -> e.dimension().equals(dim));
        }
        teleporters.entrySet().removeIf(e -> e.getValue().isEmpty());
        if (changed) {
            setDirty();
        }
    }

    /** 获取指定玩家的所有传送器条目（不可变副本）。 */
    public List<TeleporterEntry> get(UUID owner) {
        return List.copyOf(teleporters.getOrDefault(owner, List.of()));
    }

    /** 重命名指定索引的传送器条目（name 和 note）。 */
    public void rename(UUID owner, int index, String name, String note) {
        List<TeleporterEntry> list = teleporters.get(owner);
        if (list == null || index < 0 || index >= list.size()) {
            return;
        }
        TeleporterEntry old = list.get(index);
        list.set(index, new TeleporterEntry(old.dimension(), old.x(), old.y(), old.z(), name, note));
        setDirty();
    }

    // ——— SavedData 序列化 ———

    private static TeleporterData load(CompoundTag tag, HolderLookup.Provider registries) {
        TeleporterData data = new TeleporterData();
        ListTag entries = tag.getList(TAG_ENTRIES, Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            UUID owner = entry.getUUID(TAG_OWNER);
            ListTag list = entry.getList(TAG_LIST, Tag.TAG_COMPOUND);
            List<TeleporterEntry> entryList = new ArrayList<>();
            for (int j = 0; j < list.size(); j++) {
                entryList.add(TeleporterEntry.load(list.getCompound(j)));
            }
            if (!entryList.isEmpty()) {
                data.teleporters.put(owner, entryList);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        for (Map.Entry<UUID, List<TeleporterEntry>> e : teleporters.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID(TAG_OWNER, e.getKey());
            ListTag list = new ListTag();
            for (TeleporterEntry t : e.getValue()) {
                list.add(t.save(new CompoundTag()));
            }
            entry.put(TAG_LIST, list);
            entries.add(entry);
        }
        tag.put(TAG_ENTRIES, entries);
        return tag;
    }
}
