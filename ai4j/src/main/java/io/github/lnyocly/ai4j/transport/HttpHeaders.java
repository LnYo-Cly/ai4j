package io.github.lnyocly.ai4j.transport;

import io.github.lnyocly.ai4j.constant.Constants;
import io.github.lnyocly.ai4j.model.Protocol;
import io.github.lnyocly.ai4j.protocol.Credentials;
import io.github.lnyocly.ai4j.protocol.Endpoint;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Header assembly for the new chain.
 *
 * <p>Credentials are attached here from the endpoint, never from the semantic
 * model (design 7.1). The auth shape mirrors the legacy chain: {@code Bearer}
 * for Chat/Responses, {@code x-api-key} for Messages.</p>
 */
final class HttpHeaders {

    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private HttpHeaders() {
    }

    static Map<String, String> forRequest(TransportRequest request, boolean stream) {
        Map<String, String> headers = new LinkedHashMap<String, String>();
        headers.put("Content-Type", Constants.APPLICATION_JSON);
        if (stream) {
            headers.put("Accept", "text/event-stream");
        }

        Endpoint endpoint = request.getEndpoint();
        Credentials credentials = endpoint.getCredentials();
        String value = credentials.resolveValue();
        if (value != null && !value.isEmpty()) {
            if (credentials.getType() == Credentials.Type.BEARER) {
                headers.put("Authorization", "Bearer " + value);
            } else if (credentials.getType() == Credentials.Type.HEADER) {
                headers.put(credentials.getHeaderName(), value);
            }
        }
        if (endpoint.getProtocol() == Protocol.MESSAGES) {
            headers.put("anthropic-version", ANTHROPIC_VERSION);
        }
        for (Map.Entry<String, String> entry : endpoint.getHeaders().entrySet()) {
            headers.put(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, String> entry : request.getWireRequest().getHeaders().entrySet()) {
            headers.put(entry.getKey(), entry.getValue());
        }
        return headers;
    }
}
