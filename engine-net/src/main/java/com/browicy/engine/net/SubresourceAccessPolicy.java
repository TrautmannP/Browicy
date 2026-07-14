package com.browicy.engine.net;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

final class SubresourceAccessPolicy {

    private SubresourceAccessPolicy() {
    }

    static void validate(URI target, URI page) throws IOException {
        if (page == null || sameOrigin(target, page)) return;
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(target.getHost());
        } catch (UnknownHostException failure) {
            throw new IOException("Subresource-Host konnte nicht aufgelöst werden: " + target, failure);
        }
        for (InetAddress address : addresses) {
            if (!isPublic(address)) {
                throw new IOException("Zugriff auf lokale/private Subresource blockiert: " + target);
            }
        }
    }

    static boolean sameOrigin(URI first, URI second) {
        return first != null && second != null
                && equalsIgnoreCase(first.getScheme(), second.getScheme())
                && equalsIgnoreCase(first.getHost(), second.getHost())
                && effectivePort(first) == effectivePort(second);
    }

    private static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress()) return false;
        byte[] bytes = address.getAddress();
        if (bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc) return false;
        return bytes.length != 4 || !((bytes[0] & 0xff) == 100 && (bytes[1] & 0xc0) == 64);
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) return uri.getPort();
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private static boolean equalsIgnoreCase(String first, String second) {
        return first != null && second != null && first.equalsIgnoreCase(second);
    }
}
