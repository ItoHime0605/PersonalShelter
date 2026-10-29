package com.ruadou.personaldimension.client;

/** Client-only status bridge between the NeoForge radio engine and common GUI. */
public final class ClientRadioPlaybackStatus {
    private static volatile String message = "已停止";
    private ClientRadioPlaybackStatus() {}
    public static String get() { return message; }
    public static void set(String value) { message = value == null ? "未知狀態" : value; }
}
