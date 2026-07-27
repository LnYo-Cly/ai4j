package io.github.lnyocly.ai4j.agent.a2a;

/**
 * A2A 1.0 structured error — represents errors in A2A protocol responses.
 *
 * <p>Error structure per A2A spec:</p>
 * <pre>
 * {
 *   "error": {
 *     "code": "INVALID_REQUEST",
 *     "message": "Detailed error message"
 *   }
 * }
 * </pre>
 *
 * <p>Standard error codes:</p>
 * <ul>
 *   <li>{@link #INVALID_REQUEST} - Malformed request</li>
 *   <li>{@link #AUTHENTICATION_FAILED} - Auth failed</li>
 *   <li>{@link #TASK_NOT_FOUND} - Task doesn't exist</li>
 *   <li>{@link #INTERNAL_ERROR} - Server error</li>
 * </ul>
 */
public class A2AError {

    // Standard error codes
    public static final String INVALID_REQUEST = "INVALID_REQUEST";
    public static final String AUTHENTICATION_FAILED = "AUTHENTICATION_FAILED";
    public static final String TASK_NOT_FOUND = "TASK_NOT_FOUND";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    private String code;
    private String message;

    public A2AError() {
    }

    public A2AError(String code, String message) {
        this.code = code;
        this.message = message;
    }

    /**
     * Creates an INVALID_REQUEST error.
     */
    public static A2AError invalidRequest(String message) {
        return new A2AError(INVALID_REQUEST, message);
    }

    /**
     * Creates an AUTHENTICATION_FAILED error.
     */
    public static A2AError authenticationFailed(String message) {
        return new A2AError(AUTHENTICATION_FAILED, message);
    }

    /**
     * Creates a TASK_NOT_FOUND error.
     */
    public static A2AError taskNotFound(String message) {
        return new A2AError(TASK_NOT_FOUND, message);
    }

    /**
     * Creates an INTERNAL_ERROR error.
     */
    public static A2AError internalError(String message) {
        return new A2AError(INTERNAL_ERROR, message);
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    @Override
    public String toString() {
        return "A2AError{code='" + code + "', message='" + message + "'}";
    }
}