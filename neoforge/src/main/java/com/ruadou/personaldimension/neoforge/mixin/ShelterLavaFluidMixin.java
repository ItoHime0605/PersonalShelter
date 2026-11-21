package com.ruadou.personaldimension.neoforge.mixin;

import com.ruadou.personaldimension.dimension.ShelterFireRules;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.LavaFluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LavaFluid.class)
public abstract class ShelterLavaFluidMixin {
    // Lava randomTick is ignition only; fluid flow is handled separately.
    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void pshelter$blockLavaIgnition(Level level, BlockPos pos, FluidState state, RandomSource random, CallbackInfo ci) {
        if (ShelterFireRules.isDisabled(level)) ci.cancel();
    }
}
