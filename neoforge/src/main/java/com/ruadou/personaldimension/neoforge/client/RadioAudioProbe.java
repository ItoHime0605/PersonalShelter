package com.ruadou.personaldimension.neoforge.client;

import java.io.EOFException;
import java.io.IOException;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Bounded stream preflight shared by MP3, Ogg, AAC and playlist handling.
 * ID3v2 metadata can precede ADTS AAC as well as MP3; treating every ID3
 * header as MP3 misroutes those AAC stations to the wrong decoder.
 */
final class RadioAudioProbe {
    private static final int HEADER_BYTES = 12;
    private static final int MAX_ID3_BYTES = 1024 * 1024;
    private static final int MAX_LEADING_TAGS = 4;

    private RadioAudioProbe() {}

    /**
     * Skip at most four complete ID3v2 tags, then peek at the actual audio
     * header without consuming it. Caller supplies a pushback buffer of >=12.
     * An oversized or truncated tag is rejected rather than downloading an
     * unbounded metadata body from a malformed radio server.
     */
    static byte[] peekAfterId3(PushbackInputStream input) throws IOException {
        for (int tags = 0; tags <= MAX_LEADING_TAGS; tags++) {
            byte[] header = input.readNBytes(HEADER_BYTES);
            if (header.length > 0) input.unread(header);
            if (header.length < 3 || header[0] != 'I'
                    || header[1] != 'D' || header[2] != '3') {
                return header;
            }
            if (tags == MAX_LEADING_TAGS) {
                throw new IOException("電台音訊開頭的 ID3 中繼資料過多");
            }
            if (header.length < 10) {
                throw new EOFException("電台 ID3 中繼資料標頭不完整");
            }
            for (int i = 6; i <= 9; i++) {
                if ((header[i] & 0x80) != 0) {
                    throw new IOException("電台 ID3 中繼資料長度無效");
                }
            }
            int size = ((header[6] & 127) << 21) | ((header[7] & 127) << 14)
                    | ((header[8] & 127) << 7) | (header[9] & 127);
            if (size > MAX_ID3_BYTES) {
                throw new IOException("電台 ID3 中繼資料超過安全大小限制");
            }
            // ID3v2.4 supports a 10-byte footer. It is included after the body.
            int footer = (header[3] == 4 && (header[5] & 0x10) != 0) ? 10 : 0;
            input.skipNBytes(10L + size + footer);
        }
        throw new IOException("電台 ID3 中繼資料過多");
    }


    /** One audio signature policy for direct streams and playlist candidates. */
    enum Kind { MP3, ADTS_AAC, OGG, MP4_AAC, PLAYLIST, NON_AUDIO, UNKNOWN }

    static Kind classify(byte[] signature) {
        String prefix = textPrefix(signature);
        if (prefix.startsWith("#extm3u") || prefix.startsWith("#ext-x-")
                || prefix.startsWith("#extinf") || prefix.startsWith("[playlist]")) {
            return Kind.PLAYLIST;
        }
        if (prefix.startsWith("<!doctype") || prefix.startsWith("<html")
                || prefix.startsWith("<?xml") || prefix.startsWith("{")) {
            return Kind.NON_AUDIO;
        }
        if (signature.length >= 4 && signature[0] == 'O' && signature[1] == 'g'
                && signature[2] == 'g' && signature[3] == 'S') {
            return Kind.OGG;
        }
        if (signature.length >= 8 && signature[4] == 'f' && signature[5] == 't'
                && signature[6] == 'y' && signature[7] == 'p') {
            return Kind.MP4_AAC;
        }
        if (signature.length >= 2 && (signature[0] & 255) == 255
                && (signature[1] & 0xf6) == 0xf0) {
            return Kind.ADTS_AAC;
        }
        // A leading ID3 tag is an MP3 hint only. The main player first strips
        // bounded ID3v2 tags and reclassifies the actual AAC/MP3 payload.
        if (signature.length >= 3 && signature[0] == 'I'
                && signature[1] == 'D' && signature[2] == '3') {
            return Kind.MP3;
        }
        if (signature.length >= 3 && (signature[0] & 255) == 255) {
            int second = signature[1] & 255;
            int third = signature[2] & 255;
            // MPEG sync, valid layer, non-reserved sample rate and bitrate.
            if ((second & 0xe0) == 0xe0 && (second & 0x06) != 0
                    && (third & 0x0c) != 0x0c && (third & 0xf0) != 0xf0) {
                return Kind.MP3;
            }
        }
        return Kind.UNKNOWN;
    }

    /** Recognize UTF-8 BOM-prefixed playlists and text errors consistently. */
    static String textPrefix(byte[] signature) {
        String prefix = new String(signature, StandardCharsets.UTF_8);
        if (prefix.startsWith("\uFEFF")) prefix = prefix.substring(1);
        return prefix.stripLeading().toLowerCase(Locale.ROOT);
    }
}
