package com.ruadou.personaldimension.radio;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 一塊私人土地的電台設定。設定由土地擁有者管理，訪客只能收聽。
 * 這是資料模型，不負責音訊下載或播放；播放器接入前不得宣稱功能已完成。
 */
public final class LandRadioSettings {
    public static final int MAX_STATIONS = 32;
    public static final int MAX_NAME_LENGTH = 256;
    public static final int MAX_URL_LENGTH = 1024;

    private boolean enabled;
    private String selectedStationId = "";
    private final List<Station> customStations = new ArrayList<>();

    public boolean enabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }

    public String selectedStationId() { return selectedStationId; }
    public void selectStation(String id) {
        selectedStationId = Objects.requireNonNull(id, "id").trim();
        if (selectedStationId.length() > 80) throw new IllegalArgumentException("電台識別碼過長");
    }

    public List<Station> customStations() { return List.copyOf(customStations); }

    public void addStation(String id, String name, String streamUrl) {
        if (customStations.size() >= MAX_STATIONS) throw new IllegalStateException("自訂電台數量已達上限");
        Station station = new Station(id, name, streamUrl);
        if (customStations.stream().anyMatch(s -> s.id().equals(station.id()))) {
            throw new IllegalArgumentException("電台識別碼重複");
        }
        customStations.add(station);
    }

    public boolean removeStation(String id) {
        boolean removed = customStations.removeIf(s -> s.id().equals(id));
        if (removed && selectedStationId.equals(id)) {
            selectedStationId = "";
            enabled = false;
        }
        return removed;
    }

    public record Station(String id, String name, String streamUrl) {
        public Station {
            id = Objects.requireNonNull(id, "id").trim();
            name = Objects.requireNonNull(name, "name").trim();
            streamUrl = Objects.requireNonNull(streamUrl, "streamUrl").trim();
            if (!id.matches("[a-zA-Z0-9_-]{1,80}")) throw new IllegalArgumentException("電台識別碼無效");
            if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) throw new IllegalArgumentException("電台名稱長度無效");
            if (streamUrl.length() > MAX_URL_LENGTH || !isValidStreamUrl(streamUrl)) {
                throw new IllegalArgumentException("串流網址無效");
            }
        }
    }

    /**
     * 初步格式檢查，不代表網路安全驗證。播放器還必須檢查 DNS、
     * 重新導向、內網位址與連線逾時，禁止存取本機及內部服務。
     */
    public static boolean isValidStreamUrl(String url) {
        if (url == null || url.length() > MAX_URL_LENGTH) return false;
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            return ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))
                    && uri.getHost() != null && !uri.getHost().isBlank()
                    && uri.getRawUserInfo() == null && uri.getFragment() == null;
        } catch (URISyntaxException exception) {
            return false;
        }
    }
}
