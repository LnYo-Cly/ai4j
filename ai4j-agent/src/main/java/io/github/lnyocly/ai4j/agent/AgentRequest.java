package io.github.lnyocly.ai4j.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentRequest {

    private Object input;

    private Map<String, Object> metadata;
}
