package com.ruadou.personaldimension.fabric.mixin;

import com.ruadou.personaldimension.dimension.PersonalDimensionLevelData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelTimeAccess;
import net.minecraft.world.level.storage.LevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 服务端注入 {@link LevelTimeAccess#getMoonPhase()} 默认方法，使个人维度返回
 * {@link PersonalDimensionLevelData#getEffectiveMoonPhase()} 计算的月相值
 * （支持锁定 / 循环 / 跟随主世界），实现月相与时间独立控制。
 *
 * <p>1.21.1 把 {@code getMoonPhase()} 从 {@code Level} 类移到 {@link LevelTimeAccess} 接口作为
 * 默认方法（基于 {@code dayTime / 24000 % 8} 计算）。{@link PersonalDimensionLevelData}
 * 无法通过覆写父类方法影响月相，故必须由 Mixin 注入到接口默认方法。
 *
 * <p><b>回退语义</b>：{@code getEffectiveMoonPhase()} 返回 -1 表示让 Mixin 回退原版计算
 * （月相跟随主世界场景）。其它值（0..7）直接作为本维度月相返回。
 *
 * <p><b>作用范围</b>：服务端影响怪物生成上限（满月刷怪上限上升）、滑冰、区域性难度等月相相关逻辑；
 * 客户端渲染由 {@link client.ClientMoonPhaseMixin} 单独处理，从 {@link ClientDimensionInfo}
 * 获取服务端同步的月相状态。
 *
 * <p><b>instanceof 守卫</b>：{@link LevelTimeAccess} 还有其它实现（如 {@code ClientLevel}、
 * {@code WrappedWorld} 等），仅当运行时实例为 {@link Level}（服务端）且其 LevelData 为
 * {@link PersonalDimensionLevelData} 时才覆盖返回值，客户端由独立 Mixin 处理。
 */
@Mixin(LevelTimeAccess.class)
public interface LevelMixin {

    /**
     * 在 {@code getMoonPhase()} 默认方法返回前注入：仅当本对象为 {@link Level} 且其 LevelData
     * 为 {@link PersonalDimensionLevelData} 时改用其计算结果。返回 -1 时不修改返回值，回退原版。
     */
    @Inject(method = "getMoonPhase", at = @At("HEAD"), cancellable = true)
    private void personaldimension$getMoonPhase(CallbackInfoReturnable<Integer> cir) {
        if ((Object) this instanceof Level level) {
            LevelData data = level.getLevelData();
            if (data instanceof PersonalDimensionLevelData pd) {
                int phase = pd.getEffectiveMoonPhase();
                if (phase >= 0) {
                    cir.setReturnValue(phase);
                }
                // phase == -1：不设置返回值，原版方法继续执行
            }
        }
    }
}
