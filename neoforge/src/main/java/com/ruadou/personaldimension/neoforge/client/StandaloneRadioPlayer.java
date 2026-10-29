package com.ruadou.personaldimension.neoforge.client;

import com.ruadou.personaldimension.client.ClientCurrentLandRadio;
import com.ruadou.personaldimension.client.ClientRadioListening;
import com.ruadou.personaldimension.client.ClientRadioVolume;
import net.minecraft.client.Minecraft;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.AudioFormat;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.SampleBuffer;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Header;
import java.io.BufferedInputStream;
import java.io.InputStream;
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
    private static volatile String status = "已停止";
    public static String status() { return status; }
    private StandaloneRadioPlayer() {}

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        boolean allowed = mc.player != null && mc.level != null && ClientRadioListening.shouldPlay()
                && mc.level.dimension().location().toString().equals(ClientCurrentLandRadio.dimensionId());
        String url = allowed ? ClientCurrentLandRadio.streamUrl() : "";
        synchronized (LOCK) {
            if (url.isBlank()) { stopLocked(); return; }
            if (active != null && url.equals(currentUrl)) return;
            if (!url.equals(failedUrl)) retryAfter = 0;
            if (url.equals(failedUrl) && System.currentTimeMillis() < retryAfter) return;
            stopLocked();
            Session session = new Session(url);
            active = session;
            currentUrl = url;
            status = "正在連線";
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
        if (old != null) old.cancel();
        status = "已停止";
    }

    private static void play(Session session) {
        try {
            String url = RadioPlaylistResolver.resolve(session.url);
            if (session.closed.get()) return;
            URI uri = URI.create(url);
            String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
            if (path.endsWith(".ogg") || path.endsWith(".opus") || path.endsWith(".aac")
                    || path.endsWith(".m4a") || path.endsWith(".mp4")) {
                throw new IllegalArgumentException("This radio engine currently supports MP3 streams only");
            }
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            session.connection = conn;
            if (session.closed.get()) return;
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(8000);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("Icy-MetaData", "0");
            conn.setRequestProperty("User-Agent", "PersonalShelter/Radio");
            if (conn.getResponseCode() != 200) throw new IllegalStateException("Radio HTTP " + conn.getResponseCode());
            String contentType = conn.getContentType();
            if (contentType != null) {
                String type = contentType.toLowerCase(Locale.ROOT).split(";", 2)[0].trim();
                if (type.contains("ogg") || type.contains("aac") || type.contains("mp4")
                        || type.contains("opus")) {
                    throw new IllegalArgumentException("Unsupported radio stream format: " + type);
                }
            }
            try (InputStream input = new BufferedInputStream(conn.getInputStream())) {
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
                        if (session.closed.get()) return;
                        line.start();
                        if (!session.closed.get() && active == session) status = "播放中";
                    }
                    short[] samples = pcm.getBuffer();
                    int length = pcm.getBufferLength();
                    byte[] bytes = new byte[length * 2];
                    double volume = ClientRadioVolume.get() / 100.0;
                    for (int i = 0; i < length; i++) {
                        short value = (short) Math.round(samples[i] * volume);
                        bytes[i * 2] = (byte) value;
                        bytes[i * 2 + 1] = (byte) (value >>> 8);
                    }
                    if (!session.closed.get()) session.line.write(bytes, 0, bytes.length);
                    stream.closeFrame();
                }
            }
        } catch (Exception ex) {
            if (!session.closed.get() && active == session) status = "播放失敗：" + ex.getClass().getSimpleName();
        } finally {
            session.close();
            synchronized (LOCK) {
                if (active == session) {
                    active = null;
                    currentUrl = "";
                    failedUrl = session.url;
                    retryAfter = System.currentTimeMillis() + 10_000L;
                    if (!session.closed.get() && !status.startsWith("播放失敗")) status = "連線中斷，稍後重試";
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
