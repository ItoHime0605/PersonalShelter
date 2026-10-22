package com.ruadou.personaldimension.client;

/**
 * 个人维度信息快照（平台无关的数据载体）。
 *
 * <p>从 {@code DimensionInfoPacket} 抽取的纯数据视图，供客户端缓存与界面渲染使用，
 * 不依赖任何平台特定的网络包类型。平台端的 Packet 在 handle 时调用 {@link #update} 写入此快照。
 *
 * @param created                   个人维度是否已创建（未创建时其余字段无意义）
 * @param raining                   是否下雨
 * @param thundering                是否雷暴
 * @param rainTime                  距离下次雨状态切换的剩余 tick
 * @param thunderTime               距离下次雷暴状态切换的剩余 tick
 * @param clearWeatherTime          强制晴天的剩余 tick
 * @param dayTime                   个人维度当前时刻
 * @param daylightCycle             昼夜循环是否开启
 * @param weatherCycle              天气循环是否开启
 * @param followOverworldTime       时间是否跟随主世界
 * @param followOverworldWeather    天气是否跟随主世界
 * @param moonPhaseOverride         月相锁定值（-1 表示未锁定）
 * @param moonPhaseCycle            月相循环是否开启
 * @param followOverworldMoonPhase  月相是否跟随主世界
 * @param moonPhase                 当前月相值（0..7）
 */
public record DimensionInfoSnapshot(boolean created,
                                    boolean raining, boolean thundering,
                                    int rainTime, int thunderTime, int clearWeatherTime,
                                    long dayTime, boolean daylightCycle, boolean weatherCycle,
                                    boolean followOverworldTime, boolean followOverworldWeather,
                                    int moonPhaseOverride, boolean moonPhaseCycle, boolean followOverworldMoonPhase,
                                    int moonPhase, int shelterLevel) {

    /** 个人维度尚未创建时的占位快照。 */
    public static DimensionInfoSnapshot uncreated() {
        return new DimensionInfoSnapshot(false, false, false, 0, 0, 0, 0L, true, true, false, false, -1, true, false, 0, 0);
    }
}
