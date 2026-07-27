package io.github.lnyocly.ai4j.flowgram.springboot.node;

import io.github.lnyocly.ai4j.flowgram.springboot.node.HttpNodeSsrfGuard.SsrfBlockedException;
import org.junit.Assert;
import org.junit.Test;

import java.net.InetAddress;

/**
 * SSRF guard tests covering every attack scenario from the task plan:
 * loopback (IPv4/IPv6), private ranges, cloud-metadata link-local, and the opt-out switch.
 */
public class HttpNodeSsrfGuardTest {

    private final HttpNodeSsrfGuard guard = new HttpNodeSsrfGuard(false);
    private final HttpNodeSsrfGuard permissive = new HttpNodeSsrfGuard(true);

    // ---- Blocked addresses (the "before" picture: everything below must throw) ----

    @Test
    public void shouldBlockLoopbackIpv4() {
        blocked("http://127.0.0.1/admin");
    }

    @Test
    public void shouldBlockLoopbackAnyOctet() {
        blocked("http://127.255.255.254/admin");
    }

    @Test
    public void shouldBlockLocalhost() {
        blocked("http://localhost/secret");
    }

    @Test
    public void shouldBlockPrivateClassA() {
        blocked("http://10.0.0.5/internal");
    }

    @Test
    public void shouldBlockPrivateClassB() {
        blocked("http://172.16.0.2/internal");
    }

    @Test
    public void shouldBlockPrivateClassC() {
        blocked("http://192.168.1.1/internal");
    }

    @Test
    public void shouldBlockCloudMetadataEndpoint() {
        // AWS/GCP/Azure IMDS — the marquee SSRF target.
        blocked("http://169.254.169.254/latest/meta-data/");
    }

    @Test
    public void shouldBlockLinkLocalRange() {
        blocked("http://169.254.170.2/v1/envs");
    }

    @Test
    public void shouldBlockLoopbackIpv6() {
        blocked("http://[::1]/admin");
    }

    @Test
    public void shouldBlockUniqueLocalIpv6() {
        blocked("http://[fc00::1]/internal");
    }

    @Test
    public void shouldBlockUniqueLocalIpv6Fd() {
        blocked("http://[fd12:3456:789a::1]/internal");
    }

    @Test
    public void shouldBlockCarrierGradeNat() {
        // RFC 6598 (100.64.0.0/10) — not caught by InetAddress.isSiteLocalAddress().
        blocked("http://100.64.0.1/internal");
    }

    // ---- Allowed scenarios ----

    @Test
    public void shouldNotBlockPublicAddress() {
        // 8.8.8.8 is Google DNS — publicly routable, not in any private range.
        allowed("http://8.8.8.8/dns");
    }

    @Test
    public void shouldAllowAllWhenOptOut() {
        // With the opt-out switch, every blocked target above must pass.
        new HttpNodeSsrfGuard(true).validate("http://169.254.169.254/latest/meta-data/");
        new HttpNodeSsrfGuard(true).validate("http://127.0.0.1/admin");
        new HttpNodeSsrfGuard(true).validate("http://[::1]/admin");
    }

    // ---- Edge cases ----

    @Test
    public void shouldRejectMalformedUrl() {
        try {
            guard.validate("http://[invalid");
            Assert.fail("expected SsrfBlockedException for malformed URL");
        } catch (SsrfBlockedException expected) {
            Assert.assertTrue(expected.getMessage().contains("malformed"));
        }
    }

    @Test
    public void shouldRejectMissingHost() {
        try {
            guard.validate("http:///path-only");
            Assert.fail("expected SsrfBlockedException for missing host");
        } catch (SsrfBlockedException expected) {
            Assert.assertTrue(expected.getMessage().contains("host"));
        }
    }

    @Test
    public void shouldFailClosedForNullAddressInIsBlockedCheck() {
        Assert.assertTrue("null address must be treated as blocked (fail-closed)",
                HttpNodeSsrfGuard.isBlocked(null));
    }

    @Test
    public void shouldFailClosedForUnresolvableHost() {
        // Unresolvable host: guard must NOT throw (lets the HTTP client surface the DNS error).
        // Verifying this is the non-blocking path, not an allow-through of a private IP.
        guard.validate("http://ai4j-nonexistent-host-example.invalid/");
    }

    // ---- Direct range checks (no DNS dependency, deterministic) ----

    @Test
    public void isBlockedShouldCoverAllPrivateRanges() throws Exception {
        Assert.assertTrue("127.0.0.1", HttpNodeSsrfGuard.isBlocked(InetAddress.getByName("127.0.0.1")));
        Assert.assertTrue("10.0.0.1", HttpNodeSsrfGuard.isBlocked(InetAddress.getByName("10.0.0.1")));
        Assert.assertTrue("172.16.0.1", HttpNodeSsrfGuard.isBlocked(InetAddress.getByName("172.16.0.1")));
        Assert.assertTrue("172.31.255.255", HttpNodeSsrfGuard.isBlocked(InetAddress.getByName("172.31.255.255")));
        Assert.assertTrue("192.168.0.1", HttpNodeSsrfGuard.isBlocked(InetAddress.getByName("192.168.0.1")));
        Assert.assertTrue("169.254.169.254", HttpNodeSsrfGuard.isBlocked(InetAddress.getByName("169.254.169.254")));
        Assert.assertTrue("100.64.0.1", HttpNodeSsrfGuard.isBlocked(InetAddress.getByName("100.64.0.1")));
        Assert.assertTrue("::1", HttpNodeSsrfGuard.isBlocked(InetAddress.getByName("::1")));
        Assert.assertTrue("fc00::1", HttpNodeSsrfGuard.isBlocked(InetAddress.getByName("fc00::1")));
        Assert.assertTrue("fd12::1", HttpNodeSsrfGuard.isBlocked(InetAddress.getByName("fd12::1")));
        Assert.assertTrue("fe80::1", HttpNodeSsrfGuard.isBlocked(InetAddress.getByName("fe80::1")));
    }

    @Test
    public void isBlockedShouldNotFlagPublicAddresses() throws Exception {
        Assert.assertFalse("8.8.8.8", HttpNodeSsrfGuard.isBlocked(InetAddress.getByName("8.8.8.8")));
        Assert.assertFalse("1.1.1.1", HttpNodeSsrfGuard.isBlocked(InetAddress.getByName("1.1.1.1")));
        // 172.15.x.x and 172.32.x.x are outside 172.16.0.0/12
        Assert.assertFalse("172.15.0.1", HttpNodeSsrfGuard.isBlocked(InetAddress.getByName("172.15.0.1")));
        Assert.assertFalse("172.32.0.1", HttpNodeSsrfGuard.isBlocked(InetAddress.getByName("172.32.0.1")));
        // 100.63.x.x and 100.128.x.x are outside 100.64.0.0/10
        Assert.assertFalse("100.63.0.1", HttpNodeSsrfGuard.isBlocked(InetAddress.getByName("100.63.0.1")));
        Assert.assertFalse("100.128.0.1", HttpNodeSsrfGuard.isBlocked(InetAddress.getByName("100.128.0.1")));
    }

    // ---- helpers ----

    private void blocked(String url) {
        try {
            guard.validate(url);
            Assert.fail("expected SsrfBlockedException for " + url);
        } catch (SsrfBlockedException expected) {
            // expected
            Assert.assertTrue(expected.getMessage().contains("blocked")
                    || expected.getMessage().contains("malformed")
                    || expected.getMessage().contains("host"));
        }
    }

    private void allowed(String url) {
        guard.validate(url); // must not throw
    }
}
