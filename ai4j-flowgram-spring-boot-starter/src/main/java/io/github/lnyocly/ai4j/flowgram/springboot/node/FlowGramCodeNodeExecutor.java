package io.github.lnyocly.ai4j.flowgram.springboot.node;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.agent.codeact.CodeExecutionRequest;
import io.github.lnyocly.ai4j.agent.codeact.CodeExecutionResult;
import io.github.lnyocly.ai4j.agent.codeact.NashornCodeExecutor;
import io.github.lnyocly.ai4j.agent.flowgram.FlowGramNodeExecutionContext;
import io.github.lnyocly.ai4j.agent.flowgram.FlowGramNodeExecutionResult;
import io.github.lnyocly.ai4j.agent.flowgram.FlowGramNodeExecutor;

import java.util.LinkedHashMap;
import java.util.Map;

public class FlowGramCodeNodeExecutor implements FlowGramNodeExecutor {

    private final NashornCodeExecutor codeExecutor = new NashornCodeExecutor();

    @Override
    public String getType() {
        return "CODE";
    }

    @Override
    public FlowGramNodeExecutionResult execute(FlowGramNodeExecutionContext context) {
        Map<String, Object> nodeData = context == null || context.getNode() == null
                ? new LinkedHashMap<String, Object>()
                : safeMap(context.getNode().getData());
        Map<String, Object> script = mapValue(nodeData.get("script"));
        String language = valueAsString(script == null ? null : script.get("language"));
        if (isBlank(language)) {
            language = "javascript";
        }
        String content = valueAsString(script == null ? null : script.get("content"));
        if (isBlank(content)) {
            throw new IllegalArgumentException("Code node requires script.content");
        }

        CodeExecutionResult executionResult = codeExecutor.execute(CodeExecutionRequest.builder()
                .language(language)
                .code(buildScript(content, context == null ? null : context.getInputs()))
                .timeoutMs(8000L)
                .build());
        if (executionResult == null) {
            throw new IllegalStateException("Code node executor returned no result");
        }
        if (!executionResult.isSuccess()) {
            throw new IllegalStateException(executionResult.getError());
        }

        Map<String, Object> outputs = parseOutputs(executionResult.getResult());
        if (executionResult.getStdout() != null && !executionResult.getStdout().trim().isEmpty()) {
            outputs.put("stdout", executionResult.getStdout());
        }
        return FlowGramNodeExecutionResult.builder()
                .outputs(outputs)
                .build();
    }

    private String buildScript(String userCode, Map<String, Object> params) {
        String paramsJson = JSON.toJSONString(params == null ? new LinkedHashMap<String, Object>() : params);
        String paramsLiteral = JSON.toJSONString(paramsJson);
        StringBuilder builder = new StringBuilder();
        builder.append("var params = JSON.parse(").append(paramsLiteral).append(");\n");
        builder.append("var __flowgram_input = { params: params };\n");
        builder.append(userCode).append("\n");
        builder.append("if (typeof main === 'function') {\n");
        builder.append("  var __flowgram_result = main(__flowgram_input);\n");
        builder.append("  if (__flowgram_result != null && typeof __flowgram_result.then === 'function') {\n");
        builder.append("    throw new Error('async main() is not supported in FlowGram Code node yet');\n");
        builder.append("  }\n");
        builder.append("  return JSON.stringify(__flowgram_result == null ? {} : __flowgram_result);\n");
        builder.append("} else if (typeof ret !== 'undefined') {\n");
        builder.append("  return JSON.stringify(ret == null ? {} : ret);\n");
        builder.append("} else {\n");
        builder.append("  return JSON.stringify({});\n");
        builder.append("}\n");
        return builder.toString();
    }

    private Map<String, Object> parseOutputs(String rawResult) {
        if (isBlank(rawResult)) {
            return new LinkedHashMap<String, Object>();
        }
        Object parsed = tryParse(rawResult);
        if (parsed instanceof JSONObject) {
            return new LinkedHashMap<String, Object>(((JSONObject) parsed));
        }
        if (parsed instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) parsed;
            return new LinkedHashMap<String, Object>(map);
        }
        Map<String, Object> outputs = new LinkedHashMap<String, Object>();
        outputs.put("result", parsed == null ? rawResult : parsed);
        return outputs;
    }

    private Object tryParse(String raw) {
        try {
            return JSON.parse(raw);
        } catch (Exception ex) {
            return raw;
        }
    }

    private Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map)) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) value;
        return map;
    }

    private Map<String, Object> safeMap(Map<String, Object> value) {
        return value == null ? new LinkedHashMap<String, Object>() : value;
    }

    private String valueAsString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
