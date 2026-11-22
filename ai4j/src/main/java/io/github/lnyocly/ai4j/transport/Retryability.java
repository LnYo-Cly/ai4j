package io.github.lnyocly.ai4j.transport;

/**
 * Single source of HTTP-status retryability for the model kernel (design 8.3 F5).
 *
 * <p>The client maps failures to exceptions and the stream engine gates retries;
 * both delegate here so the classification exists exactly once.</p>
 */
public final class Retryability {

    private Retryability() {
    }

    /** 429/408/504/5xx are retryable; other 4xx (and success) are not. */
    public static boolean isRetryableStatus(int statusCode) {
        return statusCode == 429 || statusCode == 408 || statusCode == 504 || statusCode >= 500;
    }

    /**
     * Failure-class retryability when no HTTP status was received.
     *
     * <p>An absent status means the request may already have reached the
     * provider, so the class stays retryable while the retry gate refuses to
     * resend it (design 8.3 F5).</p>
     */
    public static boolean isRetryableStatusOrUnknown(int statusCode) {
        return statusCode == 0 || isRetryableStatus(statusCode);
    }
}
