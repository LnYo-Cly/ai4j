package io.github.lnyocly.ai4j.agent.sandbox.e2b;

import java.io.IOException;

/**
 * HTTP-level E2B API error.
 */
final class E2BApiException extends IOException {

    private final int statusCode;
    private final String responseBody;

    E2BApiException(int statusCode, String message, String responseBody) {
        super(message);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    int getStatusCode() {
        return statusCode;
    }

    String getResponseBody() {
        return responseBody;
    }

    boolean isNotFound() {
        return statusCode == 404;
    }

    boolean isAuthFailure() {
        return statusCode == 401 || statusCode == 403;
    }
}
