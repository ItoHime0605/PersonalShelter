package com.ruadou.personaldimension.neoforge.mixin;

import com.ruadou.personaldimension.dimension.GuardService;
import com.ruadou.personaldimension.friend.Permission;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
public abstract class ContainerMenuClickMixin {
    // Defense in depth for server-side menu invocations not originating from the vanilla packet.
    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void personaldimension$clicked(int slotNum, int buttonNum, ClickType clickType, Player player, CallbackInfo ci) {
        if (player.level().isClientSide()) return;
        if (!(player instanceof net.minecraft.server.level.ServerPlayer sp)
                || sp.getServer() == null || !sp.getServer().isSameThread()) return;
        boolean drop = clickType == ClickType.THROW || (slotNum == -999 && clickType == ClickType.PICKUP);
        if ((drop && GuardService.deny(player, Permission.DROP_ITEM))
                || GuardService.denyContainerClick(player, (AbstractContainerMenu)(Object)this, slotNum, buttonNum, clickType)) {
            GuardService.resyncInventory(player);
            ci.cancel();
        }
    }
    // Do not intercept removed(): vanilla owns cursor restitution and temporary recipe inputs.
    // If the inventory is full, overflow must be returned safely (vanilla drops it), never deleted.
}
