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
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Locale;

/** Independent MP3 radio engine; network and decoding never run on the game thread. */
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
            String url = RadioPlaylistResolver.resolve(session.url);
            if (session.closed.get()) return;
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))
                    || uri.getHost() == null || uri.getUserInfo() != null) {
                throw new IllegalArgumentException("電台網址僅支援 HTTP 或 HTTPS，且不得包含帳號密碼");
            }
            String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
            if (path.endsWith(".opus") || path.endsWith(".aac")
                    || path.endsWith(".m4a") || path.endsWith(".mp4")) {
                throw new IllegalArgumentException("目前不支援此音訊格式");
            }
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            session.connection = conn;
            if (session.closed.get()) {
                conn.disconnect();
                return;
            }
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(8000);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("Icy-MetaData", "0");
            conn.setRequestProperty("User-Agent", "PersonalShelter/Radio");
            int responseCode = conn.getResponseCode();
            if (session.closed.get()) return;
            if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_PARTIAL) {
                throw new IllegalStateException("電台伺服器回應 HTTP " + responseCode);
            }
            String contentType = conn.getContentType();
            if (contentType != null) {
                String type = contentType.toLowerCase(Locale.ROOT).split(";", 2)[0].trim();
                if (type.contains("aac") || type.contains("mp4")
                        || type.contains("opus")) {
                    throw new IllegalArgumentException("Unsupported radio stream format: " + type);
                }
            }
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
                if (ogg || oggSignature) {
                    session.input = input;
                    if (!session.closed.get()) playOgg(session, input);
                    return;
                }
                session.input = input;
                if (session.closed.get()) return;
                Bitstream stream = new Bitstream(input);
                Decoder decoder = new Decoder();
                while (!session.closed.get()) {
                    Header header = stream.readFrame();
                    if (header == null) break;
                    SampleBuffer pcm = (SampleBuffer) decoder.decodeFrame(header, stream);
                    if (session.closed.get()) break;
                    if (session.line == null) {
                        AudioFormat format = new AudioFormat(pcm.getSampleFrequency(), 16, pcm.getChannelCount(), true, false);
                        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
                        SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
                        line.open(format, 32768);
                        session.line = line;
                        if (session.closed.get()) {
                            line.close();
                            return;
                        }
                        line.start();
                        if (!session.closed.get() && active == session) {
                            status = "播放中";
                            ClientRadioPlaybackStatus.set(status);
                        }
                    }
                    short[] samples = pcm.getBuffer();
                    int length = pcm.getBufferLength();
                    byte[] bytes = new byte[length * 2];
                    double volume = ClientRadioVolume.get() / 100.0;
                    for (int i = 0; i < length; i++) {
                        short value = clampPcm(samples[i] * volume);
                        bytes[i * 2] = (byte) value;
                        bytes[i * 2 + 1] = (byte) (value >>> 8);
                    }
                    if (!session.closed.get()) session.line.write(bytes, 0, bytes.length);
                    stream.closeFrame();
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
                session.line = output;
                if (session.closed.get()) {
                    output.close();
                    return;
                }
                output.start();
                if (active == session) {
                    status = "播放中";
                    ClientRadioPlaybackStatus.set(status);
                }
                byte[] buffer = new byte[8193];
                int length;
                int pendingByte = -1;
                while (!session.closed.get() && (length = pcm.read(buffer, pendingByte >= 0 ? 1 : 0,
                        pendingByte >= 0 ? buffer.length - 1 : buffer.length)) != -1) {
                    if (pendingByte >= 0) {
                        buffer[0] = (byte) pendingByte;
                        length++;
                        pendingByte = -1;
                    }
                    if ((length & 1) != 0) {
                        pendingByte = buffer[--length] & 0xff;
                    }
                    if (length == 0) continue;
                    double volume = ClientRadioVolume.get() / 100.0;
                    for (int i = 0; i + 1 < length; i += 2) {
                        short sample = (short) ((buffer[i] & 255) | (buffer[i + 1] << 8));
                        short scaled = clampPcm(sample * volume);
                        buffer[i] = (byte) scaled;
                        buffer[i + 1] = (byte) (scaled >>> 8);
                    }
                    if (!session.closed.get()) output.write(buffer, 0, length);
                }
            }
        }
    }

    private static final class Session {
        final String url;
        final AtomicBoolean closed = new AtomicBoolean();
        volatile HttpURLConnection connection;
        volatile InputStream input;
        volatile SourceDataLine line;
        private final AtomicBoolean released = new AtomicBoolean();
        Session(String url) { this.url = url; }
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
            try { if (input != null) input.close(); } catch (Exception ignored) {}
            if (connection != null) connection.disconnect();
            SourceDataLine audio = line;
            if (audio != null) {
                try { audio.stop(); } catch (Exception ignored) {}
                try { audio.flush(); } catch (Exception ignored) {}
                try { audio.close(); } catch (Exception ignored) {}
            }
        }
    }
}
