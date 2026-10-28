package com.ruadou.personaldimension.client;

/** 玩家目前所在土地的電台狀態；與控制器管理的「自己的土地」設定分離。 */
public final class ClientCurrentLandRadio {
    private ClientCurrentLandRadio() {}
    private static volatile boolean enabled;
    private static volatile String stationId = "";
    private static volatile String streamUrl = "";
    public static String streamUrl() { return streamUrl; }
    public static boolean enabled() { return enabled; }
    public static String stationId() { return stationId; }
    public static void update(boolean value, String id, String url) {
        enabled = value;
        stationId = id == null ? "" : id;
        streamUrl = url == null ? "" : url;
    }
    public static void clear() { update(false, "", ""); }
}
