package com.ruadou.personaldimension.fabric.mixin.client;

import com.ruadou.personaldimension.dimension.GuardService;
import com.ruadou.personaldimension.friend.Permission;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 容器菜单客户端预测拦截（仅客户端）
 *
 * <p>服务端安全校验见 {@code ContainerMenuClickMixin}。
 * 此 mixin 仅用于客户端提前拦截，避免物品先消失再同步的闪烁。
 *
 * <p>仅在单人局域网模式下生效（需要 IntegratedServer 获取 ServerPlayer）。
 * 纯服务端多人模式由服务端 mixin 兜底。
 */
@Mixin(AbstractContainerMenu.class)
public class ContainerMenuClickClientMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("Personaldimension");

    private static boolean isDropAction(int slotNum, ClickType clickType) {
        return clickType == ClickType.THROW
               || (slotNum == -999 && clickType == ClickType.PICKUP);
    }

    @SuppressWarnings("null")
    private static boolean shouldDenyClient(Player player) {
        if (!player.level().isClientSide()) return false;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        LocalPlayer clientPlayer = mc.player;
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null) return false;
        ServerPlayer sp = server.getPlayerList().getPlayer(clientPlayer.getUUID());
        if (sp == null) return false;
        return GuardService.deny(sp, Permission.DROP_ITEM);
    }

    /**
     * clicked 客户端预测
     */
    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void personaldimension$clickedClient(int slotNum, int buttonNum, ClickType clickType, Player player, CallbackInfo ci) {
        if (!player.level().isClientSide()) return;
        if (!isDropAction(slotNum, clickType)) return;

        if (shouldDenyClient(player)) {
            LOGGER.info("[Fabric-clicked-客户端拦截] slot={}, clickType={}", slotNum, clickType);
            ci.cancel();
        }
    }

    /**
     * doClick 客户端预测（仅客户端调用）
     */
    @Inject(method = "doClick", at = @At("HEAD"), cancellable = true)
    private void personaldimension$doClick(int slotNum, int dragType, ClickType clickType, Player player, CallbackInfo ci) {
        if (!player.level().isClientSide()) return;
        if (!isDropAction(slotNum, clickType)) return;

        if (shouldDenyClient(player)) {
            LOGGER.info("[Fabric-doClick-客户端拦截] slot={}, clickType={}", slotNum, clickType);
            ci.cancel();
        }
    }
}
