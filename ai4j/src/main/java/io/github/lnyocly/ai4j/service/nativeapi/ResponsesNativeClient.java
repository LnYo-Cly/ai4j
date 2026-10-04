package io.github.lnyocly.ai4j.service.nativeapi;

import io.github.lnyocly.ai4j.model.CallExecutionOptions;
import io.github.lnyocly.ai4j.model.Protocol;
import io.github.lnyocly.ai4j.platform.openai.response.entity.Response;
import io.github.lnyocly.ai4j.platform.openai.response.entity.ResponseRequest;
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
 * Protocol-native single-turn entry for the OpenAI Responses API.
 *
 * <p>A caller supplies only the base URL, the API key and a native
 * {@link ResponseRequest}. The protocol is fixed by this client type, so no
 * {@code PlatformType}, vendor service or {@code Endpoint}/{@code Credentials}/
 * {@code Profile} assembly is required; a shared {@link Configuration} is
 * optional. {@link #create} is blocking and returns the native {@link Response};
 * {@link #createStream} starts a non-blocking, cancellable native SSE call.</p>
 *
 * <p>Neither path executes client-side tools. The request's native fields -
 * including protocol API {@code metadata} and any {@code extraBody} protocol
 * extension - are transmitted as-is; the local {@code streamExecution} field is
 * {@code @JsonIgnore} and never reaches the wire. The legacy
 * {@code functions}/{@code mcpServices} helpers have no native resolution here
 * and are rejected before networking.</p>
 */
public final class ResponsesNativeClient {

    private static final String PATH = "v1/responses";

    private static final NativeErrorMapper ERRORS = new NativeErrorMapper() {
        @Override
        public RuntimeException fromHttp(int statusCode, String bodyText, Map<String, String> headers) {
            return NativeProtocolSupport.openAiHttpError(statusCode, bodyText, headers);
        }
    };

    private final Endpoint endpoint;
    private final OkHttpModelTransport transport;

    private ResponsesNativeClient(String baseUrl, String apiKey, Configuration configuration) {
        Objects.requireNonNull(baseUrl, "baseUrl is required");
        Objects.requireNonNull(apiKey, "apiKey is required");
        Objects.requireNonNull(configuration, "configuration is required");
        this.endpoint = NativeProtocolSupport.endpoint(
                Protocol.RESPONSES, baseUrl, Credentials.bearer(apiKey));
        this.transport = NativeProtocolSupport.transport(configuration);
    }

    /** Build a client over a default shared OkHttp stack. */
    public static ResponsesNativeClient of(String baseUrl, String apiKey) {
        return new ResponsesNativeClient(baseUrl, apiKey, new Configuration());
    }

    /** Build a client over the caller's shared OkHttp stack. */
    public static ResponsesNativeClient of(String baseUrl, String apiKey, Configuration configuration) {
        return new ResponsesNativeClient(baseUrl, apiKey, configuration);
    }

    /**
     * One blocking single-turn call.
     *
     * <p>The request is deep-snapshotted before dispatch, so the caller's
     * {@link ResponseRequest} (stream flag, tools, input) is never mutated.</p>
     */
    public Response create(ResponseRequest request) throws Exception {
        rejectUnsupportedHelpers(request);
        WireResponse response = transport.execute(
                TransportRequest.of(endpoint, wire(request, false), null));
        if (!response.isSuccessful()) {
            throw ERRORS.fromHttp(response.getStatusCode(), response.getBodyText(),
                    response.getHeaders());
        }
        return NativeProtocolSupport.mapper().readValue(response.getBodyText(), Response.class);
    }

    /**
     * Start one native SSE call; returns immediately with a cancellable handle.
     *
     * <p>Events are delivered raw to {@code listener} in wire order; a
     * {@link NativeResponsesStreamListener} receives them as typed
     * {@link NativeResponsesStreamEvent}s. The local {@code streamExecution}
     * options on the request are applied to this call.</p>
     */
    public NativeStreamCall createStream(ResponseRequest request, NativeSseListener listener) {
        Objects.requireNonNull(request, "request is required");
        return createStream(request, listener,
                NativeProtocolSupport.execution(request.getStreamExecution()));
    }

    /** Start one native SSE call with explicit local execution options. */
    public NativeStreamCall createStream(ResponseRequest request, NativeSseListener listener,
                                         CallExecutionOptions execution) {
        Objects.requireNonNull(listener, "listener is required");
        rejectUnsupportedHelpers(request);
        NativeStreamCallHandle call = new NativeStreamCallHandle(listener, ERRORS,
                NativeStreamSemantics.of(Protocol.RESPONSES));
        CallHandle handle = transport.stream(
                TransportRequest.of(endpoint, wire(request, true), execution), call);
        call.bind(handle);
        return call;
    }

    private WireRequest wire(ResponseRequest request, boolean stream) {
        Objects.requireNonNull(request, "request is required");
        Map<String, Object> body = NativeProtocolSupport.snapshot(request);
        body.put("stream", stream);
        return NativeProtocolSupport.wire(Protocol.RESPONSES, PATH, body, stream);
    }

    private static void rejectUnsupportedHelpers(ResponseRequest request) {
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
