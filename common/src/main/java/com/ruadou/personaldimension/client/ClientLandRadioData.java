package com.ruadou.personaldimension.client;

/** 目前土地的電台狀態快照；收到伺服器同步後才更新。 */
public final class ClientLandRadioData {
    private ClientLandRadioData() {}
    private static volatile boolean enabled;
    private static volatile String stationId = "";
    private static volatile java.util.List<com.ruadou.personaldimension.radio.LandRadioSettings.Station> customStations = java.util.List.of();
    private static volatile long revision;
    public static boolean enabled() { return enabled; }
    public static String stationId() { return stationId; }
    public static void update(boolean active, String id) {
        enabled = active;
        stationId = id == null ? "" : id;
    }
    public static java.util.List<com.ruadou.personaldimension.radio.LandRadioSettings.Station> customStations() { return customStations; }
    public static long revision() { return revision; }
    public static void update(boolean active, String id, java.util.List<com.ruadou.personaldimension.radio.LandRadioSettings.Station> stations) {
        update(active, id);
        customStations = java.util.List.copyOf(stations);
        revision++;
    }
    public static void clear() { update(false, "", java.util.List.of()); }
}
