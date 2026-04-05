package io.github.lnyocly.ai4j.tool;

import io.github.lnyocly.ai4j.platform.openai.response.entity.ResponseRequest;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;

import java.util.ArrayList;
import java.util.List;

public final class ResponseRequestToolResolver {

    private ResponseRequestToolResolver() {
    }

    public static ResponseRequest resolve(ResponseRequest request) {
        if (request == null) {
            return null;
        }
        boolean hasFunctionRegistry = request.getFunctions() != null && !request.getFunctions().isEmpty();
        boolean hasMcpRegistry = request.getMcpServices() != null && !request.getMcpServices().isEmpty();
        if (!hasFunctionRegistry && !hasMcpRegistry) {
            return request;
        }

        List<Object> mergedTools = new ArrayList<Object>();
        if (request.getTools() != null && !request.getTools().isEmpty()) {
            mergedTools.addAll(request.getTools());
        }

        List<Tool> resolvedTools = ToolUtil.getAllTools(request.getFunctions(), request.getMcpServices());
        if (resolvedTools != null && !resolvedTools.isEmpty()) {
            mergedTools.addAll(resolvedTools);
        }

        return request.toBuilder()
                .tools(mergedTools)
                .build();
    }
}
