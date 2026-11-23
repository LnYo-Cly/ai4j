package io.github.lnyocly.ai4j.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.lnyocly.ai4j.codec.JsonCodec;
import io.github.lnyocly.ai4j.mcp.annotation.McpParameter;
import io.github.lnyocly.ai4j.mcp.annotation.McpService;
import io.github.lnyocly.ai4j.mcp.annotation.McpTool;
import io.github.lnyocly.ai4j.mcp.gateway.McpGateway;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import io.github.lnyocly.ai4j.tool.McpToolBridge;
import io.github.lnyocly.ai4j.tool.ToolUtil;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Default {@link McpToolBridge} implementation loaded via {@link java.util.ServiceLoader}
 * when {@code ai4j-mcp} is present on the classpath.
 */
public final class DefaultMcpToolBridge implements McpToolBridge {

    private static final Logger log = LoggerFactory.getLogger(DefaultMcpToolBridge.class);
    private static final String SCAN_BASE_PACKAGE = "io.github.lnyocly.ai4j";
    private static final TypeReference<Map<String, Object>> MAP_TYPE =
            new TypeReference<Map<String, Object>>() {
            };

    private final Map<String, LocalMcpToolInfo> localTools = new ConcurrentHashMap<String, LocalMcpToolInfo>();
    private volatile boolean localScanned = false;

    private static final class LocalMcpToolInfo {
        final String toolId;
        final String description;
        final Class<?> serviceClass;
        final Method method;

        LocalMcpToolInfo(String toolId, String description, Class<?> serviceClass, Method method) {
            this.toolId = toolId;
            this.description = description;
            this.serviceClass = serviceClass;
            this.method = method;
        }
    }

    @Override
    public List<Tool> getLocalMcpTools() {
        ensureLocalScanned();
        List<Tool> tools = new ArrayList<Tool>();
        for (LocalMcpToolInfo mcpTool : localTools.values()) {
            Tool tool = createMcpToolEntity(mcpTool);
            if (tool != null) {
                tools.add(tool);
            }
        }
        return tools;
    }

    @Override
    public List<Tool> getAllMcpTools(List<String> mcpServerIds) {
        List<Tool> tools = new ArrayList<Tool>();
        if (mcpServerIds == null || mcpServerIds.isEmpty()) {
            return tools;
        }
        McpGateway gateway = McpGateway.getInstance();
        if (gateway == null || !gateway.isInitialized()) {
            return tools;
        }
        try {
            List<Tool.Function> mcpTools = gateway.getAvailableTools().get(10, TimeUnit.SECONDS);
            wrapFunctions(mcpTools, tools);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("获取MCP工具被中断", e);
        } catch (Exception e) {
            log.error("获取MCP工具失败", e);
        }
        log.info("获取MCP工具完成: 请求{}个服务，获得{}个工具", mcpServerIds.size(), tools.size());
        return tools;
    }

    @Override
    public List<Tool> getGlobalMcpTools(List<String> serviceIds) {
        List<Tool> tools = new ArrayList<Tool>();
        McpGateway gateway = McpGateway.getInstance();
        if (gateway == null || !gateway.isInitialized()) {
            log.warn("MCP网关未初始化，无法获取全局MCP工具");
            return tools;
        }
        try {
            List<Tool.Function> mcpTools = gateway.getAvailableTools(serviceIds).join();
            wrapFunctions(mcpTools, tools);
        } catch (RuntimeException e) {
            log.error("获取全局MCP工具失败", e);
        }
        return tools;
    }

    @Override
    public List<Tool> getUserMcpTools(List<String> serviceIds, String userId) {
        List<Tool> tools = new ArrayList<Tool>();
        McpGateway gateway = McpGateway.getInstance();
        if (gateway == null || !gateway.isInitialized()) {
            log.warn("MCP网关未初始化，无法获取用户MCP工具");
            return tools;
        }
        try {
            List<Tool.Function> mcpTools = gateway.getUserAvailableTools(serviceIds, userId).join();
            wrapFunctions(mcpTools, tools);
        } catch (RuntimeException e) {
            log.error("获取用户MCP工具失败: userId={}", userId, e);
        }
        return tools;
    }

    @Override
    public boolean hasLocalMcpTool(String functionName) {
        if (functionName == null || functionName.trim().isEmpty()) {
            return false;
        }
        ensureLocalScanned();
        return localTools.containsKey(functionName);
    }

    @Override
    public String invokeLocalMcpTool(String functionName, String argument) {
        ensureLocalScanned();
        LocalMcpToolInfo toolInfo = localTools.get(functionName);
        if (toolInfo == null) {
            throw new RuntimeException("本地MCP工具未找到: " + functionName);
        }
        try {
            log.info("调用本地MCP工具: {}, 参数: {}", toolInfo.toolId, argument);
            Object serviceInstance = toolInfo.serviceClass.getDeclaredConstructor().newInstance();
            Map<String, Object> argumentMap = parseArgumentMap(argument);
            Parameter[] parameters = toolInfo.method.getParameters();
            Object[] methodArgs = new Object[parameters.length];
            for (int i = 0; i < parameters.length; i++) {
                Parameter param = parameters[i];
                String paramName = getParameterName(param);
                Object value = argumentMap.get(paramName);
                methodArgs[i] = convertParameterValue(value, param.getType());
            }
            Object result = toolInfo.method.invoke(serviceInstance, methodArgs);
            String response = JsonCodec.toJson(result);
            log.info("本地MCP工具调用成功: {} -> {}", toolInfo.toolId, response);
            return response;
        } catch (ReflectiveOperationException e) {
            log.error("本地MCP工具调用失败: {}", toolInfo.toolId, e);
            throw new RuntimeException("本地MCP工具调用失败: " + toolInfo.toolId, e);
        } catch (RuntimeException e) {
            log.error("本地MCP工具调用失败: {}", toolInfo.toolId, e);
            throw new RuntimeException("本地MCP工具调用失败: " + toolInfo.toolId, e);
        }
    }

    @Override
    public boolean isGatewayInitialized() {
        McpGateway gateway = McpGateway.getInstance();
        return gateway != null && gateway.isInitialized();
    }

    @Override
    public String invokeGlobalMcpTool(String functionName, String argument) {
        McpGateway gateway = McpGateway.getInstance();
        if (gateway == null || !gateway.isInitialized()) {
            throw new RuntimeException("MCP网关未初始化");
        }
        try {
            Object argumentObject = parseGatewayArguments(argument);
            return gateway.callTool(functionName, argumentObject).join();
        } catch (RuntimeException e) {
            log.debug("全局MCP工具调用失败: {}", functionName, e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public String invokeUserMcpTool(String userId, String toolName, String argument) {
        McpGateway gateway = McpGateway.getInstance();
        if (gateway == null || !gateway.isInitialized()) {
            throw new RuntimeException("MCP网关未初始化");
        }
        try {
            Object argumentObject = parseGatewayArguments(argument);
            return gateway.callUserTool(userId, toolName, argumentObject).join();
        } catch (RuntimeException e) {
            throw new RuntimeException("用户MCP工具调用失败: " + toolName, e);
        }
    }

    private void ensureLocalScanned() {
        if (localScanned) {
            return;
        }
        synchronized (this) {
            if (localScanned) {
                return;
            }
            scanMcpTools();
            localScanned = true;
        }
    }

    private void scanMcpTools() {
        try {
            Reflections reflections = new Reflections(new ConfigurationBuilder()
                    .setUrls(ClasspathHelper.forPackage(SCAN_BASE_PACKAGE))
                    .setScanners(Scanners.TypesAnnotated, Scanners.MethodsAnnotated));
            Set<Class<?>> mcpServiceClasses = reflections.getTypesAnnotatedWith(McpService.class);
            for (Class<?> serviceClass : mcpServiceClasses) {
                registerServiceClass(serviceClass);
            }
            log.info("扫描本地MCP工具完成: {}个", localTools.size());
        } catch (RuntimeException e) {
            log.error("扫描本地MCP工具失败", e);
        }
    }

    private void registerServiceClass(Class<?> serviceClass) {
        try {
            McpService mcpService = serviceClass.getAnnotation(McpService.class);
            if (mcpService == null) {
                return;
            }
            String serviceName = mcpService.name().isEmpty()
                    ? serviceClass.getSimpleName()
                    : mcpService.name();
            Method[] methods = serviceClass.getDeclaredMethods();
            for (Method method : methods) {
                McpTool mcpTool = method.getAnnotation(McpTool.class);
                if (mcpTool != null) {
                    String toolName = mcpTool.name().isEmpty()
                            ? method.getName()
                            : mcpTool.name();
                    String toolId = generateApiFunctionName(serviceName, toolName);
                    localTools.put(toolId, new LocalMcpToolInfo(toolId, mcpTool.description(), serviceClass, method));
                    log.debug("注册本地MCP工具: {}", toolId);
                }
            }
        } catch (RuntimeException e) {
            log.error("处理MCP服务类失败: {}", serviceClass.getName(), e);
        }
    }

    private static Tool createMcpToolEntity(LocalMcpToolInfo mcpTool) {
        try {
            Tool.Function function = new Tool.Function();
            function.setName(mcpTool.toolId);
            function.setDescription(mcpTool.description);

            Map<String, Tool.Function.Property> parameters = new HashMap<String, Tool.Function.Property>();
            List<String> requiredParameters = new ArrayList<String>();

            Parameter[] methodParams = mcpTool.method.getParameters();
            for (Parameter param : methodParams) {
                McpParameter mcpParam = param.getAnnotation(McpParameter.class);
                String paramName = getParameterName(param);

                Tool.Function.Property property = ToolUtil.createPropertyFromType(
                        param.getType(),
                        mcpParam != null ? mcpParam.description() : "");
                parameters.put(paramName, property);

                if (mcpParam == null || mcpParam.required()) {
                    requiredParameters.add(paramName);
                }
            }

            Tool.Function.Parameter parameter = new Tool.Function.Parameter("object", parameters, requiredParameters);
            function.setParameters(parameter);

            Tool tool = new Tool();
            tool.setType("function");
            tool.setFunction(function);
            return tool;
        } catch (RuntimeException e) {
            log.error("创建MCP工具实体失败: {}", mcpTool.toolId, e);
            return null;
        }
    }

    private static void wrapFunctions(List<Tool.Function> mcpTools, List<Tool> target) {
        if (mcpTools == null) {
            return;
        }
        for (Tool.Function function : mcpTools) {
            if (function == null) {
                continue;
            }
            Tool tool = new Tool();
            tool.setType("function");
            tool.setFunction(function);
            target.add(tool);
        }
    }

    private static Map<String, Object> parseArgumentMap(String argument) {
        if (argument == null || argument.trim().isEmpty()) {
            return new HashMap<String, Object>();
        }
        Map<String, Object> parsed = JsonCodec.fromJsonLenient(argument, MAP_TYPE);
        return parsed == null ? new HashMap<String, Object>() : parsed;
    }

    private static Object parseGatewayArguments(String argument) {
        if (argument == null || argument.trim().isEmpty()) {
            return Collections.<String, Object>emptyMap();
        }
        return JsonCodec.fromJsonLenient(argument, Object.class);
    }

    private static String getParameterName(Parameter param) {
        McpParameter mcpParam = param.getAnnotation(McpParameter.class);
        if (mcpParam != null && !mcpParam.name().isEmpty()) {
            return mcpParam.name();
        }
        return param.getName();
    }

    private static Object convertParameterValue(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }
        if (targetType.isAssignableFrom(value.getClass())) {
            return value;
        }
        String strValue = value.toString();
        if (targetType == String.class) {
            return strValue;
        }
        if (targetType == Integer.class || targetType == int.class) {
            return Integer.valueOf(strValue);
        }
        if (targetType == Long.class || targetType == long.class) {
            return Long.valueOf(strValue);
        }
        if (targetType == Boolean.class || targetType == boolean.class) {
            return Boolean.valueOf(strValue);
        }
        if (targetType == Double.class || targetType == double.class) {
            return Double.valueOf(strValue);
        }
        if (targetType == Float.class || targetType == float.class) {
            return Float.valueOf(strValue);
        }
        return JsonCodec.mapper().convertValue(value, targetType);
    }

    static String generateApiFunctionName(String serviceName, String toolName) {
        String combined = serviceName + "_" + toolName;
        String normalized = combined
                .replaceAll("[^a-zA-Z0-9_-]", "_")
                .replaceAll("_{2,}", "_")
                .replaceAll("^_+|_+$", "");
        if (normalized.length() > 64) {
            normalized = normalized.substring(0, 64);
        }
        if (normalized.isEmpty() || !Character.isLetter(normalized.charAt(0))) {
            normalized = "tool_" + normalized;
        }
        return normalized;
    }
}
