package com.ruadou.personaldimension.radio;

import java.util.List;

/** 伺服器與 GUI 共用的內建電台識別碼清單。串流網址保存在資源檔中。 */
public final class DefaultRadioStations {
    private DefaultRadioStations() {}
    public record Entry(String id, String name) {}
    public static final List<Entry> ENTRIES = List.of(
        new Entry("station_02", "飛碟聯播網"),
        new Entry("station_03", "i Radio 中廣音樂網"),
        new Entry("station_04", "中廣流行網"),
        new Entry("station_05", "Big B Radio JPOP"),
        new Entry("station_07", "Vocaloid Radio"),
        new Entry("station_09", "Radio Paradise"),
        new Entry("station_10", "Hot 108 Jamz"),
        new Entry("station_11", "181.FM Awesome 80s"),
        new Entry("station_12", "181.FM Power 181")
    );
    public static boolean contains(String id) {
        return ENTRIES.stream().anyMatch(s -> s.id().equals(id));
    }
}
