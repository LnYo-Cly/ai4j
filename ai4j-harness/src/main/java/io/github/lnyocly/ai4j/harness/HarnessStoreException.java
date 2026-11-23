package io.github.lnyocly.ai4j.harness;

public class HarnessStoreException extends RuntimeException {

    private final String errorCode;

    public HarnessStoreException(String message) {
        this((String) null, message, null);
    }

    public HarnessStoreException(String message, Throwable cause) {
        this((String) null, message, cause);
    }

    public HarnessStoreException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
