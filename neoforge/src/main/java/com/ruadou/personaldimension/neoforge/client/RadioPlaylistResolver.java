package com.ruadou.personaldimension.neoforge.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
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
    // An extensionless URL may still serve a nested M3U/PLS manifest.
    private record StreamProbe(String url, boolean playlist) {}

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
                                 Consumer<HttpURLConnection> registerConnection,
                                 String avoidStream) throws IOException {
        return resolveWithMode(address, cancelled, registerConnection, avoidStream, false);
    }

    /** Parse a manifest found in an extensionless/mislabeled HTTP response. */
    public static String resolveDetectedPlaylist(String address, BooleanSupplier cancelled,
                                                  Consumer<HttpURLConnection> registerConnection,
                                                  String avoidStream) throws IOException {
        return resolveWithMode(address, cancelled, registerConnection, avoidStream, true);
    }

    private static String resolveWithMode(String address, BooleanSupplier cancelled,
                                          Consumer<HttpURLConnection> registerConnection,
                                          String avoidStream, boolean forcePlaylist) throws IOException {
        if (avoidStream == null || avoidStream.isBlank()) {
            return resolveNested(address, cancelled, registerConnection, new HashSet<>(), 0,
                    "", forcePlaylist);
        }
        try {
            return resolveNested(address, cancelled, registerConnection, new HashSet<>(), 0,
                    avoidStream, forcePlaylist);
        } catch (IOException noAlternative) {
            checkCancelled(cancelled);
            return resolveNested(address, cancelled, registerConnection, new HashSet<>(), 0,
                    "", forcePlaylist);
        }
    }

    private static String resolveNested(String address, BooleanSupplier cancelled,
                                        Consumer<HttpURLConnection> registerConnection,
                                        Set<String> visited, int depth, String avoidStream) throws IOException {
        return resolveNested(address, cancelled, registerConnection, visited, depth, avoidStream, false);
    }

    private static String resolveNested(String address, BooleanSupplier cancelled,
                                        Consumer<HttpURLConnection> registerConnection,
                                        Set<String> visited, int depth, String avoidStream,
                                        boolean forcePlaylist) throws IOException {
        checkCancelled(cancelled);
        URI uri = RadioUrlSecurity.requirePublicHttpUrl(address);
        checkCancelled(cancelled);
        if (!forcePlaylist && !isPlaylistPath(uri)) {
            // A syntactically valid first entry can still be a dead station.
            // Probe playlist candidates before selecting one so the remaining
            // entries are genuine fallbacks, not just URL parsing fallbacks.
            // A direct user-supplied stream is opened only once by the player.
            if (depth > 0) {
                if (uri.toString().equals(avoidStream)) {
                    throw new IOException("前次串流中斷，正在尋找其他備用來源");
                }
                StreamProbe probe = probeStream(uri, cancelled, registerConnection);
                if (probe.playlist()) {
                    // Playlist directories often use /listen or /stream with no
                    // .m3u/.pls extension. Inspect the MIME/signature and parse
                    // these as nested manifests, with the same depth/loop limits.
                    return resolveNested(probe.url(), cancelled, registerConnection,
                            visited, depth, avoidStream, true);
                }
                if (probe.url().equals(avoidStream)) {
                    throw new IOException("前次串流中斷，正在尋找其他備用來源");
                }
                return probe.url();
            }
            return uri.toString();
        }
        if (depth >= MAX_PLAYLIST_DEPTH) throw new IOException("電台播放清單巢狀層數過多");
        String key = uri.normalize().toString();
        if (!visited.add(key)) throw new IOException("電台播放清單出現循環連結");
        try {
            // Close the parent socket before opening a nested playlist.
            PlaylistResult result = readPlaylist(uri, cancelled, registerConnection);
            if (result.directStream() != null) {
                if (result.directStream().equals(avoidStream)) {
                    throw new IOException("前次串流中斷，正在尋找其他備用來源");
                }
                return result.directStream();
            }
            IOException firstError = null;
            for (String candidate : result.entries()) {
                checkCancelled(cancelled);
                try {
                    return resolveNested(candidate, cancelled, registerConnection, visited, depth + 1, avoidStream);
                } catch (IOException ex) {
                    checkCancelled(cancelled);
                    if (firstError == null) firstError = ex;
                }
            }
            if (firstError != null) throw firstError;
            throw new IOException("電台播放清單沒有可用的串流網址");
        } finally {
            // Track only the active recursion path. A shared nested playlist
            // may legitimately appear again under a different fallback entry.
            visited.remove(key);
        }
    }

    /**
     * Validate a playlist stream candidate using a bounded GET. HEAD is not
     * reliable on Icecast/Shoutcast servers. Only response headers are read;
     * the temporary socket is closed before the player opens the real stream.
     */
    private static StreamProbe probeStream(URI initial, BooleanSupplier cancelled,
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
                String media = type == null ? "" : type.toLowerCase(Locale.ROOT).split(";", 2)[0].trim();
                if (media.contains("opus")) {
                    throw new IOException("電台備用串流格式不支援：" + media);
                }
                if (media.contains("mpegurl") || media.contains("scpls")
                        || media.contains("playlist")) {
                    return new StreamProbe(uri.toString(), true);
                }
                // A 200 OK response is not proof of a working station: dead
                // endpoints often serve HTML, JSON or an M3U error document.
                // Probe a small bounded prefix so the next playlist candidate
                // is attempted before returning a broken URL to the player.
                byte[] signature;
                try (InputStream stream = connection.getInputStream()) {
                    signature = stream.readNBytes(48);
                }
                checkCancelled(cancelled);
                if (signature.length == 0) throw new IOException("電台備用串流沒有音訊資料");
                String prefix = new String(signature, StandardCharsets.US_ASCII)
                        .stripLeading().toLowerCase(Locale.ROOT);
                if (prefix.startsWith("#extm3u") || prefix.startsWith("[playlist]")) {
                    return new StreamProbe(uri.toString(), true);
                }
                if (prefix.startsWith("<!doctype") || prefix.startsWith("<html")
                        || prefix.startsWith("<?xml") || prefix.startsWith("{")) {
                    throw new IOException("電台備用串流回傳網頁或錯誤訊息");
                }
                boolean ogg = signature.length >= 4 && signature[0] == 'O'
                        && signature[1] == 'g' && signature[2] == 'g' && signature[3] == 'S';
                boolean mp4 = signature.length >= 8 && signature[4] == 'f'
                        && signature[5] == 't' && signature[6] == 'y' && signature[7] == 'p';
                boolean adts = signature.length >= 2 && (signature[0] & 255) == 255
                        && (signature[1] & 0xf6) == 0xf0;
                boolean mp3 = signature.length >= 3 && signature[0] == 'I'
                        && signature[1] == 'D' && signature[2] == '3';
                if (!mp3 && signature.length >= 2) {
                    int second = signature[1] & 255;
                    mp3 = (signature[0] & 255) == 255
                            && (second & 0xe0) == 0xe0 && (second & 0x06) != 0;
                }
                if ((media.startsWith("text/") || media.contains("json") || media.contains("xml"))
                        && !(ogg || mp4 || adts || mp3)) {
                    throw new IOException("電台備用串流回傳非音訊內容：" + media);
                }
                return new StreamProbe(uri.toString(), false);
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
            boolean pls = finalPath.endsWith(".pls") || mediaType.contains("scpls");
            List<String> entries = new ArrayList<>();
            try (java.io.BufferedInputStream buffered = new java.io.BufferedInputStream(
                    connection.getInputStream(), 8192)) {
                // Some stations label #EXTM3U as audio/mpeg. Check the actual
                // response before returning it as a direct audio stream.
                buffered.mark(64);
                byte[] signature = new byte[48];
                int signatureLength = buffered.read(signature);
                buffered.reset();
                String leading = new String(signature, 0, Math.max(0, signatureLength),
                        StandardCharsets.US_ASCII).stripLeading().toLowerCase(Locale.ROOT);
                boolean manifest = leading.startsWith("#extm3u")
                        || leading.startsWith("[playlist]") || leading.startsWith("#ext-x-");
                if (!manifest && (audioType || (!isPlaylistPath(uri) && !playlistType
                        && (finalPath.endsWith(".mp3") || finalPath.endsWith(".aac")
                        || finalPath.endsWith(".ogg") || finalPath.endsWith(".oga"))))) {
                    return new PlaylistResult(uri.toString(), List.of());
                }
                if (leading.startsWith("[playlist]")) pls = true;
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(buffered, StandardCharsets.UTF_8));
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
