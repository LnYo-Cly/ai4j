package io.github.lnyocly.ai4j.service.nativeapi;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import io.github.lnyocly.ai4j.exception.AiHttpException;
import io.github.lnyocly.ai4j.exception.HttpErrorDecoder;
import io.github.lnyocly.ai4j.model.CallExecutionOptions;
import io.github.lnyocly.ai4j.model.Protocol;
import io.github.lnyocly.ai4j.protocol.Credentials;
import io.github.lnyocly.ai4j.protocol.Endpoint;
import io.github.lnyocly.ai4j.protocol.WireRequest;
import io.github.lnyocly.ai4j.protocol.WireResponse;
import io.github.lnyocly.ai4j.protocol.gemini.GeminiCodec;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.transport.CallHandle;
import io.github.lnyocly.ai4j.transport.OkHttpModelTransport;
import io.github.lnyocly.ai4j.transport.TransportRequest;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Protocol-native single-turn entry for the Google Gemini {@code generateContent} API.
 *
 * <p>Uses {@code x-goog-api-key} authentication by default and supports Bearer
 * authentication via {@link #ofBearer(String, String)} or a custom {@link Endpoint}.
 * Sync calls target {@code /v1beta/models/{model}:generateContent} and streaming calls
 * target {@code /v1beta/models/{model}:streamGenerateContent?alt=sse}.</p>
 */
public final class GeminiNativeClient {

    public static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com";
    private static final String[] GOOGLE_REQUEST_ID_HEADERS = {
            "x-goog-request-id", "x-request-id", "request-id"
    };
    private static final int MAX_RAW_BODY_CHARS = 500;
    private static final TypeReference<Map<String, Object>> MAP_TYPE =
            new TypeReference<Map<String, Object>>() {
            };

    private static final NativeErrorMapper ERRORS = new NativeErrorMapper() {
        @Override
        public RuntimeException fromHttp(int statusCode, String bodyText, Map<String, String> headers) {
            return geminiHttpError(statusCode, bodyText, headers);
        }
    };

    private final Endpoint endpoint;
    private final OkHttpModelTransport transport;

    private GeminiNativeClient(Endpoint endpoint, Configuration configuration) {
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint is required");
        Objects.requireNonNull(configuration, "configuration is required");
        this.transport = NativeProtocolSupport.transport(configuration);
    }

    /** Build a Gemini native client against the official Google endpoint using {@code x-goog-api-key}. */
    public static GeminiNativeClient of(String apiKey) {
        return of(DEFAULT_BASE_URL, apiKey, new Configuration());
    }

    /** Build a client over a default shared OkHttp stack using {@code x-goog-api-key}. */
    public static GeminiNativeClient of(String baseUrl, String apiKey) {
        return of(baseUrl, apiKey, new Configuration());
    }

    /** Build a client over the caller's shared OkHttp stack using {@code x-goog-api-key}. */
    public static GeminiNativeClient of(String baseUrl, String apiKey, Configuration configuration) {
        Objects.requireNonNull(baseUrl, "baseUrl is required");
        Objects.requireNonNull(apiKey, "apiKey is required");
        Endpoint endpoint = NativeProtocolSupport.endpoint(
                Protocol.GEMINI, baseUrl, Credentials.header("x-goog-api-key", apiKey));
        return new GeminiNativeClient(endpoint, configuration);
    }

    /** Build a client using Bearer token authentication (e.g. OAuth / Vertex proxy). */
    public static GeminiNativeClient ofBearer(String baseUrl, String bearerToken) {
        return ofBearer(baseUrl, bearerToken, new Configuration());
    }

    /** Build a client using Bearer token authentication over a shared {@link Configuration}. */
    public static GeminiNativeClient ofBearer(String baseUrl, String bearerToken, Configuration configuration) {
        Objects.requireNonNull(baseUrl, "baseUrl is required");
        Objects.requireNonNull(bearerToken, "bearerToken is required");
        Endpoint endpoint = NativeProtocolSupport.endpoint(
                Protocol.GEMINI, baseUrl, Credentials.bearer(bearerToken));
        return new GeminiNativeClient(endpoint, configuration);
    }

    /** Build a client from an explicit {@link Endpoint} and {@link Configuration}. */
    public static GeminiNativeClient of(Endpoint endpoint, Configuration configuration) {
        return new GeminiNativeClient(endpoint, configuration);
    }

    /** Execute a blocking {@code generateContent} request. */
    public Map<String, Object> generateContent(String model, Map<String, Object> request) throws Exception {
        WireResponse response = transport.execute(
                TransportRequest.of(endpoint, wire(model, request, false), null));
        if (!response.isSuccessful()) {
            throw ERRORS.fromHttp(response.getStatusCode(), response.getBodyText(), response.getHeaders());
        }
        if (response.getBodyText() == null || response.getBodyText().trim().isEmpty()) {
            return new LinkedHashMap<String, Object>();
        }
        Map<String, Object> parsed = NativeProtocolSupport.mapper().readValue(response.getBodyText(), MAP_TYPE);
        return parsed == null ? new LinkedHashMap<String, Object>() : parsed;
    }

    /** Start a non-blocking, cancellable {@code streamGenerateContent?alt=sse} call. */
    public NativeStreamCall streamGenerateContent(String model, Map<String, Object> request,
                                                  NativeSseListener listener) {
        return streamGenerateContent(model, request, listener, CallExecutionOptions.defaults());
    }

    /** Start a streaming call with explicit local {@link CallExecutionOptions}. */
    public NativeStreamCall streamGenerateContent(String model, Map<String, Object> request,
                                                  NativeSseListener listener, CallExecutionOptions execution) {
        Objects.requireNonNull(listener, "listener is required");
        NativeStreamCallHandle call = new NativeStreamCallHandle(listener, ERRORS,
                NativeStreamSemantics.of(Protocol.GEMINI));
        CallHandle handle = transport.stream(
                TransportRequest.of(endpoint, wire(model, request, true), execution), call);
        call.bind(handle);
        return call;
    }

    private WireRequest wire(String model, Map<String, Object> request, boolean stream) {
        if (model == null || model.trim().isEmpty()) {
            throw new IllegalArgumentException("model is required");
        }
        Objects.requireNonNull(request, "request is required");
        Map<String, Object> body = NativeProtocolSupport.snapshot(request);
        String path = GeminiCodec.resolvePath(endpoint.getEndpointPath(), model, stream);
        return NativeProtocolSupport.wire(Protocol.GEMINI, path, body, stream);
    }

    private static AiHttpException geminiHttpError(int statusCode, String bodyText,
                                                   Map<String, String> headers) {
        String status = null;
        String message = null;
        if (bodyText != null) {
            try {
                JsonNode root = NativeProtocolSupport.mapper().readTree(bodyText);
                if (root != null && root.isArray() && root.size() > 0) {
                    root = root.get(0);
                }
                if (root != null) {
                    JsonNode error = root.path("error");
                    status = text(error, "status");
                    message = text(error, "message");
                    if (message == null) {
                        message = text(root, "message");
                    }
                }
            } catch (java.io.IOException ignored) {
                // fall back to raw snippet
            }
        }
        if (message == null) {
            message = rawBodySnippet(bodyText);
        }
        StringBuilder formatted = new StringBuilder();
        if (status != null) {
            formatted.append("[").append(status).append("] ");
        }
        if (message != null) {
            formatted.append(message);
        } else {
            formatted.append("HTTP ").append(statusCode);
        }
        String requestId = NativeProtocolSupport.requestId(headers, GOOGLE_REQUEST_ID_HEADERS);
        if (requestId != null) {
            formatted.append(" (request-id: ").append(requestId).append(")");
        }
        return HttpErrorDecoder.decode(statusCode, formatted.toString());
    }

    private static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText(null);
        return text == null || text.trim().isEmpty() ? null : text;
    }

    private static String rawBodySnippet(String bodyText) {
        if (bodyText == null) {
            return null;
        }
        String trimmed = bodyText.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= MAX_RAW_BODY_CHARS
                ? trimmed
                : trimmed.substring(0, MAX_RAW_BODY_CHARS) + "... (truncated)";
    }
}
