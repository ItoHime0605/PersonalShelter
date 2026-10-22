package com.ruadou.personaldimension.neoforge.mixin.client;

import com.ruadou.personaldimension.client.ClientDimensionInfo;
import com.ruadou.personaldimension.client.DimensionInfoSnapshot;
import com.ruadou.personaldimension.dimension.ModDimensions;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.LevelTimeAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelTimeAccess.class)
public interface ClientMoonPhaseMixin {

    @Inject(method = "getMoonPhase", at = @At("HEAD"), cancellable = true)
    private void personaldimension$clientGetMoonPhase(CallbackInfoReturnable<Integer> cir) {
        if ((Object) this instanceof ClientLevel level) {
            if (!ModDimensions.isPersonalDimension(level.dimension())) {
                return;
            }

            DimensionInfoSnapshot info = ClientDimensionInfo.info();
            if (info == null || !info.created()) {
                return;
            }

            if (info.followOverworldMoonPhase()) {
                return;
            }

            if (info.moonPhaseOverride() >= 0) {
                cir.setReturnValue(info.moonPhaseOverride());
                return;
            }

            int phase = (int) (info.dayTime() / 24000L) % 8;
            cir.setReturnValue(phase);
        }
    }
}