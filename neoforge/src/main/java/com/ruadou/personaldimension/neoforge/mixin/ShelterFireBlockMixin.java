package com.ruadou.personaldimension.neoforge.mixin;

import com.ruadou.personaldimension.dimension.ShelterFireRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.FireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FireBlock.class)
public abstract class ShelterFireBlockMixin {
    // Prevent consumption of adjacent flammable blocks; leave the original fire tick and extinguishing intact.
    @Inject(method = "checkBurnOut", at = @At("HEAD"), cancellable = true, remap = false)
    private void pshelter$blockBurnOut(Level level, BlockPos pos, int chance, RandomSource random, int age, Direction face, CallbackInfo ci) {
        if (ShelterFireRules.isDisabled(level)) ci.cancel();
    }

    // Prevent new fire in nearby air blocks.
    @Inject(method = "getIgniteOdds(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)I", at = @At("HEAD"), cancellable = true)
    private void pshelter$blockIgnition(LevelReader level, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (ShelterFireRules.isDisabled(level)) cir.setReturnValue(0);
    }
}
