package io.github.lnyocly.ai4j.agent.codeact;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CodeExecutionResult {

    private String stdout;

    private String result;

    private String error;

    public boolean isSuccess() {
        return error == null || error.isEmpty();
    }
}
