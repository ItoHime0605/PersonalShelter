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
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Locale;

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
                    throw new IllegalArgumentException("Unsupported radio stream format: " + type);
                }
            }
            boolean aac = path.endsWith(".aac") || (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("aac"));
            boolean ogg = path.endsWith(".ogg") || (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("ogg"));
            try (PushbackInputStream input = new PushbackInputStream(new BufferedInputStream(conn.getInputStream()), 4)) {
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
                if (aac || adtsSignature) {
                    session.setInput(input);
                    if (!session.closed.get()) playAac(session, input);
                    return;
                }
                if (ogg || oggSignature) {
                    session.setInput(input);
                    if (!session.closed.get()) playOgg(session, input);
                    return;
                }
                session.setInput(input);
                if (session.closed.get()) return;
                Bitstream stream = new Bitstream(input);
                Decoder decoder = new Decoder();
                // Reuse a PCM buffer instead of allocating on every MP3 frame.
                byte[] bytes = new byte[16384];
                while (!session.closed.get()) {
                    Header header = stream.readFrame();
                    if (header == null) break;
                    try {
                        SampleBuffer pcm = (SampleBuffer) decoder.decodeFrame(header, stream);
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
                        if (!session.closed.get()) output.write(bytes, 0, byteLength);
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

    private static short clampPcm(double sample) {
        return (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(sample)));
    }

    private static void playOgg(Session session, InputStream input) throws Exception {
        try (AudioInputStream encoded = AudioSystem.getAudioInputStream(input)) {
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
                    if (aligned > 0 && !session.closed.get()) output.write(buffer, 0, aligned);
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
            decoder.decodeFrame(frame, pcm);
            if (session.closed.get()) break;
            byte[] bytes = pcm.getData();
            // Some AAC frames do not produce samples (e.g. while initializing SBR).
            if (bytes.length == 0) continue;
            if (pcm.getBitsPerSample() != 16 || pcm.getSampleRate() <= 0 || pcm.getChannels() <= 0) {
                throw new IllegalArgumentException("AAC 音訊格式不支援");
            }
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
            for (int i = 0; i + 1 < bytes.length; i += 2) {
                short value = bigEndian
                        ? (short) ((bytes[i] << 8) | (bytes[i + 1] & 255))
                        : (short) ((bytes[i] & 255) | (bytes[i + 1] << 8));
                short scaled = clampPcm(value * volume);
                bytes[i] = bigEndian ? (byte) (scaled >>> 8) : (byte) scaled;
                bytes[i + 1] = bigEndian ? (byte) scaled : (byte) (scaled >>> 8);
            }
            if (!session.closed.get()) output.write(bytes, 0, bytes.length);
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
