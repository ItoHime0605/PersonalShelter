package com.ruadou.personaldimension.client.gui;

import com.ruadou.personaldimension.client.DimensionInfoSnapshot;
import com.ruadou.personaldimension.network.SetDimensionMoonPhaseCyclePacket;
import com.ruadou.personaldimension.network.SetDimensionMoonPhasePacket;
import com.ruadou.personaldimension.network.SetFollowOverworldMoonPhasePacket;
import com.ruadou.personaldimension.platform.Platform;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** 月相子页：8 个月相（0..7）选一 + 月相循环开关。 */
final class MoonPhaseSubPanel extends SettingsSubPanel {

    /** 8 个月相按钮文案语言键。 */
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

    private Button moonPhaseCycleButton;

    MoonPhaseSubPanel(PersonalDimensionControllerScreen screen) {
        super(screen);
    }

    /** 正文：4 列 x 2 行月相预设 + 月相循环全宽开关。 */
    @Override
    protected void buildBody(int x, int bodyTop, int gap) {
        int w = screen.panelContentWidth();
        int cols = 4;
        int btnW = (w - gap * (cols - 1)) / cols;
        int btnH = 20;
        int presetTop = bodyTop;
        for (int i = 0; i < MOON_PHASE_KEYS.length; i++) {
            int phase = i;
            int col = i % cols;
            int row = i / cols;
            FlatButton preset = new FlatButton(
                    Component.translatable(MOON_PHASE_KEYS[i]),
                    x + col * (btnW + gap), presetTop + row * (btnH + gap), btnW, btnH,
                    b -> Platform.sendToServer(new SetDimensionMoonPhasePacket(phase)));
            screen.add(preset);
            this.dependents.add(preset);
        }

        // 月相循环开关（消息随服务端状态在 tick 中刷新）
        int cycleY = presetTop + 2 * (btnH + gap) + 2;
        this.moonPhaseCycleButton = new FlatButton(cycleMessage(), x, cycleY,
                screen.panelContentWidth(), 20, b -> toggleMoonPhaseCycle());
        screen.add(this.moonPhaseCycleButton);
        this.dependents.add(this.moonPhaseCycleButton);
    }

    @Override
    void tick() {
        super.tick();
        if (this.moonPhaseCycleButton != null) {
            this.moonPhaseCycleButton.setMessage(cycleMessage());
        }
    }

    @Override
    protected boolean isFollowing() {
        DimensionInfoSnapshot info = info();
        return info != null && info.followOverworldMoonPhase();
    }

    @Override
    protected void toggleFollow() {
        DimensionInfoSnapshot info = info();
        boolean current = info != null && info.followOverworldMoonPhase();
        Platform.sendToServer(new SetFollowOverworldMoonPhasePacket(!current));
    }

    private void toggleMoonPhaseCycle() {
        DimensionInfoSnapshot info = info();
        boolean current = info == null || info.moonPhaseCycle();
        Platform.sendToServer(new SetDimensionMoonPhaseCyclePacket(!current));
    }

    private Component cycleMessage() {
        DimensionInfoSnapshot info = info();
        boolean on = info == null || info.moonPhaseCycle();
        String stateKey = on
            ? "gui.personaldimension.controller.moon_phase.cycle.on"
            : "gui.personaldimension.controller.moon_phase.cycle.off";
        return Component.translatable("gui.personaldimension.controller.moon_phase.cycle")
            .append(": ")
            .append(Component.translatable(stateKey));
    }
}
