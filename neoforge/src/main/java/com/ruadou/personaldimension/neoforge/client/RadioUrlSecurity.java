package com.ruadou.personaldimension.neoforge.client;

import com.ruadou.personaldimension.radio.LandRadioSettings;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * Validate a land radio URL before each outgoing connection, including playlist
 * fetches and HTTP redirects. Never call this from the Minecraft render thread:
 * resolving DNS can block.
 *
 * The server-side URL validator is only a syntax filter. A hostname that looks
 * public can still resolve to a private or loopback address on the client.
 */
public final class RadioUrlSecurity {
    private RadioUrlSecurity() {}

    public static URI requirePublicHttpUrl(String address) throws IOException {
        if (!LandRadioSettings.isValidStreamUrl(address)) {
            throw new IOException("電台網址無效，或不允許連線至本機與內部網路");
        }
        final URI uri;
        try {
            uri = URI.create(address);
        } catch (IllegalArgumentException exception) {
            throw new IOException("電台網址格式無效", exception);
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(uri.getHost());
        } catch (UnknownHostException exception) {
            throw exception;
        }
        if (addresses.length == 0) {
            throw new UnknownHostException(uri.getHost());
        }
        // Reject the entire hostname if even one DNS answer is non-public.
        for (InetAddress address : addresses) {
            if (!isPublicAddress(address)) {
                throw new IOException("電台主機解析到本機或內部網路，已拒絕連線");
            }
        }
        return uri;
    }

    private static boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }
        byte[] ip = address.getAddress();
        if (ip.length == 4) {
            int a = ip[0] & 255;
            int b = ip[1] & 255;
            int c = ip[2] & 255;
            // Reserved, shared-address, benchmarking and documentation ranges.
            return a != 0 && a != 10 && a != 127 && a < 224
                    && !(a == 100 && b >= 64 && b <= 127)
                    && !(a == 169 && b == 254)
                    && !(a == 172 && b >= 16 && b <= 31)
                    && !(a == 192 && b == 0 && c == 0)
                    && !(a == 192 && b == 0 && c == 2)
                    && !(a == 192 && b == 168)
                    && !(a == 198 && (b == 18 || b == 19))
                    && !(a == 198 && b == 51 && c == 100)
                    && !(a == 203 && b == 0 && c == 113);
        }
        if (ip.length == 16) {
            int first = ip[0] & 255;
            // IPv6 unique-local (fc00::/7), documentation (2001:db8::/32)
            // and non-global scopes must never be used for remote radio.
            return (first & 0xfe) != 0xfc
                    && !(first == 0x20 && (ip[1] & 255) == 0x01
                         && (ip[2] & 255) == 0x0d && (ip[3] & 255) == 0xb8)
                    && (first & 0xe0) == 0x20;
        }
        return false;
    }
}
