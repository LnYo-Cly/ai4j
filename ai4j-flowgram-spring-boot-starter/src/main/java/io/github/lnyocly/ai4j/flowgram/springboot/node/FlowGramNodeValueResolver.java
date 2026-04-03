package io.github.lnyocly.ai4j.flowgram.springboot.node;

import io.github.lnyocly.ai4j.agent.flowgram.FlowGramNodeExecutionContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class FlowGramNodeValueResolver {

    public Object resolve(Object value, FlowGramNodeExecutionContext context) {
        if (value == null) {
            return null;
        }
        if (value instanceof List) {
            return resolveList((List<?>) value, context);
        }
        if (!(value instanceof Map)) {
            return copyValue(value);
        }
        Map<String, Object> valueMap = mapValue(value);
        if (valueMap == null) {
            return copyValue(value);
        }
        String type = normalizeType(valueAsString(valueMap.get("type")));
        if ("REF".equals(type)) {
            return resolveReference(valueMap.get("content"), context);
        }
        if ("CONSTANT".equals(type)) {
            return copyValue(valueMap.get("content"));
        }
        if ("TEMPLATE".equals(type)) {
            Object content = valueMap.get("content");
            if (content instanceof String) {
                return renderTemplate((String) content, context);
            }
            return resolve(content, context);
        }
        if ("EXPRESSION".equals(type)) {
            return evaluateExpression(valueAsString(valueMap.get("content")), context);
        }
        return resolveMap(valueMap, context);
    }

    public Map<String, Object> resolveMap(Map<String, Object> raw, FlowGramNodeExecutionContext context) {
        Map<String, Object> resolved = new LinkedHashMap<String, Object>();
        if (raw == null) {
            return resolved;
        }
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            resolved.put(entry.getKey(), resolve(entry.getValue(), context));
        }
        return resolved;
    }

    public List<Object> resolveList(List<?> raw, FlowGramNodeExecutionContext context) {
        List<Object> resolved = new ArrayList<Object>();
        if (raw == null) {
            return resolved;
        }
        for (Object item : raw) {
            resolved.add(resolve(item, context));
        }
        return resolved;
    }

    public String renderTemplate(String template, FlowGramNodeExecutionContext context) {
        if (template == null) {
            return null;
        }
        String rendered = template;
        rendered = replaceTemplatePattern(rendered, "${", "}", context);
        rendered = replaceTemplatePattern(rendered, "{{", "}}", context);
        return rendered;
    }

    private String replaceTemplatePattern(String template,
                                          String prefix,
                                          String suffix,
                                          FlowGramNodeExecutionContext context) {
        String rendered = template;
        int start = rendered.indexOf(prefix);
        while (start >= 0) {
            int end = rendered.indexOf(suffix, start + prefix.length());
            if (end < 0) {
                break;
            }
            String expression = rendered.substring(start + prefix.length(), end).trim();
            Object value = resolvePathExpression(expression, context);
            rendered = rendered.substring(0, start)
                    + (value == null ? "" : String.valueOf(value))
                    + rendered.substring(end + suffix.length());
            start = rendered.indexOf(prefix, start);
        }
        return rendered;
    }

    private Object evaluateExpression(String expression, FlowGramNodeExecutionContext context) {
        if (isBlank(expression)) {
            return expression;
        }
        String trimmed = expression.trim();
        if (trimmed.startsWith("${") && trimmed.endsWith("}")) {
            return resolvePathExpression(trimmed.substring(2, trimmed.length() - 1), context);
        }
        if (trimmed.startsWith("{{") && trimmed.endsWith("}}")) {
            return resolvePathExpression(trimmed.substring(2, trimmed.length() - 2), context);
        }
        if ("true".equalsIgnoreCase(trimmed) || "false".equalsIgnoreCase(trimmed)) {
            return Boolean.parseBoolean(trimmed);
        }
        Double number = valueAsDouble(trimmed);
        if (number != null) {
            return trimmed.contains(".") ? number : Integer.valueOf(number.intValue());
        }
        return renderTemplate(trimmed, context);
    }

    private Object resolvePathExpression(String expression, FlowGramNodeExecutionContext context) {
        if (isBlank(expression)) {
            return null;
        }
        List<Object> path = new ArrayList<Object>();
        for (String segment : expression.split("\\.")) {
            String trimmed = segment.trim();
            if (!trimmed.isEmpty()) {
                path.add(trimmed);
            }
        }
        return resolveReference(path, context);
    }

    private Object resolveReference(Object content, FlowGramNodeExecutionContext context) {
        List<Object> path = objectList(content);
        if (path.isEmpty()) {
            return null;
        }
        Object current = resolveRootReference(path.get(0), context);
        for (int i = 1; i < path.size(); i++) {
            current = descend(current, path.get(i));
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private Object resolveRootReference(Object segment, FlowGramNodeExecutionContext context) {
        String key = valueAsString(segment);
        if (isBlank(key)) {
            return null;
        }
        if ("locals".equals(key)) {
            return context == null ? null : context.getLocals();
        }
        if (context != null && context.getLocals() != null && context.getLocals().containsKey(key)) {
            return context.getLocals().get(key);
        }
        if ("inputs".equals(key) || "taskInputs".equals(key) || "$inputs".equals(key)) {
            return context == null ? null : context.getTaskInputs();
        }
        if (context != null && context.getInputs() != null && context.getInputs().containsKey(key)) {
            return context.getInputs().get(key);
        }
        return context == null ? null : safeMap(context.getNodeOutputs()).get(key);
    }

    private Object descend(Object current, Object segment) {
        if (current == null || segment == null) {
            return null;
        }
        if (current instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) current;
            return map.get(String.valueOf(segment));
        }
        if (current instanceof List) {
            Integer index = valueAsInteger(segment);
            if (index == null) {
                return null;
            }
            List<?> list = (List<?>) current;
            return index >= 0 && index < list.size() ? list.get(index) : null;
        }
        return null;
    }

    private List<Object> objectList(Object value) {
        List<Object> result = new ArrayList<Object>();
        if (value instanceof List) {
            result.addAll((List<?>) value);
            return result;
        }
        String text = valueAsString(value);
        if (!isBlank(text)) {
            for (String segment : text.split("\\.")) {
                String trimmed = segment.trim();
                if (!trimmed.isEmpty()) {
                    result.add(trimmed);
                }
            }
        }
        return result;
    }

    private Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map)) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> cast = (Map<String, Object>) value;
        return cast;
    }

    private Map<String, Object> safeMap(Map<String, Object> value) {
        return value == null ? new LinkedHashMap<String, Object>() : value;
    }

    private Object copyValue(Object value) {
        if (value instanceof Map) {
            Map<String, Object> copy = new LinkedHashMap<String, Object>();
            @SuppressWarnings("unchecked")
            Map<String, Object> source = (Map<String, Object>) value;
            for (Map.Entry<String, Object> entry : source.entrySet()) {
                copy.put(entry.getKey(), copyValue(entry.getValue()));
            }
            return copy;
        }
        if (value instanceof List) {
            List<Object> copy = new ArrayList<Object>();
            for (Object item : (List<?>) value) {
                copy.add(copyValue(item));
            }
            return copy;
        }
        return value;
    }

    private String normalizeType(String type) {
        return type == null ? "" : type.trim().toUpperCase();
    }

    private String valueAsString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer valueAsInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Double valueAsDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
