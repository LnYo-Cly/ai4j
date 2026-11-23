package io.github.lnyocly.ai4j.model;

import java.util.Objects;

/**
 * A wire-free description of a model call failure.
 *
 * <p>{@link ModelEvent#getFailure()} must be serializable and free of transport
 * state, so events carry this marker instead of a live exception. The
 * transport-facing exception type lives in the {@code client} package and
 * converts from this marker.</p>
 */
public final class ModelCallExceptionMarker {

    private final Protocol protocol;
    private final int httpStatus;
    private final String providerErrorType;
    private final String providerRequestId;
    private final boolean retryable;
    private final String message;
    private final String rawSnippet;
    private final ModelCallStatus status;
    private final Long retryAfterMillis;

    private ModelCallExceptionMarker(Protocol protocol, int httpStatus, String providerErrorType,
                                     String providerRequestId, boolean retryable, String message,
                                     String rawSnippet, ModelCallStatus status, Long retryAfterMillis) {
        this.protocol = Objects.requireNonNull(protocol, "protocol is required");
        this.httpStatus = httpStatus;
        this.providerErrorType = providerErrorType;
        this.providerRequestId = providerRequestId;
        this.retryable = retryable;
        this.message = message;
        this.rawSnippet = rawSnippet;
        this.status = status == null ? ModelCallStatus.FAILED : status;
        this.retryAfterMillis = retryAfterMillis;
    }

    public static Builder builder(Protocol protocol) {
        return new Builder(protocol);
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

    /** Provider request id header value when present (e.g. Anthropic {@code request-id}). */
    public String getProviderRequestId() {
        return providerRequestId;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public String getMessage() {
        return message;
    }

    /** Raw provider body snippet, at most 500 characters. */
    public String getRawSnippet() {
        return rawSnippet;
    }

    public ModelCallStatus getStatus() {
        return status;
    }

    /** Optional server-supplied {@code Retry-After} delay in milliseconds, or {@code null}. */
    public Long getRetryAfterMillis() {
        return retryAfterMillis;
    }

    @Override
    public String toString() {
        return "ModelCallExceptionMarker{status=" + httpStatus + ", retryable=" + retryable
                + ", message=" + message + '}';
    }

    /** Fluent builder. */
    public static final class Builder {

        private final Protocol protocol;
        private int httpStatus;
        private String providerErrorType;
        private String providerRequestId;
        private boolean retryable;
        private String message;
        private String rawSnippet;
        private ModelCallStatus status = ModelCallStatus.FAILED;
        private Long retryAfterMillis;

        private Builder(Protocol protocol) {
            this.protocol = protocol;
        }

        public Builder httpStatus(int httpStatus) {
            this.httpStatus = httpStatus;
            return this;
        }

        public Builder providerErrorType(String providerErrorType) {
            this.providerErrorType = providerErrorType;
            return this;
        }

        public Builder providerRequestId(String providerRequestId) {
            this.providerRequestId = providerRequestId;
            return this;
        }

        public Builder retryable(boolean retryable) {
            this.retryable = retryable;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder rawSnippet(String rawSnippet) {
            this.rawSnippet = rawSnippet;
            return this;
        }

        public Builder status(ModelCallStatus status) {
            this.status = status;
            return this;
        }

        public Builder retryAfterMillis(Long retryAfterMillis) {
            this.retryAfterMillis = retryAfterMillis;
            return this;
        }

        public ModelCallExceptionMarker build() {
            return new ModelCallExceptionMarker(protocol, httpStatus, providerErrorType,
                    providerRequestId, retryable, message, rawSnippet, status, retryAfterMillis);
        }
    }
}
