package io.github.lnyocly.ai4j.client;

import io.github.lnyocly.ai4j.model.ModelEventListener;
import io.github.lnyocly.ai4j.model.ModelRequest;
import io.github.lnyocly.ai4j.model.ModelResult;
import io.github.lnyocly.ai4j.protocol.Capabilities;
import io.github.lnyocly.ai4j.protocol.Capability;
import io.github.lnyocly.ai4j.protocol.Endpoint;

/**
 * Single-turn model client for the new chain.
 *
 * <p>One call equals one HTTP request: {@code create} returns the decoded
 * result and {@code createStream} returns a cancellable {@link ModelCall}. The
 * kernel never executes tools; a tool call is returned as data and the host
 * decides what to do (design C6).</p>
 */
public interface ModelClient {

    /** The endpoint this client was assembled from. */
    Endpoint endpoint();

    /** Resolved capability set (protocol baseline ∩ profile ∩ endpoint override). */
    Capabilities capabilities();

    /**
     * Fail fast when a capability is not declared.
     *
     * @throws io.github.lnyocly.ai4j.protocol.UnsupportedCapabilityException when unavailable
     */
    void require(Capability capability);

    /** Execute one blocking single-turn call. */
    ModelResult create(ModelRequest request) throws ModelCallException;

    /** Start one streaming single-turn call and return its handle. */
    ModelCall createStream(ModelRequest request, ModelEventListener listener);
}
