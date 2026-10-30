package com.ruadou.personaldimension.neoforge.mixin.client;

import com.ruadou.personaldimension.platform.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.world.level.border.WorldBorder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Hide only the vanilla world-border warning tint in private shelters.
 * The real server border, collisions and protection remain unchanged.
 */
@Mixin(Gui.class)
public abstract class BorderVignetteMixin {
    @Redirect(method = "renderVignette", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/border/WorldBorder;getDistanceToBorder(DD)D"))
    private double personaldimension$hidePrivateBorderWarning(WorldBorder border, double x, double z) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && Platform.isPersonalDimension(mc.level.dimension())) {
            return 1.0E9D;
        }
        return border.getDistanceToBorder(x, z);
    }
}
