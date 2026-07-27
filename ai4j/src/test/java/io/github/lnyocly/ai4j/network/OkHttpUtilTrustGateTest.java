package io.github.lnyocly.ai4j.network;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for the TLS trust-all opt-in gate on {@link OkHttpUtil}.
 *
 * <p>The trust-all methods must throw {@link IllegalStateException} unless the system property
 * {@code ai4j.ssl.trust-all} is set to {@code true}. This test saves and restores the property
 * to avoid interfering with other tests.</p>
 */
public class OkHttpUtilTrustGateTest {

    private static final String PROPERTY = OkHttpUtil.TRUST_ALL_PROPERTY;
    private String previousValue;

    @Before
    public void saveProperty() {
        previousValue = System.getProperty(PROPERTY);
        System.clearProperty(PROPERTY);
    }

    @After
    public void restoreProperty() {
        if (previousValue != null) {
            System.setProperty(PROPERTY, previousValue);
        } else {
            System.clearProperty(PROPERTY);
        }
    }

    @Test
    public void isTrustAllEnabledShouldDefaultToFalse() {
        Assert.assertFalse(OkHttpUtil.isTrustAllEnabled());
    }

    @Test
    public void isTrustAllEnabledShouldReturnTrueWhenPropertySet() {
        System.setProperty(PROPERTY, "true");
        Assert.assertTrue(OkHttpUtil.isTrustAllEnabled());
    }

    @Test
    public void isTrustAllEnabledShouldBeCaseInsensitive() {
        System.setProperty(PROPERTY, "TRUE");
        Assert.assertTrue(OkHttpUtil.isTrustAllEnabled());
    }

    @Test
    public void getIgnoreInitedSslContextShouldThrowWhenNotOptedIn() {
        try {
            OkHttpUtil.getIgnoreInitedSslContext();
            Assert.fail("expected IllegalStateException when trust-all is not opted in");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage().contains("trust-all"));
        } catch (Exception e) {
            Assert.fail("expected IllegalStateException, got " + e.getClass().getName());
        }
    }

    @Test
    public void getIgnoreSslHostnameVerifierShouldThrowWhenNotOptedIn() {
        try {
            OkHttpUtil.getIgnoreSslHostnameVerifier();
            Assert.fail("expected IllegalStateException when trust-all is not opted in");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage().contains("trust-all"));
        }
    }

    @Test
    public void getIgnoreInitedSslContextShouldWorkWhenOptedIn() throws Exception {
        System.setProperty(PROPERTY, "true");
        javax.net.ssl.SSLContext ctx = OkHttpUtil.getIgnoreInitedSslContext();
        Assert.assertNotNull(ctx);
        Assert.assertNotNull(ctx.getSocketFactory());
    }

    @Test
    public void getIgnoreSslHostnameVerifierShouldWorkWhenOptedIn() {
        System.setProperty(PROPERTY, "true");
        javax.net.ssl.HostnameVerifier verifier = OkHttpUtil.getIgnoreSslHostnameVerifier();
        Assert.assertNotNull(verifier);
        Assert.assertTrue("trust-all verifier must accept any hostname",
                verifier.verify("evil.example.com", null));
    }

    @Test
    public void ignoreSslTrustManagerShouldAcceptAllCertificates() throws Exception {
        // The trust manager constant itself is always accessible (no gate on the field).
        // This is intentional: production code references it conditionally.
        javax.net.ssl.X509TrustManager tm = OkHttpUtil.IGNORE_SSL_TRUST_MANAGER_X509;
        Assert.assertNotNull(tm);
        // checkServerTrusted must not throw (trusts everything)
        tm.checkServerTrusted(new java.security.cert.X509Certificate[0], "RSA");
        tm.checkClientTrusted(new java.security.cert.X509Certificate[0], "RSA");
        Assert.assertEquals(0, tm.getAcceptedIssuers().length);
    }
}
