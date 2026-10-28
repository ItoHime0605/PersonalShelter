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

/** Independent MP3 radio engine; network and decoding never run on the game thread. */
public final class StandaloneRadioPlayer {
    private static final Object LOCK = new Object();
    private static volatile Session active;
    private static volatile String currentUrl = "";
    private static volatile long retryAfter;
    private static volatile String failedUrl = "";
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
        if (old != null) old.close();
    }

    private static void play(Session session) {
        try {
            String url = RadioPlaylistResolver.resolve(session.url);
            if (session.closed.get()) return;
            URI uri = URI.create(url);
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            session.connection = conn;
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(8000);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("Icy-MetaData", "0");
            conn.setRequestProperty("User-Agent", "PersonalShelter/Radio");
            if (conn.getResponseCode() != 200) throw new IllegalStateException("Radio HTTP " + conn.getResponseCode());
            try (InputStream input = new BufferedInputStream(conn.getInputStream())) {
                session.input = input;
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
                        line.start();
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
            // Do not flood the client log with a failed stream every tick.
        } finally {
            session.close();
            synchronized (LOCK) {
                if (active == session) {
                    active = null;
                    currentUrl = "";
                    failedUrl = session.url;
                    retryAfter = System.currentTimeMillis() + 10_000L;
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
        void close() {
            if (!closed.compareAndSet(false, true)) return;
            try { if (input != null) input.close(); } catch (Exception ignored) {}
            if (connection != null) connection.disconnect();
            SourceDataLine audio = line;
            if (audio != null) {
                audio.stop();
                audio.flush();
                audio.close();
            }
        }
    }
}
