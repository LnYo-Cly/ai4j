package io.github.lnyocly.ai4j.agent.model;

import io.github.lnyocly.ai4j.listener.StreamExecutionOptions;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class AgentPrompt {

    private String model;

    private List<Object> items;

    private String systemPrompt;

    private String instructions;

    private List<Object> tools;

    private Object toolChoice;

    private Boolean parallelToolCalls;

    private Double temperature;

    private Double topP;

    private Integer maxOutputTokens;

    private Object reasoning;

    private Boolean store;

    private Boolean stream;

    private String user;

    private Map<String, Object> extraBody;

    private StreamExecutionOptions streamExecution;
}
