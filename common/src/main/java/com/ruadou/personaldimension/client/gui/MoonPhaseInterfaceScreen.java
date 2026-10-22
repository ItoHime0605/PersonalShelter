package com.ruadou.personaldimension.client.gui;

import com.ruadou.personaldimension.block.entity.MoonPhaseInterfaceBlockEntity;
import com.ruadou.personaldimension.network.SetMoonPhaseInterfacePacket;
import com.ruadou.personaldimension.platform.Platform;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * 维度月相接口方块的配置界面。
 *
 * <p>8 个月相（0..7）选一，选择即通过 {@link SetMoonPhaseInterfacePacket} 下发到服务端写入方块实体，
 * 由服务端复检维度归属与交互距离后落地（越权无效）。界面本身只负责回显与选择：
 * 目标月相在红石上升沿时才真正应用到所在个人维度，故顶部给出红石提示。
 * 打开时从客户端方块实体读取当前选择用于高亮回显。
 */
public class MoonPhaseInterfaceScreen extends PanelScreen {

    /** 月相按钮文案语言键（0..7 共 8 个，复用控制器月相子页文案）。 */
    private static final String[] MOON_PHASE_KEYS = {
        "gui.personaldimension.controller.moon_phase.0",
        "gui.personaldimension.controller.moon_phase.1",
        "gui.personaldimension.controller.moon_phase.2",
        "gui.personaldimension.controller.moon_phase.3",
        "gui.personaldimension.controller.moon_phase.4",
        "gui.personaldimension.controller.moon_phase.5",
        "gui.personaldimension.controller.moon_phase.6",
        "gui.personaldimension.controller.moon_phase.7"
    };

    private final BlockPos pos;
    private int selected;

    public MoonPhaseInterfaceScreen(BlockPos pos) {
        super(Component.translatable("gui.personaldimension.moon_phase_interface.title"));
        this.pos = pos;
    }

    @Override
    protected void init() {
        super.init();
        this.selected = readMoonPhase();
        rebuild();
    }

    /** 从客户端方块实体读取当前目标月相用于回显；方块实体缺失时回退为 0。 */
    private int readMoonPhase() {
        if (Minecraft.getInstance().level != null
            && Minecraft.getInstance().level.getBlockEntity(this.pos) instanceof MoonPhaseInterfaceBlockEntity be) {
            return be.getMoonPhase();
        }
        return 0;
    }

    private void rebuild() {
        clearWidgets();
        int x = contentLeft();
        int top = contentTop() + 14;
        int w = contentWidth();
        // 4 列 2 行：每列宽度 = (w - 3*gap) / 4
        int gap = 4;
        int btnW = (w - gap * 3) / 4;
        int btnH = 20;
        for (int i = 0; i < MOON_PHASE_KEYS.length; i++) {
            int phase = i;
            int col = i % 4;
            int row = i / 4;
            FlatButton button = new FlatButton(Component.translatable(MOON_PHASE_KEYS[i]),
                x + col * (btnW + gap), top + row * (btnH + gap), btnW, btnH, b -> select(phase));
            button.setSelected(this.selected == i);
            addRenderableWidget(button);
        }

        int bottomY = this.topPos + PANEL_HEIGHT - PADDING - 20;
        addRenderableWidget(new FlatButton(
            Component.translatable("gui.personaldimension.controller.back"),
            x, bottomY, w, 20, b -> onClose()));
    }

    private void select(int phase) {
        this.selected = phase;
        Platform.sendToServer(new SetMoonPhaseInterfacePacket(this.pos, phase));
        rebuild();
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.drawString(this.font,
            Component.translatable("gui.personaldimension.interface.redstone_hint"),
            contentLeft(), contentTop() + 2, ModernStyle.TEXT_SUB, false);
    }
}
