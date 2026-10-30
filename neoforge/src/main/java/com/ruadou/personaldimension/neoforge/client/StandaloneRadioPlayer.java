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
    private static volatile String lastFailure = "";
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
            if (url.toLowerCase(Locale.ROOT).matches(".*\\\\.(m4a|mp4|m3u8)(\\\\?.*)?$")) {
                if (active != null) stopLocked();
                currentUrl = url;
                status = "不支援此串流格式（M4A／MP4／HLS）";
                ClientRadioPlaybackStatus.set(status);
                return;
            }
            if (!url.equals(failedUrl)) {
                retryAfter = 0;
                failedUrl = "";
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
            Session session = new Session(url);
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
                    connection -> session.setConnection(connection));
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
            if (path.endsWith(".opus") || path.endsWith(".m4a") || path.endsWith(".mp4")) {
                throw new IllegalArgumentException("目前不支援此音訊格式");
            }
            String contentType = conn.getContentType();
            if (contentType != null) {
                String type = contentType.toLowerCase(Locale.ROOT).split(";", 2)[0].trim();
                if (type.contains("mp4") || type.contains("opus")) {
                    throw new IllegalArgumentException("不支援的電台音訊格式：" + type);
                }
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
            try (PushbackInputStream input = new PushbackInputStream(new BufferedInputStream(networkAudio, 32768), 4)) {
                // Radio servers often return application/octet-stream or omit file extensions.
                // Detect the Ogg container signature instead of relying on HTTP metadata alone.
                byte[] signature = input.readNBytes(4);
                int signatureLength = signature.length;
                if (signatureLength > 0) input.unread(signature, 0, signatureLength);
                boolean oggSignature = signatureLength == 4
                        && signature[0] == 'O' && signature[1] == 'g'
                        && signature[2] == 'g' && signature[3] == 'S';
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
                session.setInput(input);
                if (session.closed.get()) return;
                Bitstream stream = new Bitstream(input);
                Decoder decoder = new Decoder();
                // Reuse a PCM buffer instead of allocating on every MP3 frame.
                byte[] bytes = new byte[16384];
                int consecutiveDecodeFailures = 0;
                while (!session.closed.get()) {
                    Header header = stream.readFrame();
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
            if (!session.closed.get() && active == session) {
                lastFailure = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                String type = ex.getClass().getSimpleName();
                status = switch (type) {
                    case "UnknownHostException" -> "播放失敗：找不到電台主機";
                    case "SocketTimeoutException" -> "播放失敗：連線逾時";
                    case "UnsupportedAudioFileException" -> "播放失敗：音訊格式不支援";
                    case "LineUnavailableException" -> "播放失敗：音效裝置無法使用";
                    default -> "播放失敗：" + type;
                };
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
                    retryAfter = System.currentTimeMillis() + RETRY_DELAY_MS;
                    if (!interrupted && !status.startsWith("播放失敗")) {
                        status = "連線中斷，稍後重試";
                        ClientRadioPlaybackStatus.set(status);
                    }
                }
            }
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
                while (!session.closed.get()) {
                    int read = pcm.read(buffer, pending, buffer.length - pending);
                    if (read == -1) break;
                    if (read == 0) continue;
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

    /** Decode ADTS AAC on the radio worker, without blocking Minecraft's client tick. */
    private static void playAac(Session session, InputStream input) throws Exception {
        ADTSDemultiplexer adts = new ADTSDemultiplexer(input);
        net.sourceforge.jaad.aac.Decoder decoder = new net.sourceforge.jaad.aac.Decoder(adts.getDecoderSpecificInfo());
        net.sourceforge.jaad.aac.SampleBuffer pcm = new net.sourceforge.jaad.aac.SampleBuffer();
        // Never modify JAAD's decoder-owned PCM array: its storage can be reused
        // on the next frame. Keep a separate, reusable output buffer for volume.
        byte[] scaledPcm = new byte[8192];
        int consecutiveDecodeFailures = 0;
        while (!session.closed.get()) {
            byte[] frame;
            try {
                // JAAD 0.8.7 throws EOFException rather than returning null at the end
                // of an ADTS stream; treat it as a disconnect and let retry logic reconnect.
                frame = adts.readNextFrame();
            } catch (java.io.EOFException endOfStream) {
                break;
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
        final AtomicBoolean closed = new AtomicBoolean();
        private final Object resources = new Object();
        private HttpURLConnection connection;
        private InputStream input;
        private SourceDataLine line;
        private final AtomicBoolean released = new AtomicBoolean();

        Session(String url) { this.url = url; }

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
