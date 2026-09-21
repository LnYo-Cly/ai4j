package io.github.lnyocly.ai4j.testing;

import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An {@link AgentModelStreamListener} that records every callback for assertions on
 * streaming-path tests.
 */
public class RecordingStreamListener implements AgentModelStreamListener {

    private final List<String> reasoningDeltas = new ArrayList<String>();
    private final List<String> textDeltas = new ArrayList<String>();
    private final List<AgentToolCall> toolCalls = new ArrayList<AgentToolCall>();
    private final List<Object> events = new ArrayList<Object>();
    private final List<Throwable> errors = new ArrayList<Throwable>();
    private AgentModelResult completed;

    @Override
    public void onReasoningDelta(String delta) {
        reasoningDeltas.add(delta);
    }

    @Override
    public void onDeltaText(String delta) {
        textDeltas.add(delta);
    }

    @Override
    public void onToolCall(AgentToolCall call) {
        toolCalls.add(call);
    }

    @Override
    public void onEvent(Object event) {
        events.add(event);
    }

    @Override
    public void onComplete(AgentModelResult result) {
        this.completed = result;
    }

    @Override
    public void onError(Throwable t) {
        errors.add(t);
    }

    public List<String> getReasoningDeltas() {
        return Collections.unmodifiableList(reasoningDeltas);
    }

    public List<String> getTextDeltas() {
        return Collections.unmodifiableList(textDeltas);
    }

    public List<AgentToolCall> getToolCalls() {
        return Collections.unmodifiableList(toolCalls);
    }

    public List<Object> getEvents() {
        return Collections.unmodifiableList(events);
    }

    public List<Throwable> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    /** The result passed to {@link #onComplete}, or null if the stream never completed. */
    public AgentModelResult getCompleted() {
        return completed;
    }

    /** Concatenated text deltas — convenience for asserting the full streamed answer. */
    public String getText() {
        StringBuilder sb = new StringBuilder();
        for (String delta : textDeltas) {
            sb.append(delta);
        }
        return sb.toString();
    }
}
