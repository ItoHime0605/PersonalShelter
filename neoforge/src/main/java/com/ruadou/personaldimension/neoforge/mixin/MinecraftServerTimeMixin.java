package com.ruadou.personaldimension.neoforge.mixin;

import com.ruadou.personaldimension.neoforge.dimension.ShelterClockRules;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerTimeMixin {
    @Redirect(method = "synchronizeTime", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/GameRules;getBoolean(Lnet/minecraft/world/level/GameRules$Key;)Z"))
    private boolean personaldimension$syncDaylight(GameRules rules, GameRules.Key<GameRules.BooleanValue> key, ServerLevel level) {
        return ShelterClockRules.daylight(rules, key, level);
    }
}
