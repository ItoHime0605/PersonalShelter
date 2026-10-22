package com.ruadou.personaldimension.fabric.mixin;

import com.ruadou.personaldimension.dimension.GuardService;
import com.ruadou.personaldimension.friend.Permission;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.inventory.ClickType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 服务端网络包入口拦截器 —— 所有丢弃场景都在此拦截（源头拦截）
 * 场景1: handlePlayerAction (关闭背包按Q)
 * 场景2/3: handleContainerClick (背包内按Q/拖拽丢弃)
 */
@Mixin(ServerGamePacketListenerImpl.class)
public class ServerGamePacketListenerMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("Personaldimension");

    @Shadow
    public ServerPlayer player;

    /**
     * 场景1: 拦截关闭背包后按Q丢弃
     * 网络包: ServerboundPlayerActionPacket.Action.DROP_ITEM
     */
    @Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
    private void personaldimension$handlePlayerAction(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        if (packet.getAction() == ServerboundPlayerActionPacket.Action.DROP_ITEM) {
            boolean deny = GuardService.deny(player, Permission.DROP_ITEM);
            LOGGER.info("[Fabric-场景1] 关背包按Q丢弃: player={}, deny={}", player.getName(), deny);
            if (deny) {
                GuardService.resyncInventory(player);
                ci.cancel();
            }
        }
    }

    /**
     * 场景2/3: 拦截容器点击（背包内按Q/拖拽丢弃到背包外）
     * 网络包: ServerboundContainerClickPacket
     * 场景2(背包内按Q): clickType=THROW, button=0(单丢)/1(整堆丢)
     * 场景3(光标有物品时点击容器外): slotNum=-999, clickType=PICKUP
     * 注意: QUICK_CRAFT 的 slot=-999 是拖拽分堆协议占位符(button=0开始/2结束), 不是丢弃
     */
    @Inject(method = "handleContainerClick", at = @At("HEAD"), cancellable = true)
    private void personaldimension$handleContainerClick(ServerboundContainerClickPacket packet, CallbackInfo ci) {
        ClickType clickType = packet.getClickType();
        int slotNum = packet.getSlotNum();
        int buttonNum = packet.getButtonNum();
        String tName = Thread.currentThread().getName();

        LOGGER.info("[Fabric-场景2/3-包入口] thread={}, player={}, slot={}, button={}, clickType={}",
                     tName, player.getName(), slotNum, buttonNum, clickType);

        // 判断是否为丢弃动作
        // THROW: 背包内按Q; PICKUP+slot=-999: 点击容器外丢光标物品
        // 排除 QUICK_CRAFT: 其 slot=-999 是拖拽分堆开始/结束占位符, 不丢弃
        boolean isDrop = clickType == ClickType.THROW
                         || (slotNum == -999 && clickType == ClickType.PICKUP);
        if (!isDrop) return;

        boolean deny = GuardService.deny(player, Permission.DROP_ITEM);
        LOGGER.info("[Fabric-场景2/3-丢弃检测] player={}, slot={}, clickType={}, deny={}",
                     player.getName(), slotNum, clickType, deny);

        if (deny) {
            LOGGER.info("[Fabric-场景2/3-拦截丢弃] player={}, slot={}, clickType={} 已在包源头拦截",
                         player.getName(), slotNum, clickType);
            GuardService.resyncInventory(player);
            ci.cancel();
        }
    }
}
