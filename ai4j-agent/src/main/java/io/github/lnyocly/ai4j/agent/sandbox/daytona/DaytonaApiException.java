package io.github.lnyocly.ai4j.agent.sandbox.daytona;

import java.io.IOException;

/**
 * HTTP-level Daytona API error.
 */
final class DaytonaApiException extends IOException {

    private final int statusCode;
    private final String responseBody;

    DaytonaApiException(int statusCode, String message, String responseBody) {
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
}
