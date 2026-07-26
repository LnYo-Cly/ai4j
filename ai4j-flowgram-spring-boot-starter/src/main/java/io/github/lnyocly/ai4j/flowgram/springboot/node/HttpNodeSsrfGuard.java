package io.github.lnyocly.ai4j.flowgram.springboot.node;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

/**
 * SSRF guard for FlowGram HTTP node requests. Blocks requests targeting private, loopback,
 * link-local, and other non-routable addresses (cloud metadata endpoints, internal services, etc.).
 *
 * <p>The guard resolves the host to its concrete {@link InetAddress} and rejects any address that
 * falls within well-known private ranges. The check is performed after DNS resolution to defeat
 * DNS-rebinding tricks where a public-looking hostname resolves to an internal IP.</p>
 *
 * <p>Callers may opt out via {@code ai4j.flowgram.http-node.allow-private-network=true}; in that case
 * the guard logs a warning and allows the request through.</p>
 */
public class HttpNodeSsrfGuard {

    private static final Logger log = LoggerFactory.getLogger(HttpNodeSsrfGuard.class);

    private final boolean allowPrivateNetwork;

    public HttpNodeSsrfGuard() {
        this(false);
    }

    public HttpNodeSsrfGuard(boolean allowPrivateNetwork) {
        this.allowPrivateNetwork = allowPrivateNetwork;
    }

    /**
     * Validates the given URL. Returns normally if the target host is safe (or if private-network
     * access has been explicitly allowed). Throws {@link SsrfBlockedException} when the target
     * resolves to a blocked address.
     *
     * @param url the full URL to validate (must include scheme and host)
     * @throws SsrfBlockedException if the target address is private/loopback/link-local and not opted-out
     */
    public void validate(String url) {
        if (url == null || url.trim().isEmpty()) {
            return;
        }
        if (allowPrivateNetwork) {
            log.warn("FlowGram HTTP node allows private-network targets (ai4j.flowgram.http-node.allow-private-network=true) — SSRF protection is disabled");
            return;
        }

        String host;
        try {
            URI uri = new URI(url.trim());
            host = uri.getHost();
        } catch (URISyntaxException e) {
            throw new SsrfBlockedException("HTTP node URL is malformed: " + url, e);
        }
        if (host == null || host.trim().isEmpty()) {
            throw new SsrfBlockedException("HTTP node URL has no host: " + url);
        }

        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            // If we cannot resolve the host, let the HTTP client surface the error naturally.
            // We do NOT block — the request will simply fail with a DNS error.
            return;
        }

        for (InetAddress addr : addresses) {
            if (isBlocked(addr)) {
                throw new SsrfBlockedException(
                        "HTTP node URL blocked by SSRF guard: host '" + host
                                + "' resolves to private/non-routable address " + addr.getHostAddress()
                                + ". Set ai4j.flowgram.http-node.allow-private-network=true to override.");
            }
        }
    }

    /**
     * Determines whether a resolved address falls into a blocked range.
     *
     * <p>Blocked ranges (per RFC 1918 / RFC 4291 / RFC 3927 / RFC 6598):</p>
     * <ul>
     *   <li>Loopback: 127.0.0.0/8 (IPv4), ::1 (IPv6)</li>
     *   <li>Private: 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16 (IPv4), fc00::/7 (IPv6)</li>
     *   <li>Link-local: 169.254.0.0/16 (IPv4 — includes cloud metadata 169.254.169.254), fe80::/10 (IPv6)</li>
     *   <li>Carrier-grade NAT: 100.64.0.0/10</li>
     * </ul>
     */
    static boolean isBlocked(InetAddress addr) {
        if (addr == null) {
            return true; // fail-closed
        }
        return addr.isLoopbackAddress()
                || addr.isSiteLocalAddress()
                || addr.isLinkLocalAddress()
                || isCarrierGradeNat(addr)
                || isUniqueLocalIpv6(addr);
    }

    private static boolean isCarrierGradeNat(InetAddress addr) {
        // RFC 6598: 100.64.0.0/10 — not covered by InetAddress.isSiteLocalAddress()
        byte[] octets = addr.getAddress();
        if (octets.length == 4) {
            return (octets[0] & 0xFF) == 100
                    && (octets[1] & 0xC0) == 64; // 64..127
        }
        return false;
    }

    private static boolean isUniqueLocalIpv6(InetAddress addr) {
        // RFC 4193: fc00::/7 — Java 8 InetAddress doesn't have a direct check
        byte[] octets = addr.getAddress();
        if (octets.length == 16) {
            return (octets[0] & 0xFE) == 0xFC; // fc.. or fd..
        }
        return false;
    }

    /** Thrown when an HTTP node URL targets a blocked address. */
    public static class SsrfBlockedException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public SsrfBlockedException(String message) {
            super(message);
        }

        public SsrfBlockedException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
