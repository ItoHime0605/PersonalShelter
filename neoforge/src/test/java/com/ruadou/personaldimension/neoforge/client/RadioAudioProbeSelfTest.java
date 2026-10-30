package com.ruadou.personaldimension.neoforge.client;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;

/** Java 21 standalone regression checks: no Minecraft or external test runner needed. */
public final class RadioAudioProbeSelfTest {
    private static int checks;
    private RadioAudioProbeSelfTest() {}

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static void kind(byte[] bytes, RadioAudioProbe.Kind expected) {
        check(RadioAudioProbe.classify(bytes) == expected, "音訊格式辨識錯誤：" + expected);
    }

    private static byte[] text(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] withId3(byte[] payload) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        // ID3v2.4 tag with four bytes of body (syncsafe length = 4).
        output.write(new byte[] {'I', 'D', '3', 4, 0, 0, 0, 0, 0, 4});
        output.write(new byte[] {1, 2, 3, 4});
        output.write(payload);
        return output.toByteArray();
    }

    public static void main(String[] args) throws Exception {
        kind(new byte[] {(byte) 0xff, (byte) 0xfb, (byte) 0x90, 0x64},
                RadioAudioProbe.Kind.MP3);
        kind(new byte[] {(byte) 0xff, (byte) 0xf1, 0x50, (byte) 0x80},
                RadioAudioProbe.Kind.ADTS_AAC);
        kind(text("OggS"), RadioAudioProbe.Kind.OGG);
        kind(new byte[] {0, 0, 0, 24, 'f', 't', 'y', 'p', 'M', '4', 'A', ' '},
                RadioAudioProbe.Kind.MP4_AAC);
        kind(text("\uFEFF#EXTM3U\n"), RadioAudioProbe.Kind.PLAYLIST);
        kind(text("[playlist]\nFile1="), RadioAudioProbe.Kind.PLAYLIST);
        kind(text("#EXTINF:-1,Radio"), RadioAudioProbe.Kind.PLAYLIST);
        kind(text("<!doctype html>"), RadioAudioProbe.Kind.NON_AUDIO);
        kind(text("{"), RadioAudioProbe.Kind.NON_AUDIO);
        kind(new byte[] {(byte) 0xff, (byte) 0xfb, (byte) 0xfc, 0x00},
                RadioAudioProbe.Kind.UNKNOWN);
        // Corrupt ADTS frames must not be routed to the AAC decoder.
        kind(new byte[] {(byte) 0xff, (byte) 0xf1, 0x7c, (byte) 0x80, 1, 2, 0},
                RadioAudioProbe.Kind.UNKNOWN); // Reserved sample-rate index 15.
        kind(new byte[] {(byte) 0xff, (byte) 0xf1, 0x50, (byte) 0x80, 0, 0, 0},
                RadioAudioProbe.Kind.UNKNOWN); // Frame shorter than ADTS header.
        kind(new byte[] {(byte) 0xff, (byte) 0xeb, (byte) 0x90, 0x64},
                RadioAudioProbe.Kind.UNKNOWN); // Reserved MPEG version.
        kind(new byte[] {(byte) 0xff, (byte) 0xfb, 0x00, 0x64},
                RadioAudioProbe.Kind.UNKNOWN); // Unsupported free bitrate.
        byte[] aac = new byte[] {(byte) 0xff, (byte) 0xf1, 0x50, (byte) 0x80, 1, 2, 3, 4, 5, 6, 7, 8};
        try (PushbackInputStream input = new PushbackInputStream(
                new ByteArrayInputStream(withId3(aac)), 12)) {
            kind(RadioAudioProbe.peekAfterId3(input), RadioAudioProbe.Kind.ADTS_AAC);
            check(input.read() == 255, "略過 ID3 後不得消耗 AAC 標頭");
        }
        try (PushbackInputStream input = new PushbackInputStream(
                new ByteArrayInputStream(new byte[] {'I', 'D', '3', 4, 0}), 12)) {
            boolean failed = false;
            try { RadioAudioProbe.peekAfterId3(input); }
            catch (IOException expected) { failed = true; }
            check(failed, "不完整 ID3 標頭必須拒絕");
        }
        try (PushbackInputStream input = new PushbackInputStream(
                new ByteArrayInputStream(new byte[] {'I', 'D', '3', 5, 0, 0, 0, 0, 0, 0, 1, 2}), 12)) {
            boolean failed = false;
            try { RadioAudioProbe.peekAfterId3(input); }
            catch (IOException expected) { failed = true; }
            check(failed, "不支援的 ID3 版本必須拒絕");
        }
        System.out.println("RadioAudioProbeSelfTest: " + checks + " checks passed");
    }
}
