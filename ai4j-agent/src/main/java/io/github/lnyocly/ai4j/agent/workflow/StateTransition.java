package io.github.lnyocly.ai4j.agent.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StateTransition {

    private String from;

    private String to;

    private StateCondition condition;
}
