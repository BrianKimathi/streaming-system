package com.streamx.media.imports;

import com.streamx.common.exception.BadRequestException;
import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/** Refuses links that would make the server talk to itself, the internal network or cloud metadata endpoints. */
@Component
public class SsrfGuard {

    private static final Pattern IPV4_LITERAL = Pattern.compile("^[0-9.]+$");

    @FunctionalInterface
    public interface HostResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    public static class UnsafeUrlException extends BadRequestException {
        public UnsafeUrlException(String message) {
            super(message);
        }
    }

    private final HostResolver resolver;
    private final Predicate<InetAddress> blocked;

    public SsrfGuard() {
        this(InetAddress::getAllByName, SsrfGuard::isBlockedAddress);
    }

    public SsrfGuard(HostResolver resolver, Predicate<InetAddress> blocked) {
        this.resolver = resolver;
        this.blocked = blocked;
    }

    /** Static checks only (scheme, credentials, host shape, IP literals); does not touch DNS. */
    public URI checkUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new UnsafeUrlException("A link is required");
        }
        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (URISyntaxException e) {
            throw new UnsafeUrlException("The link is not a valid URL");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new UnsafeUrlException("Only http and https links are supported");
        }
        if (uri.getRawUserInfo() != null) {
            throw new UnsafeUrlException("Links with embedded credentials are not supported");
        }
        String host = normalizeHost(uri.getHost());
        if (host.isEmpty()) {
            throw new UnsafeUrlException("The link has no host");
        }
        if (isIpLiteral(host)) {
            checkAddresses(host, resolve(host));
        } else if (!host.contains(".")) {
            throw new UnsafeUrlException("Links to internal hosts are not allowed");
        }
        return uri;
    }

    /** Resolves the host and returns its addresses only if every one of them is publicly routable. */
    public List<InetAddress> resolveAllowed(String rawHost) {
        String host = normalizeHost(rawHost);
        if (host.isEmpty()) {
            throw new UnsafeUrlException("The link has no host");
        }
        if (!isIpLiteral(host) && !host.contains(".")) {
            throw new UnsafeUrlException("Links to internal hosts are not allowed");
        }
        InetAddress[] addresses = resolve(host);
        checkAddresses(host, addresses);
        return List.of(addresses);
    }

    private InetAddress[] resolve(String host) {
        try {
            InetAddress[] addresses = resolver.resolve(host);
            if (addresses == null || addresses.length == 0) {
                throw new UnsafeUrlException("The link's host could not be resolved");
            }
            return addresses;
        } catch (UnknownHostException e) {
            throw new UnsafeUrlException("The link's host could not be resolved");
        }
    }

    private void checkAddresses(String host, InetAddress[] addresses) {
        for (InetAddress address : addresses) {
            if (blocked.test(address)) {
                throw new UnsafeUrlException("Links to private or internal addresses are not allowed (" + host + ")");
            }
        }
    }

    static String normalizeHost(String host) {
        if (host == null) {
            return "";
        }
        String value = host.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("[") && value.endsWith("]")) {
            value = value.substring(1, value.length() - 1);
        }
        while (value.endsWith(".")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static boolean isIpLiteral(String host) {
        return host.contains(":") || IPV4_LITERAL.matcher(host).matches();
    }

    public static boolean isBlockedAddress(InetAddress address) {
        if (address.isLoopbackAddress() || address.isAnyLocalAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return true;
        }
        byte[] b = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = b[0] & 0xFF;
            int second = b[1] & 0xFF;
            return first == 0                                   // 0.0.0.0/8
                    || first == 127                             // loopback
                    || first == 10                              // 10/8
                    || (first == 172 && second >= 16 && second <= 31)
                    || (first == 192 && second == 168)
                    || (first == 169 && second == 254)          // link-local, cloud metadata
                    || (first == 100 && second >= 64 && second <= 127) // carrier-grade NAT
                    || first >= 224;                            // multicast, reserved, broadcast
        }
        if (address instanceof Inet6Address) {
            int first = b[0] & 0xFF;
            int second = b[1] & 0xFF;
            if ((first & 0xFE) == 0xFC) {                       // fc00::/7 unique local
                return true;
            }
            if (first == 0xFE && (second & 0xC0) == 0x80) {    // fe80::/10 link-local
                return true;
            }
            boolean mappedOrCompatible = true;                  // ::ffff:a.b.c.d / ::a.b.c.d
            for (int i = 0; i < 10; i++) {
                if (b[i] != 0) {
                    mappedOrCompatible = false;
                    break;
                }
            }
            if (mappedOrCompatible && ((b[10] == 0 && b[11] == 0) || (b[10] == (byte) 0xFF && b[11] == (byte) 0xFF))) {
                try {
                    return isBlockedAddress(InetAddress.getByAddress(new byte[]{b[12], b[13], b[14], b[15]}));
                } catch (UnknownHostException e) {
                    return true;
                }
            }
        }
        return false;
    }
}
