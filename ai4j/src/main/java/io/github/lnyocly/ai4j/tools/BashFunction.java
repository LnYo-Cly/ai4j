package io.github.lnyocly.ai4j.tools;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.annotation.FunctionCall;
import io.github.lnyocly.ai4j.annotation.FunctionParameter;
import io.github.lnyocly.ai4j.annotation.FunctionRequest;
import io.github.lnyocly.ai4j.tool.BuiltInToolExecutor;
import io.github.lnyocly.ai4j.tool.BuiltInTools;
import lombok.Data;

import java.util.function.Function;

@FunctionCall(name = "bash", description = "Execute non-interactive shell commands or manage interactive/background shell processes inside the workspace.")
public class BashFunction implements Function<BashFunction.Request, String> {

    @Override
    public String apply(Request request) {
        try {
            return BuiltInToolExecutor.invoke(BuiltInTools.BASH, JSON.toJSONString(request), null);
        } catch (Exception ex) {
            throw new RuntimeException("bash failed", ex);
        }
    }

    @Data
    @FunctionRequest
    public static class Request {
        @FunctionParameter(description = "bash action to perform: exec, start, status, logs, write, stop, list.")
        private String action;
        @FunctionParameter(description = "Command string to execute.")
        private String command;
        @FunctionParameter(description = "Relative working directory inside the workspace.")
        private String cwd;
        @FunctionParameter(description = "Execution timeout in milliseconds for exec.")
        private Long timeoutMs;
        @FunctionParameter(description = "Background process identifier.")
        private String processId;
        @FunctionParameter(description = "Log cursor offset.")
        private Long offset;
        @FunctionParameter(description = "Maximum log characters to return.")
        private Integer limit;
        @FunctionParameter(description = "Text written to stdin for a background process.")
        private String input;
    }
}
