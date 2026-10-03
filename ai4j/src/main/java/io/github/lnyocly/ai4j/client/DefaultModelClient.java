package io.github.lnyocly.ai4j.client;

import io.github.lnyocly.ai4j.model.CallExecutionOptions;
import io.github.lnyocly.ai4j.model.ModelCallStatus;
import io.github.lnyocly.ai4j.model.ModelEvent;
import io.github.lnyocly.ai4j.model.ModelEventListener;
import io.github.lnyocly.ai4j.model.ModelEventType;
import io.github.lnyocly.ai4j.model.ModelRequest;
import io.github.lnyocly.ai4j.model.ModelResult;
import io.github.lnyocly.ai4j.model.Protocol;
import io.github.lnyocly.ai4j.model.ToolDeclaration;
import io.github.lnyocly.ai4j.model.ToolKind;
import io.github.lnyocly.ai4j.protocol.Capabilities;
import io.github.lnyocly.ai4j.protocol.Capability;
import io.github.lnyocly.ai4j.protocol.Endpoint;
import io.github.lnyocly.ai4j.protocol.EndpointPolicy;
import io.github.lnyocly.ai4j.protocol.ProtocolCodec;
import io.github.lnyocly.ai4j.protocol.ProtocolDescriptor;
import io.github.lnyocly.ai4j.protocol.WireRequest;
import io.github.lnyocly.ai4j.protocol.WireResponse;
import io.github.lnyocly.ai4j.transport.ModelTransport;
import io.github.lnyocly.ai4j.transport.TransportRequest;

import java.util.Objects;

/**
 * Default assembly: endpoint + descriptor + codec + transport, with no vendor
 * branch (design A1).
 */
final class DefaultModelClient implements ModelClient {

    private final Endpoint endpoint;
    private final ProtocolDescriptor descriptor;
    private final ProtocolCodec codec;
    private final EndpointPolicy policy;
    private final ModelTransport transport;

    DefaultModelClient(Endpoint endpoint, ProtocolDescriptor descriptor, ModelTransport transport) {
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint is required");
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor is required");
        this.codec = descriptor.getCodec();
        this.policy = EndpointPolicy.resolve(endpoint, descriptor);
        this.transport = Objects.requireNonNull(transport, "transport is required");
    }

    @Override
    public Endpoint endpoint() {
        return endpoint;
    }

    @Override
    public Capabilities capabilities() {
        return policy.getCapabilities();
    }

    @Override
    public void require(Capability capability) {
        policy.getCapabilities().require(capability);
    }

    @Override
    public ModelResult create(ModelRequest request) throws ModelCallException {
        Objects.requireNonNull(request, "request is required");
        Protocol protocol = resolveProtocol(request);
        WireRequest wire = codec.encode(request, policy);
        ModelResult.Builder result;
        try {
            WireResponse response = transport.execute(
                    TransportRequest.of(endpoint, wire, request.getExecution()));
            if (!response.isSuccessful()) {
                throw ErrorMapping.fromResponse(protocol, response);
            }
            result = toBuilder(codec.decode(response));
        } catch (ModelCallException e) {
            throw e;
        } catch (Exception e) {
            throw ErrorMapping.fromFailure(protocol, e);
        }
        result.warnings(wire.getWarnings());
        return result.build();
    }

    private static ModelResult.Builder toBuilder(ModelResult result) {
        return ModelResult.builder()
                .items(result.getItems())
                .usage(result.getUsage())
                .stopReason(result.getStopReason())
                .responseId(result.getResponseId())
                .warnings(result.getWarnings())
                .raw(result.getRaw());
    }

    @Override
    public ModelCall createStream(ModelRequest request, ModelEventListener listener) {
        Objects.requireNonNull(request, "request is required");
        return new DefaultModelCall(request, listener);
    }

    private Protocol resolveProtocol(ModelRequest request) {
        return request.getProtocol() == null ? endpoint.getProtocol() : request.getProtocol();
    }

    /** Streaming call: owns the channel, aggregates events, exposes cancel/await. */
    private final class DefaultModelCall implements ModelCall {

        private final ModelRequest request;
        private final ModelEventListener listener;
        private final Protocol protocol;
        private final StreamAggregator aggregator = new StreamAggregator();
        private final java.util.concurrent.CountDownLatch settled =
                new java.util.concurrent.CountDownLatch(1);
        private final java.util.concurrent.atomic.AtomicReference<ModelCallStatus> status =
                new java.util.concurrent.atomic.AtomicReference<ModelCallStatus>(ModelCallStatus.RUNNING);
        private final java.util.concurrent.atomic.AtomicReference<ModelCallException> failure =
                new java.util.concurrent.atomic.AtomicReference<ModelCallException>();
        private final java.util.concurrent.atomic.AtomicReference<io.github.lnyocly.ai4j.transport.CallHandle>
                handle = new java.util.concurrent.atomic.AtomicReference<io.github.lnyocly.ai4j.transport.CallHandle>();
        private final java.util.concurrent.atomic.AtomicBoolean cancelled =
                new java.util.concurrent.atomic.AtomicBoolean();
        private final java.util.concurrent.atomic.AtomicBoolean doneDispatched =
                new java.util.concurrent.atomic.AtomicBoolean();

        DefaultModelCall(ModelRequest request, ModelEventListener listener) {
            this.request = request;
            this.listener = listener;
            this.protocol = resolveProtocol(request);
            start();
        }

        private void start() {
            WireRequest wire = codec.encode(request, policy);
            aggregator.addWarnings(wire.getWarnings());
            CallExecutionOptions execution = effectiveExecution(request);
            TransportRequest transportRequest = TransportRequest.of(endpoint, wire, execution);
            io.github.lnyocly.ai4j.transport.SseChannel channel =
                    new io.github.lnyocly.ai4j.transport.SseChannel() {
                        @Override
                        public void onOpen() {
                        }

                        @Override
                        public void onEvent(io.github.lnyocly.ai4j.protocol.RawSseEvent event) {
                            if (cancelled.get()) {
                                return;
                            }
                            ModelEvent modelEvent;
                            try {
                                modelEvent = codec.decodeEvent(event);
                            } catch (RuntimeException parseError) {
                                modelEvent = ModelEvent.builder(protocol, ModelEventType.OPAQUE)
                                        .itemType("decode_error")
                                        .build();
                            }
                            if (modelEvent == null) {
                                return;
                            }
                            aggregator.accept(modelEvent);
                            if (modelEvent.getType() == ModelEventType.ERROR) {
                                io.github.lnyocly.ai4j.model.ModelCallExceptionMarker marker =
                                        modelEvent.getFailure();
                                if (marker != null) {
                                    failure.compareAndSet(null, ModelCallException.fromMarker(marker));
                                }
                                settle(ModelCallStatus.FAILED);
                                dispatch(modelEvent);
                                return;
                            }
                            if (modelEvent.getType() == ModelEventType.DONE) {
                                settle(ModelCallStatus.COMPLETED);
                                dispatchDone();
                                return;
                            }
                            dispatch(modelEvent);
                        }

                        @Override
                        public void onClosed() {
                            if (cancelled.get() || status.get() == ModelCallStatus.FAILED) {
                                return;
                            }
                            settle(ModelCallStatus.COMPLETED);
                            dispatchDone();
                        }

                        @Override
                        public void onFailure(Throwable failureCause, okhttp3.Response response) {
                            onFailure(failureCause, response, null);
                        }

                        @Override
                        public void onFailure(Throwable failureCause, okhttp3.Response response,
                                              String responseBody) {
                            if (cancelled.get()) {
                                settle(ModelCallStatus.CANCELLED);
                                return;
                            }
                            ModelCallException mapped = response == null
                                    ? ErrorMapping.fromFailure(protocol, failureCause)
                                    : ErrorMapping.fromResponse(protocol,
                                            wireResponseOf(response, responseBody));
                            failure.compareAndSet(null, mapped);
                            settle(ModelCallStatus.FAILED);
                            dispatch(ModelEvent.builder(protocol, ModelEventType.ERROR)
                                    .failure(io.github.lnyocly.ai4j.model.ModelCallExceptionMarker
                                            .builder(protocol)
                                            .message(mapped.getMessage())
                                            .httpStatus(mapped.getHttpStatus())
                                            .providerErrorType(mapped.getProviderErrorType())
                                            .providerRequestId(mapped.getProviderRequestId())
                                            .retryable(mapped.isRetryable())
                                            .rawSnippet(mapped.getRawSnippet())
                                            .status(mapped.getStatus())
                                            .build())
                                    .build());
                        }

                        @Override
                        public void onRetrying(Throwable failureCause, int attempt, int maxAttempts) {
                            dispatch(ModelEvent.builder(protocol, ModelEventType.RETRYING)
                                    .retry(attempt, maxAttempts)
                                    .build());
                        }
                    };
            try {
                handle.set(transport.stream(transportRequest, channel));
            } catch (Exception startFailure) {
                failure.set(ErrorMapping.fromFailure(protocol, startFailure));
                settle(ModelCallStatus.FAILED);
            }
        }

        /**
         * Retry budget is zeroed when the request cannot be safely replayed or declares a
         * provider-hosted tool (design 8.3 F5).
         */
        private CallExecutionOptions effectiveExecution(ModelRequest request) {
            CallExecutionOptions base = request.getExecution() == null
                    ? CallExecutionOptions.defaults() : request.getExecution();
            if (request.isReplayable() && !declaresHostedTool(request)) {
                return base;
            }
            return CallExecutionOptions.builder()
                    .firstTokenTimeoutMs(base.getFirstTokenTimeoutMs())
                    .idleTimeoutMs(base.getIdleTimeoutMs())
                    .maxRetries(0)
                    .retryBackoffMs(base.getRetryBackoffMs())
                    .retryEnabled(base.isRetryEnabled())
                    .build();
        }

        private boolean declaresHostedTool(ModelRequest request) {
            for (ToolDeclaration tool : request.getTools()) {
                if (tool.getKind() == ToolKind.HOSTED) {
                    return true;
                }
            }
            return false;
        }

        /** Dispatch the single terminal event exactly once, before {@code await()} returns. */
        private void dispatchDone() {
            if (doneDispatched.compareAndSet(false, true)) {
                dispatch(ModelEvent.builder(protocol, ModelEventType.DONE)
                        .usage(aggregator.usage())
                        .build());
            }
        }

        private void dispatch(ModelEvent event) {
            if (listener != null) {
                listener.onEvent(event);
            }
        }

        private void settle(ModelCallStatus newStatus) {
            if (status.compareAndSet(ModelCallStatus.RUNNING, newStatus)) {
                settled.countDown();
            }
        }

        @Override
        public void cancel() {
            if (cancelled.compareAndSet(false, true)) {
                io.github.lnyocly.ai4j.transport.CallHandle active = handle.get();
                if (active != null) {
                    active.cancel();
                }
                settle(ModelCallStatus.CANCELLED);
            }
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get();
        }

        @Override
        public ModelResult await() throws ModelCallException {
            try {
                settled.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                cancel();
                throw new ModelCallException(protocol, 0, "model call interrupted", null,
                        ModelCallStatus.CANCELLED);
            }
            ModelCallException error = failure.get();
            if (error != null) {
                throw error;
            }
            return aggregator.result(protocol);
        }

        @Override
        public ModelCallStatus status() {
            return status.get();
        }

        private WireResponse wireResponseOf(okhttp3.Response response, String responseBody) {
            String bodyText = responseBody;
            if (bodyText == null) {
                try {
                    if (response.body() != null) {
                        bodyText = response.body().string();
                    }
                } catch (Exception ignored) {
                    bodyText = null;
                }
            }
            java.util.Map<String, String> headers = new java.util.LinkedHashMap<String, String>();
            for (int i = 0; i < response.headers().size(); i++) {
                headers.put(response.headers().name(i), response.headers().value(i));
            }
            return WireResponse.of(protocol, response.code(), bodyText, headers);
        }
    }
}
