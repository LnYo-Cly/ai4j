package io.github.lnyocly.ai4j.agent.codeact;

import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CodeExecutionRequest {

    private String language;

    private String code;

    private List<String> toolNames;

    private ToolExecutor toolExecutor;

    private String user;

    private Long timeoutMs;
}
