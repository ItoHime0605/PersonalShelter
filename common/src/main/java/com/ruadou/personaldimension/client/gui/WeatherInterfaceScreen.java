package com.ruadou.personaldimension.client.gui;

import com.ruadou.personaldimension.block.entity.WeatherInterfaceBlockEntity;
import com.ruadou.personaldimension.network.SetWeatherInterfacePacket;
import com.ruadou.personaldimension.platform.Platform;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * 维度天气接口方块的配置界面。
 *
 * <p>晴/雨/雷雨三选一，选择即通过 {@link SetWeatherInterfacePacket} 下发到服务端写入方块实体，
 * 由服务端复检维度归属与交互距离后落地（越权无效）。界面本身只负责回显与选择：
 * 目标天气在红石上升沿时才真正应用到所在个人维度，故顶部给出红石提示。
 * 打开时从客户端方块实体读取当前选择用于高亮回显。
 */
public class WeatherInterfaceScreen extends PanelScreen {

    /** 与天气编号 0/1/2 一一对应的按钮语言键（复用控制器天气子页的文案）。 */
    private static final String[] WEATHER_KEYS = {
        "gui.personaldimension.controller.weather.clear",
        "gui.personaldimension.controller.weather.rain",
        "gui.personaldimension.controller.weather.thunder"
    };

    private final BlockPos pos;
    private int selected;

    public WeatherInterfaceScreen(BlockPos pos) {
        super(Component.translatable("gui.personaldimension.weather_interface.title"));
        this.pos = pos;
    }

    @Override
    protected void init() {
        super.init();
        this.selected = readWeatherState();
        rebuild();
    }

    /** 从客户端方块实体读取当前目标天气用于回显；方块实体缺失时回退为晴（0）。 */
    private int readWeatherState() {
        if (Minecraft.getInstance().level != null
            && Minecraft.getInstance().level.getBlockEntity(this.pos) instanceof WeatherInterfaceBlockEntity be) {
            return be.getWeatherState();
        }
        return 0;
    }

    private void rebuild() {
        clearWidgets();
        int x = contentLeft();
        int top = contentTop() + 14;
        int w = contentWidth();
        for (int i = 0; i < WEATHER_KEYS.length; i++) {
            int state = i;
            FlatButton button = new FlatButton(Component.translatable(WEATHER_KEYS[i]),
                x, top + i * 24, w, 20, b -> select(state));
            button.setSelected(this.selected == i);
            addRenderableWidget(button);
        }

        int bottomY = this.topPos + PANEL_HEIGHT - PADDING - 20;
        addRenderableWidget(new FlatButton(
            Component.translatable("gui.personaldimension.controller.back"),
            x, bottomY, w, 20, b -> onClose()));
    }

    private void select(int state) {
        this.selected = state;
        Platform.sendToServer(new SetWeatherInterfacePacket(this.pos, state));
        rebuild();
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.drawString(this.font,
            Component.translatable("gui.personaldimension.interface.redstone_hint"),
            contentLeft(), contentTop() + 2, ModernStyle.TEXT_SUB, false);
    }
}
