package com.ruadou.personaldimension.neoforge.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Resolve M3U/PLS radio playlists on the audio worker, without OpenFM.
 * Nested playlists are common in radio directories; recursion, redirects,
 * input size and candidate count are bounded to avoid infinite loops.
 */
public final class RadioPlaylistResolver {
    private static final int MAX_LINES = 256;
    private static final int MAX_CHARS = 65_536;
    private static final int MAX_ENTRIES = 32;
    private static final int MAX_PLAYLIST_DEPTH = 4;
    private static final int TIMEOUT_MS = 5_000;

    private RadioPlaylistResolver() {}

    private record PlaylistResult(String directStream, List<String> entries) {}

    /** Avoid BufferedReader.readLine allocating an unbounded line before size checks. */
    private static String readBoundedLine(BufferedReader reader, int remaining,
                                          BooleanSupplier cancelled) throws IOException {
        StringBuilder line = new StringBuilder(Math.min(remaining, 256));
        int ch;
        while (true) {
            checkCancelled(cancelled);
            ch = reader.read();
            if (ch == -1 || ch == '\n') break;
            if (line.length() >= remaining) throw new IOException("電台播放清單內容過大");
            if (ch != '\r') line.append((char) ch);
        }
        if (ch == -1 && line.length() == 0) return null;
        return line.toString();
    }

    private static void checkCancelled(BooleanSupplier cancelled) throws IOException {
        if (cancelled.getAsBoolean()) throw new IOException("電台連線已取消");
    }

    private static boolean isPlaylistPath(URI uri) {
        String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
        return path.endsWith(".m3u") || path.endsWith(".m3u8") || path.endsWith(".pls");
    }

    public static String resolve(String address) throws IOException {
        return resolve(address, () -> false, connection -> {});
    }

    /**
     * Register each in-flight HTTP connection so a stop or dimension change can
     * interrupt the current playlist fetch. Never invoke on the render thread.
     */
    public static String resolve(String address, BooleanSupplier cancelled,
                                 Consumer<HttpURLConnection> registerConnection) throws IOException {
        return resolveNested(address, cancelled, registerConnection, new HashSet<>(), 0);
    }

    private static String resolveNested(String address, BooleanSupplier cancelled,
                                        Consumer<HttpURLConnection> registerConnection,
                                        Set<String> visited, int depth) throws IOException {
        checkCancelled(cancelled);
        URI uri = RadioUrlSecurity.requirePublicHttpUrl(address);
        checkCancelled(cancelled);
        if (!isPlaylistPath(uri)) {
            // A syntactically valid first entry can still be a dead station.
            // Probe playlist candidates before selecting one so the remaining
            // entries are genuine fallbacks, not just URL parsing fallbacks.
            // A direct user-supplied stream is opened only once by the player.
            if (depth > 0) return probeStream(uri, cancelled, registerConnection);
            return uri.toString();
        }
        if (depth >= MAX_PLAYLIST_DEPTH) throw new IOException("電台播放清單巢狀層數過多");
        if (!visited.add(uri.normalize().toString())) throw new IOException("電台播放清單出現循環連結");

        // Parse and CLOSE the parent playlist before opening a nested one.
        // Otherwise cancellation might only disconnect the child connection,
        // leaving the parent socket alive in the background.
        PlaylistResult result = readPlaylist(uri, cancelled, registerConnection);
        // A playlist URL can redirect to a raw MP3/AAC/Ogg response while
        // keeping its original .m3u suffix. Do not parse that URL again.
        if (result.directStream() != null) return result.directStream();
        List<String> entries = result.entries();
        IOException firstError = null;
        for (String candidate : entries) {
            checkCancelled(cancelled);
            try {
                return resolveNested(candidate, cancelled, registerConnection, visited, depth + 1);
            } catch (IOException ex) {
                checkCancelled(cancelled);
                if (firstError == null) firstError = ex;
            }
        }
        if (firstError != null) throw firstError;
        throw new IOException("電台播放清單沒有可用的串流網址");
    }

    /**
     * Validate a playlist stream candidate using a bounded GET. HEAD is not
     * reliable on Icecast/Shoutcast servers. Only response headers are read;
     * the temporary socket is closed before the player opens the real stream.
     */
    private static String probeStream(URI initial, BooleanSupplier cancelled,
                                      Consumer<HttpURLConnection> registerConnection) throws IOException {
        URI uri = initial;
        HttpURLConnection connection = null;
        try {
            for (int redirects = 0; redirects <= 3; redirects++) {
                checkCancelled(cancelled);
                uri = RadioUrlSecurity.requirePublicHttpUrl(uri.toString());
                checkCancelled(cancelled);
                connection = (HttpURLConnection) uri.toURL().openConnection();
                registerConnection.accept(connection);
                checkCancelled(cancelled);
                connection.setConnectTimeout(TIMEOUT_MS);
                connection.setReadTimeout(TIMEOUT_MS);
                connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("User-Agent", "PersonalShelter/Radio");
                connection.setRequestProperty("Icy-MetaData", "0");
                int status = connection.getResponseCode();
                checkCancelled(cancelled);
                if (status == 301 || status == 302 || status == 303
                        || status == 307 || status == 308) {
                    if (redirects == 3) throw new IOException("電台備用串流轉址次數過多");
                    String location = connection.getHeaderField("Location");
                    if (location == null || location.isBlank()) throw new IOException("電台備用串流轉址缺少網址");
                    URI next = RadioUrlSecurity.requirePublicHttpUrl(uri.resolve(location).toString());
                    checkCancelled(cancelled);
                    connection.disconnect();
                    registerConnection.accept(null);
                    connection = null;
                    uri = next;
                    continue;
                }
                if (status != HttpURLConnection.HTTP_OK && status != HttpURLConnection.HTTP_PARTIAL) {
                    throw new IOException("電台備用串流 HTTP 回應：" + status);
                }
                String type = connection.getContentType();
                if (type != null) {
                    String media = type.toLowerCase(Locale.ROOT).split(";", 2)[0].trim();
                    if (media.startsWith("text/html") || media.contains("mpegurl")
                            || media.contains("scpls") || media.contains("vnd.apple.mpegurl")) {
                        throw new IOException("電台備用串流回傳網頁或不支援的播放清單");
                    }
                }
                return uri.toString();
            }
            throw new IOException("電台備用串流轉址次數過多");
        } finally {
            if (connection != null) connection.disconnect();
            registerConnection.accept(null);
        }
    }

    private static PlaylistResult readPlaylist(URI initial, BooleanSupplier cancelled,
                                             Consumer<HttpURLConnection> registerConnection) throws IOException {
        URI uri = initial;
        HttpURLConnection connection = null;
        try {
            for (int redirects = 0; redirects <= 3; redirects++) {
                checkCancelled(cancelled);
                connection = (HttpURLConnection) uri.toURL().openConnection();
                registerConnection.accept(connection);
                checkCancelled(cancelled);
                connection.setConnectTimeout(TIMEOUT_MS);
                connection.setReadTimeout(TIMEOUT_MS);
                connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("User-Agent", "PersonalShelter/Radio");
                int status = connection.getResponseCode();
                checkCancelled(cancelled);
                if (status == 301 || status == 302 || status == 303
                        || status == 307 || status == 308) {
                    if (redirects == 3) throw new IOException("電台播放清單轉址次數過多");
                    String location = connection.getHeaderField("Location");
                    if (location == null || location.isBlank()) throw new IOException("電台播放清單轉址缺少網址");
                    URI next = RadioUrlSecurity.requirePublicHttpUrl(uri.resolve(location).toString());
                    checkCancelled(cancelled);
                    connection.disconnect();
                    registerConnection.accept(null);
                    connection = null;
                    uri = next;
                    continue;
                }
                if (status != HttpURLConnection.HTTP_OK) {
                    throw new IOException("電台播放清單 HTTP 回應：" + status);
                }
                break;
            }

            String type = connection.getContentType();
            String mediaType = type == null ? "" : type.toLowerCase(Locale.ROOT).split(";", 2)[0].trim();
            boolean playlistType = mediaType.contains("mpegurl") || mediaType.contains("scpls")
                    || mediaType.contains("playlist");
            boolean audioType = mediaType.startsWith("audio/") && !playlistType;
            String finalPath = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
            if (audioType || (!isPlaylistPath(uri) && !playlistType
                    && (finalPath.endsWith(".mp3") || finalPath.endsWith(".aac")
                    || finalPath.endsWith(".ogg") || finalPath.endsWith(".oga")))) {
                return new PlaylistResult(uri.toString(), List.of());
            }

            boolean pls = finalPath.endsWith(".pls") || mediaType.contains("scpls");
            List<String> entries = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                int chars = 0;
                for (int i = 0; i < MAX_LINES; i++) {
                    checkCancelled(cancelled);
                    String line = readBoundedLine(reader, MAX_CHARS - chars, cancelled);
                    if (line == null) break;
                    chars += line.length();
                    line = line.trim();
                    if (line.regionMatches(true, 0, "#EXT-X-", 0, 7)) {
                        throw new IOException("此電台使用 HLS 串流，目前尚不支援");
                    }
                    if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")
                            || line.startsWith("[")) continue;

                    // PLS metadata (Title1, Length1, etc.) is not a stream URL.
                    if (pls) {
                        if (!line.matches("(?i)^File[0-9]+\\s*=.*")) continue;
                        line = line.substring(line.indexOf('=') + 1).trim();
                    } else if (line.matches("(?i)^File[0-9]+\\s*=.*")) {
                        line = line.substring(line.indexOf('=') + 1).trim();
                    }
                    if (line.isEmpty()) continue;
                    try {
                        String candidate = RadioUrlSecurity.requirePublicHttpUrl(uri.resolve(line).toString()).toString();
                        entries.add(candidate);
                        if (entries.size() >= MAX_ENTRIES) break;
                    } catch (IOException | IllegalArgumentException ignored) {
                        checkCancelled(cancelled);
                        // Skip malformed/private entries, try the next public stream.
                    }
                }
            }
            if (entries.isEmpty()) throw new IOException("電台播放清單沒有可用的串流網址");
            return new PlaylistResult(null, entries);
        } finally {
            if (connection != null) connection.disconnect();
            registerConnection.accept(null);
        }
    }
}
