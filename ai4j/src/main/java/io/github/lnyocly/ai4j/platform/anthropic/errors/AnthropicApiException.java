package io.github.lnyocly.ai4j.platform.anthropic.errors;

import lombok.Getter;

/**
 * Anthropic Messages API 错误基类。原生调用方可按子类精确 catch（如 rate_limit / overloaded）。
 */
@Getter
public class AnthropicApiException extends RuntimeException {

    private final int status;
    private final String type;
    private final String requestId;

    public AnthropicApiException(int status, String type, String message, String requestId) {
        super(message);
        this.status = status;
        this.type = type;
        this.requestId = requestId;
    }

    public AnthropicApiException(int status, String type, String message, String requestId, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.type = type;
        this.requestId = requestId;
    }

    /** 按 HTTP 状态 / error.type 选择具体子类。 */
    public static AnthropicApiException of(int status, String type, String message, String requestId) {
        if (type != null) {
            if (type.contains("rate_limit")) {
                return new AnthropicRateLimitException(status, type, message, requestId);
            }
            if (type.contains("overloaded")) {
                return new AnthropicOverloadedException(status, type, message, requestId);
            }
            if (type.contains("authentication")) {
                return new AnthropicAuthenticationException(status, type, message, requestId);
            }
            if (type.contains("invalid_request") || type.contains("invalid")) {
                return new AnthropicInvalidRequestException(status, type, message, requestId);
            }
        }
        if (status == 429) {
            return new AnthropicRateLimitException(status, type, message, requestId);
        }
        if (status == 529) {
            return new AnthropicOverloadedException(status, type, message, requestId);
        }
        if (status == 401 || status == 403) {
            return new AnthropicAuthenticationException(status, type, message, requestId);
        }
        return new AnthropicApiException(status, type, message, requestId);
    }

    public static class AnthropicRateLimitException extends AnthropicApiException {
        public AnthropicRateLimitException(int status, String type, String message, String requestId) {
            super(status, type, message, requestId);
        }
    }

    public static class AnthropicOverloadedException extends AnthropicApiException {
        public AnthropicOverloadedException(int status, String type, String message, String requestId) {
            super(status, type, message, requestId);
        }
    }

    public static class AnthropicAuthenticationException extends AnthropicApiException {
        public AnthropicAuthenticationException(int status, String type, String message, String requestId) {
            super(status, type, message, requestId);
        }
    }

    public static class AnthropicInvalidRequestException extends AnthropicApiException {
        public AnthropicInvalidRequestException(int status, String type, String message, String requestId) {
            super(status, type, message, requestId);
        }
    }
}
