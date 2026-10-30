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
    private static final long RETRY_DELAY_MS = 10_000L;
    public static String status() { return status; }
    /** True only while the active session has started audio output. */
    public static boolean isPlaying() { return active != null && "播放中".equals(status); }
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
                return;
            }
            if (active != null && url.equals(currentUrl)) return;
            // A .m3u8 URL can be a plain UTF-8 M3U radio playlist, not HLS.
            // Let RadioPlaylistResolver inspect the manifest and reject actual HLS.
            if (url.equals(unsupportedUrl)) return;
            if (!url.equals(failedUrl)) {
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
        synchronized (LOCK) { stopLocked(); }
    }

    private static void stopLocked() {
        Session old = active;
        unsupportedUrl = "";
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
            if (contentType != null) {
                String type = contentType.toLowerCase(Locale.ROOT).split(";", 2)[0].trim();
                if (type.contains("opus") || type.contains("mpegurl") || type.contains("scpls")) {
                    throw new IllegalArgumentException("不支援的電台音訊格式：" + type);
                }
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
                if (interval <= 0) throw new IOException("電台 ICY 中繼資料間隔無效");
                networkAudio = new IcyMetadataInputStream(networkAudio, interval);
            }
            try (PushbackInputStream input = new PushbackInputStream(new BufferedInputStream(networkAudio, 32768), 12)) {
                // Radio servers often return application/octet-stream or omit file extensions.
                // Detect the Ogg container signature instead of relying on HTTP metadata alone.
                byte[] signature = input.readNBytes(12);
                int signatureLength = signature.length;
                if (signatureLength > 0) input.unread(signature, 0, signatureLength);
                String prefix = new String(signature, 0, signatureLength,
                        java.nio.charset.StandardCharsets.US_ASCII).trim().toLowerCase(Locale.ROOT);
                if (prefix.startsWith("<!doctype") || prefix.startsWith("<html")
                        || prefix.startsWith("<?xml") || prefix.startsWith("{\"error\"")) {
                    throw new IOException("電台網址回傳網頁或錯誤訊息，並非音訊串流");
                }
                boolean oggSignature = signatureLength >= 4
                        && signature[0] == 'O' && signature[1] == 'g'
                        && signature[2] == 'g' && signature[3] == 'S';
                // MP4/M4A has an ISO BMFF box header (size followed by "ftyp").
                // The extension alone is unreliable: some .m4a radios serve ADTS.
                boolean mp4Signature = signatureLength >= 8
                        && signature[4] == 'f' && signature[5] == 't'
                        && signature[6] == 'y' && signature[7] == 'p';
                if (signatureLength >= 7 && signature[0] == '#' && signature[1] == 'E'
                        && signature[2] == 'X' && signature[3] == 'T'
                        && signature[4] == 'M' && signature[5] == '3'
                        && signature[6] == 'U') {
                    throw new IOException("此電台使用播放清單，請提供直接串流網址");
                }
                boolean adtsSignature = signatureLength >= 2 && (signature[0] & 255) == 0xff
                        && (signature[1] & 0xf6) == 0xf0;
                // Prefer the actual container header over extension / Content-Type.
                // Some stations advertise audio/aacp or audio/mpeg incorrectly, and
                // misrouting an Ogg or MP3 stream into the AAC decoder breaks playback.
                boolean mp3Signature = signatureLength >= 3
                        && signature[0] == 'I' && signature[1] == 'D' && signature[2] == '3';
                if (!mp3Signature && signatureLength >= 2) {
                    int second = signature[1] & 255;
                    mp3Signature = (signature[0] & 255) == 255
                            && (second & 0xe0) == 0xe0 && (second & 0x06) != 0;
                }
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
                            output = (SourceDataLine) AudioSystem.getLine(new DataLine.Info(SourceDataLine.class, format));
                            output.open(format, 32768);
                            session.setLine(output);
                            if (session.closed.get()) {
                                output.close();
                                return;
                            }
                            output.start();
                            if (session.closed.get()) {
                                output.close();
                                return;
                            }
                            if (active == session) {
                                status = "播放中";
                                ClientRadioPlaybackStatus.set(status);
                            }
                        }
                        short[] samples = pcm.getBuffer();
                        int length = pcm.getBufferLength();
                        int byteLength = length * 2;
                        if (bytes.length < byteLength) bytes = new byte[byteLength];
                        double volume = ClientRadioVolume.get() / 100.0;
                        for (int i = 0; i < length; i++) {
                            short value = clampPcm(samples[i] * volume);
                            bytes[i * 2] = (byte) value;
                            bytes[i * 2 + 1] = (byte) (value >>> 8);
                        }
                        writePcm(session, output, bytes, byteLength, format.getFrameSize());
                    } finally {
                        stream.closeFrame();
                    }
                }
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
            if (!session.closed.get() && active == session) {
                lastFailure = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                status = describeFailure(ex);
                ClientRadioPlaybackStatus.set(status);
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

    /** Write whole PCM frames in bounded chunks so stop/switch can release the line promptly. */
    private static void writePcm(Session session, SourceDataLine output, byte[] data,
                                 int length, int frameSize) throws IOException {
        if (frameSize <= 0 || length < 0 || length > data.length || length % frameSize != 0) {
            throw new IOException("電台 PCM 音訊影格長度無效");
        }
        int chunkSize = Math.max(frameSize, (8192 / frameSize) * frameSize);
        for (int offset = 0; offset < length && !session.closed.get();) {
            int requested = Math.min(chunkSize, length - offset);
            int written = output.write(data, offset, requested);
            if (written <= 0 || written % frameSize != 0) {
                if (session.closed.get()) return;
                throw new IOException("電台音訊輸出中斷");
            }
            offset += written;
        }
    }

    private static short clampPcm(double sample) {
        return (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(sample)));
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
                SourceDataLine output = (SourceDataLine) AudioSystem.getLine(new DataLine.Info(SourceDataLine.class, pcmFormat));
                output.open(pcmFormat, 32768);
                session.setLine(output);
                if (session.closed.get()) {
                    output.close();
                    return;
                }
                output.start();
                if (active == session) {
                    status = "播放中";
                    ClientRadioPlaybackStatus.set(status);
                }
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
                        // A broken Java Sound SPI may repeatedly return zero without
                        // advancing the stream. Do not spin forever at 100% CPU.
                        if (++consecutiveEmptyReads >= 32) {
                            throw new IOException("OGG 音訊解碼長時間沒有資料");
                        }
                        Thread.yield();
                        continue;
                    }
                    consecutiveEmptyReads = 0;
                    int total = pending + read;
                    int aligned = total - total % frameSize;
                    int remainder = total - aligned;
                    double volume = ClientRadioVolume.get() / 100.0;
                    for (int i = 0; i + 1 < aligned; i += 2) {
                        short sample = (short) ((buffer[i] & 255) | (buffer[i + 1] << 8));
                        short scaled = clampPcm(sample * volume);
                        buffer[i] = (byte) scaled;
                        buffer[i + 1] = (byte) (scaled >>> 8);
                    }
                    if (aligned > 0) writePcm(session, output, buffer, aligned, frameSize);
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
            // Some AAC frames do not produce samples (e.g. while initializing SBR).
            if (decoded == null || decoded.length == 0) continue;
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
                output = (SourceDataLine) AudioSystem.getLine(new DataLine.Info(SourceDataLine.class, format));
                output.open(format, 32768);
                session.setLine(output);
                if (session.closed.get()) {
                    output.close();
                    return;
                }
                output.start();
                if (session.closed.get()) {
                    output.close();
                    return;
                }
                if (active == session) {
                    status = "播放中";
                    ClientRadioPlaybackStatus.set(status);
                }
            }
            double volume = ClientRadioVolume.get() / 100.0;
            boolean bigEndian = pcm.isBigEndian();
            for (int i = 0; i < decoded.length; i += 2) {
                short value = bigEndian
                        ? (short) ((decoded[i] << 8) | (decoded[i + 1] & 255))
                        : (short) ((decoded[i] & 255) | (decoded[i + 1] << 8));
                short scaled = clampPcm(value * volume);
                scaledPcm[i] = bigEndian ? (byte) (scaled >>> 8) : (byte) scaled;
                scaledPcm[i + 1] = bigEndian ? (byte) scaled : (byte) (scaled >>> 8);
            }
            writePcm(session, output, scaledPcm, decoded.length, frameSize);
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
            if (interval <= 0) throw new IllegalArgumentException("ICY interval must be positive");
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
                if (count == 0) continue;
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
            // Do potentially blocking cleanup off the client thread.
            if (audio != null) {
                try { audio.stop(); } catch (Exception ignored) {}
                try { audio.flush(); } catch (Exception ignored) {}
                try { audio.close(); } catch (Exception ignored) {}
            }
            if (network != null) network.disconnect();
            try { if (stream != null) stream.close(); } catch (Exception ignored) {}
        }
    }
}
