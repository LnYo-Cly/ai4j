package io.github.lnyocly.ai4j.agent;

import io.github.lnyocly.ai4j.agent.event.AgentEventPublisher;
import io.github.lnyocly.ai4j.agent.codeact.CodeExecutor;
import io.github.lnyocly.ai4j.agent.codeact.CodeActOptions;
import io.github.lnyocly.ai4j.agent.memory.AgentMemory;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.tool.AgentToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder(toBuilder = true)
public class AgentContext {

    private AgentModelClient modelClient;

    private AgentToolRegistry toolRegistry;

    private ToolExecutor toolExecutor;

    private CodeExecutor codeExecutor;

    private AgentMemory memory;

    private AgentOptions options;

    private CodeActOptions codeActOptions;

    private AgentEventPublisher eventPublisher;

    private String model;

    private String instructions;

    private String systemPrompt;

    private Double temperature;

    private Double topP;

    private Integer maxOutputTokens;

    private Object reasoning;

    private Object toolChoice;

    private Boolean parallelToolCalls;

    private Boolean store;

    private String user;

    private Map<String, Object> extraBody;
}
