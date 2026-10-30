package com.ruadou.personaldimension.neoforge.mixin.client;

import com.ruadou.personaldimension.platform.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Avoid fragile redirects into the vanilla vignette's internal border call.
 * Some rendering mods replace that invocation, causing a critical Mixin
 * injection failure before the client can reach the title screen.
 *
 * Suppress the vignette in personal dimensions only. This does not change
 * server-side world-border enforcement, collision or protection.
 */
@Mixin(Gui.class)
public abstract class BorderVignetteMixin {
    @Inject(method = "renderVignette", at = @At("HEAD"), cancellable = true, require = 0)
    private void personaldimension$hidePrivateBorderWarning(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && Platform.isPersonalDimension(mc.level.dimension())) {
            ci.cancel();
        }
    }
}
