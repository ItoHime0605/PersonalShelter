package com.ruadou.personaldimension.neoforge.mixin;

import com.ruadou.personaldimension.neoforge.dimension.ShelterClockRules;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PlayerList.class)
public abstract class PlayerListTimeMixin {
    @Redirect(method = "sendLevelInfo", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/GameRules;getBoolean(Lnet/minecraft/world/level/GameRules$Key;)Z"))
    private boolean personaldimension$entryDaylight(GameRules rules, GameRules.Key<GameRules.BooleanValue> key,
            ServerPlayer player, ServerLevel level) {
        return ShelterClockRules.daylight(rules, key, level);
    }
}
