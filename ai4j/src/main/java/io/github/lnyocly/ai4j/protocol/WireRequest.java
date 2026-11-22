package io.github.lnyocly.ai4j.protocol;

import io.github.lnyocly.ai4j.model.JsonValues;
import io.github.lnyocly.ai4j.model.Protocol;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A fully encoded wire request produced by a codec.
 *
 * <p>{@code body} is the JSON object that will be serialized; {@code headers}
 * carry protocol headers only — credentials are attached by the transport from
 * the endpoint, never from the model (design 7.1).</p>
 */
public final class WireRequest {

    private final Protocol protocol;
    private final String method;
    private final String path;
    private final Map<String, Object> body;
    private final Map<String, String> headers;
    private final boolean stream;
    private final java.util.List<io.github.lnyocly.ai4j.model.DroppedField> warnings;

    private WireRequest(Protocol protocol, String method, String path, Map<String, Object> body,
                        Map<String, String> headers, boolean stream,
                        java.util.List<io.github.lnyocly.ai4j.model.DroppedField> warnings) {
        this.protocol = Objects.requireNonNull(protocol, "protocol is required");
        this.method = method == null ? "POST" : method;
        this.path = path;
        this.body = JsonValues.copyObject(Objects.requireNonNull(body, "body is required"));
        this.headers = headers == null || headers.isEmpty()
                ? Collections.<String, String>emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<String, String>(headers));
        this.stream = stream;
        this.warnings = warnings == null || warnings.isEmpty()
                ? Collections.<io.github.lnyocly.ai4j.model.DroppedField>emptyList()
                : Collections.unmodifiableList(
                        new java.util.ArrayList<io.github.lnyocly.ai4j.model.DroppedField>(warnings));
    }

    public static WireRequest of(Protocol protocol, String path, Map<String, Object> body, boolean stream) {
        return new WireRequest(protocol, "POST", path, body, null, stream, null);
    }

    public static WireRequest of(Protocol protocol, String method, String path, Map<String, Object> body,
                                 Map<String, String> headers, boolean stream) {
        return new WireRequest(protocol, method, path, body, headers, stream, null);
    }

    public static WireRequest of(Protocol protocol, String method, String path, Map<String, Object> body,
                                 Map<String, String> headers, boolean stream,
                                 java.util.List<io.github.lnyocly.ai4j.model.DroppedField> warnings) {
        return new WireRequest(protocol, method, path, body, headers, stream, warnings);
    }

    public Protocol getProtocol() {
        return protocol;
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    /** Immutable deep-copied JSON request body. */
    public Map<String, Object> getBody() {
        return body;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public boolean isStream() {
        return stream;
    }

    /** Explicit encode-time losses; never silently discarded (design 5.1 invariant 2). */
    public java.util.List<io.github.lnyocly.ai4j.model.DroppedField> getWarnings() {
        return warnings;
    }

    @Override
    public String toString() {
        return "WireRequest{" + protocol + " " + method + " " + path + ", stream=" + stream + '}';
    }
}
