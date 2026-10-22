package com.ruadou.personaldimension.client.gui;

import com.ruadou.personaldimension.block.entity.LightningInterfaceBlockEntity;
import com.ruadou.personaldimension.network.SetLightningInterfacePacket;
import com.ruadou.personaldimension.platform.Platform;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * 维度闪电接口方块的配置界面。
 *
 * <p>二选一："自然闪电" / "人造闪电"。选择即通过 {@link SetLightningInterfacePacket} 下发到服务端
 * 写入方块实体，由服务端复检维度归属与交互距离后落地（越权无效）。红石上升沿时根据该选项决定
 * 是否给生成的闪电打 AE2LT 兼容标签。顶部给出红石提示，下方给出说明文案。
 * 打开时从客户端方块实体读取当前选择用于高亮回显。
 */
public class LightningInterfaceScreen extends PanelScreen {

    private final BlockPos pos;
    private boolean natural;

    public LightningInterfaceScreen(BlockPos pos) {
        super(Component.translatable("gui.personaldimension.lightning_interface.title"));
        this.pos = pos;
    }

    @Override
    protected void init() {
        super.init();
        this.natural = readNatural();
        rebuild();
    }

    /** 从客户端方块实体读取当前选项用于回显；方块实体缺失时回退为自然（true）。 */
    private boolean readNatural() {
        if (Minecraft.getInstance().level != null
            && Minecraft.getInstance().level.getBlockEntity(this.pos) instanceof LightningInterfaceBlockEntity be) {
            return be.isNatural();
        }
        return true;
    }

    private void rebuild() {
        clearWidgets();
        int x = contentLeft();
        int top = contentTop() + 14;
        int w = contentWidth();

        // 两个等宽选项按钮
        int gap = 4;
        int btnW = (w - gap) / 2;
        FlatButton naturalBtn = new FlatButton(
            Component.translatable("gui.personaldimension.lightning_interface.natural"),
            x, top, btnW, 20, b -> select(true));
        FlatButton artificialBtn = new FlatButton(
            Component.translatable("gui.personaldimension.lightning_interface.artificial"),
            x + btnW + gap, top, btnW, 20, b -> select(false));
        naturalBtn.setSelected(this.natural);
        artificialBtn.setSelected(!this.natural);
        addRenderableWidget(naturalBtn);
        addRenderableWidget(artificialBtn);

        int bottomY = this.topPos + PANEL_HEIGHT - PADDING - 20;
        addRenderableWidget(new FlatButton(
            Component.translatable("gui.personaldimension.controller.back"),
            x, bottomY, w, 20, b -> onClose()));
    }

    private void select(boolean value) {
        this.natural = value;
        Platform.sendToServer(new SetLightningInterfacePacket(this.pos, value));
        rebuild();
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.drawString(this.font,
            Component.translatable("gui.personaldimension.interface.redstone_hint"),
            contentLeft(), contentTop() + 2, ModernStyle.TEXT_SUB, false);
        int descY = contentTop() + 40;
        graphics.drawString(this.font,
            Component.translatable("gui.personaldimension.lightning_interface.desc"),
            contentLeft(), descY, ModernStyle.TEXT_SUB, false);
    }
}
