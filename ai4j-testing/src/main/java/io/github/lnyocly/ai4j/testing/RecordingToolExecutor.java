package io.github.lnyocly.ai4j.testing;

import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A {@link ToolExecutor} that records every call it executes — either by returning a fixed
 * output or by wrapping a real executor to spy on it.
 */
public class RecordingToolExecutor implements ToolExecutor {

    private final ToolExecutor delegate;
    private final String fixedOutput;
    private final List<AgentToolCall> calls = new ArrayList<AgentToolCall>();

    private RecordingToolExecutor(ToolExecutor delegate, String fixedOutput) {
        this.delegate = delegate;
        this.fixedOutput = fixedOutput;
    }

    /** Record calls and answer each with the same output. */
    public static RecordingToolExecutor returning(String output) {
        return new RecordingToolExecutor(null, output);
    }

    /** Record calls while delegating execution to a real executor. */
    public static RecordingToolExecutor wrapping(ToolExecutor delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate executor is required");
        }
        return new RecordingToolExecutor(delegate, null);
    }

    @Override
    public String execute(AgentToolCall call) throws Exception {
        calls.add(call);
        if (delegate != null) {
            return delegate.execute(call);
        }
        return fixedOutput;
    }

    /** Every call that reached this executor, in order. */
    public List<AgentToolCall> getCalls() {
        return Collections.unmodifiableList(calls);
    }

    public int getCallCount() {
        return calls.size();
    }

    /** The last call received, or null if none. */
    public AgentToolCall getLastCall() {
        return calls.isEmpty() ? null : calls.get(calls.size() - 1);
    }
}
