package com.ruadou.personaldimension.client.gui;

import com.ruadou.personaldimension.client.ClientDimensionInfo;
import com.ruadou.personaldimension.client.ClientShelterName;
import com.ruadou.personaldimension.client.DimensionInfoSnapshot;
import com.ruadou.personaldimension.platform.Platform;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.Level;

/**
 * 信息页正文的文本渲染工具（纯静态、无状态）。
 *
 * <p>从控制器界面抽出，仅依赖服务端权威快照 {@link DimensionInfoSnapshot} 与客户端维度集合，
 * 展示玩家自己个人维度的维度 ID / 天气 / 天气持续 / 月相 / 时刻。
 * 与界面骨架解耦，便于跨版本段复用。
 */
public final class ControllerInfoText {

    private ControllerInfoText() {}

    /** 绘制信息页五行文本（维度 / 天气 / 天气持续 / 月相 / 时刻）；过长的值按 {@code maxWidth} 自动折行，避免溢出面板。 */
    public static void render(GuiGraphics graphics, Font font, int x, int y, int maxWidth) {
        Minecraft mc = Minecraft.getInstance();
        DimensionInfoSnapshot info = ClientDimensionInfo.info();
        int cursorY = y;

        cursorY = drawLine(graphics, font, x, cursorY, maxWidth,
            dimensionValue(mc), Math.max(100, maxWidth - 84));
        if (info != null && info.created()) {
            int level = Math.max(0, Math.min(4, info.shelterLevel()));
            String[] numerals = {"零", "壹", "貳", "參", "肆"};
            int size = com.ruadou.personaldimension.shelter.ShelterUpgradeRules.size(level);
            cursorY = drawLine(graphics, font, x, cursorY, maxWidth,
                Component.literal("目前土地等級：" + numerals[level] + " (" + size + "×" + size + ")"));
        }
        cursorY = drawLine(graphics, font, x, cursorY, maxWidth,
            Component.translatable("gui.personaldimension.controller.info.weather").append(": ").append(weatherText(info)));
        cursorY = drawLine(graphics, font, x, cursorY, maxWidth,
            Component.translatable("gui.personaldimension.controller.info.weather_duration").append(": ").append(weatherDurationText(info)));
        cursorY = drawLine(graphics, font, x, cursorY, maxWidth,
            Component.translatable("gui.personaldimension.controller.info.moon_phase").append(": ").append(moonPhaseText(info)));
        drawLine(graphics, font, x, cursorY, maxWidth,
            Component.translatable("gui.personaldimension.controller.info.time").append(": ").append(timeText(info)));
    }

    /**
     * 绘制一条信息（超出 {@code maxWidth} 时自动折行）：折行子行按行高紧排，
     * 逻辑行末尾额外留 5px 间隙以区隔各条信息。返回下一条信息的起始 y。
     */
    private static int drawLine(GuiGraphics graphics, Font font, int x, int y, int maxWidth, Component text) {
        return drawLine(graphics, font, x, y, maxWidth, text, maxWidth);
    }

    private static int drawLine(GuiGraphics graphics, Font font, int x, int y, int maxWidth, Component text, int lineWidth) {
        float scale = ModernStyle.largeText() ? 1.22F : 1.0F;
        List<FormattedCharSequence> lines = font.split(text, Math.max(20, (int) (lineWidth / scale)));
        int sub = 0;
        for (FormattedCharSequence seq : lines) {
            graphics.pose().pushPose();
            graphics.pose().translate(x, y + (int) (font.lineHeight * scale * sub), 0);
            graphics.pose().scale(scale, scale, 1.0F);
            graphics.drawString(font, seq, 0, 0, ModernStyle.TEXT, false);
            graphics.pose().popPose();
            sub++;
        }
        return y + (int) (font.lineHeight * scale * Math.max(1, sub)) + 5;
    }

    /** 维度 ID 值：玩家自己的个人维度键；客户端已知维度集合中不含该键则视为暂未创建。 */
    private static Component dimensionValue(Minecraft mc) {
        if (mc.player == null) {
            return Component.translatable("gui.personaldimension.controller.info.dimension_uncreated");
        }
        ResourceKey<Level> personalKey = Platform.levelKeyFor(mc.player.getUUID());
        boolean created = mc.player.connection.levels().contains(personalKey);
        return created
            ? com.ruadou.personaldimension.client.gui.ShelterNameFormatting.parse(ClientShelterName.get().isBlank() ? mc.player.getGameProfile().getName() + "的私人土地" : ClientShelterName.get())
            : Component.translatable("gui.personaldimension.controller.info.dimension_uncreated");
    }

    /** 当前天气文字：取服务端权威布尔标志（切换瞬间翻转）。 */
    private static Component weatherText(DimensionInfoSnapshot info) {
        if (info == null) {
            return Component.translatable("gui.personaldimension.controller.info.fetching");
        }
        if (!info.created()) {
            return Component.translatable("gui.personaldimension.controller.info.dimension_uncreated");
        }
        // 雷雨 = 下雨 + 打雷；thundering 与 raining 独立计时，未下雨时打雷标志不构成雷雨
        String key;
        if (info.raining() && info.thundering()) {
            key = "gui.personaldimension.controller.weather.thunder";
        } else if (info.raining()) {
            key = "gui.personaldimension.controller.weather.rain";
        } else {
            key = "gui.personaldimension.controller.weather.clear";
        }
        Component base = Component.translatable(key);
        // 跟随主世界时优先提示（此时天气即主世界天气）
        if (info.followOverworldWeather()) {
            return Component.empty().append(base).append(" ")
                .append(Component.translatable("gui.personaldimension.controller.info.following"));
        }
        // 天气循环关闭时追加"(已冻结)"提示，与时间冻结显示一致
        if (!info.weatherCycle()) {
            return Component.empty().append(base).append(" ")
                .append(Component.translatable("gui.personaldimension.controller.info.time_frozen"));
        }
        return base;
    }

    /** 天气持续时间：取当前天气状态对应的剩余计时。 */
    private static Component weatherDurationText(DimensionInfoSnapshot info) {
        if (info == null) {
            return Component.translatable("gui.personaldimension.controller.info.fetching");
        }
        if (!info.created()) {
            return Component.translatable("gui.personaldimension.controller.info.dimension_uncreated");
        }
        int ticks;
        if (info.clearWeatherTime() > 0) {
            ticks = info.clearWeatherTime();
        } else if (info.raining() && info.thundering()) {
            ticks = Math.min(info.rainTime(), info.thunderTime());
        } else {
            ticks = info.rainTime();
        }
        return Component.literal(formatDuration(ticks));
    }

    private static String formatDuration(int ticks) {
        int totalSeconds = Math.max(0, ticks) / 20;
        return String.format("%02d:%02d", totalSeconds / 60, totalSeconds % 60);
    }

    /** 当前月相：取服务端权威值 0..7 映射到原版月相名；跟随主世界时追加"(跟随主世界)"后缀。 */
    private static Component moonPhaseText(DimensionInfoSnapshot info) {
        if (info == null) {
            return Component.translatable("gui.personaldimension.controller.info.fetching");
        }
        if (!info.created()) {
            return Component.translatable("gui.personaldimension.controller.info.dimension_uncreated");
        }
        int phase = Math.floorMod(info.moonPhase(), 8);
        Component base = Component.translatable("gui.personaldimension.controller.moon_phase." + phase);
        if (info.followOverworldMoonPhase()) {
            return Component.empty().append(base).append(" ")
                .append(Component.translatable("gui.personaldimension.controller.info.following"));
        }
        return base;
    }

    /** 当前时间：跟随玩家个人维度（服务端权威快照），dayTime 0 对应 06:00。 */
    private static Component timeText(DimensionInfoSnapshot info) {
        if (info == null) {
            return Component.translatable("gui.personaldimension.controller.info.fetching");
        }
        if (!info.created()) {
            return Component.translatable("gui.personaldimension.controller.info.dimension_uncreated");
        }
        long dayTime = info.dayTime() % 24000L;
        int totalMinutes = (int) (((dayTime + 6000L) % 24000L) * 1440L / 24000L);
        String base = String.format("%02d:%02d", totalMinutes / 60, totalMinutes % 60);
        return Component.literal(base);
    }
}
