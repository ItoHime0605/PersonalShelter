package com.ruadou.personaldimension.neoforge.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

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

    /** Avoid BufferedReader.readLine allocating an unbounded line before size checks. */
    private static String readBoundedLine(BufferedReader reader, int remaining) throws IOException {
        StringBuilder line = new StringBuilder(Math.min(remaining, 256));
        int ch;
        while ((ch = reader.read()) != -1) {
            if (ch == '\n') break;
            if (line.length() >= remaining) throw new IOException("Radio playlist too large");
            line.append((char) ch);
        }
        if (ch == -1 && line.length() == 0) return null;
        return line.toString();
    }



    public static String resolve(String address) throws IOException {
        return resolve(address, () -> false, connection -> {});
    }

    /**
     * Register the playlist connection with the player so stop/leave-world
     * can disconnect a blocked playlist fetch, not just the final audio stream.
     */
    public static String resolve(String address, BooleanSupplier cancelled,
                                 Consumer<HttpURLConnection> registerConnection) throws IOException {
        if (cancelled.getAsBoolean()) throw new IOException("電台連線已取消");
        URI uri = RadioUrlSecurity.requirePublicHttpUrl(address);
        if (cancelled.getAsBoolean()) throw new IOException("電台連線已取消");
        String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
        if (!path.endsWith(".m3u") && !path.endsWith(".m3u8") && !path.endsWith(".pls")) {
            return uri.toString();
        }
        HttpURLConnection connection = null;
        try {
            for (int redirects = 0; redirects <= 3; redirects++) {
                if (cancelled.getAsBoolean()) throw new IOException("電台連線已取消");
                connection = (HttpURLConnection) uri.toURL().openConnection();
                registerConnection.accept(connection);
                if (cancelled.getAsBoolean()) throw new IOException("電台連線已取消");
                connection.setConnectTimeout(TIMEOUT_MS);
                connection.setReadTimeout(TIMEOUT_MS);
                connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("User-Agent", "PersonalShelter/Radio");
                int status = connection.getResponseCode();
                if (cancelled.getAsBoolean()) throw new IOException("電台連線已取消");
                if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                    if (redirects == 3) throw new IOException("電台播放清單轉址次數過多");
                    String location = connection.getHeaderField("Location");
                    if (location == null || location.isBlank()) throw new IOException("電台播放清單轉址缺少網址");
                    URI next = RadioUrlSecurity.requirePublicHttpUrl(uri.resolve(location).toString());
                    if (cancelled.getAsBoolean()) throw new IOException("電台連線已取消");
                    connection.disconnect();
                    registerConnection.accept(null);
                    connection = null;
                    uri = next;
                    continue;
                }
                if (status != HttpURLConnection.HTTP_OK) {
                    throw new IOException("Radio playlist HTTP status: " + status);
                }
                break;
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                int chars = 0;
                for (int i = 0; i < MAX_LINES; i++) {
                    if (cancelled.getAsBoolean()) throw new IOException("電台連線已取消");
                    String line = readBoundedLine(reader, MAX_CHARS - chars);
                    if (line == null) break;
                    chars += line.length();
                    line = line.trim();
                    if (line.startsWith("#EXT-X-")) throw new IOException("HLS playlists are not supported by the radio decoder");
                    if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")
                            || line.startsWith("[") || line.regionMatches(true, 0, "NumberOfEntries", 0, 15)
                            || line.regionMatches(true, 0, "Title", 0, 5)
                            || line.regionMatches(true, 0, "Length", 0, 6)
                            || line.regionMatches(true, 0, "Version", 0, 7)) continue;
                    if (line.matches("(?i)^File[0-9]+\\s*=.*")) {
                        int equals = line.indexOf('=');
                        if (equals < 0) continue;
                        line = line.substring(equals + 1).trim();
                    }
                    try {
                        return RadioUrlSecurity.requirePublicHttpUrl(uri.resolve(line).toString()).toString();
                    } catch (IOException | IllegalArgumentException ignored) {
                        // Ignore invalid playlist entries and try the next one.
                    }
                }
            }
            throw new IOException("Radio playlist contains no valid stream");
        } finally {
            if (connection != null) connection.disconnect();
            registerConnection.accept(null);
        }
    }

}
