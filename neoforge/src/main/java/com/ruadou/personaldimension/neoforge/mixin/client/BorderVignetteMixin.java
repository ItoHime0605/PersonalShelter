package com.ruadou.personaldimension.neoforge.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.ruadou.personaldimension.platform.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * In private shelters, hide the world-border red warning until the player
 * actually touches the border (distance <= 0). Keep the vanilla vignette and
 * all server-side world-border rules intact.
 *
 * A non-required expression modifier avoids the critical injection failure
 * if another rendering mod replaces the internal border-distance invocation.
 */
@Mixin(Gui.class)
public abstract class BorderVignetteMixin {
    @ModifyExpressionValue(
        method = "renderVignette",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/border/WorldBorder;getDistanceToBorder(DD)D"),
        require = 0
    )
    private double personaldimension$warnOnlyAtBorder(double distance) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && Platform.isPersonalDimension(mc.level.dimension())
                && distance > 0.0D) {
            return 1.0E9D;
        }
        return distance;
    }
}
