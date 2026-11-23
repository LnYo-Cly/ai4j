package io.github.lnyocly.ai4j.service.nativeapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lnyocly.ai4j.constant.Constants;
import io.github.lnyocly.ai4j.exception.AiHttpException;
import io.github.lnyocly.ai4j.exception.HttpErrorDecoder;
import io.github.lnyocly.ai4j.listener.StreamExecutionOptions;
import io.github.lnyocly.ai4j.model.CallExecutionOptions;
import io.github.lnyocly.ai4j.model.Protocol;
import io.github.lnyocly.ai4j.protocol.Credentials;
import io.github.lnyocly.ai4j.protocol.Endpoint;
import io.github.lnyocly.ai4j.protocol.WireRequest;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.transport.OkHttpModelTransport;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Shared plumbing for the protocol-native single-turn clients.
 *
 * <p>Builds the endpoint, the deep-snapshot wire body and the shared OkHttp
 * transport, so the three clients stay thin and a caller never assembles
 * {@code Platform}/{@code Endpoint}/{@code Profile} objects by hand.</p>
 */
final class NativeProtocolSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** Request-id headers the OpenAI-compatible endpoints are known to return. */
    private static final String[] OPENAI_REQUEST_ID_HEADERS = {
            "x-request-id", "request-id", "openai-request-id", "apim-request-id", "x-ms-request-id"
    };

    /** Request-id headers the Anthropic-compatible endpoints are known to return. */
    private static final String[] ANTHROPIC_REQUEST_ID_HEADERS = {
            "request-id", "x-request-id", "anthropic-request-id"
    };

    private NativeProtocolSupport() {
    }

    static Endpoint endpoint(Protocol protocol, String baseUrl, Credentials credentials) {
        return Endpoint.builder(protocol)
                .name("native-" + protocol.name().toLowerCase(Locale.ROOT))
                .baseUrl(baseUrl)
                .credentials(credentials)
                .build();
    }

    static OkHttpModelTransport transport(Configuration configuration) {
        return new OkHttpModelTransport(configuration.getOkHttpClient(),
                configuration.createRequestFactory());
    }

    /**
     * Deep-copy the native request into a wire body so mutating the snapshot
     * (stream flag, tool resolution) never reaches the caller's DTO.
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> snapshot(Object request) {
        return OBJECT_MAPPER.convertValue(request, Map.class);
    }

    static ObjectMapper mapper() {
        return OBJECT_MAPPER;
    }

    static WireRequest wire(Protocol protocol, String path, Map<String, Object> body, boolean stream) {
        return WireRequest.of(protocol, path, body, stream);
    }

    /**
     * Convert a request's local {@link StreamExecutionOptions} into the
     * transport-facing {@link CallExecutionOptions}. These controls are local
     * and are never serialized onto the wire.
     */
    static CallExecutionOptions execution(StreamExecutionOptions options) {
        if (options == null) {
            return CallExecutionOptions.defaults();
        }
        return CallExecutionOptions.builder()
                .firstTokenTimeoutMs(options.getFirstTokenTimeoutMs())
                .idleTimeoutMs(options.getIdleTimeoutMs())
                .maxRetries(options.getMaxRetries())
                .retryBackoffMs(options.getRetryBackoffMs())
                .build();
    }

    /**
     * Reuse the legacy OpenAI decoder so native HTTP errors keep their typed
     * shape. The request-id header (case-insensitive) is folded into the message
     * so the diagnostic is not lost.
     */
    static AiHttpException openAiHttpError(int statusCode, String bodyText, Map<String, String> headers) {
        AiHttpException decoded = HttpErrorDecoder.decode(syntheticResponse(statusCode, bodyText));
        String requestId = requestId(headers, OPENAI_REQUEST_ID_HEADERS);
        if (requestId == null) {
            return decoded;
        }
        return HttpErrorDecoder.decode(statusCode, decoded.getMessage() + " (request-id: " + requestId + ")");
    }

    static Map<String, String> headersOf(okhttp3.Response response) {
        Map<String, String> headers = new LinkedHashMap<String, String>();
        for (int i = 0; i < response.headers().size(); i++) {
            headers.put(response.headers().name(i), response.headers().value(i));
        }
        return headers;
    }

    /** Case-insensitive header lookup across the given candidate names. */
    static String requestId(Map<String, String> headers, String... names) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            for (String name : names) {
                if (name.equalsIgnoreCase(entry.getKey())) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    static String anthropicRequestId(Map<String, String> headers) {
        return requestId(headers, ANTHROPIC_REQUEST_ID_HEADERS);
    }

    private static okhttp3.Response syntheticResponse(int statusCode, String bodyText) {
        okhttp3.ResponseBody body = bodyText == null ? null
                : okhttp3.ResponseBody.create(bodyText, okhttp3.MediaType.get(Constants.APPLICATION_JSON));
        return new okhttp3.Response.Builder()
                .request(new okhttp3.Request.Builder().url("http://native-entry.invalid/").build())
                .protocol(okhttp3.Protocol.HTTP_1_1)
                .code(statusCode)
                .message("")
                .body(body)
                .build();
    }
}
