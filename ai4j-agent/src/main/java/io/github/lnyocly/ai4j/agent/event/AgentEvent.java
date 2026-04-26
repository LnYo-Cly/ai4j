package io.github.lnyocly.ai4j.agent.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentEvent {

    private AgentEventType type;

    private Integer step;

    private String message;

    private Object payload;
}
