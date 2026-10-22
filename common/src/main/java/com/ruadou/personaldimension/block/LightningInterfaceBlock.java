package com.ruadou.personaldimension.block;

import com.ruadou.personaldimension.block.entity.LightningInterfaceBlockEntity;
import com.ruadou.personaldimension.platform.Platform;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 维度闪电接口方块：右键在自己的个人维度选择"自然 / 人造闪电"，外部红石上升沿激活时
 * 在方块所在 chunk 内尝试生成一次闪电——保留原版 {@code ServerLevel.tickChunk} 自然闪电的全部条件
 * 校验（能见天 + 降雨非雪 + 生物群系支持），晴天 / 暴雪 / 室内按按钮不会劈。所选"是否自然"选项
 * 存于 {@link LightningInterfaceBlockEntity}，仅影响是否打 AE2LT 兼容标签，不改变落点与伤害逻辑。
 *
 * <p><b>作用范围</b>：与 WeatherInterface/TimeInterface 一致——只对个人维度生效，非个人维度红石激活静默忽略。
 */
public class LightningInterfaceBlock extends AbstractDimensionInterfaceBlock {

    public LightningInterfaceBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LightningInterfaceBlockEntity(pos, state);
    }

    @Override
    protected void applyEffect(ServerLevel level, BlockPos pos) {
        DimensionInterfaceService.applyLightning(level, pos);
    }

    @Override
    protected void openScreen(BlockPos pos) {
        Platform.openLightningInterfaceScreen(pos);
    }
}
