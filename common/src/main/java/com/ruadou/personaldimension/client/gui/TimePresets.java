package com.ruadou.personaldimension.client.gui;

/**
 * 时刻预设与输入解析的集中定义，供时间子页（控制器）与维度时间接口界面复用（DRY）。
 * 预设值对应原版 {@code /time set} 常用时刻；解析支持 "时:分"（06:00 对应 0 tick）或直接 tick。
 */
public final class TimePresets {

    private TimePresets() {}

    /** 预设时刻值（tick）。 */
    public static final long[] TIMES = {23000L, 1000L, 6000L, 12000L, 13000L, 18000L};

    /** 与 {@link #TIMES} 一一对应的显示名语言键。 */
    public static final String[] KEYS = {
        "gui.personaldimension.controller.time.preset.sunrise",
        "gui.personaldimension.controller.time.preset.day",
        "gui.personaldimension.controller.time.preset.noon",
        "gui.personaldimension.controller.time.preset.sunset",
        "gui.personaldimension.controller.time.preset.night",
        "gui.personaldimension.controller.time.preset.midnight"
    };

    /**
     * 解析输入为 dayTime（0..23999）；非法返回 {@code null}。
     * 含 ":" 按 时:分 解析（dayTime 0 对应 06:00，24000 tick = 24h），否则按纯 tick 解析。
     */
    public static Long parse(String text) {
        try {
            if (text.contains(":")) {
                String[] parts = text.split(":", 2);
                int hour = Integer.parseInt(parts[0].trim());
                int minute = Integer.parseInt(parts[1].trim());
                if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
                    return null;
                }
                int minutesOfDay = hour * 60 + minute;
                return Math.floorMod(Math.round(minutesOfDay * (24000.0 / 1440.0)) - 6000L, 24000L);
            }
            return Math.floorMod(Long.parseLong(text), 24000L);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
