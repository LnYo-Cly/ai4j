package io.github.lnyocly.ai4j.service.nativeapi;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.lnyocly.ai4j.model.Protocol;
import io.github.lnyocly.ai4j.platform.anthropic.chat.entity.AnthropicChatCompletion;
import io.github.lnyocly.ai4j.platform.anthropic.chat.entity.AnthropicChatCompletionResponse;
import io.github.lnyocly.ai4j.platform.anthropic.errors.AnthropicApiException;
import io.github.lnyocly.ai4j.protocol.Credentials;
import io.github.lnyocly.ai4j.protocol.Endpoint;
import io.github.lnyocly.ai4j.protocol.WireRequest;
import io.github.lnyocly.ai4j.protocol.WireResponse;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.transport.CallHandle;
import io.github.lnyocly.ai4j.transport.OkHttpModelTransport;
import io.github.lnyocly.ai4j.transport.TransportRequest;

import java.util.Map;
import java.util.Objects;

/**
 * Protocol-native single-turn entry for the Anthropic Messages API.
 *
 * <p>A caller supplies only the base URL, the API key and a native
 * {@link AnthropicChatCompletion}. The protocol is fixed by this client type, so
 * no {@code PlatformType}, vendor service or {@code Endpoint}/{@code Credentials}/
 * {@code Profile} assembly is required; a shared {@link Configuration} is
 * optional. {@link #messages} is blocking and returns the native
 * {@link AnthropicChatCompletionResponse}; {@link #messagesStream} starts a
 * non-blocking, cancellable native SSE call. Authentication is the native
 * {@code x-api-key} header plus {@code anthropic-version}.</p>
 *
 * <p>Neither path executes client-side tools. The request's native fields
 * (including {@code tools}, {@code system} and {@code extraBody} protocol
 * extensions such as {@code thinking}) are transmitted as-is.</p>
 */
public final class MessagesNativeClient {

    private static final String PATH = "v1/messages";

    private static final NativeErrorMapper ERRORS = new NativeErrorMapper() {
        @Override
        public RuntimeException fromHttp(int statusCode, String bodyText, Map<String, String> headers) {
            return anthropicHttpError(statusCode, bodyText, headers);
        }
    };

    private final Endpoint endpoint;
    private final OkHttpModelTransport transport;

    private MessagesNativeClient(String baseUrl, String apiKey, Configuration configuration) {
        Objects.requireNonNull(baseUrl, "baseUrl is required");
        Objects.requireNonNull(apiKey, "apiKey is required");
        Objects.requireNonNull(configuration, "configuration is required");
        this.endpoint = NativeProtocolSupport.endpoint(
                Protocol.MESSAGES, baseUrl, Credentials.header("x-api-key", apiKey));
        this.transport = NativeProtocolSupport.transport(configuration);
    }

    /** Build a client over a default shared OkHttp stack. */
    public static MessagesNativeClient of(String baseUrl, String apiKey) {
        return new MessagesNativeClient(baseUrl, apiKey, new Configuration());
    }

    /** Build a client over the caller's shared OkHttp stack. */
    public static MessagesNativeClient of(String baseUrl, String apiKey, Configuration configuration) {
        return new MessagesNativeClient(baseUrl, apiKey, configuration);
    }

    /**
     * One blocking single-turn call.
     *
     * <p>The request is deep-snapshotted before dispatch, so the caller's
     * {@link AnthropicChatCompletion} (stream flag, tools, messages) is never
     * mutated.</p>
     */
    public AnthropicChatCompletionResponse messages(AnthropicChatCompletion request) throws Exception {
        WireResponse response = transport.execute(
                TransportRequest.of(endpoint, wire(request, false), null));
        if (!response.isSuccessful()) {
            throw ERRORS.fromHttp(response.getStatusCode(), response.getBodyText(),
                    response.getHeaders());
        }
        return NativeProtocolSupport.mapper()
                .readValue(response.getBodyText(), AnthropicChatCompletionResponse.class);
    }

    /**
     * Start one native SSE call; returns immediately with a cancellable handle.
     * Events are delivered raw to {@code listener} in wire order.
     */
    public NativeStreamCall messagesStream(AnthropicChatCompletion request, NativeSseListener listener) {
        Objects.requireNonNull(listener, "listener is required");
        NativeStreamCallHandle call = new NativeStreamCallHandle(listener, ERRORS);
        CallHandle handle = transport.stream(
                TransportRequest.of(endpoint, wire(request, true), null), call);
        call.bind(handle);
        return call;
    }

    private WireRequest wire(AnthropicChatCompletion request, boolean stream) {
        Objects.requireNonNull(request, "request is required");
        Map<String, Object> body = NativeProtocolSupport.snapshot(request);
        body.put("stream", stream);
        return NativeProtocolSupport.wire(Protocol.MESSAGES, PATH, body, stream);
    }

    private static AnthropicApiException anthropicHttpError(int statusCode, String bodyText,
                                                            Map<String, String> headers) {
        String type = null;
        String message = null;
        if (bodyText != null) {
            try {
                JsonNode node = NativeProtocolSupport.mapper().readTree(bodyText);
                JsonNode error = node.path("error");
                type = error.path("type").asText(null);
                String errorMessage = error.path("message").asText(null);
                message = errorMessage != null ? errorMessage : node.path("message").asText(null);
            } catch (java.io.IOException ignored) {
                // keep the HTTP status even when the body is not the expected JSON shape
            }
        }
        String requestId = headers == null ? null : headers.get("request-id");
        return AnthropicApiException.of(statusCode, type, message, requestId);
    }
}
