package io.github.lnyocly.ai4j.transport;

import io.github.lnyocly.ai4j.model.CallExecutionOptions;
import io.github.lnyocly.ai4j.protocol.Credentials;
import io.github.lnyocly.ai4j.protocol.Endpoint;
import io.github.lnyocly.ai4j.protocol.WireRequest;

import java.util.Objects;

/**
 * Everything the transport needs for one HTTP attempt: where to send it, with
 * which credentials, and under which execution budget.
 */
public final class TransportRequest {

    private final Endpoint endpoint;
    private final WireRequest wireRequest;
    private final CallExecutionOptions execution;

    private TransportRequest(Endpoint endpoint, WireRequest wireRequest, CallExecutionOptions execution) {
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint is required");
        this.wireRequest = Objects.requireNonNull(wireRequest, "wireRequest is required");
        this.execution = execution == null ? CallExecutionOptions.defaults() : execution;
    }

    public static TransportRequest of(Endpoint endpoint, WireRequest wireRequest,
                                      CallExecutionOptions execution) {
        return new TransportRequest(endpoint, wireRequest, execution);
    }

    public Endpoint getEndpoint() {
        return endpoint;
    }

    public WireRequest getWireRequest() {
        return wireRequest;
    }

    public CallExecutionOptions getExecution() {
        return execution;
    }

    public Credentials getCredentials() {
        return endpoint.getCredentials();
    }
}
