package com.ruadou.personaldimension.neoforge.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Resolve a small M3U/PLS radio playlist into a stream URL off the render thread.
 * Does not decode or play audio. The future standalone player must call this
 * from its network worker, never from a Minecraft client tick.
 */
public final class RadioPlaylistResolver {
    private static final int MAX_LINES = 256;
    private static final int MAX_CHARS = 65_536;
    private static final int TIMEOUT_MS = 5_000;

    private RadioPlaylistResolver() {}

    public static String resolve(String address) throws IOException {
        URI uri = checkedUri(address);
        String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
        if (!path.endsWith(".m3u") && !path.endsWith(".m3u8") && !path.endsWith(".pls")) {
            return uri.toString();
        }
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("User-Agent", "PersonalShelter/Radio");
        try {
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("Radio playlist HTTP status: " + status);
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                int chars = 0;
                for (int i = 0; i < MAX_LINES; i++) {
                    String line = reader.readLine();
                    if (line == null) break;
                    chars += line.length();
                    if (chars > MAX_CHARS) throw new IOException("Radio playlist too large");
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")
                            || line.startsWith("[") || line.regionMatches(true, 0, "NumberOfEntries", 0, 15)
                            || line.regionMatches(true, 0, "Title", 0, 5)
                            || line.regionMatches(true, 0, "Length", 0, 6)
                            || line.regionMatches(true, 0, "Version", 0, 7)) continue;
                    if (line.regionMatches(true, 0, "File", 0, 4)) {
                        int equals = line.indexOf('=');
                        if (equals < 0) continue;
                        line = line.substring(equals + 1).trim();
                    }
                    try {
                        return checkedUri(uri.resolve(line).toString()).toString();
                    } catch (IllegalArgumentException ignored) {
                        // Ignore invalid playlist entries and try the next one.
                    }
                }
            }
            throw new IOException("Radio playlist contains no valid stream");
        } finally {
            connection.disconnect();
        }
    }

    private static URI checkedUri(String text) {
        URI uri = URI.create(text);
        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))
                || uri.getHost() == null || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("Only HTTP/HTTPS radio streams are allowed");
        }
        return uri;
    }
}
