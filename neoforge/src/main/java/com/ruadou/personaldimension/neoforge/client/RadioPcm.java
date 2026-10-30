package com.ruadou.personaldimension.neoforge.client;

import java.util.Objects;

/** Codec-independent signed 16-bit PCM scaling for MP3, OGG and AAC. */
final class RadioPcm {
    private RadioPcm() {}

    private static void validateVolume(int volume) {
        if (volume < 0 || volume > 100)
            throw new IllegalArgumentException("電台音量必須介於 0～100");
    }

    private static short scale(short sample, int volume) {
        if (volume == 100) return sample; // Bit-exact full volume.
        long value = Math.round(sample * (volume / 100.0));
        return (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value));
    }

    /** Convert JLayer's interleaved samples to little-endian 16-bit PCM. */
    static void fromShorts(short[] samples, int count, byte[] output, int volume) {
        Objects.requireNonNull(samples);
        Objects.requireNonNull(output);
        validateVolume(volume);
        if (count < 0 || count > samples.length || (long) count * 2 > output.length)
            throw new IllegalArgumentException("MP3 音訊緩衝區長度無效");
        for (int i = 0; i < count; i++) {
            short value = scale(samples[i], volume);
            output[i * 2] = (byte) value;
            output[i * 2 + 1] = (byte) (value >>> 8);
        }
    }

    /** Scale little/big-endian PCM, either in-place (OGG) or separately (AAC). */
    static void scale16(byte[] source, byte[] output, int length,
                        int volume, boolean bigEndian) {
        Objects.requireNonNull(source);
        Objects.requireNonNull(output);
        validateVolume(volume);
        if (length < 0 || (length & 1) != 0
                || length > source.length || length > output.length)
            throw new IllegalArgumentException("電台 PCM 音訊資料長度無效");
        for (int i = 0; i < length; i += 2) {
            short sample = bigEndian
                    ? (short) ((source[i] << 8) | (source[i + 1] & 255))
                    : (short) ((source[i] & 255) | (source[i + 1] << 8));
            short value = scale(sample, volume);
            if (bigEndian) {
                output[i] = (byte) (value >>> 8);
                output[i + 1] = (byte) value;
            } else {
                output[i] = (byte) value;
                output[i + 1] = (byte) (value >>> 8);
            }
        }
    }
}
