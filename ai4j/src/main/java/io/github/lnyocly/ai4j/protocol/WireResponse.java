package io.github.lnyocly.ai4j.protocol;

import io.github.lnyocly.ai4j.model.Protocol;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A decoded HTTP response handed to a codec.
 *
 * <p>This is a protocol-private wire carrier: only the codec sees raw JSON
 * text, and the public semantic surface stays {@code Map<String,Object>}
 * (design F2).</p>
 */
public final class WireResponse {

    private final Protocol protocol;
    private final int statusCode;
    private final String bodyText;
    private final Map<String, String> headers;

    private WireResponse(Protocol protocol, int statusCode, String bodyText, Map<String, String> headers) {
        this.protocol = Objects.requireNonNull(protocol, "protocol is required");
        this.statusCode = statusCode;
        this.bodyText = bodyText;
        this.headers = headers == null || headers.isEmpty()
                ? Collections.<String, String>emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<String, String>(headers));
    }

    public static WireResponse of(Protocol protocol, int statusCode, String bodyText) {
        return new WireResponse(protocol, statusCode, bodyText, null);
    }

    public static WireResponse of(Protocol protocol, int statusCode, String bodyText,
                                  Map<String, String> headers) {
        return new WireResponse(protocol, statusCode, bodyText, headers);
    }

    public Protocol getProtocol() {
        return protocol;
    }

    public int getStatusCode() {
        return statusCode;
    }

    /** Raw JSON response text, or {@code null} when the body was empty. */
    public String getBodyText() {
        return bodyText;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public boolean isSuccessful() {
        return statusCode >= 200 && statusCode < 300;
    }
}
