package com.ruadou.personaldimension.dimension;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.WorldData;
import net.minecraft.world.level.timers.TimerCallbacks;
import net.minecraft.world.level.timers.TimerQueue;

/**
 * 个人维度专属的世界数据，实现时间与主世界隔离。
 *
 * <p>原版非主世界维度使用 {@link DerivedLevelData}：其 {@code getDayTime/getGameTime} 委托主世界、
 * {@code setDayTime/setGameTime} 为空实现、{@code getScheduledEvents} 复用主世界定时器队列——
 * 这是所有维度共享时间的根因；天气同理：{@code isRaining/getRainTime} 等委托主世界、
 * 对应 setter 为空实现，使派生维度只能只读镜像主世界天气。本类覆写<b>时间与天气</b>相关方法，
 * 改用自身字段，其余（游戏规则、出生点等）继续委托主世界，遵循开闭原则。
 *
 * <p>配合构造 {@code ServerLevel} 时传入 {@code tickTime=true}，
 * 使 {@code ServerLevel.tickTime()} 独立推进本维度的 gameTime 与 dayTime。
 */
public class PersonalDimensionLevelData extends DerivedLevelData {

    private long gameTime;
    private long dayTime;

    /** 本维度独立的定时器队列，避免污染主世界的 TimerQueue。 */
    private final TimerQueue<MinecraftServer> scheduledEvents =
        new TimerQueue<>(TimerCallbacks.SERVER_CALLBACKS);

    /** 昼夜循环开关：为 false 时冻结时间，{@code ServerLevel.tickTime()} 的自增被忽略。 */
    private boolean daylightCycle = true;

    // 独立天气字段：默认晴天，由 ServerLevel.advanceWeatherCycle() 每刻独立推进，
    // 不再镜像主世界（DerivedLevelData 的天气 setter 为空实现，故必须在此覆写读写）。
    private boolean raining;
    private int rainTime;
    private boolean thundering;
    private int thunderTime;
    private int clearWeatherTime;

    /** 天气循环开关：为 false 时冻结天气，{@code ServerLevel.advanceWeatherCycle()} 每刻的写入被忽略。 */
    private boolean weatherCycle = true;

    /** 手动设置天气时的默认持续时长（tick）；天气冻结时该时长不再倒数，等价于永久保持。 */
    private static final int WEATHER_DEFAULT_DURATION = 6000;

    /**
     * 时间跟随主世界开关：为 true 时时间相关 getter 回退到 {@code super}（主世界实时值），
     * setter 全部忽略，效果是本维度时刻/昼夜与主世界完全一致，且优先级高于昼夜循环与手动设置。
     */
    private boolean followOverworldTime = false;

    /**
     * 天气跟随主世界开关：为 true 时天气相关 getter 回退到 {@code super}（主世界实时值），
     * setter 全部忽略，效果是本维度天气与主世界完全一致，且优先级高于天气循环与手动设置。
     */
    private boolean followOverworldWeather = false;

    /**
     * 月相独立控制：override 为 -1 表示跟随时间自然变化（{@code moonPhaseCycle=true}），
     * 0..7 表示锁定到对应月相（{@code moonPhaseCycle=false}）。
     * 覆写 {@link #getMoonPhase()} 时：跟随主世界读 super，否则 override&gt;=0 返回 override，否则按 dayTime 计算。
     * <p><b>注意</b>：客户端月亮纹理仍按 dayTime 渲染（{@code Level.getMoonPhase()} 在客户端基于 dayTime），
     * 此覆写仅影响服务端月相（怪物生成、滑冰、区域性难度等）。
     */
    private int moonPhaseOverride = -1;
    private boolean moonPhaseCycle = true;
    private boolean followOverworldMoonPhase = false;

    // 变量日时字段，默认原版语义（-1 表示 1:1 随游戏刻推进），保持与主世界完全隔离
    private float dayTimeFraction = 0.0F;
    private float dayTimePerTick = -1.0F;

    /**
     * 持久化载体（可空）。绑定后，dayTime 与昼夜开关的每次变化都会同步进它，
     * 由维度自己的存档随世界一并写盘，保证重载后不丢失冻结/时刻设置。
     */
    private PersonalDimensionData persistence;

    /**
     * @param worldData 全局世界数据（提供 gamerule、难度等共享信息）
     * @param wrapped   主世界数据（天气、出生点等仍委托它）
     */
    public PersonalDimensionLevelData(WorldData worldData, ServerLevelData wrapped) {
        super(worldData, wrapped);
        // 创建时从主世界当前时间派生，之后独立推进
        this.gameTime = wrapped.getGameTime();
        this.dayTime = wrapped.getDayTime();
    }

    @Override
    public long getGameTime() {
        return this.gameTime;
    }

    @Override
    public void setGameTime(long time) {
        this.gameTime = time;
    }

    @Override
    public long getDayTime() {
        // 跟随主世界时读取主世界实时时刻（super 委托主世界），优先级高于昼夜循环/手动设置
        return this.followOverworldTime ? super.getDayTime() : this.dayTime;
    }

    /**
     * 由 {@code ServerLevel.tickTime()} 与睡眠等路径调用。
     * 跟随主世界或冻结（daylightCycle=false）时忽略写入；
     * 需绕过冻结主动设置时刻请用 {@link #forceSetDayTime(long)}。
     */
    @Override
    public void setDayTime(long time) {
        if (!this.followOverworldTime && this.daylightCycle) {
            this.dayTime = time;
            syncPersistence();
        }
    }

    /** 强制设置时刻，无视昼夜循环开关，供控制器"设置时刻"使用；跟随主世界时不接受手动设置。 */
    public void forceSetDayTime(long time) {
        if (this.followOverworldTime) {
            return;
        }
        this.dayTime = time;
        syncPersistence();
    }

    public boolean isDaylightCycle() {
        return this.daylightCycle;
    }

    public void setDaylightCycle(boolean daylightCycle) {
        this.daylightCycle = daylightCycle;
        syncPersistence();
    }

    /**
     * 从持久化存档恢复时间状态（重载/首次绑定时调用），不回写存档避免多余脏标记。
     */
    public void applyState(long dayTime, boolean daylightCycle, boolean followOverworldTime) {
        this.dayTime = dayTime;
        this.daylightCycle = daylightCycle;
        this.followOverworldTime = followOverworldTime;
    }

    /**
     * 从持久化存档恢复天气状态（重载/首次绑定时调用），不回写存档避免多余脏标记。
     */
    public void applyWeatherState(boolean raining, int rainTime,
                                  boolean thundering, int thunderTime, int clearWeatherTime,
                                  boolean weatherCycle, boolean followOverworldWeather) {
        this.raining = raining;
        this.rainTime = rainTime;
        this.thundering = thundering;
        this.thunderTime = thunderTime;
        this.clearWeatherTime = clearWeatherTime;
        this.weatherCycle = weatherCycle;
        this.followOverworldWeather = followOverworldWeather;
    }

    /**
     * 从持久化存档恢复月相状态（重载/首次绑定时调用），不回写存档避免多余脏标记。
     */
    public void applyMoonPhaseState(int moonPhaseOverride, boolean moonPhaseCycle, boolean followOverworldMoonPhase) {
        this.moonPhaseOverride = moonPhaseOverride;
        this.moonPhaseCycle = moonPhaseCycle;
        this.followOverworldMoonPhase = followOverworldMoonPhase;
    }

    /** 是否已绑定持久化载体，避免重复绑定。 */
    public boolean hasPersistence() {
        return this.persistence != null;
    }

    public void setPersistence(PersonalDimensionData persistence) {
        this.persistence = persistence;
    }

    /** 把当前 dayTime 与昼夜/跟随开关同步进持久化载体（若已绑定）。 */
    private void syncPersistence() {
        if (this.persistence != null) {
            this.persistence.updateTime(this.dayTime, this.daylightCycle, this.followOverworldTime);
        }
    }

    /** 把当前天气计时与循环/跟随开关同步进持久化载体（若已绑定）。 */
    private void syncWeatherPersistence() {
        if (this.persistence != null) {
            this.persistence.updateWeather(
                this.raining, this.rainTime, this.thundering, this.thunderTime,
                this.clearWeatherTime, this.weatherCycle, this.followOverworldWeather);
        }
    }

    /** 把当前月相覆盖/循环/跟随开关同步进持久化载体（若已绑定）。 */
    private void syncMoonPhasePersistence() {
        if (this.persistence != null) {
            this.persistence.updateMoonPhase(this.moonPhaseOverride, this.moonPhaseCycle, this.followOverworldMoonPhase);
        }
    }

    // ==== 天气：改用自身字段，使 advanceWeatherCycle 在本维度独立推进 ====
    // 5 个 setter 由 advanceWeatherCycle 每刻调用；weatherCycle=false 时忽略写入，
    // 使天气状态与计时定格（冻结）。需绕过冻结主动设置请用 forceSetWeather。

    @Override
    public boolean isRaining() {
        return this.followOverworldWeather ? super.isRaining() : this.raining;
    }

    @Override
    public void setRaining(boolean raining) {
        if (!this.followOverworldWeather && this.weatherCycle) {
            this.raining = raining;
            syncWeatherPersistence();
        }
    }

    @Override
    public int getRainTime() {
        return this.followOverworldWeather ? super.getRainTime() : this.rainTime;
    }

    @Override
    public void setRainTime(int time) {
        if (!this.followOverworldWeather && this.weatherCycle) {
            this.rainTime = time;
            syncWeatherPersistence();
        }
    }

    @Override
    public boolean isThundering() {
        return this.followOverworldWeather ? super.isThundering() : this.thundering;
    }

    @Override
    public void setThundering(boolean thundering) {
        if (!this.followOverworldWeather && this.weatherCycle) {
            this.thundering = thundering;
            syncWeatherPersistence();
        }
    }

    @Override
    public int getThunderTime() {
        return this.followOverworldWeather ? super.getThunderTime() : this.thunderTime;
    }

    @Override
    public void setThunderTime(int time) {
        if (!this.followOverworldWeather && this.weatherCycle) {
            this.thunderTime = time;
            syncWeatherPersistence();
        }
    }

    @Override
    public int getClearWeatherTime() {
        return this.followOverworldWeather ? super.getClearWeatherTime() : this.clearWeatherTime;
    }

    @Override
    public void setClearWeatherTime(int time) {
        if (!this.followOverworldWeather && this.weatherCycle) {
            this.clearWeatherTime = time;
            syncWeatherPersistence();
        }
    }

    /**
     * 强制设置天气，无视天气循环开关，供控制器"天气控制"使用；跟随主世界时不接受手动设置。
     * rainLevel/thunderLevel 的客户端插值仍由 advanceWeatherCycle 依据这些字段每刻推进，
     * 因此无需手动广播即可让维度内玩家看到天气变化。
     */
    public void forceSetWeather(int clearWeatherTime, int rainTime, int thunderTime,
                                boolean raining, boolean thundering) {
        if (this.followOverworldWeather) {
            return;
        }
        this.clearWeatherTime = clearWeatherTime;
        this.rainTime = rainTime;
        this.thunderTime = thunderTime;
        this.raining = raining;
        this.thundering = thundering;
        syncWeatherPersistence();
    }

    /**
     * 按天气编号（0晴/1雨/2雷雨）强制设置天气，集中编号到 {@link #forceSetWeather} 参数的映射，
     * 供控制器"天气控制"与维度天气接口方块复用（DRY）；跟随主世界时由 forceSetWeather 内部忽略。
     */
    public void setWeatherState(int weatherState) {
        switch (weatherState) {
            case 1 -> forceSetWeather(0, WEATHER_DEFAULT_DURATION, WEATHER_DEFAULT_DURATION, true, false);
            case 2 -> forceSetWeather(0, WEATHER_DEFAULT_DURATION, WEATHER_DEFAULT_DURATION, true, true);
            default -> forceSetWeather(WEATHER_DEFAULT_DURATION, 0, 0, false, false);
        }
    }

    public boolean isWeatherCycle() {
        return this.weatherCycle;
    }

    public void setWeatherCycle(boolean weatherCycle) {
        this.weatherCycle = weatherCycle;
        syncWeatherPersistence();
    }

    // ==== 跟随主世界开关 ====

    public boolean isFollowOverworldTime() {
        return this.followOverworldTime;
    }

    /**
     * 切换时间跟随主世界。关闭时以主世界当前时刻为基准继续，避免时刻跳回跟随前的旧值。
     */
    public void setFollowOverworldTime(boolean follow) {
        if (!follow && this.followOverworldTime) {
            this.dayTime = super.getDayTime();
        }
        this.followOverworldTime = follow;
        syncPersistence();
    }

    public boolean isFollowOverworldWeather() {
        return this.followOverworldWeather;
    }

    /**
     * 切换天气跟随主世界。关闭时快照主世界当前天气计时为基准继续，避免天气跳回跟随前的旧值。
     */
    public void setFollowOverworldWeather(boolean follow) {
        if (!follow && this.followOverworldWeather) {
            this.raining = super.isRaining();
            this.rainTime = super.getRainTime();
            this.thundering = super.isThundering();
            this.thunderTime = super.getThunderTime();
            this.clearWeatherTime = super.getClearWeatherTime();
        }
        this.followOverworldWeather = follow;
        syncWeatherPersistence();
    }

    // ==== 月相：独立于时间，可锁定到 0..7 或跟随时间自然变化 ====

    /**
     * 计算本维度当前应返回的月相值（由 {@code LevelMixin} 在 {@code Level.getMoonPhase()} 注入调用）。
     * 跟随主世界时返回 -1 表示"按原版逻辑"（让 Mixin 回退），否则 override&gt;=0 返回 override，
     * 否则按本维度 dayTime 计算。
     * <p>此方法不覆写父类（{@code DerivedLevelData} 无 {@code getMoonPhase()}），仅由 Mixin 调用。
     * 客户端月亮纹理仍按 dayTime 渲染，此方法仅影响服务端月相（怪物生成、滑冰、区域性难度等）。
     *
     * @return 0..7 锁定的月相值；-1 表示让 Mixin 回退到原版计算
     */
    public int getEffectiveMoonPhase() {
        if (this.followOverworldMoonPhase) {
            return -1; // 跟随主世界：Mixin 回退原版
        }
        if (this.moonPhaseOverride >= 0) {
            return this.moonPhaseOverride;
        }
        // 跟随时间：基于本维度独立 dayTime 计算（与原版 Level.getMoonPhase() 语义一致）
        return (int) (this.dayTime / 24000L) % 8;
    }

    public int getMoonPhaseOverride() {
        return this.moonPhaseOverride;
    }

    public boolean isMoonPhaseCycle() {
        return this.moonPhaseCycle;
    }

    public boolean isFollowOverworldMoonPhase() {
        return this.followOverworldMoonPhase;
    }

    /**
     * 强制锁定月相到指定值（0..7），并关闭月相循环；跟随主世界时忽略。
     * 供控制器"月相控制"与维度月相接口方块复用（DRY）。
     */
    public void setMoonPhase(int phase) {
        if (this.followOverworldMoonPhase) {
            return;
        }
        this.moonPhaseOverride = Math.floorMod(phase, 8);
        this.moonPhaseCycle = false;
        syncMoonPhasePersistence();
    }

    /** 切换月相循环开关：开启时清除 override（按 dayTime 自然变化），关闭时锁定到当前月相。 */
    public void setMoonPhaseCycle(boolean cycle) {
        if (this.followOverworldMoonPhase) {
            return;
        }
        this.moonPhaseCycle = cycle;
        if (cycle) {
            this.moonPhaseOverride = -1;
        } else if (this.moonPhaseOverride < 0) {
            // 由循环切到锁定：以当前计算值锁定，避免突跳
            this.moonPhaseOverride = (int) (this.dayTime / 24000L) % 8;
        }
        syncMoonPhasePersistence();
    }

    /**
     * 切换月相跟随主世界。关闭时以主世界当前月相为基准锁定，避免月相跳回跟随前的旧值。
     * 月相由 {@code Level.getMoonPhase()} 基于 {@code dayTime / 24000 % 8} 计算，不通过 LevelData，
     * 故这里用 {@code super.getDayTime()} 派生主世界当前月相。
     */
    public void setFollowOverworldMoonPhase(boolean follow) {
        if (!follow && this.followOverworldMoonPhase) {
            this.moonPhaseOverride = (int) (super.getDayTime() / 24000L) % 8;
            this.moonPhaseCycle = false;
        }
        this.followOverworldMoonPhase = follow;
        syncMoonPhasePersistence();
    }

    @Override
    public TimerQueue<MinecraftServer> getScheduledEvents() {
        return this.scheduledEvents;
    }

    // 变量日时使用独立字段，避免主世界执行 /time 命令时影响本维度；跟随主世界时回退主世界值以保证插值一致。
    // 注意：getDayTimeFraction/getDayTimePerTick 是 NeoForge patch 给 ServerLevelData 添加的方法，
    // 原 vanilla mappings 中不存在，因此这里不加 @Override；跟随主世界时按原版语义返回主世界 dayTime 派生值。

    public float getDayTimeFraction() {
        return this.followOverworldTime ? super.getDayTime() / 24000.0f : this.dayTimeFraction;
    }

    public void setDayTimeFraction(float dayTimeFraction) {
        this.dayTimeFraction = dayTimeFraction;
    }

    public float getDayTimePerTick() {
        return this.followOverworldTime ? 1.0f : this.dayTimePerTick;
    }

    public void setDayTimePerTick(float dayTimePerTick) {
        this.dayTimePerTick = dayTimePerTick;
    }
}
