package com.ruadou.personaldimension.block.entity;

import java.util.UUID;

import com.ruadou.personaldimension.platform.Platform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 维度传送器的方块实体：仅存储放置者 UUID。
 * 不需要客户端同步（数据由服务端全局统一管理）。
 */
public class TeleporterBlockEntity extends BlockEntity {

    private static final String TAG_OWNER = "Owner";

    private UUID owner;

    public TeleporterBlockEntity(BlockPos pos, BlockState state) {
        super(Platform.teleporterBlockEntityType(), pos, state);
    }

    public UUID getOwner() {
        return owner;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (owner != null) {
            tag.putUUID(TAG_OWNER, owner);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID(TAG_OWNER)) {
            this.owner = tag.getUUID(TAG_OWNER);
        }
    }
}
