package io.github.lnyocly.ai4j.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

/**
 * Utility for disabling TLS certificate validation.
 *
 * <p><strong>Deprecated for security reasons.</strong> Disabling TLS validation allows
 * man-in-the-middle attacks. Every method in this class now requires an explicit opt-in
 * via the system property {@code -Dai4j.ssl.trust-all=true}. Without this property,
 * calling {@link #getIgnoreInitedSslContext()} or {@link #getIgnoreSslHostnameVerifier()}
 * throws {@link IllegalStateException}.</p>
 *
 * <p>To migrate: use properly configured trust stores or test-scoped self-signed
 * certificate helpers instead of disabling validation globally.</p>
 *
 * @author Vania
 */
@Deprecated
public class OkHttpUtil {

    private static final Logger log = LoggerFactory.getLogger(OkHttpUtil.class);

    /** System property name that must be set to {@code true} to unlock the trust-all methods. */
    public static final String TRUST_ALL_PROPERTY = "ai4j.ssl.trust-all";

    /**
     * X509TrustManager instance which ignored SSL certification.
     *
     * @deprecated trust-all manager accepts any certificate, enabling MITM attacks.
     */
    @Deprecated
    public static final X509TrustManager IGNORE_SSL_TRUST_MANAGER_X509 = new X509TrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[] {};
        }
    };

    /**
     * Checks whether the trust-all opt-in system property is set.
     *
     * @return {@code true} if {@code -Dai4j.ssl.trust-all=true} is set
     */
    public static boolean isTrustAllEnabled() {
        return "true".equalsIgnoreCase(System.getProperty(TRUST_ALL_PROPERTY));
    }

    /**
     * Throws {@link IllegalStateException} when the trust-all opt-in is not active.
     * Called by every method that disables TLS validation.
     */
    private static void requireTrustAllOptIn() {
        if (!isTrustAllEnabled()) {
            throw new IllegalStateException(
                    "TLS validation bypass is disabled by default for security. "
                            + "To use OkHttpUtil trust-all methods, set -D" + TRUST_ALL_PROPERTY + "=true. "
                            + "For production, configure proper trust stores instead.");
        }
        log.warn("OkHttpUtil trust-all TLS bypass is active ({}=true) — "
                + "certificate validation is DISABLED. Do NOT use in production.", TRUST_ALL_PROPERTY);
    }

    /**
     * Get initialized SSLContext instance which ignored SSL certification.
     *
     * @return an SSLContext that trusts all certificates
     * @throws NoSuchAlgorithmException if the SSL algorithm is unavailable
     * @throws KeyManagementException   if key initialization fails
     * @throws IllegalStateException    if {@code -Dai4j.ssl.trust-all=true} is not set
     * @deprecated trust-all SSLContext disables certificate validation, enabling MITM attacks.
     */
    @Deprecated
    public static SSLContext getIgnoreInitedSslContext() throws NoSuchAlgorithmException, KeyManagementException {
        requireTrustAllOptIn();
        SSLContext sslContext = SSLContext.getInstance("SSL");
        sslContext.init(null, new TrustManager[] { IGNORE_SSL_TRUST_MANAGER_X509 }, new SecureRandom());
        return sslContext;
    }

    /**
     * Get HostnameVerifier which ignored SSL certification.
     *
     * @return a HostnameVerifier that accepts any hostname
     * @throws IllegalStateException if {@code -Dai4j.ssl.trust-all=true} is not set
     * @deprecated trust-all HostnameVerifier disables hostname verification, enabling MITM attacks.
     */
    @Deprecated
    public static HostnameVerifier getIgnoreSslHostnameVerifier() {
        requireTrustAllOptIn();
        return new HostnameVerifier() {
            @Override
            public boolean verify(String arg0, SSLSession arg1) {
                return true;
            }
        };
    }
}
