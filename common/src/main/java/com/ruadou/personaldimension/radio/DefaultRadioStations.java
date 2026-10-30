package com.ruadou.personaldimension.radio;

import java.util.List;

/** 伺服器與 GUI 共用的內建電台識別碼清單。串流網址保存在資源檔中。 */
public final class DefaultRadioStations {
    private DefaultRadioStations() {}
    public record Entry(String id, String name, String url) {}
    public static final List<Entry> ENTRIES = List.of(
        new Entry("station_02", "飛碟聯播網", "https://stream.rcs.revma.com/em90w4aeewzuv.m4a"),
        new Entry("station_03", "i Radio 中廣音樂網", "https://stream.rcs.revma.com/ks4vsmg3qtzuv"),
        new Entry("station_04", "中廣流行網", "https://stream.rcs.revma.com/s1zttsg3qtzuv"),
        new Entry("station_05", "Big B Radio JPOP", "http://82.145.63.122:9700/s"),
        new Entry("station_07", "Vocaloid Radio", "http://curiosity.shoutca.st:8019/stream"),
        new Entry("station_09", "Radio Paradise", "http://stream-dc1.radioparadise.com/mp3-128"),
        new Entry("station_10", "Hot 108 Jamz", "https://live.powerhitz.com/hot108"),
        new Entry("station_11", "181.FM Awesome 80s", "https://listen.181fm.com/181-awesome80s_128k.mp3"),
        new Entry("station_12", "181.FM Power 181", "https://listen.181fm.com/181-power_128k.mp3")
    );
    public static String url(String id) {
        return ENTRIES.stream().filter(s -> s.id().equals(id)).map(Entry::url).findFirst().orElse("");
    }
    public static boolean contains(String id) {
        return ENTRIES.stream().anyMatch(s -> s.id().equals(id));
    }
}
