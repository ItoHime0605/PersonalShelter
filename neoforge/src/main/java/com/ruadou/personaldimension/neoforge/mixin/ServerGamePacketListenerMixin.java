package com.ruadou.personaldimension.neoforge.mixin;

import com.ruadou.personaldimension.dimension.GuardService;
import com.ruadou.personaldimension.friend.Permission;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.inventory.ClickType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public class ServerGamePacketListenerMixin {
    @Shadow public ServerPlayer player;

    @Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
    private void personaldimension$handlePlayerAction(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        // On the network thread, let vanilla schedule the packet on the server thread.
        if (player.getServer() == null || !player.getServer().isSameThread()) return;
        var action = packet.getAction();
        if ((action == ServerboundPlayerActionPacket.Action.DROP_ITEM
                || action == ServerboundPlayerActionPacket.Action.DROP_ALL_ITEMS)
                && GuardService.deny(player, Permission.DROP_ITEM)) {
            GuardService.resyncInventory(player);
            ci.cancel();
        }
    }

    @Inject(method = "handleContainerClick", at = @At("HEAD"), cancellable = true)
    private void personaldimension$handleContainerClick(ServerboundContainerClickPacket packet, CallbackInfo ci) {
        if (player.getServer() == null || !player.getServer().isSameThread()) return;
        // Reject before vanilla applies client-predicted remote slot changes.
        var menu = player.containerMenu;
        if (packet.getContainerId() != menu.containerId) return;
        ClickType type = packet.getClickType();
        int slot = packet.getSlotNum();
        boolean drop = type == ClickType.THROW || (slot == -999 && type == ClickType.PICKUP);
        if ((drop && GuardService.deny(player, Permission.DROP_ITEM))
                || GuardService.denyContainerClick(player, menu, slot, packet.getButtonNum(), type)) {
            GuardService.resyncInventory(player);
            ci.cancel();
        }
    }
}
