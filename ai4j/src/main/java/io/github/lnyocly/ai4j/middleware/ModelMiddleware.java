package io.github.lnyocly.ai4j.middleware;

import io.github.lnyocly.ai4j.client.ModelCall;
import io.github.lnyocly.ai4j.client.ModelCallException;
import io.github.lnyocly.ai4j.model.ModelEventListener;
import io.github.lnyocly.ai4j.model.ModelResult;

/**
 * Onion-ring interceptor around {@link io.github.lnyocly.ai4j.client.ModelClient} calls.
 */
public interface ModelMiddleware {

    /** Intercept a blocking {@code create} invocation. */
    ModelResult intercept(ModelCallContext context, Next next) throws ModelCallException;

    /** Intercept a streaming {@code createStream} invocation. */
    default ModelCall interceptStream(ModelCallContext context, ModelEventListener listener, StreamNext next) {
        return next.proceed(context, listener);
    }

    /** Continuation for blocking calls. */
    interface Next {
        ModelResult proceed(ModelCallContext context) throws ModelCallException;
    }

    /** Continuation for streaming calls. */
    interface StreamNext {
        ModelCall proceed(ModelCallContext context, ModelEventListener listener);
    }
}
