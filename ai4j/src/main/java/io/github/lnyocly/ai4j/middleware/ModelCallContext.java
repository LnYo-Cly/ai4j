package io.github.lnyocly.ai4j.middleware;

import io.github.lnyocly.ai4j.client.ModelClient;
import io.github.lnyocly.ai4j.model.ModelRequest;
import io.github.lnyocly.ai4j.protocol.Endpoint;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mutable per-invocation context passed through the {@link ModelMiddleware} onion chain.
 */
public final class ModelCallContext {

    private final ModelClient client;
    private final ModelRequest request;
    private final Map<String, Object> attributes;

    public ModelCallContext(ModelClient client, ModelRequest request) {
        this(client, request, new ConcurrentHashMap<String, Object>());
    }

    private ModelCallContext(ModelClient client, ModelRequest request, Map<String, Object> attributes) {
        this.client = Objects.requireNonNull(client, "client is required");
        this.request = Objects.requireNonNull(request, "request is required");
        this.attributes = attributes;
    }

    public ModelClient client() {
        return client;
    }

    public ModelRequest request() {
        return request;
    }

    public Endpoint endpoint() {
        return client.endpoint();
    }

    /** Derive a context with a rewritten {@link ModelRequest}, sharing invocation attributes. */
    public ModelCallContext withRequest(ModelRequest updatedRequest) {
        return new ModelCallContext(client, Objects.requireNonNull(updatedRequest, "request is required"), attributes);
    }

    /** Derive a context targeting another {@link ModelClient}, sharing invocation attributes. */
    public ModelCallContext withClient(ModelClient updatedClient) {
        return new ModelCallContext(Objects.requireNonNull(updatedClient, "client is required"), request, attributes);
    }

    public void attribute(String key, Object value) {
        if (key == null) {
            return;
        }
        if (value == null) {
            attributes.remove(key);
        } else {
            attributes.put(key, value);
        }
    }

    public Object getAttribute(String key) {
        return key == null ? null : attributes.get(key);
    }

    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key, Class<T> type) {
        Object value = getAttribute(key);
        return type != null && type.isInstance(value) ? (T) value : null;
    }
}
