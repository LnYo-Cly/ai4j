package io.github.lnyocly.ai4j.tools;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.annotation.FunctionCall;
import io.github.lnyocly.ai4j.annotation.FunctionParameter;
import io.github.lnyocly.ai4j.annotation.FunctionRequest;
import io.github.lnyocly.ai4j.tool.BuiltInToolExecutor;
import io.github.lnyocly.ai4j.tool.BuiltInTools;
import lombok.Data;

import java.util.function.Function;

@FunctionCall(name = "apply_patch", description = "Apply a structured patch to workspace files.")
public class ApplyPatchFunction implements Function<ApplyPatchFunction.Request, String> {

    @Override
    public String apply(Request request) {
        try {
            return BuiltInToolExecutor.invoke(BuiltInTools.APPLY_PATCH, JSON.toJSONString(request), null);
        } catch (Exception ex) {
            throw new RuntimeException("apply_patch failed", ex);
        }
    }

    @Data
    @FunctionRequest
    public static class Request {
        @FunctionParameter(description = "Patch text to apply. Must include *** Begin Patch and *** End Patch envelope.")
        private String patch;
    }
}
