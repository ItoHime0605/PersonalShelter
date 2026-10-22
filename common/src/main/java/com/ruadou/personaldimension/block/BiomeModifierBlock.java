package com.ruadou.personaldimension.block;

import java.util.Optional;
import java.util.UUID;

import com.ruadou.personaldimension.platform.Platform;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 群系改造器。
 * 右键在<b>玩家自己的个人维度</b>内打开界面选择已收录群系覆盖当前区块；其它维度提示不可用。
 * 打开界面为客户端动作；实际覆盖由服务端在收到 ApplyBiomePacket 后复检维度归属与收录状态再执行，
 * 客户端校验仅用于避免打开无用界面，不构成权威。
 */
public class BiomeModifierBlock extends Block {

    public BiomeModifierBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        Optional<UUID> owner = Platform.ownerOf(level.dimension());
        boolean inOwnDimension = owner.isPresent() && owner.get().equals(player.getUUID());
        if (!inOwnDimension) {
            // 服务端权威提示；客户端不打开界面
            if (!level.isClientSide()) {
                player.displayClientMessage(
                    Component.translatable("personaldimension.message.modifier_wrong_dim"), true);
            }
            return InteractionResult.SUCCESS;
        }
        // 仅客户端打开界面；Platform 实现委托给 ClientScreenOpener，客户端类只在方法调用时加载
        if (level.isClientSide()) {
            Platform.openBiomeModifierScreen(pos);
        }
        return InteractionResult.SUCCESS;
    }
}
