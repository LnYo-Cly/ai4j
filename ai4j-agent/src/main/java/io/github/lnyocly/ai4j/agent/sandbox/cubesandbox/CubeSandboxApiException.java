package io.github.lnyocly.ai4j.agent.sandbox.cubesandbox;

import java.io.IOException;

/**
 * HTTP-level CubeSandbox API error.
 */
final class CubeSandboxApiException extends IOException {

    private final int statusCode;
    private final String responseBody;

    CubeSandboxApiException(int statusCode, String message, String responseBody) {
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
