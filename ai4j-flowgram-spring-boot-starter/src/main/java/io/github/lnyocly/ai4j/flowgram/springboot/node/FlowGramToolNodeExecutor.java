package io.github.lnyocly.ai4j.flowgram.springboot.node;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.agent.flowgram.FlowGramNodeExecutionContext;
import io.github.lnyocly.ai4j.agent.flowgram.FlowGramNodeExecutionResult;
import io.github.lnyocly.ai4j.agent.flowgram.FlowGramNodeExecutor;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import io.github.lnyocly.ai4j.agent.tool.ToolUtilExecutor;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class FlowGramToolNodeExecutor implements FlowGramNodeExecutor {

    @Override
    public String getType() {
        return "TOOL";
    }

    @Override
    public FlowGramNodeExecutionResult execute(FlowGramNodeExecutionContext context) throws Exception {
        Map<String, Object> inputs = context == null || context.getInputs() == null
                ? new LinkedHashMap<String, Object>()
                : new LinkedHashMap<String, Object>(context.getInputs());

        String toolName = trimToNull(valueAsString(inputs.remove("toolName")));
        if (toolName == null) {
            throw new IllegalArgumentException("Tool node requires toolName");
        }

        Object argumentsValue = inputs.remove("argumentsJson");
        String argumentsJson;
        if (argumentsValue == null) {
            argumentsJson = JSON.toJSONString(inputs);
        } else if (argumentsValue instanceof String) {
            String text = ((String) argumentsValue).trim();
            argumentsJson = text.isEmpty() ? "{}" : text;
        } else {
            argumentsJson = JSON.toJSONString(argumentsValue);
        }

        String rawOutput = tryExecuteBuiltinDemoTool(toolName, argumentsJson);
        if (rawOutput == null) {
            ToolExecutor executor = new ToolUtilExecutor(Collections.singleton(toolName));
            rawOutput = executor.execute(AgentToolCall.builder()
                    .name(toolName)
                    .arguments(argumentsJson)
                    .type("function")
                    .callId(context == null ? null : context.getTaskId())
                    .build());
        }

        Map<String, Object> outputs = new LinkedHashMap<String, Object>();
        outputs.put("toolName", toolName);
        outputs.put("rawOutput", rawOutput);

        Object parsed = tryParse(rawOutput);
        if (parsed instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsedMap = (Map<String, Object>) parsed;
            outputs.put("data", parsedMap);
            if (!parsedMap.containsKey("result")) {
                outputs.put("result", rawOutput);
            }
            outputs.putAll(parsedMap);
        } else {
            outputs.put("result", parsed == null ? rawOutput : parsed);
        }
        return FlowGramNodeExecutionResult.builder()
                .outputs(outputs)
                .build();
    }

    private String tryExecuteBuiltinDemoTool(String toolName, String argumentsJson) {
        if ("queryTrainInfo".equals(toolName)) {
            Integer type = extractIntegerArgument(argumentsJson, "type");
            return type != null && type.intValue() > 35
                    ? "天气情况正常，允许发车"
                    : "天气情况较差，不允许发车";
        }
        return null;
    }

    private Object tryParse(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return JSON.parse(raw);
        } catch (Exception ex) {
            return raw;
        }
    }

    private String valueAsString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Integer extractIntegerArgument(String argumentsJson, String key) {
        try {
            Map<String, Object> map = JSON.parseObject(argumentsJson);
            if (map == null) {
                return null;
            }
            Object value = map.get(key);
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            if (value == null) {
                return null;
            }
            return Integer.valueOf(String.valueOf(value));
        } catch (Exception ex) {
            return null;
        }
    }
}
