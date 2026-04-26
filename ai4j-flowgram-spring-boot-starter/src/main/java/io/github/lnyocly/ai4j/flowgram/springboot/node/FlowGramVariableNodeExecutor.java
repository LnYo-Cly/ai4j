package io.github.lnyocly.ai4j.flowgram.springboot.node;

import io.github.lnyocly.ai4j.agent.flowgram.FlowGramNodeExecutionContext;
import io.github.lnyocly.ai4j.agent.flowgram.FlowGramNodeExecutionResult;
import io.github.lnyocly.ai4j.agent.flowgram.FlowGramNodeExecutor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FlowGramVariableNodeExecutor implements FlowGramNodeExecutor {

    private final FlowGramNodeValueResolver valueResolver = new FlowGramNodeValueResolver();

    @Override
    public String getType() {
        return "VARIABLE";
    }

    @Override
    public FlowGramNodeExecutionResult execute(FlowGramNodeExecutionContext context) {
        Map<String, Object> outputs = new LinkedHashMap<String, Object>();
        List<Object> assigns = valueResolver.resolveList(listValue(dataValue(context, "assign")), context);
        for (Object assignObject : assigns) {
            Map<String, Object> assign = mapValue(assignObject);
            if (assign == null) {
                continue;
            }
            String left = valueAsString(assign.get("left"));
            if (isBlank(left)) {
                continue;
            }
            outputs.put(left, assign.get("right"));
        }
        if (outputs.isEmpty() && context != null && context.getInputs() != null) {
            outputs.putAll(context.getInputs());
        }
        return FlowGramNodeExecutionResult.builder()
                .outputs(outputs)
                .build();
    }

    private Object dataValue(FlowGramNodeExecutionContext context, String key) {
        Map<String, Object> data = context == null || context.getNode() == null ? null : context.getNode().getData();
        return data == null ? null : data.get(key);
    }

    private List<Object> listValue(Object value) {
        if (value instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) value;
            return list;
        }
        return null;
    }

    private Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map)) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) value;
        return map;
    }

    private String valueAsString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
