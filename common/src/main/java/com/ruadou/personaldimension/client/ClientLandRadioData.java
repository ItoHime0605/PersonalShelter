package com.ruadou.personaldimension.client;

/** 目前土地的電台狀態快照；收到伺服器同步後才更新。 */
public final class ClientLandRadioData {
    private ClientLandRadioData() {}
    private static volatile boolean enabled;
    private static volatile String stationId = "";
    public static boolean enabled() { return enabled; }
    public static String stationId() { return stationId; }
    public static void update(boolean active, String id) {
        enabled = active;
        stationId = id == null ? "" : id;
    }
    public static void clear() { update(false, ""); }
}
