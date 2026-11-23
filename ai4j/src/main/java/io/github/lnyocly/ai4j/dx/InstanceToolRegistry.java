package io.github.lnyocly.ai4j.dx;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import io.github.lnyocly.ai4j.codec.JsonCodec;
import io.github.lnyocly.ai4j.exception.Ai4jException;
import io.github.lnyocly.ai4j.model.ToolCallItem;
import io.github.lnyocly.ai4j.model.ToolDeclaration;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Instance-scoped registry for {@link AiTool}-annotated methods on arbitrary
 * Java objects and Spring beans, preserving DI, transactions, and AOP proxies.
 */
public final class InstanceToolRegistry {

    public static final class ToolExecutionOutcome {

        private final String content;
        private final Object details;
        private final boolean error;

        private ToolExecutionOutcome(String content, Object details, boolean error) {
            this.content = content == null ? "" : content;
            this.details = details;
            this.error = error;
        }

        public String getContent() {
            return content;
        }

        public Object getDetails() {
            return details;
        }

        public boolean isError() {
            return error;
        }
    }

    private static final class RegisteredTool {

        private final String name;
        private final Object target;
        private final Method method;
        private final ToolDeclaration declaration;
        private final List<ParamBinding> params;

        private RegisteredTool(String name, Object target, Method method,
                               ToolDeclaration declaration, List<ParamBinding> params) {
            this.name = name;
            this.target = target;
            this.method = method;
            this.declaration = declaration;
            this.params = params;
        }
    }

    private static final class ParamBinding {

        private final String name;
        private final Parameter parameter;
        private final boolean progressChannel;
        private final boolean required;

        private ParamBinding(String name, Parameter parameter, boolean progressChannel, boolean required) {
            this.name = name;
            this.parameter = parameter;
            this.progressChannel = progressChannel;
            this.required = required;
        }
    }

    private final Map<String, RegisteredTool> tools = new LinkedHashMap<String, RegisteredTool>();

    public InstanceToolRegistry() {
    }

    public static InstanceToolRegistry of(Object... targets) {
        return new InstanceToolRegistry().registerAll(targets);
    }

    public InstanceToolRegistry registerAll(Object... targets) {
        if (targets != null) {
            for (Object target : targets) {
                if (target != null) {
                    register(target);
                }
            }
        }
        return this;
    }

    public InstanceToolRegistry registerAll(InstanceToolRegistry other) {
        if (other != null) {
            this.tools.putAll(other.tools);
        }
        return this;
    }

    /** Scan {@code target} for {@link AiTool}-annotated methods and register them. */
    public InstanceToolRegistry register(Object target) {
        Objects.requireNonNull(target, "tool target must not be null");
        Class<?> clazz = target.getClass();
        List<Method> candidateMethods = discoverToolMethods(clazz);
        for (Method method : candidateMethods) {
            AiTool annotation = findAiToolAnnotation(clazz, method);
            if (annotation == null) {
                continue;
            }
            if (Modifier.isStatic(method.getModifiers())) {
                throw new IllegalArgumentException("@AiTool method must be an instance method: " + method);
            }
            if (!method.isAccessible()) {
                method.setAccessible(true);
            }
            String toolName = resolveToolName(annotation, method);
            String description = resolveToolDescription(annotation);
            List<ParamBinding> bindings = buildParamBindings(method);
            Map<String, Object> schema = buildParameterSchema(bindings);
            ToolDeclaration declaration = ToolDeclaration.function(toolName)
                    .description(description)
                    .parameters(schema)
                    .build();
            tools.put(toolName, new RegisteredTool(toolName, target, method, declaration, bindings));
        }
        return this;
    }

    public boolean hasTool(String toolName) {
        return toolName != null && tools.containsKey(toolName);
    }

    public boolean isEmpty() {
        return tools.isEmpty();
    }

    public List<ToolDeclaration> declarations() {
        List<ToolDeclaration> list = new ArrayList<ToolDeclaration>(tools.size());
        for (RegisteredTool tool : tools.values()) {
            list.add(tool.declaration);
        }
        return Collections.unmodifiableList(list);
    }

    /** Invoke a tool call, forwarding live progress and separating LLM content from UI/audit details. */
    public ToolExecutionOutcome execute(ToolCallItem call, ToolProgress progress) {
        Objects.requireNonNull(call, "call is required");
        RegisteredTool tool = tools.get(call.getName());
        if (tool == null) {
            throw new Ai4jException("No registered @AiTool named '" + call.getName() + "'");
        }
        ToolProgress effectiveProgress = progress == null ? ToolProgress.noop() : progress;
        Object[] args = bindArguments(tool, call.getArgumentsJson(), effectiveProgress);
        try {
            Object rawResult = tool.method.invoke(tool.target, args);
            return toOutcome(rawResult);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            return new ToolExecutionOutcome("Tool error: " + cause.getMessage(), null, true);
        } catch (Exception e) {
            return new ToolExecutionOutcome("Tool error: " + e.getMessage(), null, true);
        }
    }

    private static ToolExecutionOutcome toOutcome(Object rawResult) {
        if (rawResult == null) {
            return new ToolExecutionOutcome("", null, false);
        }
        if (rawResult instanceof ToolOut<?>) {
            ToolOut<?> out = (ToolOut<?>) rawResult;
            return new ToolExecutionOutcome(out.getContent(), out.getDetails(), out.isError());
        }
        if (rawResult instanceof String) {
            return new ToolExecutionOutcome((String) rawResult, null, false);
        }
        return new ToolExecutionOutcome(JsonCodec.toJson(rawResult), rawResult, false);
    }

    private static Object[] bindArguments(RegisteredTool tool, String argumentsJson, ToolProgress progress) {
        JsonNode argsNode = parseArgumentsNode(argumentsJson);
        int nonProgressCount = 0;
        for (ParamBinding b : tool.params) {
            if (!b.progressChannel) {
                nonProgressCount++;
            }
        }
        Object[] values = new Object[tool.params.size()];
        for (int i = 0; i < tool.params.size(); i++) {
            ParamBinding binding = tool.params.get(i);
            if (binding.progressChannel) {
                values[i] = progress;
                continue;
            }
            JsonNode valueNode = argsNode == null ? null : argsNode.get(binding.name);
            if ((valueNode == null || valueNode.isMissingNode())
                    && nonProgressCount == 1
                    && !binding.parameter.isNamePresent()
                    && argsNode != null && argsNode.isObject() && argsNode.size() == 1) {
                valueNode = argsNode.elements().next();
            }
            if (valueNode == null || valueNode.isMissingNode() || valueNode.isNull()) {
                if (binding.parameter.getType().isPrimitive()) {
                    values[i] = defaultPrimitiveValue(binding.parameter.getType());
                } else {
                    values[i] = null;
                }
                continue;
            }
            JavaType javaType = JsonCodec.protocolMapper().getTypeFactory()
                    .constructType(binding.parameter.getParameterizedType());
            values[i] = JsonCodec.lenientMapper().convertValue(valueNode, javaType);
        }
        return values;
    }

    private static JsonNode parseArgumentsNode(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.trim().isEmpty()) {
            return JsonCodec.createObjectNode();
        }
        try {
            return JsonCodec.readTreeLenient(argumentsJson);
        } catch (RuntimeException e) {
            return JsonCodec.createObjectNode();
        }
    }

    private static Object defaultPrimitiveValue(Class<?> primitiveType) {
        if (primitiveType == boolean.class) {
            return Boolean.FALSE;
        }
        if (primitiveType == int.class) {
            return Integer.valueOf(0);
        }
        if (primitiveType == long.class) {
            return Long.valueOf(0L);
        }
        if (primitiveType == double.class) {
            return Double.valueOf(0.0d);
        }
        if (primitiveType == float.class) {
            return Float.valueOf(0.0f);
        }
        if (primitiveType == short.class) {
            return Short.valueOf((short) 0);
        }
        if (primitiveType == byte.class) {
            return Byte.valueOf((byte) 0);
        }
        if (primitiveType == char.class) {
            return Character.valueOf('\0');
        }
        return null;
    }

    private static List<Method> discoverToolMethods(Class<?> clazz) {
        List<Method> result = new ArrayList<Method>();
        Set<String> seenSignatures = new HashSet<String>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.isBridge() || method.isSynthetic()) {
                    continue;
                }
                String sig = method.getName() + ":" + Arrays.toString(method.getParameterTypes());
                if (seenSignatures.contains(sig)) {
                    continue;
                }
                if (findAiToolAnnotation(clazz, method) != null) {
                    seenSignatures.add(sig);
                    result.add(method);
                }
            }
            current = current.getSuperclass();
        }
        return result;
    }

    private static AiTool findAiToolAnnotation(Class<?> targetClass, Method method) {
        AiTool direct = method.getAnnotation(AiTool.class);
        if (direct != null) {
            return direct;
        }
        Class<?> current = targetClass;
        while (current != null && current != Object.class) {
            try {
                Method declared = current.getDeclaredMethod(method.getName(), method.getParameterTypes());
                AiTool ann = declared.getAnnotation(AiTool.class);
                if (ann != null) {
                    return ann;
                }
            } catch (NoSuchMethodException ignored) {
                // check next superclass
            }
            for (Class<?> iface : current.getInterfaces()) {
                try {
                    Method ifaceMethod = iface.getMethod(method.getName(), method.getParameterTypes());
                    AiTool ifaceAnn = ifaceMethod.getAnnotation(AiTool.class);
                    if (ifaceAnn != null) {
                        return ifaceAnn;
                    }
                } catch (NoSuchMethodException ignored) {
                    // not on this interface
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static String resolveToolName(AiTool ann, Method method) {
        if (ann.name() != null && !ann.name().trim().isEmpty()) {
            return ann.name().trim();
        }
        return method.getName();
    }

    private static String resolveToolDescription(AiTool ann) {
        if (ann.description() != null && !ann.description().trim().isEmpty()) {
            return ann.description().trim();
        }
        if (ann.value() != null && !ann.value().trim().isEmpty()) {
            return ann.value().trim();
        }
        return "";
    }

    private static List<ParamBinding> buildParamBindings(Method method) {
        Parameter[] parameters = method.getParameters();
        List<ParamBinding> bindings = new ArrayList<ParamBinding>(parameters.length);
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            if (ToolProgress.class.isAssignableFrom(parameter.getType())) {
                bindings.add(new ParamBinding(parameter.getName(), parameter, true, false));
                continue;
            }
            AiToolParam ann = parameter.getAnnotation(AiToolParam.class);
            String paramName = (ann != null && !ann.name().trim().isEmpty())
                    ? ann.name().trim()
                    : parameter.getName();
            boolean required = ann == null || ann.required();
            bindings.add(new ParamBinding(paramName, parameter, false, required));
        }
        return bindings;
    }

    private static Map<String, Object> buildParameterSchema(List<ParamBinding> bindings) {
        Map<String, Object> schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        List<String> required = new ArrayList<String>();

        for (ParamBinding binding : bindings) {
            if (binding.progressChannel) {
                continue;
            }
            Map<String, Object> paramSchema = DxJsonSchema.buildTypeSchema(
                    binding.parameter.getParameterizedType(), new HashSet<java.lang.reflect.Type>());
            AiToolParam ann = binding.parameter.getAnnotation(AiToolParam.class);
            if (ann != null) {
                String desc = !ann.description().trim().isEmpty()
                        ? ann.description().trim()
                        : ann.value().trim();
                if (!desc.isEmpty()) {
                    paramSchema.put("description", desc);
                }
            }
            properties.put(binding.name, paramSchema);
            if (binding.required) {
                required.add(binding.name);
            }
        }
        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        return schema;
    }
}
