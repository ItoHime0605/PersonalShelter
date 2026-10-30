package com.ruadou.personaldimension.neoforge.mixin;

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
 * RuntimeDistCleaner 会报 "Attempted to load class for invalid dist" 错误。
 */
@Mixin(AbstractContainerMenu.class)
public abstract class ContainerMenuClickMixin {

    private static final Logger personaldimension$serverLogger = LoggerFactory.getLogger("Personaldimension");

    @Shadow
    public abstract ItemStack getCarried();

    @Shadow
    public abstract void setCarried(ItemStack stack);

    private static boolean personaldimension$serverIsDropAction(int slotNum, ClickType clickType) {
        // THROW: Q键丢弃
        // PICKUP + slot=-999: 拖拽物品到容器外松开（丢弃）
        // 注意：QUICK_CRAFT + slot=-999 是拖拽分配的开始/结束信号，不是丢弃！
        return clickType == ClickType.THROW
               || (slotNum == -999 && clickType == ClickType.PICKUP);
    }

    /**
     * clicked 服务端拦截：核心安全防线
     * 客户端预测由 ContainerMenuClickClientMixin 处理
     */
    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void personaldimension$clicked(int slotNum, int buttonNum, ClickType clickType, Player player, CallbackInfo ci) {
        if (player.level().isClientSide()) return; // 客户端由 client mixin 处理
        if (!personaldimension$serverIsDropAction(slotNum, clickType)) return;

        if (GuardService.deny(player, Permission.DROP_ITEM)) {
            personaldimension$serverLogger.info("[NeoForge-clicked-拦截] slot={}, clickType={}", slotNum, clickType);
            GuardService.resyncInventory(player);
            ci.cancel();
        }
    }

    /**
     * removed: 容器关闭时处理光标物品
     * 原版会直接 player.drop(carried) 丢弃光标物品，
     * 如果玩家无丢弃权限，把光标物品放回背包而非丢弃。
     * 只在服务端处理（客户端 onClose 的放回会被服务端覆盖）。
     */
    @Inject(method = "removed", at = @At("HEAD"), cancellable = true)
    private void personaldimension$removed(Player player, CallbackInfo ci) {
        if (player.level().isClientSide()) return;

        ItemStack carried = getCarried();
        if (carried == null || carried.isEmpty()) return;

        if (!GuardService.deny(player, Permission.DROP_ITEM)) return;

        // 把光标物品放回背包，清空光标，取消原版丢弃
        player.getInventory().placeItemBackInInventory(carried);
        setCarried(ItemStack.EMPTY);
        personaldimension$serverLogger.info("[NeoForge-removed] 关闭容器时光标物品已放回背包: player={}", player.getName());
        ci.cancel();
    }
}
