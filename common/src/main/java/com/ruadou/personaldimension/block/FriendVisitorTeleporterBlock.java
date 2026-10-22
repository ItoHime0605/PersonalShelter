package com.ruadou.personaldimension.block;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.ruadou.personaldimension.block.entity.FriendVisitorTeleporterBlockEntity;
import com.ruadou.personaldimension.dimension.FriendVisitorTeleporterData;
import com.ruadou.personaldimension.platform.Platform;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 好友访问传送器方块：放置后记录位置到全局 {@link FriendVisitorTeleporterData}，
 * 供好友在玩家外出时通过访问 GUI 传送到此方块上方。
 *
 * <p>放置时绑定放置者 UUID 并初始化权限组绑定（默认空、仅控制器可用）；
 * 破坏时从全局数据中移除对应条目。右键打开配置界面（仅维度所有者可操作）。
 */
public class FriendVisitorTeleporterBlock extends Block implements EntityBlock {

    public FriendVisitorTeleporterBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FriendVisitorTeleporterBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            LivingEntity placer, ItemStack stack) {
        if (level.isClientSide() || !(placer instanceof ServerPlayer player)) {
            return;
        }
        Optional<UUID> dimOwner = Platform.ownerOf(level.dimension());
        if (dimOwner.isPresent() && !dimOwner.get().equals(player.getUUID())) {
            return; // 在他人维度放置已被平台事件拦截，此处为防御性兜底
        }
        if (level.getBlockEntity(pos) instanceof FriendVisitorTeleporterBlockEntity be) {
            be.setOwner(player.getUUID());
            FriendVisitorTeleporterData.get(player.server).add(player.getUUID(), level.dimension(), pos, List.of(), true);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!level.isClientSide() && !state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof FriendVisitorTeleporterBlockEntity be && be.getOwner() != null) {
                FriendVisitorTeleporterData.get(((ServerLevel) level).getServer())
                    .remove(be.getOwner(), level.dimension(), pos);
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        Optional<UUID> dimOwner = Platform.ownerOf(level.dimension());
        if (dimOwner.isEmpty() || !dimOwner.get().equals(player.getUUID())) {
            if (!level.isClientSide()) {
                player.displayClientMessage(
                    Component.translatable("personaldimension.message.not_owner"), true);
            }
            return InteractionResult.SUCCESS;
        }
        if (level.isClientSide()) {
            Platform.openFriendVisitorTeleporterScreen(pos);
        }
        return InteractionResult.SUCCESS;
    }

    public static Vec3 getSpawnPos(Level level, BlockPos pos) {
        return new Vec3(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
    }
}
