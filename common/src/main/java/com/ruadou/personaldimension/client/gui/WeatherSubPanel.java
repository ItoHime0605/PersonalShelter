package com.ruadou.personaldimension.client.gui;

import com.ruadou.personaldimension.client.DimensionInfoSnapshot;
import com.ruadou.personaldimension.network.SetDimensionWeatherCyclePacket;
import com.ruadou.personaldimension.network.SetDimensionWeatherPacket;
import com.ruadou.personaldimension.network.SetFollowOverworldWeatherPacket;
import com.ruadou.personaldimension.platform.Platform;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** 天气子页：晴/雨/雷雨 3 个预设 + 天气循环开关。 */
final class WeatherSubPanel extends SettingsSubPanel {

    private Button weatherCycleButton;

    WeatherSubPanel(PersonalDimensionControllerScreen screen) {
        super(screen);
    }

    /** 正文：晴/雨/雷雨 3 列预设 + 天气循环全宽开关。 */
    @Override
    protected void buildBody(int x, int bodyTop, int gap) {
        int w = screen.panelContentWidth();
        int btnW = (w - gap * 2) / 3;
        int presetTop = bodyTop;
        FlatButton clear = new FlatButton(
                Component.translatable("gui.personaldimension.controller.weather.clear"),
                x, presetTop, btnW, 20,
                b -> Platform.sendToServer(new SetDimensionWeatherPacket(SetDimensionWeatherPacket.CLEAR)));
        FlatButton rain = new FlatButton(
                Component.translatable("gui.personaldimension.controller.weather.rain"),
                x + (btnW + gap), presetTop, btnW, 20,
                b -> Platform.sendToServer(new SetDimensionWeatherPacket(SetDimensionWeatherPacket.RAIN)));
        FlatButton thunder = new FlatButton(
                Component.translatable("gui.personaldimension.controller.weather.thunder"),
                x + 2 * (btnW + gap), presetTop, btnW, 20,
                b -> Platform.sendToServer(new SetDimensionWeatherPacket(SetDimensionWeatherPacket.THUNDER)));
        screen.add(clear);
        screen.add(rain);
        screen.add(thunder);
        this.dependents.add(clear);
        this.dependents.add(rain);
        this.dependents.add(thunder);

        // 天气循环开关（消息随服务端状态在 tick 中刷新）
        int cycleY = presetTop + 20 + screen.panelPadding();
        this.weatherCycleButton = new FlatButton(cycleMessage(), x, cycleY,
                screen.panelContentWidth(), 20, b -> toggleWeatherCycle());
        screen.add(this.weatherCycleButton);
        this.dependents.add(this.weatherCycleButton);
    }

    @Override
    void tick() {
        super.tick();
        if (this.weatherCycleButton != null) {
            this.weatherCycleButton.setMessage(cycleMessage());
        }
    }

    @Override
    protected boolean isFollowing() {
        DimensionInfoSnapshot info = info();
        return info != null && info.followOverworldWeather();
    }

    @Override
    protected void toggleFollow() {
        DimensionInfoSnapshot info = info();
        boolean current = info != null && info.followOverworldWeather();
        Platform.sendToServer(new SetFollowOverworldWeatherPacket(!current));
    }

    private void toggleWeatherCycle() {
        DimensionInfoSnapshot info = info();
        boolean current = info == null || info.weatherCycle();
        Platform.sendToServer(new SetDimensionWeatherCyclePacket(!current));
    }

    private Component cycleMessage() {
        DimensionInfoSnapshot info = info();
        boolean on = info == null || info.weatherCycle();
        String stateKey = on
            ? "gui.personaldimension.controller.weather.cycle.on"
            : "gui.personaldimension.controller.weather.cycle.off";
        return Component.translatable("gui.personaldimension.controller.weather.cycle")
            .append(": ")
            .append(Component.translatable(stateKey));
    }
}
