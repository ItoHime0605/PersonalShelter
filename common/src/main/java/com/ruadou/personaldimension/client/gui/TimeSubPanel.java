package com.ruadou.personaldimension.client.gui;

import com.ruadou.personaldimension.client.DimensionInfoSnapshot;
import com.ruadou.personaldimension.network.SetDimensionDaylightPacket;
import com.ruadou.personaldimension.network.SetDimensionTimePacket;
import com.ruadou.personaldimension.network.SetFollowOverworldTimePacket;
import com.ruadou.personaldimension.platform.Platform;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/** 时间子页：6 个预设时刻 + 昼夜循环开关 + 精确输入。 */
final class TimeSubPanel extends SettingsSubPanel {

    private Button daylightButton;
    private EditBox timeInput;

    TimeSubPanel(PersonalDimensionControllerScreen screen) {
        super(screen);
    }

    /** 正文：3 列 x 2 行预设时刻 + 昼夜循环全宽开关 + 精确输入框与确认按钮。 */
    @Override
    protected void buildBody(int x, int bodyTop, int gap) {
        int w = screen.panelContentWidth();
        int btnW = (w - gap * 2) / 3;
        int presetTop = bodyTop;
        for (int i = 0; i < TimePresets.TIMES.length; i++) {
            int col = i % 3;
            int row = i / 3;
            long value = TimePresets.TIMES[i];
            FlatButton preset = new FlatButton(
                    Component.translatable(TimePresets.KEYS[i]),
                    x + col * (btnW + gap), presetTop + row * 24, btnW, 20,
                    b -> Platform.sendToServer(new SetDimensionTimePacket(value)));
            screen.add(preset);
            this.dependents.add(preset);
        }

        // 昼夜循环开关（消息随服务端状态在 tick 中刷新）
        int daylightY = presetTop + 2 * 24 + 4;
        this.daylightButton = new FlatButton(daylightMessage(), x, daylightY,
                screen.panelContentWidth(), 20, b -> toggleDaylight());
        screen.add(this.daylightButton);
        this.dependents.add(this.daylightButton);

        // 精确输入框 + 确认按钮
        int inputY = daylightY + 24;
        int applyW = btnW;
        int inputW = w - applyW - gap;
        this.timeInput = new EditBox(Minecraft.getInstance().font, x, inputY, inputW, 20,
            Component.translatable("gui.personaldimension.controller.time.input"));
        this.timeInput.setMaxLength(8);
        this.timeInput.setFilter(s -> s.matches("[0-9:]*"));
        this.timeInput.setHint(Component.translatable("gui.personaldimension.controller.time.input_hint"));
        screen.add(this.timeInput);

        FlatButton apply = new FlatButton(
                Component.translatable("gui.personaldimension.controller.time.apply"),
                x + inputW + gap, inputY, applyW, 20,
                b -> submitTimeInput());
        screen.add(apply);
        this.dependents.add(apply);
    }

    /** 跟随开启时额外禁用输入框编辑。 */
    @Override
    protected void refreshGating() {
        super.refreshGating();
        if (this.timeInput != null) {
            this.timeInput.setEditable(!isFollowing());
        }
    }

    @Override
    void tick() {
        super.tick();
        if (this.daylightButton != null) {
            this.daylightButton.setMessage(daylightMessage());
        }
    }

    /** 输入框聚焦时按回车（257）或小键盘回车（335）应用。 */
    @Override
    boolean keyPressed(int keyCode) {
        if (this.timeInput != null && this.timeInput.isFocused() && (keyCode == 257 || keyCode == 335)) {
            submitTimeInput();
            return true;
        }
        return false;
    }

    @Override
    protected boolean isFollowing() {
        DimensionInfoSnapshot info = info();
        return info != null && info.followOverworldTime();
    }

    @Override
    protected void toggleFollow() {
        DimensionInfoSnapshot info = info();
        boolean current = info != null && info.followOverworldTime();
        Platform.sendToServer(new SetFollowOverworldTimePacket(!current));
    }

    private void toggleDaylight() {
        DimensionInfoSnapshot info = info();
        boolean current = info == null || info.daylightCycle();
        Platform.sendToServer(new SetDimensionDaylightPacket(!current));
    }

    private Component daylightMessage() {
        DimensionInfoSnapshot info = info();
        boolean on = info == null || info.daylightCycle();
        String stateKey = on
            ? "gui.personaldimension.controller.time.daylight.on"
            : "gui.personaldimension.controller.time.daylight.off";
        return Component.translatable("gui.personaldimension.controller.time.daylight")
            .append(": ")
            .append(Component.translatable(stateKey));
    }

    /** 解析精确输入并下发。支持 "时:分"（06:00 对应 0 tick）或直接输入 tick。 */
    private void submitTimeInput() {
        if (this.timeInput == null) {
            return;
        }
        String text = this.timeInput.getValue().trim();
        if (text.isEmpty()) {
            return;
        }
        Long dayTime = TimePresets.parse(text);
        if (dayTime != null) {
            Platform.sendToServer(new SetDimensionTimePacket(dayTime));
        }
    }
}
