package io.github.lnyocly.ai4j.transport;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lnyocly.ai4j.exception.Ai4jException;
import io.github.lnyocly.ai4j.model.Protocol;
import io.github.lnyocly.ai4j.network.UrlUtils;
import io.github.lnyocly.ai4j.protocol.Endpoint;
import io.github.lnyocly.ai4j.protocol.WireRequest;
import io.github.lnyocly.ai4j.protocol.WireResponse;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Shared OkHttp transport for the new chain (design C4).
 *
 * <p>Reuses the caller-supplied {@code OkHttpClient} and
 * {@code EventSource.Factory} from {@code Configuration}; no second HTTP stack
 * is introduced. Streaming retry/timeout/cancel semantics are delegated to
 * {@code StreamExecutionSupport} through {@link StreamBridgeListener}.</p>
 */
public final class OkHttpModelTransport implements ModelTransport {

    private static final MediaType JSON_MEDIA_TYPE =
            MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient okHttpClient;
    private final okhttp3.sse.EventSource.Factory eventSourceFactory;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OkHttpModelTransport(OkHttpClient okHttpClient,
                                okhttp3.sse.EventSource.Factory eventSourceFactory) {
        if (okHttpClient == null) {
            throw new IllegalArgumentException("okHttpClient is required");
        }
        if (eventSourceFactory == null) {
            throw new IllegalArgumentException("eventSourceFactory is required");
        }
        this.okHttpClient = okHttpClient;
        this.eventSourceFactory = eventSourceFactory;
    }

    @Override
    public WireResponse execute(TransportRequest request) throws Exception {
        Request httpRequest = buildRequest(request, false);
        final Response response;
        try {
            response = okHttpClient.newCall(httpRequest).execute();
        } catch (IOException e) {
            throw new ModelTransportIOException("transport failure before a response was received", e);
        }
        try {
            ResponseBody body = response.body();
            return WireResponse.of(resolveProtocol(request), response.code(),
                    body == null ? null : body.string(), headersOf(response));
        } finally {
            response.close();
        }
    }

    @Override
    public CallHandle stream(TransportRequest request, SseChannel channel) {
        StreamingHandle handle = new StreamingHandle(request, channel);
        handle.start();
        return handle;
    }

    private Request buildRequest(TransportRequest request, boolean stream) {
        WireRequest wireRequest = request.getWireRequest();
        String baseUrl = request.getEndpoint().getBaseUrl();
        String path = wireRequest.getPath();
        String url = path == null || path.isEmpty() ? baseUrl : UrlUtils.concatUrl(baseUrl, path);
        String body;
        try {
            body = objectMapper.writeValueAsString(wireRequest.getBody());
        } catch (Exception e) {
            throw new Ai4jException("unable to serialize wire request body", e);
        }
        Request.Builder builder = new Request.Builder().url(url);
        for (Map.Entry<String, String> header : HttpHeaders.forRequest(request, stream).entrySet()) {
            builder.header(header.getKey(), header.getValue());
        }
        if ("GET".equalsIgnoreCase(wireRequest.getMethod())) {
            builder.get();
        } else {
            builder.method(wireRequest.getMethod(), RequestBody.create(body, JSON_MEDIA_TYPE));
        }
        return builder.build();
    }

    private static Protocol resolveProtocol(TransportRequest request) {
        Protocol protocol = request.getWireRequest().getProtocol();
        return protocol == null ? request.getEndpoint().getProtocol() : protocol;
    }

    private static Map<String, String> headersOf(Response response) {
        Map<String, String> headers = new LinkedHashMap<String, String>();
        for (int i = 0; i < response.headers().size(); i++) {
            headers.put(response.headers().name(i), response.headers().value(i));
        }
        return headers;
    }

    /** Streaming handle: one attempt per {@code StreamExecutionSupport} run. */
    private final class StreamingHandle implements CallHandle {

        private final TransportRequest request;
        private final SseChannel channel;
        private final CountDownLatch completion = new CountDownLatch(1);
        private final AtomicReference<StreamBridgeListener> listenerRef =
                new AtomicReference<StreamBridgeListener>();
        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        StreamingHandle(TransportRequest request, SseChannel channel) {
            this.request = request;
            this.channel = channel;
        }

        /**
         * Run the streaming attempt on a daemon worker so the returned handle is
         * usable immediately: the caller can {@code await()} on one thread while
         * another calls {@code cancel()} (design 8.4).
         */
        void start() {
            Thread worker = new Thread(new Runnable() {
                @Override
                public void run() {
                    runStream();
                }
            }, "ai4j-model-stream");
            worker.setDaemon(true);
            worker.start();
        }

        private void runStream() {
            try {
                io.github.lnyocly.ai4j.listener.StreamExecutionOptions options =
                        io.github.lnyocly.ai4j.listener.StreamExecutionOptions.builder()
                                .firstTokenTimeoutMs(request.getExecution().getFirstTokenTimeoutMs())
                                .idleTimeoutMs(request.getExecution().getIdleTimeoutMs())
                                .maxRetries(request.getExecution().isRetryEnabled()
                                        ? request.getExecution().getMaxRetries() : 0)
                                .retryBackoffMs(request.getExecution().getRetryBackoffMs())
                                .build();
                StreamBridgeListener listener = new StreamBridgeListener(channel);
                listenerRef.set(listener);
                io.github.lnyocly.ai4j.listener.StreamExecutionSupport.execute(
                        listener, options, () -> {
                            Request httpRequest = buildRequest(request, true);
                            eventSourceFactory.newEventSource(httpRequest, listener);
                        });
            } catch (Throwable failure) {
                channel.onFailure(failure, null);
            } finally {
                completion.countDown();
            }
        }

        @Override
        public void cancel() {
            if (cancelled.compareAndSet(false, true)) {
                StreamBridgeListener listener = listenerRef.get();
                if (listener != null) {
                    listener.cancelStream();
                }
            }
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get();
        }

        @Override
        public void await() throws InterruptedException {
            completion.await();
        }
    }

    /** Transport-level failure before any HTTP response existed (design 8.5 candidate). */
    public static final class ModelTransportIOException extends Ai4jException {

        public ModelTransportIOException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
