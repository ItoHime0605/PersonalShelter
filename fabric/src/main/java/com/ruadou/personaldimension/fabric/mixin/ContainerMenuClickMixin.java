package com.ruadou.personaldimension.fabric.mixin;

import com.ruadou.personaldimension.dimension.GuardService;
import com.ruadou.personaldimension.friend.Permission;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 容器菜单服务端拦截（仅服务端逻辑）
 *
 * <p>客户端预测逻辑见 {@code client.ContainerMenuClickClientMixin}。
 * 拆分原因：通用 mixin 不能引用客户端类，否则服务端启动时
 * 会报 "Attempted to load class for invalid dist" 错误。
 */
@Mixin(AbstractContainerMenu.class)
public abstract class ContainerMenuClickMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("Personaldimension");

    @Shadow
    public abstract ItemStack getCarried();

    @Shadow
    public abstract void setCarried(ItemStack stack);

    private static boolean isDropAction(int slotNum, ClickType clickType) {
        return clickType == ClickType.THROW
               || (slotNum == -999 && clickType == ClickType.PICKUP);
    }

    /**
     * clicked 服务端拦截：核心安全防线
     * 客户端预测由 ContainerMenuClickClientMixin 处理
     */
    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void personaldimension$clicked(int slotNum, int buttonNum, ClickType clickType, Player player, CallbackInfo ci) {
        if (player.level().isClientSide()) return;
        if (!isDropAction(slotNum, clickType)) return;

        if (GuardService.deny(player, Permission.DROP_ITEM)) {
            LOGGER.info("[Fabric-clicked-拦截] slot={}, clickType={}", slotNum, clickType);
            GuardService.resyncInventory(player);
            ci.cancel();
        }
    }

    /**
     * removed: 容器关闭时处理光标物品
     * 原版会直接 player.drop(carried) 丢弃光标物品，
     * 如果玩家无丢弃权限，把光标物品放回背包而非丢弃。
     */
    @Inject(method = "removed", at = @At("HEAD"), cancellable = true)
    private void personaldimension$removed(Player player, CallbackInfo ci) {
        if (player.level().isClientSide()) return;

        ItemStack carried = getCarried();
        if (carried == null || carried.isEmpty()) return;

        if (!GuardService.deny(player, Permission.DROP_ITEM)) return;

        player.getInventory().placeItemBackInInventory(carried);
        setCarried(ItemStack.EMPTY);
        LOGGER.info("[Fabric-removed] 关闭容器时光标物品已放回背包: player={}", player.getName());
        ci.cancel();
    }
}
