package io.github.lnyocly.ai4j.middleware;

import io.github.lnyocly.ai4j.client.ModelCall;
import io.github.lnyocly.ai4j.client.ModelCallException;
import io.github.lnyocly.ai4j.client.ModelClient;
import io.github.lnyocly.ai4j.model.ModelEventListener;
import io.github.lnyocly.ai4j.model.ModelRequest;
import io.github.lnyocly.ai4j.model.ModelResult;
import io.github.lnyocly.ai4j.protocol.Capabilities;
import io.github.lnyocly.ai4j.protocol.Capability;
import io.github.lnyocly.ai4j.protocol.Endpoint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * {@link ModelClient} decorator executing an ordered {@link ModelMiddleware} chain.
 */
public final class MiddlewareModelClient implements ModelClient {

    private final ModelClient delegate;
    private final List<ModelMiddleware> middlewares;

    private MiddlewareModelClient(ModelClient delegate, List<ModelMiddleware> middlewares) {
        this.delegate = Objects.requireNonNull(delegate, "delegate is required");
        this.middlewares = middlewares == null || middlewares.isEmpty()
                ? Collections.<ModelMiddleware>emptyList()
                : Collections.unmodifiableList(new ArrayList<ModelMiddleware>(middlewares));
    }

    public static MiddlewareModelClient wrap(ModelClient delegate, ModelMiddleware... middlewares) {
        return new MiddlewareModelClient(delegate,
                middlewares == null ? Collections.<ModelMiddleware>emptyList() : Arrays.asList(middlewares));
    }

    public static MiddlewareModelClient wrap(ModelClient delegate, List<ModelMiddleware> middlewares) {
        return new MiddlewareModelClient(delegate, middlewares);
    }

    public static Builder builder(ModelClient delegate) {
        return new Builder(delegate);
    }

    public ModelClient delegate() {
        return delegate;
    }

    public List<ModelMiddleware> middlewares() {
        return middlewares;
    }

    @Override
    public Endpoint endpoint() {
        return delegate.endpoint();
    }

    @Override
    public Capabilities capabilities() {
        return delegate.capabilities();
    }

    @Override
    public void require(Capability capability) {
        delegate.require(capability);
    }

    @Override
    public ModelResult create(ModelRequest request) throws ModelCallException {
        Objects.requireNonNull(request, "request is required");
        ModelCallContext context = new ModelCallContext(delegate, request);
        return chainAt(0).proceed(context);
    }

    @Override
    public ModelCall createStream(ModelRequest request, ModelEventListener listener) {
        Objects.requireNonNull(request, "request is required");
        ModelCallContext context = new ModelCallContext(delegate, request);
        return streamChainAt(0).proceed(context, listener);
    }

    private ModelMiddleware.Next chainAt(final int index) {
        if (index >= middlewares.size()) {
            return new ModelMiddleware.Next() {
                @Override
                public ModelResult proceed(ModelCallContext context) throws ModelCallException {
                    return context.client().create(context.request());
                }
            };
        }
        final ModelMiddleware current = middlewares.get(index);
        final ModelMiddleware.Next next = chainAt(index + 1);
        return new ModelMiddleware.Next() {
            @Override
            public ModelResult proceed(ModelCallContext context) throws ModelCallException {
                return current.intercept(context, next);
            }
        };
    }

    private ModelMiddleware.StreamNext streamChainAt(final int index) {
        if (index >= middlewares.size()) {
            return new ModelMiddleware.StreamNext() {
                @Override
                public ModelCall proceed(ModelCallContext context, ModelEventListener listener) {
                    return context.client().createStream(context.request(), listener);
                }
            };
        }
        final ModelMiddleware current = middlewares.get(index);
        final ModelMiddleware.StreamNext next = streamChainAt(index + 1);
        return new ModelMiddleware.StreamNext() {
            @Override
            public ModelCall proceed(ModelCallContext context, ModelEventListener listener) {
                return current.interceptStream(context, listener, next);
            }
        };
    }

    /** Fluent builder. */
    public static final class Builder {

        private final ModelClient delegate;
        private final List<ModelMiddleware> middlewares = new ArrayList<ModelMiddleware>();

        private Builder(ModelClient delegate) {
            this.delegate = Objects.requireNonNull(delegate, "delegate is required");
        }

        public Builder add(ModelMiddleware middleware) {
            if (middleware != null) {
                this.middlewares.add(middleware);
            }
            return this;
        }

        public Builder addAll(List<ModelMiddleware> middlewares) {
            if (middlewares != null) {
                for (ModelMiddleware middleware : middlewares) {
                    add(middleware);
                }
            }
            return this;
        }

        public MiddlewareModelClient build() {
            return new MiddlewareModelClient(delegate, middlewares);
        }
    }
}
