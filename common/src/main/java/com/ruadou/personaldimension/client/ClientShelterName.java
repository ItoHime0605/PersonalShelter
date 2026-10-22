package com.ruadou.personaldimension.client;

public final class ClientShelterName {
    private static String name = "";
    private ClientShelterName() {}
    public static String get() { return name; }
    public static void update(String value) { name = value == null ? "" : value; }
}
