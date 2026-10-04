package io.github.lnyocly.ai4j.service.nativeapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lnyocly.ai4j.constant.Constants;
import io.github.lnyocly.ai4j.exception.AiHttpException;
import io.github.lnyocly.ai4j.exception.HttpErrorDecoder;
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

    /** Reuse the legacy OpenAI decoder so native HTTP errors keep their typed shape. */
    static AiHttpException openAiHttpError(int statusCode, String bodyText) {
        okhttp3.ResponseBody body = bodyText == null ? null
                : okhttp3.ResponseBody.create(bodyText, okhttp3.MediaType.get(Constants.APPLICATION_JSON));
        okhttp3.Response synthetic = new okhttp3.Response.Builder()
                .request(new okhttp3.Request.Builder().url("http://native-entry.invalid/").build())
                .protocol(okhttp3.Protocol.HTTP_1_1)
                .code(statusCode)
                .message("")
                .body(body)
                .build();
        return HttpErrorDecoder.decode(synthetic);
    }

    static Map<String, String> headersOf(okhttp3.Response response) {
        Map<String, String> headers = new LinkedHashMap<String, String>();
        for (int i = 0; i < response.headers().size(); i++) {
            headers.put(response.headers().name(i), response.headers().value(i));
        }
        return headers;
    }
}
