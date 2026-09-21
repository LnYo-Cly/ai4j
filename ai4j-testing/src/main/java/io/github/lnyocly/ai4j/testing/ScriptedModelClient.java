package io.github.lnyocly.ai4j.testing;

import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * A queue-driven {@link AgentModelClient} for deterministic agent tests.
 *
 * <p>Enqueue one result per expected model invocation — e.g. a tool-call request followed by a
 * final answer — and the client replays them in order while recording every {@link AgentPrompt}
 * the runtime actually sent:
 *
 * <pre>{@code
 * ScriptedModelClient model = new ScriptedModelClient()
 *         .enqueue(ModelResults.toolCall("get_weather", "{\"city\":\"sh\"}"))
 *         .enqueue(ModelResults.text("sunny"));
 *
 * Agent agent = Agents.react()
 *         .modelClient(model)
 *         .model("test-model")
 *         .toolRegistry(registry)
 *         .toolExecutor(executor)
 *         .build();
 * agent.newSession().run("weather?");
 *
 * assertEquals(2, model.getPrompts().size());
 * }</pre>
 *
 * <p>When the script is exhausted the client returns {@link ModelResults#empty()} by default;
 * call {@link #failWhenExhausted(boolean)} to turn that into an {@link IllegalStateException}
 * when a test wants to prove the runtime stopped looping.
 *
 * <p>{@link #createStream(AgentPrompt, AgentModelStreamListener)} replays the same queued result
 * as stream events (reasoning delta, text delta, tool-call events, then {@code onComplete}), so
 * streaming-path tests exercise the same script.
 */
public class ScriptedModelClient implements AgentModelClient {

    private final Deque<AgentModelResult> queue = new ArrayDeque<AgentModelResult>();
    private final List<AgentPrompt> prompts = new ArrayList<AgentPrompt>();
    private boolean failWhenExhausted;

    /** Enqueue a raw result. */
    public ScriptedModelClient enqueue(AgentModelResult result) {
        queue.add(result == null ? ModelResults.empty() : result);
        return this;
    }

    /** Enqueue a plain text answer. */
    public ScriptedModelClient enqueueText(String output) {
        return enqueue(ModelResults.text(output));
    }

    /** Enqueue a single tool-call request. */
    public ScriptedModelClient enqueueToolCall(String name, String argumentsJson) {
        return enqueue(ModelResults.toolCall(name, argumentsJson));
    }

    /** Enqueue a single tool-call request. */
    public ScriptedModelClient enqueueToolCall(AgentToolCall call) {
        return enqueue(ModelResults.toolCall(call));
    }

    /**
     * When true, invoking the client past the end of the script throws
     * {@link IllegalStateException} instead of returning an empty result.
     */
    public ScriptedModelClient failWhenExhausted(boolean value) {
        this.failWhenExhausted = value;
        return this;
    }

    @Override
    public AgentModelResult create(AgentPrompt prompt) {
        prompts.add(prompt);
        return poll();
    }

    @Override
    public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
        prompts.add(prompt);
        AgentModelResult result;
        try {
            result = poll();
        } catch (RuntimeException e) {
            if (listener != null) {
                listener.onError(e);
            }
            throw e;
        }
        emit(result, listener);
        return result;
    }

    private AgentModelResult poll() {
        if (queue.isEmpty()) {
            if (failWhenExhausted) {
                throw new IllegalStateException(
                        "ScriptedModelClient script exhausted after " + prompts.size() + " invocation(s)");
            }
            return ModelResults.empty();
        }
        return queue.poll();
    }

    private void emit(AgentModelResult result, AgentModelStreamListener listener) {
        if (listener == null || result == null) {
            return;
        }
        if (result.getReasoningText() != null && !result.getReasoningText().isEmpty()) {
            listener.onReasoningDelta(result.getReasoningText());
        }
        if (result.getOutputText() != null && !result.getOutputText().isEmpty()) {
            listener.onDeltaText(result.getOutputText());
        }
        if (result.getToolCalls() != null) {
            for (AgentToolCall call : result.getToolCalls()) {
                listener.onToolCall(call);
            }
        }
        listener.onComplete(result);
    }

    /** Every prompt the runtime sent, in invocation order. */
    public List<AgentPrompt> getPrompts() {
        return Collections.unmodifiableList(prompts);
    }

    /** The most recent prompt, or null if the client was never invoked. */
    public AgentPrompt getLastPrompt() {
        return prompts.isEmpty() ? null : prompts.get(prompts.size() - 1);
    }

    public int getInvocationCount() {
        return prompts.size();
    }

    /** How many scripted results remain unplayed. */
    public int remaining() {
        return queue.size();
    }
}
