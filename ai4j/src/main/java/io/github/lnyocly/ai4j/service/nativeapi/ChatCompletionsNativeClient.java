package io.github.lnyocly.ai4j.service.nativeapi;

import io.github.lnyocly.ai4j.model.CallExecutionOptions;
import io.github.lnyocly.ai4j.model.Protocol;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletion;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletionResponse;
import io.github.lnyocly.ai4j.protocol.Credentials;
import io.github.lnyocly.ai4j.protocol.Endpoint;
import io.github.lnyocly.ai4j.protocol.WireRequest;
import io.github.lnyocly.ai4j.protocol.WireResponse;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.transport.CallHandle;
import io.github.lnyocly.ai4j.transport.OkHttpModelTransport;
import io.github.lnyocly.ai4j.transport.TransportRequest;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Protocol-native single-turn entry for OpenAI Chat Completions.
 *
 * <p>A caller supplies only the base URL, the API key and a native
 * {@link ChatCompletion}. The protocol is fixed by this client type, so no
 * {@code PlatformType}, vendor service or {@code Endpoint}/{@code Credentials}/
 * {@code Profile} assembly is required; a shared {@link Configuration} is
 * optional. {@link #chatCompletion} is blocking and returns the native
 * {@link ChatCompletionResponse}; {@link #chatCompletionStream} starts a
 * non-blocking, cancellable native SSE call.</p>
 *
 * <p>Neither path executes client-side tools: a returned tool call is data for
 * the host to run. The native fields (including {@code tools} and
 * {@code extraBody}) are transmitted as-is. The legacy helper fields
 * {@code functions}/{@code mcpServices} have no native resolution here, so they
 * are rejected before networking instead of being silently dropped - pass
 * {@code tools} directly.</p>
 *
 * <p>The request's local {@code streamExecution} options (first-token/idle
 * timeout, retry budget) are applied locally and never serialized. Retries are
 * delegated to the shared engine, which only resends a side-effect-safe failure
 * that received a retryable HTTP status before any event.</p>
 */
public final class ChatCompletionsNativeClient {

    private static final String PATH = "v1/chat/completions";

    private static final NativeErrorMapper ERRORS = new NativeErrorMapper() {
        @Override
        public RuntimeException fromHttp(int statusCode, String bodyText, Map<String, String> headers) {
            return NativeProtocolSupport.openAiHttpError(statusCode, bodyText, headers);
        }
    };

    private final Endpoint endpoint;
    private final OkHttpModelTransport transport;

    private ChatCompletionsNativeClient(String baseUrl, String apiKey, Configuration configuration) {
        Objects.requireNonNull(baseUrl, "baseUrl is required");
        Objects.requireNonNull(apiKey, "apiKey is required");
        Objects.requireNonNull(configuration, "configuration is required");
        this.endpoint = NativeProtocolSupport.endpoint(
                Protocol.CHAT_COMPLETIONS, baseUrl, Credentials.bearer(apiKey));
        this.transport = NativeProtocolSupport.transport(configuration);
    }

    /** Build a client over a default shared OkHttp stack. */
    public static ChatCompletionsNativeClient of(String baseUrl, String apiKey) {
        return new ChatCompletionsNativeClient(baseUrl, apiKey, new Configuration());
    }

    /** Build a client over the caller's shared OkHttp stack. */
    public static ChatCompletionsNativeClient of(String baseUrl, String apiKey, Configuration configuration) {
        return new ChatCompletionsNativeClient(baseUrl, apiKey, configuration);
    }

    /**
     * One blocking single-turn call.
     *
     * <p>The request is deep-snapshotted before dispatch, so the caller's
     * {@link ChatCompletion} (stream flag, tools, messages) is never mutated.</p>
     */
    public ChatCompletionResponse chatCompletion(ChatCompletion request) throws Exception {
        rejectUnsupportedHelpers(request);
        WireResponse response = transport.execute(
                TransportRequest.of(endpoint, wire(request, false), null));
        if (!response.isSuccessful()) {
            throw ERRORS.fromHttp(response.getStatusCode(), response.getBodyText(),
                    response.getHeaders());
        }
        return NativeProtocolSupport.mapper().readValue(response.getBodyText(), ChatCompletionResponse.class);
    }

    /**
     * Start one native SSE call; returns immediately with a cancellable handle.
     *
     * <p>Events are delivered raw to {@code listener} in wire order; a
     * {@link NativeChatStreamListener} receives them as typed
     * {@link NativeChatStreamEvent}s. The local {@code streamExecution} options
     * on the request are applied to this call.</p>
     */
    public NativeStreamCall chatCompletionStream(ChatCompletion request, NativeSseListener listener) {
        Objects.requireNonNull(request, "request is required");
        return chatCompletionStream(request, listener,
                NativeProtocolSupport.execution(request.getStreamExecution()));
    }

    /** Start one native SSE call with explicit local execution options. */
    public NativeStreamCall chatCompletionStream(ChatCompletion request, NativeSseListener listener,
                                                 CallExecutionOptions execution) {
        Objects.requireNonNull(listener, "listener is required");
        rejectUnsupportedHelpers(request);
        NativeStreamCallHandle call = new NativeStreamCallHandle(listener, ERRORS,
                NativeStreamSemantics.of(Protocol.CHAT_COMPLETIONS, expectedChoices(request)));
        CallHandle handle = transport.stream(
                TransportRequest.of(endpoint, wire(request, true), execution), call);
        call.bind(handle);
        return call;
    }

    private WireRequest wire(ChatCompletion request, boolean stream) {
        Objects.requireNonNull(request, "request is required");
        Map<String, Object> body = NativeProtocolSupport.snapshot(request);
        body.put("stream", stream);
        return NativeProtocolSupport.wire(Protocol.CHAT_COMPLETIONS, PATH, body, stream);
    }

    /**
     * The request's expected choice count ({@code n}); a whole-call finish signal
     * must cover this many choices before a clean EOF can complete the stream.
     */
    private static int expectedChoices(ChatCompletion request) {
        Integer n = request.getN();
        return n == null || n < 1 ? 1 : n;
    }

    private static void rejectUnsupportedHelpers(ChatCompletion request) {
        Objects.requireNonNull(request, "request is required");
        List<String> functions = request.getFunctions();
        if (functions != null && !functions.isEmpty()) {
            throw new IllegalArgumentException(
                    "functions is a legacy helper without a native resolution on this entry; "
                            + "pass tools directly");
        }
        List<String> mcpServices = request.getMcpServices();
        if (mcpServices != null && !mcpServices.isEmpty()) {
            throw new IllegalArgumentException(
                    "mcpServices is a legacy helper without a native resolution on this entry; "
                            + "pass tools directly");
        }
    }
}
