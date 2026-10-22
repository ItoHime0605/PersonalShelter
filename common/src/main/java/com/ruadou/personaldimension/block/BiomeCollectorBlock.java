package com.ruadou.personaldimension.block;

import com.ruadou.personaldimension.platform.Platform;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 群系收录器。
 * 右键打开界面，展示方块所在区块出现的群系供玩家勾选收录（不限维度）。
 * 打开界面为客户端动作；实际收录由服务端在收到 CollectChunkBiomesPacket 后复检方块存在、
 * 交互距离并过滤伪造项后执行，客户端校验仅用于避免打开无用界面，不构成权威。
 */
public class BiomeCollectorBlock extends Block {

    public BiomeCollectorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        // 仅客户端打开界面；Platform 实现委托给 ClientScreenOpener，客户端类只在方法调用时加载
        if (level.isClientSide()) {
            Platform.openBiomeCollectorScreen(pos);
        }
        return InteractionResult.SUCCESS;
    }
}
