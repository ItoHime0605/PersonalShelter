package com.ruadou.personaldimension.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 需要把自身少量状态同步到客户端的方块实体基类。
 *
 * <p>集中封装客户端同步的三段样板：区块加载时经 {@link #getUpdateTag} 携带 NBT、
 * 状态变更时经 {@link #getUpdatePacket} 发送 {@link ClientboundBlockEntityDataPacket}、
 * 客户端经 {@link #onDataPacket} 回写。子类只需实现 {@code saveAdditional}/{@code loadAdditional}
 * 存取各自字段（DRY：新增同类方块实体无需重复同步逻辑）。
 */
public abstract class SyncedBlockEntity extends BlockEntity {

    protected SyncedBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet,
                             HolderLookup.Provider registries) {
        CompoundTag tag = packet.getTag();
        if (tag != null) {
            loadAdditional(tag, registries);
        }
    }
}
