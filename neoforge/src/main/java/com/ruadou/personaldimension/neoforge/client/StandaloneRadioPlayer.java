package com.ruadou.personaldimension.neoforge.client;

import com.ruadou.personaldimension.client.ClientCurrentLandRadio;
import com.ruadou.personaldimension.client.ClientRadioListening;
import com.ruadou.personaldimension.client.ClientRadioVolume;
import com.ruadou.personaldimension.client.ClientRadioPlaybackStatus;
import net.minecraft.client.Minecraft;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.AudioFormat;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.SampleBuffer;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Header;
import net.sourceforge.jaad.adts.ADTSDemultiplexer;
import net.sourceforge.jaad.mp4.MP4Container;
import net.sourceforge.jaad.mp4.api.AudioTrack;
import net.sourceforge.jaad.mp4.api.Track;
import java.util.List;
import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Locale;
import java.util.Objects;

/** Independent MP3, Ogg and ADTS AAC radio engine; decoding never runs on the game thread. */
public final class StandaloneRadioPlayer {
    private static final Object LOCK = new Object();
    private static volatile Session active;
    private static volatile String currentUrl = "";
    private static volatile long retryAfter;
    private static volatile String failedUrl = "";
    // On a playlist reconnect, prefer another source after the last one failed.
    private static volatile String failedStreamUrl = "";
    private static volatile String lastFailure = "";
    // Unsupported HLS manifests must not be downloaded on every retry cycle.
    private static volatile String unsupportedUrl = "";
    private static volatile String status = "已停止";
    private static final long RETRY_DELAY_MS = 5_000L;
    public static String status() { return status; }
    /** True only while the active session has started audio output. */
    public static boolean isPlaying() {
        Session session = active;
        if (session == null || session.closed.get() || !"播放中".equals(status)) return false;
        SourceDataLine line = session.getLine();
        return line != null && line.isOpen() && line.isRunning();
    }
    public static String lastFailure() { return lastFailure; }
    private StandaloneRadioPlayer() {}

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        boolean allowed = mc.player != null && mc.level != null && ClientRadioListening.shouldPlay()
                && mc.level.dimension().location().toString().equals(ClientCurrentLandRadio.dimensionId());
        String url = allowed ? ClientCurrentLandRadio.streamUrl() : "";
        synchronized (LOCK) {
            if (url.isBlank()) {
                if (active != null || !currentUrl.isEmpty() || !status.equals("已停止")) stopLocked();
                unsupportedUrl = "";
                failedUrl = "";
                failedStreamUrl = "";
                retryAfter = 0;
                return;
            }
            if (active != null && url.equals(currentUrl)) return;
            // A .m3u8 URL can be a plain UTF-8 M3U radio playlist, not HLS.
            // Let RadioPlaylistResolver inspect the manifest and reject actual HLS.
            // A blocked station must never leave a previous station playing.
            if (url.equals(unsupportedUrl)) {
                if (active != null || !currentUrl.isEmpty()) stopLocked();
                return;
            }
            // An unsupported HLS station stays blocked until the listener
            // switches stations or disables listening. Do not clear the block
            // during an automatic reconnect.
            if (!url.equals(failedUrl)) {
                unsupportedUrl = "";
                retryAfter = 0;
                failedUrl = "";
                failedStreamUrl = "";
                lastFailure = "";
            }
            if (url.equals(failedUrl) && System.currentTimeMillis() < retryAfter) {
                String waiting = "等待重試（" + Math.max(1L, (retryAfter - System.currentTimeMillis() + 999L) / 1000L) + " 秒）";
                if (!waiting.equals(status)) {
                    status = waiting;
                    ClientRadioPlaybackStatus.set(status);
                }
                return;
            }
            stopLocked();
            Session session = new Session(url, url.equals(failedUrl) ? failedStreamUrl : "");
            active = session;
            currentUrl = url;
            status = "正在連線";
            ClientRadioPlaybackStatus.set(status);
            Thread thread = new Thread(() -> play(session), "PersonalShelter-Radio");
            thread.setDaemon(true);
            thread.start();
        }
    }

    public static void stop() {
        synchronized (LOCK) {
            stopLocked();
            // Explicit stop is a fresh listening session. Allow a later
            // deliberate retry of a station previously identified as HLS.
            unsupportedUrl = "";
            failedUrl = "";
            failedStreamUrl = "";
            retryAfter = 0;
        }
    }

    private static void stopLocked() {
        Session old = active;
        active = null;
        currentUrl = "";
        if (old != null) {
            old.cancel();
        }
        status = "已停止";
        ClientRadioPlaybackStatus.set(status);
    }

    private static void play(Session session) {
        try {
            String url = RadioPlaylistResolver.resolve(session.url, session.closed::get,
                    connection -> session.setConnection(connection), session.avoidStreamUrl);
            session.resolvedStreamUrl = url;
            if (session.closed.get()) return;
            // DNS and address checks happen off-thread, before opening any socket.
            URI uri = RadioUrlSecurity.requirePublicHttpUrl(url);
            if (session.closed.get()) return;
            // Inspect an already-open response before treating an extensionless
            // URL as a direct stream. Normal streams pay no extra probe cost.
            boolean retriedDetectedPlaylist = false;
            while (!session.closed.get()) {
            HttpURLConnection conn = null;
            for (int redirects = 0; redirects <= 3; redirects++) {
                if (session.closed.get()) return;
                conn = (HttpURLConnection) uri.toURL().openConnection();
                session.setConnection(conn);
                if (session.closed.get()) return;
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(8000);
                conn.setInstanceFollowRedirects(false);
                conn.setRequestProperty("Icy-MetaData", "0");
                conn.setRequestProperty("User-Agent", "PersonalShelter/Radio");
                int responseCode = conn.getResponseCode();
                if (session.closed.get()) return;
                if (responseCode == 301 || responseCode == 302 || responseCode == 303
                        || responseCode == 307 || responseCode == 308) {
                    if (redirects == 3) throw new IllegalStateException("電台轉址次數過多");
                    String location = conn.getHeaderField("Location");
                    if (location == null || location.isBlank()) throw new IllegalStateException("電台轉址缺少目標網址");
                    // A public URL may redirect to a private address; validate every hop.
                    URI target = RadioUrlSecurity.requirePublicHttpUrl(uri.resolve(location).toString());
                    if (session.closed.get()) return;
                    conn.disconnect();
                    session.setConnection(null);
                    uri = target;
                    continue;
                }
                if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_PARTIAL) {
                    throw new IllegalStateException("電台伺服器回應 HTTP " + responseCode);
                }
                break;
            }
            String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
            if (path.endsWith(".opus")) {
                throw new IllegalArgumentException("目前不支援此音訊格式");
            }
            String contentType = conn.getContentType();
            boolean suspiciousContentType = false;
            boolean playlistContentType = false;
            if (contentType != null) {
                String type = contentType.toLowerCase(Locale.ROOT).split(";", 2)[0].trim();
                if (type.contains("opus")) {
                    throw new IllegalArgumentException("不支援的電台音訊格式：" + type);
                }
                playlistContentType = type.contains("mpegurl") || type.contains("scpls")
                        || type.contains("playlist");
                // Some legitimate Icecast streams mislabel MP3/AAC as text/plain.
                // Defer rejecting suspicious MIME types until the audio signature
                // has been inspected; still reject real HTML/JSON/XML responses.
                suspiciousContentType = type.startsWith("text/") || type.contains("json")
                        || type.contains("xml");
            }
            boolean aac = path.endsWith(".aac") || (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("aac"));
            boolean ogg = path.endsWith(".ogg") || (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("ogg"));
            // Some Icecast/Shoutcast servers insert ICY metadata even when the
            // client requests Icy-MetaData: 0. Strip it before probing or
            // decoding, otherwise MP3/AAC frames become corrupted periodically.
            InputStream networkAudio = conn.getInputStream();
            String metaintHeader = conn.getHeaderField("icy-metaint");
            if (metaintHeader != null && !metaintHeader.isBlank()) {
                final int interval;
                try {
                    interval = Integer.parseInt(metaintHeader.trim());
                } catch (NumberFormatException exception) {
                    throw new IOException("電台 ICY 中繼資料間隔無效", exception);
                }
                // Reject implausibly large intervals before handing an untrusted
                // HTTP stream to the decoders. A corrupt metaint can otherwise
                // postpone metadata handling indefinitely and hide stream errors.
                if (interval <= 0 || interval > 16 * 1024 * 1024)
                    throw new IOException("電台 ICY 中繼資料間隔無效");
                networkAudio = new IcyMetadataInputStream(networkAudio, interval);
            }
            try (PushbackInputStream input = new PushbackInputStream(new BufferedInputStream(networkAudio, 65536), 12)) {
                // Radio servers often return application/octet-stream or omit file extensions.
                // Detect the Ogg container signature instead of relying on HTTP metadata alone.
                // Live AAC and MP3 streams may begin with ID3v2 metadata.
                // Remove only the bounded tag before probing; leave audio bytes unread.
                byte[] signature = RadioAudioProbe.peekAfterId3(input);
                RadioAudioProbe.Kind kind = RadioAudioProbe.classify(signature);
                if (kind == RadioAudioProbe.Kind.NON_AUDIO) {
                    throw new IOException("電台網址回傳網頁或錯誤訊息，並非音訊串流");
                }
                boolean oggSignature = kind == RadioAudioProbe.Kind.OGG;
                boolean mp4Signature = kind == RadioAudioProbe.Kind.MP4_AAC;
                boolean playlistSignature = kind == RadioAudioProbe.Kind.PLAYLIST;
                if (playlistSignature || playlistContentType) {
                    if (retriedDetectedPlaylist) {
                        throw new IOException("電台播放清單重複回傳播放清單，沒有可播放的音訊");
                    }
                    // Release the manifest socket before opening a resolver
                    // connection; cancellation still closes in-flight requests.
                    input.close();
                    conn.disconnect();
                    session.setConnection(null);
                    url = RadioPlaylistResolver.resolveDetectedPlaylist(url, session.closed::get,
                            connection -> session.setConnection(connection), session.avoidStreamUrl);
                    if (session.closed.get()) return;
                    session.resolvedStreamUrl = url;
                    uri = RadioUrlSecurity.requirePublicHttpUrl(url);
                    retriedDetectedPlaylist = true;
                    continue;
                }
                // Use the same validated signature rules as the playlist resolver.
                boolean adtsSignature = kind == RadioAudioProbe.Kind.ADTS_AAC;
                boolean mp3Signature = kind == RadioAudioProbe.Kind.MP3;
                if (suspiciousContentType && !(oggSignature || mp4Signature || adtsSignature || mp3Signature)) {
                    throw new IOException("電台回傳網頁或非音訊內容：" + contentType);
                }
                if (mp4Signature) {
                    session.setInput(input);
                    if (!session.closed.get()) playMp4Aac(session, input);
                    return;
                }
                if (oggSignature || (ogg && !adtsSignature && !mp3Signature)) {
                    session.setInput(input);
                    if (!session.closed.get()) playOgg(session, input);
                    return;
                }
                if (adtsSignature || (aac && !mp3Signature)) {
                    session.setInput(input);
                    if (!session.closed.get()) playAac(session, input);
                    return;
                }
                if (!mp3Signature && (path.endsWith(".m4a") || path.endsWith(".mp4")
                        || (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("mp4")))) {
                    throw new IOException("M4A／MP4 串流缺少可辨識的 AAC 音訊標頭");
                }
                session.setInput(input);
                if (session.closed.get()) return;
                Bitstream stream = new Bitstream(input);
                Decoder decoder = new Decoder();
                // Reuse a PCM buffer instead of allocating on every MP3 frame.
                byte[] bytes = new byte[16384];
                int consecutiveDecodeFailures = 0;
                int emptyDecodedFrames = 0;
                while (!session.closed.get()) {
                    Header header;
                    try {
                        header = stream.readFrame();
                    } catch (javazoom.jl.decoder.BitstreamException badHeader) {
                        // A damaged transport frame should not kill the entire station.
                        // Bound retries to avoid a tight loop on a permanently broken stream.
                        stream.closeFrame();
                        if (++consecutiveDecodeFailures >= 5) {
                            throw new IOException("MP3 音訊連續讀取失敗", badHeader);
                        }
                        continue;
                    }
                    if (header == null) break;
                    try {
                        SampleBuffer pcm;
                        try {
                            pcm = (SampleBuffer) decoder.decodeFrame(header, stream);
                            consecutiveDecodeFailures = 0;
                        } catch (javazoom.jl.decoder.DecoderException badFrame) {
                            // A single corrupt MP3 frame must not kill a live radio session.
                            // Give up after repeated failures instead of looping forever.
                            if (++consecutiveDecodeFailures >= 5) {
                                throw new IOException("MP3 音訊連續解碼失敗", badFrame);
                            }
                            continue;
                        }
                        if (session.closed.get()) break;
                        AudioFormat format = new AudioFormat(pcm.getSampleFrequency(), 16,
                                pcm.getChannelCount(), true, false);
                        SourceDataLine output = session.getLine();
                        // Some radio streams splice tracks with different sample rates or
                        // channel counts. Never send their PCM to a stale audio format.
                        if (output == null || !output.getFormat().matches(format)) {
                            if (output != null) {
                                session.setLine(null);
                                output.stop();
                                output.flush();
                                output.close();
                            }
                            output = openAudioLine(session, format);
                            if (output == null) return;
                        }
                        short[] samples = pcm.getBuffer();
                        int length = pcm.getBufferLength();
                        if (length == 0) {
                            // Reject a damaged stream that decodes headers but never PCM.
                            if (++emptyDecodedFrames >= 128)
                                throw new IOException("MP3 音訊持續沒有可播放的資料");
                            continue;
                        }
                        emptyDecodedFrames = 0;
                        int byteLength = Math.multiplyExact(length, 2);
                        if (bytes.length < byteLength) bytes = new byte[byteLength];
                        RadioPcm.fromShorts(samples, length, bytes, ClientRadioVolume.get());
                        writePcm(session, output, bytes, byteLength, format.getFrameSize());
                        markPlaying(session);
                    } finally {
                        stream.closeFrame();
                    }
                }
            }
            // The MP3 stream reached EOF; reconnect through the normal policy.
            break;
            }
        } catch (Exception ex) {
            // Do not hammer unsupported HLS manifests every ten seconds.
            // Changing stations or disabling/re-enabling listening clears this state.
            String reason = ex.getMessage();
            if (reason != null && reason.contains("HLS")) {
                synchronized (LOCK) {
                    if (active == session && !session.closed.get()) unsupportedUrl = session.url;
                }
            }
            synchronized (LOCK) {
                if (!session.closed.get() && active == session) {
                    lastFailure = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                    status = describeFailure(ex);
                    ClientRadioPlaybackStatus.set(status);
                }
            }
        } finally {
            boolean interrupted = session.closed.get();
            session.close();
            synchronized (LOCK) {
                if (active == session) {
                    active = null;
                    currentUrl = "";
                    failedUrl = session.url;
                    failedStreamUrl = session.resolvedStreamUrl == null ? "" : session.resolvedStreamUrl;
                    retryAfter = System.currentTimeMillis() + RETRY_DELAY_MS;
                    if (!interrupted && !status.startsWith("播放失敗")) {
                        status = "連線中斷，稍後重試";
                        ClientRadioPlaybackStatus.set(status);
                    }
                }
            }
        }
    }

    /**
     * Publish the playing state only after a complete PCM buffer was accepted.
     * Serializing with stop/switch prevents a cancelled session from briefly
     * marking a newer station as playing (and stopping vanilla music).
     */
    private static void markPlaying(Session session) {
        synchronized (LOCK) {
            if (active == session && !session.closed.get()
                    && session.getLine() != null && session.getLine().isRunning()
                    && !"播放中".equals(status)) {
                status = "播放中";
                ClientRadioPlaybackStatus.set(status);
            }
        }
    }

    /** Give players a useful Traditional Chinese error instead of a Java exception name. */
    private static String describeFailure(Exception exception) {
        if (exception instanceof java.net.UnknownHostException) return "播放失敗：找不到電台主機";
        if (exception instanceof java.net.SocketTimeoutException) return "播放失敗：電台連線逾時";
        if (exception instanceof javax.net.ssl.SSLException) return "播放失敗：電台安全連線失敗";
        if (exception instanceof javax.sound.sampled.UnsupportedAudioFileException) return "播放失敗：音訊格式不支援";
        if (exception instanceof javax.sound.sampled.LineUnavailableException) return "播放失敗：音效裝置無法使用";
        if (exception instanceof IOException || exception instanceof IllegalArgumentException
                || exception instanceof IllegalStateException) {
            String message = exception.getMessage();
            if (message != null && !message.isBlank()) {
                // Avoid long/line-breaking server responses overflowing the radio GUI.
                String safe = message.replace('\n', ' ').replace('\r', ' ').trim();
                return "播放失敗：" + safe.substring(0, Math.min(64, safe.length()));
            }
        }
        return "播放失敗：音訊解碼或播放發生錯誤";
    }

    /**
     * Open and register each playback device in one place. A failed open/start
     * must close its native line even if it was never published to the session.
     * The stop worker can still close a published line during a station switch.
     */
    private static SourceDataLine openAudioLine(Session session, AudioFormat format) throws Exception {
        SourceDataLine output = (SourceDataLine) AudioSystem.getLine(
                new DataLine.Info(SourceDataLine.class, format));
        try {
            // Prefer a larger device buffer to absorb network/decoder jitter,
            // but some sound drivers reject large line allocations. Fall back
            // to smaller frame-aligned buffers instead of dropping every station.
            Exception lastOpenFailure = null;
            boolean opened = false;
            for (int requestedBytes : new int[] {131072, 65536, 32768}) {
                if (session.closed.get()) return null;
                try {
                    output.open(format, RadioPcm.alignedBufferSize(
                            requestedBytes, format.getFrameSize()));
                    opened = true;
                    break;
                } catch (javax.sound.sampled.LineUnavailableException
                        | IllegalArgumentException failure) {
                    lastOpenFailure = failure;
                    // A failed attempt must not leave a half-open native line.
                    if (output.isOpen()) output.close();
                }
            }
            if (!opened && !session.closed.get()) {
                // Some mixers only support their own default buffer size.
                // Explicit buffer sizes can all fail even when plain open works.
                try {
                    output.open(format);
                    opened = true;
                } catch (javax.sound.sampled.LineUnavailableException
                        | IllegalArgumentException failure) {
                    lastOpenFailure = failure;
                    if (output.isOpen()) output.close();
                }
            }
            if (!opened) {
                if (session.closed.get()) return null;
                throw new javax.sound.sampled.LineUnavailableException(
                        "音效裝置無法配置播放緩衝區：" + lastOpenFailure);
            }
            session.setLine(output);
            if (session.closed.get()) {
                output.close();
                return null;
            }
            // Keep the device paused until the decoder has filled half its
            // actual buffer. Starting an empty line makes brief network stalls
            // audible even when a large buffer was allocated.
            return output;
        } catch (Exception failure) {
            try { output.close(); } catch (Exception ignored) {}
            throw failure;
        }
    }

    /** Write whole PCM frames in bounded chunks so stop/switch can release the line promptly. */
    private static void writePcm(Session session, SourceDataLine output, byte[] data,
                                 int length, int frameSize) throws IOException {
        if (frameSize <= 0 || length < 0 || length > data.length || length % frameSize != 0) {
            throw new IOException("電台 PCM 音訊影格長度無效");
        }
        int chunkSize = Math.max(frameSize, (8192 / frameSize) * frameSize);
        for (int offset = 0; offset < length && !session.closed.get();) {
            int requested = Math.min(chunkSize, length - offset);
            // Never block forever trying to fill a stopped mixer whose
            // available capacity is smaller than its advertised buffer.
            // Start early if the next write cannot fit, then let the device drain.
            if (!output.isRunning() && !session.closed.get() && output.isOpen()
                    && output.available() < requested) {
                output.start();
            }
            int written = output.write(data, offset, requested);
            if (written <= 0 || written % frameSize != 0) {
                if (session.closed.get()) return;
                throw new IOException("電台音訊輸出中斷");
            }
            offset += written;
            // Start only after prebuffering. getBufferSize() reflects the
            // device's real allocation (which may differ from our request).
            // Do not restart a line that was closed by stop/switch.
            if (!output.isRunning() && !session.closed.get()
                    && output.isOpen()
                    && output.getBufferSize() - output.available() >= output.getBufferSize() / 2) {
                output.start();
            }
        }
    }

    private static void playOgg(Session session, InputStream input) throws Exception {
        // Java Sound SPI readers require mark/reset during format probing.
        // PushbackInputStream alone does not support mark(), so wrap it first.
        try (AudioInputStream encoded = AudioSystem.getAudioInputStream(new BufferedInputStream(input, 65536))) {
            AudioFormat source = encoded.getFormat();
            AudioFormat pcmFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                    source.getSampleRate(), 16, source.getChannels(),
                    source.getChannels() * 2, source.getSampleRate(), false);
            try (AudioInputStream pcm = AudioSystem.getAudioInputStream(pcmFormat, encoded)) {
                SourceDataLine output = openAudioLine(session, pcmFormat);
                if (output == null) return;
                // SourceDataLine requires writes aligned to whole PCM frames:
                // 4 bytes for stereo 16-bit, not merely an even byte count.
                int frameSize = pcmFormat.getFrameSize();
                if (frameSize <= 0) throw new IllegalArgumentException("OGG 音訊影格大小無效");
                byte[] buffer = new byte[8192 + frameSize];
                int pending = 0;
                int consecutiveEmptyReads = 0;
                while (!session.closed.get()) {
                    int read = pcm.read(buffer, pending, buffer.length - pending);
                    if (read == -1) break;
                    if (read == 0) {
                        // A decoder can briefly return zero while waiting for a live
                        // packet. Yielding 32 times can exhaust the limit in a few
                        // milliseconds; wait up to 2 seconds without busy-spinning.
                        if (++consecutiveEmptyReads >= 40) {
                            throw new IOException("OGG 音訊解碼長時間沒有資料");
                        }
                        try {
                            Thread.sleep(50L);
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            throw new IOException("OGG 音訊播放已中斷", interrupted);
                        }
                        continue;
                    }
                    consecutiveEmptyReads = 0;
                    int total = pending + read;
                    int aligned = total - total % frameSize;
                    int remainder = total - aligned;
                    RadioPcm.scale16(buffer, buffer, aligned, ClientRadioVolume.get(), false);
                    if (aligned > 0) {
                        writePcm(session, output, buffer, aligned, frameSize);
                        markPlaying(session);
                    }
                    // Preserve a partial frame without corrupting the next chunk.
                    if (remainder > 0) System.arraycopy(buffer, aligned, buffer, 0, remainder);
                    pending = remainder;
                }
            }
        }
    }

    /** ADTS and MP4 AAC share the same decoding, volume and audio-line lifecycle. */
    @FunctionalInterface
    private interface AacFrameReader {
        byte[] readNextFrame() throws Exception;
    }

    private static void playAac(Session session, InputStream input) throws Exception {
        ADTSDemultiplexer adts = new ADTSDemultiplexer(input);
        playAacFrames(session, adts.getDecoderSpecificInfo(), adts::readNextFrame);
    }

    /** MP4/M4A AAC is decoded by the existing bundled JAAD library, without OpenFM. */
    private static void playMp4Aac(Session session, InputStream input) throws Exception {
        MP4Container container = new MP4Container(input);
        if (session.closed.get()) return;
        List<Track> tracks = container.getMovie().getTracks(AudioTrack.AudioCodec.AAC);
        if (tracks.isEmpty()) throw new IOException("M4A／MP4 檔案沒有支援的 AAC 音軌");
        Track track = tracks.get(0);
        playAacFrames(session, track.getDecoderSpecificInfo(), () -> {
            if (!track.hasMoreFrames()) return null;
            net.sourceforge.jaad.mp4.api.Frame frame = track.readNextFrame();
            return frame == null ? null : frame.getData();
        });
    }

    private static void playAacFrames(Session session, byte[] decoderSpecificInfo,
                                      AacFrameReader frames) throws Exception {
        net.sourceforge.jaad.aac.Decoder decoder = new net.sourceforge.jaad.aac.Decoder(decoderSpecificInfo);
        net.sourceforge.jaad.aac.SampleBuffer pcm = new net.sourceforge.jaad.aac.SampleBuffer();
        // Never modify JAAD's decoder-owned PCM array: its storage can be reused
        // on the next frame. Keep a separate, reusable output buffer for volume.
        byte[] scaledPcm = new byte[8192];
        int consecutiveDecodeFailures = 0;
        int consecutiveReadFailures = 0;
        int emptyDecodedFrames = 0;
        while (!session.closed.get()) {
            byte[] frame;
            try {
                // JAAD can throw EOFException at the end of an ADTS or MP4 stream.
                frame = frames.readNextFrame();
                consecutiveReadFailures = 0;
            } catch (java.io.EOFException endOfStream) {
                break;
            } catch (IOException damagedTransport) {
                // ADTS demultiplexers may recover after a damaged frame header.
                // Bound the retries so a broken live stream cannot spin forever.
                if (session.closed.get()) return;
                if (++consecutiveReadFailures >= 5) {
                    throw new IOException("AAC 音訊連續讀取失敗", damagedTransport);
                }
                continue;
            }
            if (frame == null || session.closed.get()) break;
            try {
                decoder.decodeFrame(frame, pcm);
                consecutiveDecodeFailures = 0;
            } catch (net.sourceforge.jaad.aac.AACException badFrame) {
                // Isolated AAC transport damage is common on live streams.
                // Reconnect if decoding remains broken for five frames.
                if (++consecutiveDecodeFailures >= 5) {
                    throw new IOException("AAC 音訊連續解碼失敗", badFrame);
                }
                continue;
            }
            if (session.closed.get()) break;
            byte[] decoded = pcm.getData();
            // SBR may need a few frames to initialize, but a stream that never
            // produces PCM must not keep decoding forever.
            if (decoded == null || decoded.length == 0) {
                if (++emptyDecodedFrames >= 128) {
                    throw new IOException("AAC 音訊持續沒有可播放的資料");
                }
                continue;
            }
            emptyDecodedFrames = 0;
            if (pcm.getBitsPerSample() != 16 || pcm.getSampleRate() <= 0 || pcm.getChannels() <= 0) {
                throw new IllegalArgumentException("AAC 音訊格式不支援");
            }
            int frameSize = pcm.getChannels() * 2;
            if (decoded.length % frameSize != 0) {
                throw new IllegalArgumentException("AAC 音訊資料未對齊完整影格");
            }
            if (scaledPcm.length < decoded.length) scaledPcm = new byte[decoded.length];
            AudioFormat format = new AudioFormat(pcm.getSampleRate(), 16, pcm.getChannels(), true, pcm.isBigEndian());
            SourceDataLine output = session.getLine();
            // HE-AAC can switch output rate after SBR initializes. Reopen the output
            // device rather than sending differently formatted PCM into an old line.
            if (output == null || !output.getFormat().matches(format)) {
                if (output != null) {
                    session.setLine(null);
                    output.stop();
                    output.flush();
                    output.close();
                }
                output = openAudioLine(session, format);
                if (output == null) return;
            }
            // JAAD reuses decoder buffers; scale into a separate output array.
            RadioPcm.scale16(decoded, scaledPcm, decoded.length,
                    ClientRadioVolume.get(), pcm.isBigEndian());
            writePcm(session, output, scaledPcm, decoded.length, frameSize);
            markPlaying(session);
        }
    }

    /**
     * Strip the optional Icecast/Shoutcast metadata blocks that appear after
     * every icy-metaint audio bytes. The stream exposed to codecs contains only
     * audio; skip() must use read() to preserve the metadata boundary.
     */
    private static final class IcyMetadataInputStream extends FilterInputStream {
        private final int interval;
        private int remaining;
        private boolean ended;
        private final byte[] discard = new byte[4096];

        IcyMetadataInputStream(InputStream source, int interval) {
            super(source);
            if (interval <= 0 || interval > 16 * 1024 * 1024)
                throw new IllegalArgumentException("ICY interval must be within 1..16777216");
            this.interval = interval;
            this.remaining = interval;
        }

        private void advance() throws IOException {
            int blocks = in.read();
            if (blocks < 0) {
                ended = true;
                return;
            }
            int metadataBytes = blocks * 16;
            while (metadataBytes > 0) {
                int count = in.read(discard, 0, Math.min(metadataBytes, discard.length));
                if (count < 0) throw new EOFException("電台 ICY 中繼資料遭到截斷");
                if (count == 0) {
                    // Some HTTP stream wrappers return zero on a bulk read.
                    // Fall back to one byte rather than spinning indefinitely.
                    if (in.read() < 0) throw new EOFException("電台 ICY 中繼資料遭到截斷");
                    metadataBytes--;
                    continue;
                }
                metadataBytes -= count;
            }
            remaining = interval;
        }

        @Override
        public int read() throws IOException {
            if (ended) return -1;
            if (remaining == 0) advance();
            if (ended) return -1;
            int value = in.read();
            if (value < 0) ended = true;
            else remaining--;
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, bytes.length);
            if (length == 0) return 0;
            if (ended) return -1;
            if (remaining == 0) advance();
            if (ended) return -1;
            int count = in.read(bytes, offset, Math.min(length, remaining));
            if (count == 0) {
                int single = in.read();
                if (single < 0) {
                    ended = true;
                    return -1;
                }
                bytes[offset] = (byte) single;
                remaining--;
                return 1;
            }
            if (count < 0) ended = true;
            else remaining -= count;
            return count;
        }

        @Override
        public long skip(long length) throws IOException {
            if (length <= 0) return 0;
            long skipped = 0;
            while (skipped < length) {
                int count = read(discard, 0, (int) Math.min(length - skipped, discard.length));
                if (count < 0) break;
                if (count == 0) continue;
                skipped += count;
            }
            return skipped;
        }

        @Override
        public int available() throws IOException {
            return ended ? 0 : Math.min(remaining, in.available());
        }

        @Override
        public boolean markSupported() { return false; }

        @Override
        public void reset() throws IOException {
            throw new IOException("ICY 音訊串流不支援重設讀取位置");
        }
    }

    private static final class Session {
        final String url;
        final String avoidStreamUrl;
        volatile String resolvedStreamUrl;
        final AtomicBoolean closed = new AtomicBoolean();
        private final Object resources = new Object();
        private HttpURLConnection connection;
        private InputStream input;
        private SourceDataLine line;
        private final AtomicBoolean released = new AtomicBoolean();

        Session(String url, String avoidStreamUrl) {
            this.url = url;
            this.avoidStreamUrl = avoidStreamUrl;
        }

        // A stop can happen between openConnection()/AudioSystem.getLine()
        // and publishing the resource. Reject late resources immediately so
        // they cannot escape the one-shot cleanup thread.
        void setConnection(HttpURLConnection next) {
            boolean reject;
            synchronized (resources) {
                reject = closed.get();
                if (!reject) connection = next;
            }
            if (reject && next != null) next.disconnect();
        }

        void setInput(InputStream next) {
            boolean reject;
            synchronized (resources) {
                reject = closed.get();
                if (!reject) input = next;
            }
            if (reject && next != null) {
                try { next.close(); } catch (Exception ignored) {}
            }
        }

        void setLine(SourceDataLine next) {
            boolean reject;
            synchronized (resources) {
                reject = closed.get();
                if (!reject) line = next;
            }
            if (reject && next != null) {
                try { next.close(); } catch (Exception ignored) {}
            }
        }

        SourceDataLine getLine() {
            synchronized (resources) { return line; }
        }

        void cancel() {
            if (!closed.compareAndSet(false, true)) return;
            Thread worker = new Thread(this::release, "PersonalShelter-Radio-Stop");
            worker.setDaemon(true);
            worker.start();
        }

        void close() {
            closed.set(true);
            release();
        }

        private void release() {
            if (!released.compareAndSet(false, true)) return;
            SourceDataLine audio;
            HttpURLConnection network;
            InputStream stream;
            synchronized (resources) {
                audio = line;
                network = connection;
                stream = input;
                line = null;
                connection = null;
                input = null;
            }
            // Disconnect first: a blocked network read should not keep the old
            // station alive while an audio driver is stopping its PCM line.
            // All cleanup runs off the client/render thread.
            if (network != null) {
                try { network.disconnect(); } catch (Exception ignored) {}
            }
            if (audio != null) {
                try { audio.stop(); } catch (Exception ignored) {}
                try { audio.flush(); } catch (Exception ignored) {}
                try { audio.close(); } catch (Exception ignored) {}
            }
            try { if (stream != null) stream.close(); } catch (Exception ignored) {}
        }
    }
}
