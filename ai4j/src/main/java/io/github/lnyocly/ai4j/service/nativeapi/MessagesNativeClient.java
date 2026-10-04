package io.github.lnyocly.ai4j.service.nativeapi;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.lnyocly.ai4j.model.CallExecutionOptions;
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
 * extensions such as {@code thinking}) are transmitted as-is. The Anthropic DTO
 * has no local {@code streamExecution} field, so stream timeouts are supplied
 * through the explicit {@link CallExecutionOptions} overload.</p>
 */
public final class MessagesNativeClient {

    private static final String PATH = "v1/messages";
    private static final int MAX_RAW_BODY_CHARS = 500;

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
     *
     * <p>Events are delivered raw to {@code listener} in wire order; a
     * {@link NativeMessagesStreamListener} receives them as typed
     * {@link NativeMessagesStreamEvent}s.</p>
     */
    public NativeStreamCall messagesStream(AnthropicChatCompletion request, NativeSseListener listener) {
        return messagesStream(request, listener, CallExecutionOptions.defaults());
    }

    /** Start one native SSE call with explicit local execution options. */
    public NativeStreamCall messagesStream(AnthropicChatCompletion request, NativeSseListener listener,
                                           CallExecutionOptions execution) {
        Objects.requireNonNull(listener, "listener is required");
        NativeStreamCallHandle call = new NativeStreamCallHandle(listener, ERRORS,
                NativeStreamSemantics.of(Protocol.MESSAGES));
        CallHandle handle = transport.stream(
                TransportRequest.of(endpoint, wire(request, true), execution), call);
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
                type = text(error, "type");
                message = text(error, "message");
                if (message == null) {
                    message = text(node, "message");
                }
            } catch (java.io.IOException ignored) {
                // keep the HTTP status even when the body is not the expected JSON shape
            }
        }
        if (message == null) {
            message = rawBodySnippet(bodyText);
        }
        String requestId = NativeProtocolSupport.anthropicRequestId(headers);
        return AnthropicApiException.of(statusCode, type, message, requestId);
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
