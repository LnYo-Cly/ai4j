package io.github.lnyocly.ai4j.tool;

import io.github.lnyocly.ai4j.platform.openai.response.entity.ResponseRequest;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
            for (Tool tool : resolvedTools) {
                mergedTools.add(toResponsesTool(tool));
            }
        }

        return request.toBuilder()
                .tools(mergedTools)
                .build();
    }

    /**
     * Converts a Chat Completions function tool into the shape the Responses API
     * expects.
     *
     * <p>Chat Completions nests the declaration:
     * {@code {"type":"function","function":{"name":...,"parameters":...}}}
     * while the Responses API declares {@code type} / {@code name} /
     * {@code description} / {@code parameters} at the top level — see the
     * official function-calling guide, whose {@code responses.create} examples
     * carry {@code name} unnested. The two mainlines share one tool registry, so
     * the registered {@link Tool} has to be projected here rather than passed
     * through.
     *
     * <p>Lenient gateways accept either form, but only the flat one matches the
     * documented contract.
     */
    private static Object toResponsesTool(Tool tool) {
        if (tool == null || tool.getFunction() == null) {
            return tool;
        }
        Tool.Function function = tool.getFunction();

        Map<String, Object> flat = new LinkedHashMap<String, Object>();
        flat.put("type", tool.getType() == null ? "function" : tool.getType());
        flat.put("name", function.getName());
        if (function.getDescription() != null) {
            flat.put("description", function.getDescription());
        }
        if (function.getParameters() != null) {
            flat.put("parameters", function.getParameters());
        }
        return flat;
    }
}
