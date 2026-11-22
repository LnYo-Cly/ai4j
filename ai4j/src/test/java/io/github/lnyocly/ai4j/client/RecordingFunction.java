package io.github.lnyocly.ai4j.client;

import io.github.lnyocly.ai4j.annotation.FunctionCall;
import io.github.lnyocly.ai4j.annotation.FunctionParameter;
import io.github.lnyocly.ai4j.annotation.FunctionRequest;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test double registered into the legacy {@code ToolUtil} registry.
 *
 * <p>Its {@code apply} counts invocations, so a test can assert that the kernel
 * never executes a tool: the counter must stay at zero after a completed
 * single-turn call that returned a tool call as data (design C6 / 4.3).</p>
 */
@FunctionCall(name = "audit_recording_function", description = "Records invocation for the kernel test")
public class RecordingFunction implements java.util.function.Function<RecordingFunction.Request, String> {

    /** Invocation counter; static so a test can read it without re-resolving the instance. */
    public static final AtomicInteger INVOCATIONS = new AtomicInteger();

    @Override
    public String apply(Request request) {
        INVOCATIONS.incrementAndGet();
        return "executed:" + (request == null ? "" : request.value);
    }

    /** Request payload for the recorded function. */
    @FunctionRequest
    public static class Request {

        @FunctionParameter(description = "value passed by the caller")
        private String value;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }
}
