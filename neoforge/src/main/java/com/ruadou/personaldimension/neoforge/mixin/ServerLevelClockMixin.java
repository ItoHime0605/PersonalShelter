package com.ruadou.personaldimension.neoforge.mixin;

import com.ruadou.personaldimension.dimension.PersonalDimensionLevelData;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
public abstract class ServerLevelClockMixin {
    @Inject(method = "getDayTimeFraction", at = @At("HEAD"), cancellable = true, remap = false)
    private void personaldimension$clockFraction(CallbackInfoReturnable<Float> cir) {
        ServerLevel level = (ServerLevel)(Object)this;
        if (level.getLevelData() instanceof PersonalDimensionLevelData pd) {
            if (pd.isFollowOverworldTime()) cir.setReturnValue(level.getServer().overworld().getDayTimeFraction());
            else if (!pd.isDaylightCycle()) cir.setReturnValue(0.0f);
        }
    }
    @Inject(method = "getDayTimePerTick", at = @At("HEAD"), cancellable = true, remap = false)
    private void personaldimension$clockSpeed(CallbackInfoReturnable<Float> cir) {
        ServerLevel level = (ServerLevel)(Object)this;
        if (level.getLevelData() instanceof PersonalDimensionLevelData pd) {
            if (pd.isFollowOverworldTime()) cir.setReturnValue(level.getServer().overworld().getDayTimePerTick());
            else if (!pd.isDaylightCycle()) cir.setReturnValue(-1.0f);
        }
    }
}
