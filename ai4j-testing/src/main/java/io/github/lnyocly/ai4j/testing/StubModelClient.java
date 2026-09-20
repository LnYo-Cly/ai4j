package io.github.lnyocly.ai4j.testing;

import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An {@link AgentModelClient} that always returns the same result. Useful when the loop shape
 * does not matter — e.g. memory, compaction, or permission tests that just need a working model.
 */
public class StubModelClient implements AgentModelClient {

    private final AgentModelResult result;
    private final List<AgentPrompt> prompts = new ArrayList<AgentPrompt>();

    public StubModelClient(String output) {
        this(ModelResults.text(output));
    }

    public StubModelClient(AgentModelResult result) {
        this.result = result == null ? ModelResults.empty() : result;
    }

    @Override
    public AgentModelResult create(AgentPrompt prompt) {
        prompts.add(prompt);
        return result;
    }

    @Override
    public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
        prompts.add(prompt);
        if (listener != null && result != null) {
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
        return result;
    }

    public List<AgentPrompt> getPrompts() {
        return Collections.unmodifiableList(prompts);
    }

    public int getInvocationCount() {
        return prompts.size();
    }
}
