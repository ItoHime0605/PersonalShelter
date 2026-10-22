package com.ruadou.personaldimension.block.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.ruadou.personaldimension.platform.Platform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 好友来访传送器方块实体：存储所有者 UUID、绑定的好友分组列表、是否仅控制者可用。
 * 数据随方块持久化并同步到客户端供界面回显。
 */
public class FriendVisitorTeleporterBlockEntity extends SyncedBlockEntity {

    private static final String TAG_OWNER = "Owner";
    private static final String TAG_GROUPS = "Groups";
    private static final String TAG_CONTROLLER_ONLY = "ControllerOnly";

    private UUID owner;
    private final List<String> boundGroups = new ArrayList<>();
    private boolean controllerOnly = true;

    public FriendVisitorTeleporterBlockEntity(BlockPos pos, BlockState state) {
        super(Platform.friendVisitorTeleporterBlockEntityType(), pos, state);
    }

    public UUID getOwner() {
        return owner;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
        setChanged();
    }

    public List<String> getBoundGroups() {
        return List.copyOf(boundGroups);
    }

    public void setBoundGroups(List<String> groups) {
        this.boundGroups.clear();
        this.boundGroups.addAll(groups);
        setChanged();
    }

    public boolean isControllerOnly() {
        return controllerOnly;
    }

    public void setControllerOnly(boolean controllerOnly) {
        this.controllerOnly = controllerOnly;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (owner != null) {
            tag.putUUID(TAG_OWNER, owner);
        }
        ListTag groupList = new ListTag();
        for (String group : boundGroups) {
            groupList.add(StringTag.valueOf(group));
        }
        tag.put(TAG_GROUPS, groupList);
        tag.putBoolean(TAG_CONTROLLER_ONLY, controllerOnly);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID(TAG_OWNER)) {
            this.owner = tag.getUUID(TAG_OWNER);
        }
        this.boundGroups.clear();
        ListTag groupList = tag.getList(TAG_GROUPS, Tag.TAG_STRING);
        for (int i = 0; i < groupList.size(); i++) {
            this.boundGroups.add(groupList.getString(i));
        }
        this.controllerOnly = tag.getBoolean(TAG_CONTROLLER_ONLY);
    }
}
