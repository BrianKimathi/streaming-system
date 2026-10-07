package com.streamx.media.imports;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SsrfGuardTest {

    private static InetAddress ip(String literal) {
        try {
            return InetAddress.getByName(literal);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static SsrfGuard guardResolving(Map<String, List<String>> dns) {
        return new SsrfGuard(host -> {
            List<String> addresses = dns.get(host);
            if (addresses == null) {
                if (host.contains(":") || host.matches("[0-9.]+")) {
                    return new InetAddress[]{InetAddress.getByName(host)};
                }
                throw new UnknownHostException(host);
            }
            return addresses.stream().map(SsrfGuardTest::ip).toArray(InetAddress[]::new);
        }, SsrfGuard::isBlockedAddress);
    }

    @Test
    void blocksPrivateLoopbackLinkLocalMulticastAndUnspecifiedAddresses() {
        for (String literal : new String[]{
                "127.0.0.1", "127.8.9.10", "10.0.0.1", "10.255.255.255", "172.16.0.1", "172.31.255.254",
                "192.168.1.1", "169.254.169.254", "0.0.0.0", "100.64.0.1", "224.0.0.1", "255.255.255.255",
                "::1", "::", "fc00::1", "fd12:3456::1", "fe80::1", "ff02::1", "::ffff:127.0.0.1", "::ffff:10.1.2.3"}) {
            assertTrue(SsrfGuard.isBlockedAddress(ip(literal)), literal);
        }
    }

    @Test
    void allowsPublicAddresses() {
        for (String literal : new String[]{"8.8.8.8", "1.1.1.1", "172.15.0.1", "172.32.0.1", "100.128.0.1",
                "2606:4700:4700::1111", "::ffff:8.8.8.8"}) {
            assertFalse(SsrfGuard.isBlockedAddress(ip(literal)), literal);
        }
    }

    @Test
    void onlyHttpAndHttpsLinks() {
        SsrfGuard guard = guardResolving(Map.of());
        for (String url : new String[]{"ftp://cdn.example.com/a.mp4", "file:///etc/passwd", "gopher://x.example.com/",
                "javascript:alert(1)", "cdn.example.com/movie.mp4", "", "http://"}) {
            assertThrows(SsrfGuard.UnsafeUrlException.class, () -> guard.checkUrl(url), url);
        }
        assertEquals("https", guard.checkUrl("HTTPS://cdn.example.com/movie.mp4").getScheme().toLowerCase());
    }

    @Test
    void refusesHostnamesWithoutADotAndEmbeddedCredentials() {
        SsrfGuard guard = guardResolving(Map.of());
        for (String url : new String[]{"http://localhost/a.mp4", "http://minio:9000/streamx-media/x",
                "http://media-service:8087/", "http://localhost./a.mp4", "http://2130706433/"}) {
            assertThrows(SsrfGuard.UnsafeUrlException.class, () -> guard.checkUrl(url), url);
        }
        assertThrows(SsrfGuard.UnsafeUrlException.class, () -> guard.checkUrl("https://user:pw@cdn.example.com/a.mp4"));
    }

    @Test
    void ipLiteralsAreCheckedWithoutDns() {
        SsrfGuard guard = guardResolving(Map.of());
        for (String url : new String[]{"http://127.0.0.1/a.mp4", "http://10.0.0.8:8080/a.mp4", "http://[::1]/a.mp4",
                "http://169.254.169.254/latest/meta-data", "http://[fd00::5]/x", "http://0.0.0.0/"}) {
            assertThrows(SsrfGuard.UnsafeUrlException.class, () -> guard.checkUrl(url), url);
        }
        guard.checkUrl("http://8.8.8.8/a.mp4");
    }

    @Test
    void resolutionMustYieldOnlyPublicAddresses() {
        SsrfGuard guard = guardResolving(Map.of(
                "cdn.example.com", List.of("93.184.216.34"),
                "rebind.example.com", List.of("93.184.216.34", "10.0.0.7"),
                "internal.example.com", List.of("192.168.0.10"),
                "v6.example.com", List.of("fe80::abcd")));

        assertEquals(List.of(ip("93.184.216.34")), guard.resolveAllowed("cdn.example.com"));
        assertEquals(List.of(ip("93.184.216.34")), guard.resolveAllowed("CDN.example.com."));
        for (String host : new String[]{"rebind.example.com", "internal.example.com", "v6.example.com",
                "unknown.example.com", "intranet"}) {
            assertThrows(SsrfGuard.UnsafeUrlException.class, () -> guard.resolveAllowed(host), host);
        }
    }
}
