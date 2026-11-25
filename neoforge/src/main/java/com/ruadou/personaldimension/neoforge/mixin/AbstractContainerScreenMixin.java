package com.ruadou.personaldimension.neoforge.mixin;

import com.ruadou.personaldimension.dimension.GuardService;
import com.ruadou.personaldimension.friend.Permission;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 客户端容器界面拦截器：在发包前取消丢弃操作，零视觉不同步
 *
 * <p>拦截三个方法：
 * <ul>
 *   <li>keyPressed → 场景2（背包内按Q）</li>
 *   <li>mouseClicked → 场景3（拖拽丢弃的按下动作，日志调试用）</li>
 *   <li>mouseReleased → 场景3（拖拽丢弃的松开动作，实际丢弃在这里）</li>
 *   <li>slotClicked → 所有槽位点击的实际入口（含 THROW 类型）</li>
 * </ul>
 *
 * <p>注：真正的终极兜底是 PlayerDropItemMixin，这里是为了减少网络包和视觉不同步。
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("Personaldimension");

    @Shadow
    protected int leftPos;

    @Shadow
    protected int topPos;

    @Shadow
    public abstract AbstractContainerMenu getMenu();

    @SuppressWarnings("null") // getUUID() 的 @Nonnull 转换警告是 Mojang mappings 注解摩擦
    private static boolean shouldDenyDrop(Minecraft mc) {
        if (mc.player == null) return false;
        LocalPlayer player = mc.player;
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null) return false;
        ServerPlayer sp = server.getPlayerList().getPlayer(player.getUUID());
        if (sp == null) return false;
        return GuardService.deny(sp, Permission.DROP_ITEM);
    }

    /**
     * 场景2：拦截背包内按Q键丢弃
     */
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void personaldimension$keyPressed(int keyCode, int scanCode, int modifiers,
                                              CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (!mc.options.keyDrop.matches(keyCode, scanCode)) return;
        if (!shouldDenyDrop(mc)) return;

        LOGGER.info("[NeoForge-客户端拦截] 场景2: 背包内按Q丢弃已拦截");
        cir.setReturnValue(true);
    }

    /**
     * 场景3：拖拽丢弃在鼠标松开时才触发丢弃
     * 条件：光标上有物品 + 鼠标不在任何 slot 上
     */
    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void personaldimension$mouseReleased(double mouseX, double mouseY, int button,
                                                  CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (button != 0) return;

        ItemStack carried = getMenu().getCarried();
        if (carried == null || carried.isEmpty()) return;

        // 遍历所有 slot，检查鼠标是否在某个 slot 上
        boolean onSlot = false;
        for (Slot slot : getMenu().slots) {
            int slotScreenX = slot.x + leftPos;
            int slotScreenY = slot.y + topPos;
            if (mouseX >= slotScreenX && mouseX < slotScreenX + 16
                && mouseY >= slotScreenY && mouseY < slotScreenY + 16) {
                onSlot = true;
                break;
            }
        }
        if (onSlot) return;

        if (!shouldDenyDrop(mc)) return;

        LOGGER.info("[NeoForge-客户端拦截] 场景3: 拖拽丢弃(mouseReleased)已拦截 (mouseX={}, mouseY={})", mouseX, mouseY);
        cir.setReturnValue(true);
    }

    /**
     * slotClicked：所有槽位点击的实际入口（包括 Q 键的 THROW 类型）
     * 这是 AbstractContainerScreen 内部方法，直接处理 slot 操作类型
     */
    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    private void personaldimension$slotClicked(Slot slot, int slotId, int button,
                                               net.minecraft.world.inventory.ClickType clickType,
                                               CallbackInfo ci) {
        // THROW: Q键丢弃; PICKUP + slot=-999: 拖拽到容器外丢弃
        // QUICK_CRAFT + slot=-999 是拖拽分配的开始/结束，不是丢弃
        boolean isDrop = clickType == net.minecraft.world.inventory.ClickType.THROW
                || (slotId == -999 && clickType == net.minecraft.world.inventory.ClickType.PICKUP);

        if (!isDrop) return;

        Minecraft mc = Minecraft.getInstance();
        if (!shouldDenyDrop(mc)) return;

        LOGGER.info("[NeoForge-客户端拦截] slotClicked 丢弃动作已拦截: slotId={}, clickType={}", slotId, clickType);
        ci.cancel();
    }

    // Closing a menu is handled by the server; never mutate predicted inventory here.
}
