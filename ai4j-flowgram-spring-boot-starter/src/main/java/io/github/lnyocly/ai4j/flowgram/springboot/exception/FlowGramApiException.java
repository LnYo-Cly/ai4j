package io.github.lnyocly.ai4j.flowgram.springboot.exception;

import org.springframework.http.HttpStatus;

public class FlowGramApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Object details;

    public FlowGramApiException(HttpStatus status, String code, String message) {
        this(status, code, message, null);
    }

    public FlowGramApiException(HttpStatus status, String code, String message, Object details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public Object getDetails() {
        return details;
    }
}
