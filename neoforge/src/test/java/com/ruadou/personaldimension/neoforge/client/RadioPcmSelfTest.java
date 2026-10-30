package com.ruadou.personaldimension.neoforge.client;

import java.util.Arrays;

/** Java 21 regression checks for codec-independent PCM conversion. */
public final class RadioPcmSelfTest {
    private static int checks;
    private RadioPcmSelfTest() {}
    private static void check(boolean result, String description) {
        checks++;
        if (!result) throw new AssertionError(description);
    }
    private static void invalid(Runnable operation) {
        boolean failed = false;
        try { operation.run(); } catch (IllegalArgumentException expected) { failed = true; }
        check(failed, "無效的 PCM 參數必須拒絕");
    }
    public static void main(String[] args) {
        short[] samples = {Short.MIN_VALUE, -1000, 0, 1000, Short.MAX_VALUE};
        byte[] mp3 = new byte[10];
        RadioPcm.fromShorts(samples, 5, mp3, 100);
        check(Arrays.equals(mp3, new byte[] {0, (byte) 128, 24, (byte) 252,
                0, 0, (byte) 232, 3, (byte) 255, 127}), "MP3 100% 需保留原始資料");
        RadioPcm.fromShorts(samples, 5, mp3, 50);
        check(Arrays.equals(mp3, new byte[] {0, (byte) 192, 12, (byte) 254,
                0, 0, (byte) 244, 1, 0, 64}), "MP3 50% 縮放錯誤");
        RadioPcm.fromShorts(samples, 5, mp3, 0);
        check(Arrays.equals(mp3, new byte[10]), "MP3 0% 靜音錯誤");
        byte[] ogg = {24, (byte) 252, (byte) 232, 3, 99};
        RadioPcm.scale16(ogg, ogg, 4, 50, false);
        check(Arrays.equals(ogg, new byte[] {12, (byte) 254, (byte) 244, 1, 99}),
                "OGG 原地縮放及尾端保護失敗");
        byte[] aac = {(byte) 252, 24, 3, (byte) 232};
        byte[] original = aac.clone();
        byte[] out = new byte[6];
        Arrays.fill(out, (byte) 77);
        RadioPcm.scale16(aac, out, 4, 50, true);
        check(Arrays.equals(out, new byte[] {(byte) 254, 12, 1, (byte) 244, 77, 77}),
                "AAC 大端序縮放失敗");
        check(Arrays.equals(aac, original), "AAC 不可改寫解碼器 PCM");
        RadioPcm.scale16(aac, out, 4, 100, true);
        check(Arrays.equals(Arrays.copyOf(out, 4), aac), "AAC 100% 必須完全相同");
        invalid(() -> RadioPcm.scale16(aac, out, 3, 50, true));
        invalid(() -> RadioPcm.scale16(aac, new byte[2], 4, 50, true));
        invalid(() -> RadioPcm.scale16(aac, out, 4, 101, true));
        invalid(() -> RadioPcm.fromShorts(samples, 6, mp3, 50));
        invalid(() -> RadioPcm.fromShorts(samples, 5, new byte[9], 50));
        System.out.println("RadioPcmSelfTest: " + checks + " checks passed");
    }
}
