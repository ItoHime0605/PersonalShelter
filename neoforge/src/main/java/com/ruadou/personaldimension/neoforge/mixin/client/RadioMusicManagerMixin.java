package com.ruadou.personaldimension.neoforge.mixin.client;

import com.ruadou.personaldimension.neoforge.client.StandaloneRadioPlayer;
import com.ruadou.personaldimension.client.ClientRadioVolume;
import net.minecraft.client.sounds.MusicManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevent vanilla background music from repeatedly restarting while the
 * personal-land radio is playing. Other sound categories (records, effects,
 * ambient sounds) continue normally. Music scheduling resumes on radio stop.
 */
@Mixin(MusicManager.class)
public abstract class RadioMusicManagerMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void personaldimension$suspendBackgroundMusic(CallbackInfo ci) {
        if (ClientRadioVolume.get() > 0 && StandaloneRadioPlayer.isPlaying()) {
            ci.cancel();
        }
    }
}
