package io.github.lnyocly.ai4j.tools;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.annotation.FunctionCall;
import io.github.lnyocly.ai4j.annotation.FunctionParameter;
import io.github.lnyocly.ai4j.annotation.FunctionRequest;
import io.github.lnyocly.ai4j.tool.BuiltInToolExecutor;
import io.github.lnyocly.ai4j.tool.BuiltInTools;
import lombok.Data;

import java.util.function.Function;

@FunctionCall(name = "read_file", description = "Read a text file from the workspace or from an approved read-only skill directory.")
public class ReadFileFunction implements Function<ReadFileFunction.Request, String> {

    @Override
    public String apply(Request request) {
        try {
            return BuiltInToolExecutor.invoke(BuiltInTools.READ_FILE, JSON.toJSONString(request), null);
        } catch (Exception ex) {
            throw new RuntimeException("read_file failed", ex);
        }
    }

    @Data
    @FunctionRequest
    public static class Request {
        @FunctionParameter(description = "Relative file path inside the workspace, or an absolute path inside an approved read-only skill root.")
        private String path;
        @FunctionParameter(description = "First line number to read, starting from 1.")
        private Integer startLine;
        @FunctionParameter(description = "Last line number to read, inclusive.")
        private Integer endLine;
        @FunctionParameter(description = "Maximum characters to return.")
        private Integer maxChars;
    }
}
