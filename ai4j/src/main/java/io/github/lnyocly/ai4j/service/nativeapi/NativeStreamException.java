package io.github.lnyocly.ai4j.service.nativeapi;

import io.github.lnyocly.ai4j.exception.Ai4jException;
import io.github.lnyocly.ai4j.model.Protocol;

/**
 * Native stream failure that is not an HTTP error.
 *
 * <p>Two kinds occur on the protocol-native streaming path:</p>
 * <ul>
 *   <li>{@link #KIND_IN_BAND_ERROR}: the provider reported an error as a stream
 *       event on a 2xx response (OpenAI chat / Responses).</li>
 *   <li>{@link #KIND_INCOMPLETE}: the transport closed cleanly without the
 *       protocol's required terminal event, so the model completion cannot be
 *       claimed.</li>
 * </ul>
 */
public class NativeStreamException extends Ai4jException {

    public static final String KIND_IN_BAND_ERROR = "in_band_error";
    public static final String KIND_INCOMPLETE = "incomplete";

    private final Protocol protocol;
    private final String kind;
    private final String errorType;
    private final String errorCode;
    private final String requestId;
    private final String rawData;

    NativeStreamException(Protocol protocol, String kind, String errorType, String errorCode,
                          String message, String requestId, String rawData) {
        super(message);
        this.protocol = protocol;
        this.kind = kind;
        this.errorType = errorType;
        this.errorCode = errorCode;
        this.requestId = requestId;
        this.rawData = rawData;
    }

    public Protocol getProtocol() {
        return protocol;
    }

    /** {@link #KIND_IN_BAND_ERROR} or {@link #KIND_INCOMPLETE}. */
    public String getKind() {
        return kind;
    }

    public String getErrorType() {
        return errorType;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getRawData() {
        return rawData;
    }
}
