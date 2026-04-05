package io.github.lnyocly.ai4j.agent.workflow;

import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.AgentSession;
import io.github.lnyocly.ai4j.agent.event.AgentEvent;
import io.github.lnyocly.ai4j.agent.event.AgentEventPublisher;
import io.github.lnyocly.ai4j.agent.event.AgentEventType;
import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
@Builder
public class WorkflowContext {

    private AgentSession session;

    @Builder.Default
    private Map<String, Object> state = new HashMap<>();

    private AgentEventPublisher eventPublisher;

    public void put(String key, Object value) {
        state.put(key, value);
    }

    public Object get(String key) {
        return state.get(key);
    }

    public AgentEvent createResultEvent(AgentResult result) {
        return AgentEvent.builder()
                .type(AgentEventType.FINAL_OUTPUT)
                .message(result == null ? null : result.getOutputText())
                .payload(result)
                .build();
    }
}
