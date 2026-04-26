package io.github.lnyocly.ai4j.tools;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.annotation.FunctionCall;
import io.github.lnyocly.ai4j.annotation.FunctionParameter;
import io.github.lnyocly.ai4j.annotation.FunctionRequest;
import io.github.lnyocly.ai4j.tool.BuiltInToolExecutor;
import io.github.lnyocly.ai4j.tool.BuiltInTools;
import lombok.Data;

import java.util.function.Function;

@FunctionCall(name = "write_file", description = "Create, overwrite, or append a text file.")
public class WriteFileFunction implements Function<WriteFileFunction.Request, String> {

    @Override
    public String apply(Request request) {
        try {
            return BuiltInToolExecutor.invoke(BuiltInTools.WRITE_FILE, JSON.toJSONString(request), null);
        } catch (Exception ex) {
            throw new RuntimeException("write_file failed", ex);
        }
    }

    @Data
    @FunctionRequest
    public static class Request {
        @FunctionParameter(description = "File path to write. Relative paths resolve from the workspace root; absolute paths are allowed.")
        private String path;
        @FunctionParameter(description = "Full text content to write.")
        private String content;
        @FunctionParameter(description = "Write mode: create, overwrite, append.")
        private String mode;
    }
}
