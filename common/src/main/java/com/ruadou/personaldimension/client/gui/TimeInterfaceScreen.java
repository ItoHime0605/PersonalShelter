package com.ruadou.personaldimension.client.gui;

import com.ruadou.personaldimension.block.entity.TimeInterfaceBlockEntity;
import com.ruadou.personaldimension.network.SetTimeInterfacePacket;
import com.ruadou.personaldimension.platform.Platform;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * 维度时间接口方块的配置界面。
 *
 * <p>6 个预设时刻（复用 {@link TimePresets}）+ 精确输入（时:分 或 tick），选择即通过
 * {@link SetTimeInterfacePacket} 下发到服务端写入方块实体，由服务端复检维度归属与交互距离后落地。
 * 目标时刻在红石上升沿时才真正应用到所在个人维度，故顶部给出红石提示。
 * 打开时从客户端方块实体读取当前选择用于高亮回显。
 */
public class TimeInterfaceScreen extends PanelScreen {

    private final BlockPos pos;
    private long selected;
    private EditBox timeInput;

    public TimeInterfaceScreen(BlockPos pos) {
        super(Component.translatable("gui.personaldimension.time_interface.title"));
        this.pos = pos;
    }

    @Override
    protected void init() {
        super.init();
        this.selected = readDayTime();
        rebuild();
    }

    /** 从客户端方块实体读取当前目标时刻用于回显；方块实体缺失时回退为 1000（白天）。 */
    private long readDayTime() {
        if (Minecraft.getInstance().level != null
            && Minecraft.getInstance().level.getBlockEntity(this.pos) instanceof TimeInterfaceBlockEntity be) {
            return be.getDayTime();
        }
        return 1000L;
    }

    private void rebuild() {
        clearWidgets();
        int x = contentLeft();
        int gap = 4;
        int btnW = (contentWidth() - 2 * gap) / 3;
        int presetTop = contentTop() + 14;
        for (int i = 0; i < TimePresets.TIMES.length; i++) {
            int col = i % 3;
            int row = i / 3;
            long value = TimePresets.TIMES[i];
            FlatButton preset = new FlatButton(Component.translatable(TimePresets.KEYS[i]),
                x + col * (btnW + gap), presetTop + row * 24, btnW, 20, b -> applyTime(value));
            preset.setSelected(this.selected == value);
            addRenderableWidget(preset);
        }

        // 精确输入框 + 应用按钮
        int inputY = presetTop + 2 * 24 + gap;
        int applyW = 60;
        int inputW = contentWidth() - applyW - gap;
        this.timeInput = new EditBox(this.font, x, inputY, inputW, 20,
            Component.translatable("gui.personaldimension.controller.time.input"));
        this.timeInput.setMaxLength(8);
        this.timeInput.setFilter(s -> s.matches("[0-9:]*"));
        this.timeInput.setHint(Component.translatable("gui.personaldimension.controller.time.input_hint"));
        addRenderableWidget(this.timeInput);
        addRenderableWidget(new FlatButton(
            Component.translatable("gui.personaldimension.controller.time.apply"),
            x + inputW + gap, inputY, applyW, 20, b -> submitInput()));

        int bottomY = this.topPos + PANEL_HEIGHT - PADDING - 20;
        addRenderableWidget(new FlatButton(
            Component.translatable("gui.personaldimension.controller.back"),
            x, bottomY, contentWidth(), 20, b -> onClose()));
    }

    private void applyTime(long value) {
        this.selected = value;
        Platform.sendToServer(new SetTimeInterfacePacket(this.pos, value));
        rebuild();
    }

    /** 解析精确输入并下发；支持 "时:分"（06:00 对应 0 tick）或直接 tick，非法输入忽略。 */
    private void submitInput() {
        if (this.timeInput == null) {
            return;
        }
        String text = this.timeInput.getValue().trim();
        if (text.isEmpty()) {
            return;
        }
        Long dayTime = TimePresets.parse(text);
        if (dayTime != null) {
            applyTime(dayTime);
        }
    }

    /** 输入框聚焦时按回车（257）或小键盘回车（335）提交。 */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.timeInput != null && this.timeInput.isFocused() && (keyCode == 257 || keyCode == 335)) {
            submitInput();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.drawString(this.font,
            Component.translatable("gui.personaldimension.interface.redstone_hint"),
            contentLeft(), contentTop() + 2, ModernStyle.TEXT_SUB, false);
    }
}
