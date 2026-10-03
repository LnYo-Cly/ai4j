package io.github.lnyocly.ai4j.client;

import io.github.lnyocly.ai4j.exception.Ai4jException;
import io.github.lnyocly.ai4j.model.ModelCallExceptionMarker;
import io.github.lnyocly.ai4j.model.ModelCallStatus;
import io.github.lnyocly.ai4j.model.Protocol;

/**
 * Unified failure for the new chain (design 8.1).
 *
 * <p>Carries the protocol, HTTP status, provider error type/request id,
 * retryability, status and a bounded raw snippet. It extends {@link Ai4jException}
 * so existing legacy catch blocks keep working (design 12).</p>
 */
public class ModelCallException extends Ai4jException {

    private final Protocol protocol;
    private final int httpStatus;
    private final String providerErrorType;
    private final String providerRequestId;
    private final boolean retryable;
    private final String rawSnippet;
    private final ModelCallStatus status;

    public ModelCallException(Protocol protocol, int httpStatus, String providerErrorType,
                              String providerRequestId, boolean retryable, String message,
                              String rawSnippet, ModelCallStatus status) {
        super(message);
        this.protocol = protocol;
        this.httpStatus = httpStatus;
        this.providerErrorType = providerErrorType;
        this.providerRequestId = providerRequestId;
        this.retryable = retryable;
        this.rawSnippet = rawSnippet;
        this.status = status == null ? ModelCallStatus.FAILED : status;
    }

    public ModelCallException(Protocol protocol, int httpStatus, String message,
                              String rawSnippet, ModelCallStatus status) {
        this(protocol, httpStatus, null, null, false, message, rawSnippet, status);
    }

    /** Build a client exception from a wire-free event marker. */
    public static ModelCallException fromMarker(ModelCallExceptionMarker marker) {
        return new ModelCallException(marker.getProtocol(), marker.getHttpStatus(),
                marker.getProviderErrorType(), marker.getProviderRequestId(), marker.isRetryable(),
                marker.getMessage(), marker.getRawSnippet(), marker.getStatus());
    }

    public Protocol getProtocol() {
        return protocol;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getProviderErrorType() {
        return providerErrorType;
    }

    public String getProviderRequestId() {
        return providerRequestId;
    }

    /** Whether the failure class is retryable (design 8.3); budget/replayability still apply. */
    public boolean isRetryable() {
        return retryable;
    }

    /** Raw provider body snippet, at most 500 characters. */
    public String getRawSnippet() {
        return rawSnippet;
    }

    public ModelCallStatus getStatus() {
        return status;
    }
}
