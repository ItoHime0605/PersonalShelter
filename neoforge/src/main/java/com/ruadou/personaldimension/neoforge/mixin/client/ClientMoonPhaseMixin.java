package com.ruadou.personaldimension.neoforge.mixin.client;

import com.ruadou.personaldimension.neoforge.network.ShelterMoonSyncPacket;
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
        if ((Object)this instanceof ClientLevel level) {
            var snapshot = ShelterMoonSyncPacket.current();
            if (snapshot != null && snapshot.phase() >= 0 && snapshot.phase() <= 7
                    && snapshot.dimension().equals(level.dimension().location())) {
                cir.setReturnValue(snapshot.phase());
            }
        }
    }
}
