package com.ruadou.personaldimension.dimension;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 个人维度持久化状态（时间 + 天气）。
 *
 * <p>{@link PersonalDimensionLevelData} 属于派生型世界数据（{@code DerivedLevelData}），
 * 自身没有独立存档文件；而个人维度隔离出的时刻/昼夜开关与天气计时必须跨存档保留，
 * 否则世界重载后会丢失这些设置。因此单独用一个 {@link SavedData} 存到该维度自己的
 * {@code DimensionDataStorage}（随维度存档自动读写），由 {@link PersonalDimensionLevelData}
 * 在其值变化时同步进来。
 */
public class PersonalDimensionData extends SavedData {

    /** 存档文件名（位于 dimensions/&lt;dim&gt;/data/personal_dimension_state.dat）。 */
    public static final String ID = "personal_dimension_state";

    private static final String TAG_DAY_TIME = "DayTime";
    private static final String TAG_DAYLIGHT_CYCLE = "DaylightCycle";
    private static final String TAG_RAINING = "Raining";
    private static final String TAG_RAIN_TIME = "RainTime";
    private static final String TAG_THUNDERING = "Thundering";
    private static final String TAG_THUNDER_TIME = "ThunderTime";
    private static final String TAG_CLEAR_WEATHER_TIME = "ClearWeatherTime";
    private static final String TAG_WEATHER_CYCLE = "WeatherCycle";
    private static final String TAG_FOLLOW_OVERWORLD_TIME = "FollowOverworldTime";
    private static final String TAG_FOLLOW_OVERWORLD_WEATHER = "FollowOverworldWeather";
    private static final String TAG_MOON_PHASE_OVERRIDE = "MoonPhaseOverride";
    private static final String TAG_MOON_PHASE_CYCLE = "MoonPhaseCycle";
    private static final String TAG_FOLLOW_OVERWORLD_MOON_PHASE = "FollowOverworldMoonPhase";

    /** 读取旧存档时用于兼容缺省的 SavedData 工厂。第三参 DataFixTypes 传 null：本数据无需 Mojang 数据修复器（用原版 3 参构造，跨加载器通用）。 */
    public static final SavedData.Factory<PersonalDimensionData> FACTORY =
        new SavedData.Factory<>(PersonalDimensionData::new, PersonalDimensionData::load, null);

    private long dayTime;
    private boolean daylightCycle = true;

    private boolean raining;
    private int rainTime;
    private boolean thundering;
    private int thunderTime;
    private int clearWeatherTime;
    private boolean weatherCycle = true;

    // 跟随主世界开关：旧存档缺失时默认关闭（保持既有独立时间/天气行为）
    private boolean followOverworldTime;
    private boolean followOverworldWeather;

    // 月相独立控制：override 为 -1 表示跟随时间自然变化（cycle=true），
    // 0..7 表示锁定到对应月相（cycle=false）。旧存档缺失时按"跟随时间"处理，保持原行为。
    private int moonPhaseOverride = -1;
    private boolean moonPhaseCycle = true;
    private boolean followOverworldMoonPhase;

    // Personal Shelter: 每個維度各自保存土地等級與預設進入點。
    private static final String TAG_SHELTER_LEVEL = "ShelterLevel";
    private static final String TAG_SHELTER_ENTRY = "ShelterEntry";
    private int shelterLevel = 0;
    private net.minecraft.core.BlockPos shelterEntry;

    public PersonalDimensionData() {}

    private static PersonalDimensionData load(CompoundTag tag, HolderLookup.Provider registries) {
        PersonalDimensionData data = new PersonalDimensionData();
        data.dayTime = tag.getLong(TAG_DAY_TIME);
        data.daylightCycle = tag.getBoolean(TAG_DAYLIGHT_CYCLE);
        data.raining = tag.getBoolean(TAG_RAINING);
        data.rainTime = tag.getInt(TAG_RAIN_TIME);
        data.thundering = tag.getBoolean(TAG_THUNDERING);
        data.thunderTime = tag.getInt(TAG_THUNDER_TIME);
        data.clearWeatherTime = tag.getInt(TAG_CLEAR_WEATHER_TIME);
        // 旧存档缺该标签时默认开启天气循环
        data.weatherCycle = !tag.contains(TAG_WEATHER_CYCLE) || tag.getBoolean(TAG_WEATHER_CYCLE);
        // 旧存档缺该标签时默认关闭跟随（getBoolean 缺失即返回 false）
        data.followOverworldTime = tag.getBoolean(TAG_FOLLOW_OVERWORLD_TIME);
        data.followOverworldWeather = tag.getBoolean(TAG_FOLLOW_OVERWORLD_WEATHER);
        // 月相：旧存档缺该标签时按"跟随时间"处理（override=-1, cycle=true, follow=false）
        data.moonPhaseOverride = tag.contains(TAG_MOON_PHASE_OVERRIDE) ? tag.getInt(TAG_MOON_PHASE_OVERRIDE) : -1;
        data.moonPhaseCycle = !tag.contains(TAG_MOON_PHASE_CYCLE) || tag.getBoolean(TAG_MOON_PHASE_CYCLE);
        data.followOverworldMoonPhase = tag.getBoolean(TAG_FOLLOW_OVERWORLD_MOON_PHASE);
        data.shelterLevel = Math.max(0, Math.min(4, tag.getInt(TAG_SHELTER_LEVEL)));
        if (tag.contains(TAG_SHELTER_ENTRY, 10)) {
            CompoundTag entry = tag.getCompound(TAG_SHELTER_ENTRY);
            data.shelterEntry = new net.minecraft.core.BlockPos(entry.getInt("X"), entry.getInt("Y"), entry.getInt("Z"));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong(TAG_DAY_TIME, this.dayTime);
        tag.putBoolean(TAG_DAYLIGHT_CYCLE, this.daylightCycle);
        tag.putBoolean(TAG_RAINING, this.raining);
        tag.putInt(TAG_RAIN_TIME, this.rainTime);
        tag.putBoolean(TAG_THUNDERING, this.thundering);
        tag.putInt(TAG_THUNDER_TIME, this.thunderTime);
        tag.putInt(TAG_CLEAR_WEATHER_TIME, this.clearWeatherTime);
        tag.putBoolean(TAG_WEATHER_CYCLE, this.weatherCycle);
        tag.putBoolean(TAG_FOLLOW_OVERWORLD_TIME, this.followOverworldTime);
        tag.putBoolean(TAG_FOLLOW_OVERWORLD_WEATHER, this.followOverworldWeather);
        tag.putInt(TAG_MOON_PHASE_OVERRIDE, this.moonPhaseOverride);
        tag.putBoolean(TAG_MOON_PHASE_CYCLE, this.moonPhaseCycle);
        tag.putBoolean(TAG_FOLLOW_OVERWORLD_MOON_PHASE, this.followOverworldMoonPhase);
        tag.putInt(TAG_SHELTER_LEVEL, shelterLevel);
        if (shelterEntry != null) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("X", shelterEntry.getX());
            entry.putInt("Y", shelterEntry.getY());
            entry.putInt("Z", shelterEntry.getZ());
            tag.put(TAG_SHELTER_ENTRY, entry);
        }
        return tag;
    }

    public int getShelterLevel() { return shelterLevel; }

    /** 僅在完成土地擴張後呼叫；實際升級必須由伺服器驗證許可證。 */
    public void setShelterLevel(int level) {
        if (level < 0 || level > 4) throw new IllegalArgumentException("Shelter level must be 0..4");
        if (shelterLevel != level) { shelterLevel = level; setDirty(); }
    }

    public net.minecraft.core.BlockPos getShelterEntry() { return shelterEntry; }

    /** 呼叫端必須先驗證擁有者、可站立空間及目前土地邊界。 */
    public void setShelterEntry(net.minecraft.core.BlockPos pos) {
        if (pos != null && !com.ruadou.personaldimension.shelter.ShelterUpgradeRules.inside(shelterLevel, pos.getX(), pos.getZ())) {
            throw new IllegalArgumentException("Entry point outside shelter border");
        }
        shelterEntry = pos == null ? null : pos.immutable();
        setDirty();
    }

    public long getDayTime() {
        return this.dayTime;
    }

    public boolean isDaylightCycle() {
        return this.daylightCycle;
    }

    public boolean isRaining() {
        return this.raining;
    }

    public int getRainTime() {
        return this.rainTime;
    }

    public boolean isThundering() {
        return this.thundering;
    }

    public int getThunderTime() {
        return this.thunderTime;
    }

    public int getClearWeatherTime() {
        return this.clearWeatherTime;
    }

    public boolean isWeatherCycle() {
        return this.weatherCycle;
    }

    public boolean isFollowOverworldTime() {
        return this.followOverworldTime;
    }

    public boolean isFollowOverworldWeather() {
        return this.followOverworldWeather;
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

    /** 由 {@link PersonalDimensionLevelData} 在时刻/昼夜/跟随开关变化时调用，写入并标记脏。 */
    public void updateTime(long dayTime, boolean daylightCycle, boolean followOverworldTime) {
        this.dayTime = dayTime;
        this.daylightCycle = daylightCycle;
        this.followOverworldTime = followOverworldTime;
        this.setDirty();
    }

    /** 由 {@link PersonalDimensionLevelData} 在天气计时/循环/跟随开关变化时调用，写入并标记脏。 */
    public void updateWeather(boolean raining, int rainTime,
                              boolean thundering, int thunderTime, int clearWeatherTime,
                              boolean weatherCycle, boolean followOverworldWeather) {
        this.raining = raining;
        this.rainTime = rainTime;
        this.thundering = thundering;
        this.thunderTime = thunderTime;
        this.clearWeatherTime = clearWeatherTime;
        this.weatherCycle = weatherCycle;
        this.followOverworldWeather = followOverworldWeather;
        this.setDirty();
    }

    /** 由 {@link PersonalDimensionLevelData} 在月相覆盖/循环/跟随开关变化时调用，写入并标记脏。 */
    public void updateMoonPhase(int moonPhaseOverride, boolean moonPhaseCycle, boolean followOverworldMoonPhase) {
        this.moonPhaseOverride = moonPhaseOverride;
        this.moonPhaseCycle = moonPhaseCycle;
        this.followOverworldMoonPhase = followOverworldMoonPhase;
        this.setDirty();
    }
}
