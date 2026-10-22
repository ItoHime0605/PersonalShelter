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
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

public final class FriendVisitorTeleporterData extends SavedData {

    public static final String ID = "personal_dimension_friend_visitor_teleporters";

    private static final String TAG_ENTRIES = "Entries";
    private static final String TAG_OWNER = "Owner";
    private static final String TAG_DIM = "Dimension";
    private static final String TAG_POS = "Pos";
    private static final String TAG_GROUPS = "Groups";
    private static final String TAG_CONTROLLER_ONLY = "ControllerOnly";

    public static final SavedData.Factory<FriendVisitorTeleporterData> FACTORY =
        new SavedData.Factory<>(FriendVisitorTeleporterData::new, FriendVisitorTeleporterData::load, null);

    private final Map<UUID, List<TeleporterEntry>> teleporters = new LinkedHashMap<>();

    public FriendVisitorTeleporterData() {}

    public static FriendVisitorTeleporterData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, ID);
    }

    public void add(UUID owner, ResourceKey<Level> dim, BlockPos pos, List<String> groups, boolean controllerOnly) {
        teleporters.computeIfAbsent(owner, k -> new ArrayList<>()).add(
            new TeleporterEntry(dim, pos, groups, controllerOnly));
        setDirty();
    }

    public void update(UUID owner, ResourceKey<Level> dim, BlockPos pos, List<String> groups, boolean controllerOnly) {
        List<TeleporterEntry> list = teleporters.computeIfAbsent(owner, k -> new ArrayList<>());
        for (int i = 0; i < list.size(); i++) {
            TeleporterEntry e = list.get(i);
            if (e.dimension().equals(dim) && e.pos().equals(pos)) {
                list.set(i, new TeleporterEntry(dim, pos, groups, controllerOnly));
                setDirty();
                return;
            }
        }
        list.add(new TeleporterEntry(dim, pos, groups, controllerOnly));
        setDirty();
    }

    public void remove(UUID owner, ResourceKey<Level> dim, BlockPos pos) {
        List<TeleporterEntry> list = teleporters.get(owner);
        if (list == null) {
            return;
        }
        boolean removed = list.removeIf(e -> e.dimension().equals(dim) && e.pos().equals(pos));
        if (removed) {
            if (list.isEmpty()) {
                teleporters.remove(owner);
            }
            setDirty();
        }
    }

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

    public TeleporterEntry findByGroup(UUID owner, String group) {
        List<TeleporterEntry> list = teleporters.get(owner);
        if (list == null) {
            return null;
        }
        for (TeleporterEntry e : list) {
            if (e.groups().contains(group)) {
                return e;
            }
        }
        return null;
    }

    public List<TeleporterEntry> get(UUID owner) {
        return List.copyOf(teleporters.getOrDefault(owner, List.of()));
    }

    public record TeleporterEntry(ResourceKey<Level> dimension, BlockPos pos, List<String> groups, boolean controllerOnly) {
        private static TeleporterEntry load(CompoundTag tag, HolderLookup.Provider registries) {
            ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.parse(tag.getString(TAG_DIM)));
            int x = tag.getInt(TAG_POS + "_x");
            int y = tag.getInt(TAG_POS + "_y");
            int z = tag.getInt(TAG_POS + "_z");
            List<String> groups = new ArrayList<>();
            ListTag groupList = tag.getList(TAG_GROUPS, Tag.TAG_STRING);
            for (int i = 0; i < groupList.size(); i++) {
                groups.add(groupList.getString(i));
            }
            boolean controllerOnly = tag.getBoolean(TAG_CONTROLLER_ONLY);
            return new TeleporterEntry(dim, new BlockPos(x, y, z), List.copyOf(groups), controllerOnly);
        }

        private CompoundTag save(CompoundTag tag) {
            tag.putString(TAG_DIM, dimension.location().toString());
            tag.putInt(TAG_POS + "_x", pos.getX());
            tag.putInt(TAG_POS + "_y", pos.getY());
            tag.putInt(TAG_POS + "_z", pos.getZ());
            ListTag groupList = new ListTag();
            for (String group : groups) {
                groupList.add(StringTag.valueOf(group));
            }
            tag.put(TAG_GROUPS, groupList);
            tag.putBoolean(TAG_CONTROLLER_ONLY, controllerOnly);
            return tag;
        }
    }

    private static FriendVisitorTeleporterData load(CompoundTag tag, HolderLookup.Provider registries) {
        FriendVisitorTeleporterData data = new FriendVisitorTeleporterData();
        ListTag entries = tag.getList(TAG_ENTRIES, Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            UUID owner = entry.getUUID(TAG_OWNER);
            TeleporterEntry te = TeleporterEntry.load(entry, registries);
            data.teleporters.computeIfAbsent(owner, k -> new ArrayList<>()).add(te);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        for (Map.Entry<UUID, List<TeleporterEntry>> e : teleporters.entrySet()) {
            for (TeleporterEntry te : e.getValue()) {
                CompoundTag entry = te.save(new CompoundTag());
                entry.putUUID(TAG_OWNER, e.getKey());
                entries.add(entry);
            }
        }
        tag.put(TAG_ENTRIES, entries);
        return tag;
    }
}