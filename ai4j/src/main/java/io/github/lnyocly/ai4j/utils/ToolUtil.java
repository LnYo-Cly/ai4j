package io.github.lnyocly.ai4j.utils;

/**
 * @Author cly
 * @Description 统一工具管理器，支持传统Function工具、本地MCP工具和远程MCP服务
 */
import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.annotation.FunctionParameter;
import io.github.lnyocly.ai4j.annotation.FunctionCall;
import io.github.lnyocly.ai4j.annotation.FunctionRequest;
import io.github.lnyocly.ai4j.mcp.annotation.McpService;
import io.github.lnyocly.ai4j.mcp.annotation.McpTool;
import io.github.lnyocly.ai4j.mcp.annotation.McpParameter;
import io.github.lnyocly.ai4j.mcp.gateway.McpGateway;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ToolUtil {

    private static final Logger log = LoggerFactory.getLogger(ToolUtil.class);

    // 反射扫描器，支持注解和方法扫描
    private static final Reflections reflections = new Reflections(new ConfigurationBuilder()
            .setUrls(ClasspathHelper.forPackage(""))
            .setScanners(Scanners.TypesAnnotated, Scanners.MethodsAnnotated));

    // === 传统Function工具缓存 ===
    public static final Map<String, Tool> toolEntityMap = new ConcurrentHashMap<>();
    public static final Map<String, Class<?>> toolClassMap = new ConcurrentHashMap<>();
    public static final Map<String, Class<?>> toolRequestMap = new ConcurrentHashMap<>();

    // === 本地MCP工具缓存 ===
    private static final Map<String, McpToolInfo> mcpToolCache = new ConcurrentHashMap<>();

    // === 初始化状态 ===
    private static volatile boolean initialized = false;

    /**
     * 本地MCP工具信息类
     */
    private static class McpToolInfo {
        final String toolId;  // 工具唯一标识符（符合OpenAI API命名规范）
        final String description;
        final Class<?> serviceClass;
        final Method method;

        McpToolInfo(String toolId, String description, Class<?> serviceClass, Method method) {
            this.toolId = toolId;
            this.description = description;
            this.serviceClass = serviceClass;
            this.method = method;
        }
    }

    /**
     * 统一工具调用入口（支持自动识别用户上下文）
     */
    public static String invoke(String functionName, String argument) {
        ensureInitialized();

        try {
            // 1. 检查是否为用户工具调用
            String userId = extractUserIdFromFunctionName(functionName);
            
            if (userId != null) {
                // 用户工具调用：user_123_tool_create_issue -> userId=123, toolName=create_issue
                String actualToolName = extractActualToolName(functionName);
                return callUserMcpTool(userId, actualToolName, argument);
            }
            
            // 2. 全局工具调用流程
            return callGlobalTool(functionName, argument);
            
        } catch (Exception e) {
            throw new RuntimeException("工具调用失败: " + functionName + " - " + e.getMessage(), e);
        }
    }
    
    /**
     * 显式指定用户ID的调用方式
     */
    public static String invoke(String functionName, String argument, String userId) {
        if (userId != null && !userId.isEmpty()) {
            return callUserMcpTool(userId, functionName, argument);
        } else {
            return invoke(functionName, argument);
        }
    }
    

    /**
     * 从函数名中提取用户ID
     * @param functionName 如 "user_123_tool_create_issue" 或 "create_issue"
     * @return 用户ID或null
     */
    private static String extractUserIdFromFunctionName(String functionName) {
        if (functionName != null && functionName.startsWith("user_") && functionName.contains("_tool_")) {
            // 格式：user_{userId}_tool_{toolName}
            String[] parts = functionName.split("_tool_");
            if (parts.length == 2) {
                String userPart = parts[0]; // "user_123"
                if (userPart.startsWith("user_")) {
                    return userPart.substring(5); // 提取 "123"
                }
            }
        }
        return null;
    }

    /**
     * 调用用户MCP工具
     */
    private static String callUserMcpTool(String userId, String toolName, String argument) {
        McpGateway gateway = McpGateway.getInstance();
        
        if (gateway == null || !gateway.isInitialized()) {
            throw new RuntimeException("MCP网关未初始化");
        }
        
        try {
            Object argumentObject = JSON.parseObject(argument);
            return gateway.callUserTool(userId, toolName, argumentObject).join();
        } catch (Exception e) {
            throw new RuntimeException("用户MCP工具调用失败: " + toolName, e);
        }
    }

    /**
     * 调用全局工具
     */
    private static String callGlobalTool(String functionName, String argument) {
        // 1. 优先检查本地MCP工具
        if (mcpToolCache.containsKey(functionName)) {
            return invokeMcpTool(functionName, argument);
        }

        // 2. 检查传统Function工具
        if (toolClassMap.containsKey(functionName) && toolRequestMap.containsKey(functionName)) {
            return invokeFunctionTool(functionName, argument);
        }

        // 3. 尝试调用全局MCP服务
        McpGateway gateway = McpGateway.getInstance();
        if (gateway != null && gateway.isInitialized()) {
            try {
                Object argumentObject = JSON.parseObject(argument);
                return gateway.callTool(functionName, argumentObject).join();
            } catch (Exception e) {
                // MCP调用失败，继续其他逻辑
                log.debug("全局MCP工具调用失败: {}", functionName, e);
                throw new RuntimeException(e.getMessage());
            }
        }

        throw new RuntimeException("工具未找到: " + functionName);
    }
    /**
     * 从用户工具名中提取实际工具名
     * @param functionName 如 "user_123_tool_create_issue"
     * @return 实际工具名 如 "create_issue"
     */
    private static String extractActualToolName(String functionName) {
        if (functionName != null && functionName.contains("_tool_")) {
            String[] parts = functionName.split("_tool_");
            if (parts.length == 2) {
                return parts[1]; // "create_issue"
            }
        }
        return functionName;
    }


    /**
     * 确保工具已初始化
     */
    private static void ensureInitialized() {
        if (!initialized) {
            synchronized (ToolUtil.class) {
                if (!initialized) {
                    scanAndRegisterAllTools();
                    initialized = true;
                }
            }
        }
    }

    /**
     * 扫描并注册所有工具
     */
    private static void scanAndRegisterAllTools() {
        log.info("开始扫描并注册所有工具...");

        // 扫描传统Function工具
        scanFunctionTools();

        // 扫描本地MCP工具
        scanMcpTools();

        log.info("工具扫描完成 - 传统Function工具: {}, 本地MCP工具: {}",
                toolClassMap.size(), mcpToolCache.size());
    }

    /**
     * 调用本地MCP工具
     */
    private static String invokeMcpTool(String functionName, String argument) {
        McpToolInfo toolInfo = mcpToolCache.get(functionName);
        if (toolInfo == null) {
            throw new RuntimeException("本地MCP工具未找到: " + functionName);
        }

        try {
            log.info("调用本地MCP工具: {}, 参数: {}", toolInfo.toolId, argument);

            // 创建服务实例
            Object serviceInstance = toolInfo.serviceClass.newInstance();

            // 解析参数
            Map<String, Object> argumentMap = JSON.parseObject(argument, Map.class);
            if (argumentMap == null) {
                argumentMap = new HashMap<>();
            }

            // 准备方法参数
            Parameter[] parameters = toolInfo.method.getParameters();
            Object[] methodArgs = new Object[parameters.length];

            for (int i = 0; i < parameters.length; i++) {
                Parameter param = parameters[i];
                String paramName = getParameterName(param);
                Object value = argumentMap.get(paramName);

                // 类型转换
                methodArgs[i] = convertParameterValue(value, param.getType());
            }

            // 调用方法
            Object result = toolInfo.method.invoke(serviceInstance, methodArgs);
            String response = JSON.toJSONString(result);

            log.info("本地MCP工具调用成功: {} -> {}", toolInfo.toolId, response);
            return response;

        } catch (Exception e) {
            log.error("本地MCP工具调用失败: {}", toolInfo.toolId, e);
            throw new RuntimeException("本地MCP工具调用失败: " + toolInfo.toolId, e);
        }
    }

    /**
     * 调用传统Function工具
     */
    private static String invokeFunctionTool(String functionName, String argument) {
        Class<?> functionClass = toolClassMap.get(functionName);
        Class<?> functionRequestClass = toolRequestMap.get(functionName);

        log.info("调用传统Function工具: {}, 参数: {}", functionName, argument);

        try {
            // 获取调用函数
            Method apply = functionClass.getMethod("apply", functionRequestClass);

            // 解析参数
            Object arg = JSON.parseObject(argument, functionRequestClass);

            // 调用函数
            Object functionInstance = functionClass.newInstance();
            Object result = apply.invoke(functionInstance, arg);

            String response = JSON.toJSONString(result);
            log.info("传统Function工具调用成功: {} -> {}", functionName, response);
            return response;

        } catch (Exception e) {
            log.error("传统Function工具调用失败: {}", functionName, e);
            throw new RuntimeException("传统Function工具调用失败: " + functionName, e);
        }
    }

    /**
     * 调用远程MCP服务
     */
    private static String callRemoteMcpService(String functionName, String argument) {
        try {
            // 使用反射调用McpGateway，避免直接依赖
            Class<?> mcpGatewayClass = Class.forName("io.github.lnyocly.ai4j.mcp.gateway.McpGateway");
            Method getInstanceMethod = mcpGatewayClass.getMethod("getInstance");
            Object mcpGateway = getInstanceMethod.invoke(null);

            if (mcpGateway != null) {
                Method isInitializedMethod = mcpGatewayClass.getMethod("isInitialized");
                Boolean isInitialized = (Boolean) isInitializedMethod.invoke(mcpGateway);

                if (isInitialized) {
                    Method callToolMethod = mcpGatewayClass.getMethod("callTool", String.class, Object.class);

                    // 解析参数为对象
                    Object argumentObject;
                    try {
                        argumentObject = JSON.parseObject(argument);
                    } catch (Exception e) {
                        argumentObject = argument;
                    }

                    Object futureResult = callToolMethod.invoke(mcpGateway, functionName, argumentObject);

                    if (futureResult instanceof java.util.concurrent.CompletableFuture) {
                        return (String) ((java.util.concurrent.CompletableFuture<?>) futureResult)
                                .get(30, java.util.concurrent.TimeUnit.SECONDS);
                    }
                }
            }
        } catch (ClassNotFoundException e) {
            log.debug("未找到McpGateway类");
        } catch (Exception e) {
            log.debug("远程MCP服务调用异常: {}", e.getMessage());
        }
        return null;
    }


    /**
     * 获取传统Function工具列表（保持向后兼容）
     *
     * @param functionList 需要获取的Function工具名称列表
     * @return 工具列表
     */
    public static List<Tool> getAllFunctionTools(List<String> functionList) {
        ensureInitialized();

        List<Tool> tools = new ArrayList<>();

        if (functionList == null || functionList.isEmpty()) {
            return tools;
        }

        log.debug("获取{}个传统Function工具", functionList.size());

        for (String functionName : functionList) {
            if (functionName == null || functionName.trim().isEmpty()) {
                continue;
            }

            try {
                Tool tool = toolEntityMap.get(functionName);
                if (tool == null) {
                    tool = getToolEntity(functionName);
                    if (tool != null) {
                        toolEntityMap.put(functionName, tool);
                    }
                }

                if (tool != null) {
                    tools.add(tool);
                }
            } catch (Exception e) {
                log.error("获取Function工具失败: {}", functionName, e);
            }
        }

        log.info("获取传统Function工具完成: 请求{}个，成功{}个", functionList.size(), tools.size());
        return tools;
    }

    /**
     * 根据MCP服务ID列表获取所有MCP工具
     *
     * @param mcpServerIds MCP服务ID列表
     * @return 工具列表
     */
    public static List<Tool> getAllMcpTools(List<String> mcpServerIds) {
        List<Tool> tools = new ArrayList<>();

        if (mcpServerIds == null || mcpServerIds.isEmpty()) {
            return tools;
        }

        log.debug("获取{}个MCP服务的工具", mcpServerIds.size());

        try {
            // 使用反射调用McpGateway获取工具
            Class<?> mcpGatewayClass = Class.forName("io.github.lnyocly.ai4j.mcp.gateway.McpGateway");
            Method getInstanceMethod = mcpGatewayClass.getMethod("getInstance");
            Object mcpGateway = getInstanceMethod.invoke(null);

            if (mcpGateway != null) {
                Method isInitializedMethod = mcpGatewayClass.getMethod("isInitialized");
                Boolean isInitialized = (Boolean) isInitializedMethod.invoke(mcpGateway);

                if (isInitialized) {
                    Method getAvailableToolsMethod = mcpGatewayClass.getMethod("getAvailableTools");
                    Object futureResult = getAvailableToolsMethod.invoke(mcpGateway);

                    if (futureResult instanceof java.util.concurrent.CompletableFuture) {
                        @SuppressWarnings("unchecked")
                        List<Tool.Function> mcpTools = (List<Tool.Function>)
                                ((java.util.concurrent.CompletableFuture<?>) futureResult)
                                .get(10, java.util.concurrent.TimeUnit.SECONDS);

                        // 转换为Tool对象
                        for (Tool.Function function : mcpTools) {
                            Tool tool = new Tool();
                            tool.setType("function");
                            tool.setFunction(function);
                            tools.add(tool);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("获取MCP工具失败", e);
        }

        log.info("获取MCP工具完成: 请求{}个服务，获得{}个工具", mcpServerIds.size(), tools.size());
        return tools;
    }

   /**
     * 获取所有工具（自动识别用户上下文）
     */
    public static List<Tool> getAllTools(List<String> functionList, List<String> mcpServerIds) {
        List<Tool> allTools = new ArrayList<>();

        // 获取传统Function工具
        if (functionList != null && !functionList.isEmpty()) {
            allTools.addAll(getAllFunctionTools(functionList));
        }

        // 获取MCP工具（自动识别用户上下文）
        if (mcpServerIds != null && !mcpServerIds.isEmpty()) {
            String userId = extractUserIdFromServiceIds(mcpServerIds);
            
            if (userId != null) {
                // 包含用户服务，获取用户工具
                List<String> actualServiceIds = extractActualServiceIds(mcpServerIds);
                allTools.addAll(getUserMcpTools(actualServiceIds, userId));
            } else {
                // 全局服务，获取全局工具
                allTools.addAll(getGlobalMcpTools(mcpServerIds));
            }
        }

        // 添加本地MCP工具
        for (McpToolInfo mcpTool : mcpToolCache.values()) {
            Tool tool = createMcpToolEntity(mcpTool);
            if (tool != null) {
                allTools.add(tool);
            }
        }

        return allTools;
    }

    /**
     * 显式指定用户ID的工具获取
     */
    public static List<Tool> getAllTools(List<String> functionList, List<String> mcpServerIds, String userId) {
        List<Tool> allTools = new ArrayList<>();

        // 获取传统Function工具
        if (functionList != null && !functionList.isEmpty()) {
            allTools.addAll(getAllFunctionTools(functionList));
        }

        // 获取MCP工具
        if (mcpServerIds != null && !mcpServerIds.isEmpty()) {
            if (userId != null && !userId.isEmpty()) {
                allTools.addAll(getUserMcpTools(mcpServerIds, userId));
            } else {
                allTools.addAll(getGlobalMcpTools(mcpServerIds));
            }
        }

        // 添加本地MCP工具
        for (McpToolInfo mcpTool : mcpToolCache.values()) {
            Tool tool = createMcpToolEntity(mcpTool);
            if (tool != null) {
                allTools.add(tool);
            }
        }

        return allTools;
    }
    /**
     * 获取全局MCP工具
     */
    private static List<Tool> getGlobalMcpTools(List<String> serviceIds) {
        List<Tool> tools = new ArrayList<>();
        
        McpGateway gateway = McpGateway.getInstance();
        if (gateway == null || !gateway.isInitialized()) {
            log.warn("MCP网关未初始化，无法获取全局MCP工具");
            return tools;
        }
        
        try {
            List<Tool.Function> mcpTools = gateway.getAvailableTools().join();
            
            // 转换为Tool对象
            for (Tool.Function function : mcpTools) {
                Tool tool = new Tool();
                tool.setType("function");
                tool.setFunction(function);
                tools.add(tool);
            }
        } catch (Exception e) {
            log.error("获取全局MCP工具失败", e);
        }

        return tools;
    }
    /**
     * 从服务ID列表中提取用户ID
     * @param serviceIds 如 ["user_123_service_github", "user_123_service_slack"]
     * @return 用户ID或null
     */
    private static String extractUserIdFromServiceIds(List<String> serviceIds) {
        for (String serviceId : serviceIds) {
            if (serviceId != null && serviceId.startsWith("user_") && serviceId.contains("_service_")) {
                String[] parts = serviceId.split("_service_");
                if (parts.length == 2) {
                    String userPart = parts[0]; // "user_123"
                    if (userPart.startsWith("user_")) {
                        return userPart.substring(5); // 提取 "123"
                    }
                }
            }
        }
        return null;
    }

    /**
     * 从用户服务ID列表中提取实际服务ID
     * @param serviceIds 如 ["user_123_service_github", "user_123_service_slack"]
     * @return 实际服务ID列表 如 ["github", "slack"]
     */
    private static List<String> extractActualServiceIds(List<String> serviceIds) {
        List<String> actualIds = new ArrayList<>();
        for (String serviceId : serviceIds) {
            if (serviceId != null && serviceId.contains("_service_")) {
                String[] parts = serviceId.split("_service_");
                if (parts.length == 2) {
                    actualIds.add(parts[1]); // 提取实际的serviceId
                }
            } else {
                actualIds.add(serviceId); // 全局服务ID
            }
        }
        return actualIds;
    }

   /**
     * 获取用户MCP工具
     */
    private static List<Tool> getUserMcpTools(List<String> serviceIds, String userId) {
        List<Tool> tools = new ArrayList<>();
        
        McpGateway gateway = McpGateway.getInstance();
        if (gateway == null || !gateway.isInitialized()) {
            log.warn("MCP网关未初始化，无法获取用户MCP工具");
            return tools;
        }
        
        try {
            List<Tool.Function> mcpTools = gateway.getUserAvailableTools(userId).join();
            
            // 转换为Tool对象
            for (Tool.Function function : mcpTools) {
                Tool tool = new Tool();
                tool.setType("function");
                tool.setFunction(function);
                tools.add(tool);
            }
        } catch (Exception e) {
            log.error("获取用户MCP工具失败: userId={}", userId, e);
        }

        return tools;
    }
    
    /**
     * 扫描传统Function工具
     */
    private static void scanFunctionTools() {
        try {
            Set<Class<?>> functionSet = reflections.getTypesAnnotatedWith(FunctionCall.class);

            for (Class<?> functionClass : functionSet) {
                try {
                    FunctionCall functionCall = functionClass.getAnnotation(FunctionCall.class);
                    if (functionCall != null) {
                        String functionName = functionCall.name();
                        toolClassMap.put(functionName, functionClass);

                        // 查找Request类
                        Class<?>[] innerClasses = functionClass.getDeclaredClasses();
                        for (Class<?> innerClass : innerClasses) {
                            if (innerClass.getAnnotation(FunctionRequest.class) != null) {
                                toolRequestMap.put(functionName, innerClass);
                                break;
                            }
                        }

                        log.debug("注册传统Function工具: {}", functionName);
                    }
                } catch (Exception e) {
                    log.error("处理Function类失败: {}", functionClass.getName(), e);
                }
            }

            log.info("扫描传统Function工具完成: {}个", toolClassMap.size());
        } catch (Exception e) {
            log.error("扫描传统Function工具失败", e);
        }
    }

    /**
     * 扫描本地MCP工具
     */
    private static void scanMcpTools() {
        try {
            Set<Class<?>> mcpServiceClasses = reflections.getTypesAnnotatedWith(McpService.class);

            for (Class<?> serviceClass : mcpServiceClasses) {
                try {
                    McpService mcpService = serviceClass.getAnnotation(McpService.class);
                    String serviceName = mcpService.name().isEmpty() ?
                            serviceClass.getSimpleName() : mcpService.name();

                    // 扫描方法
                    Method[] methods = serviceClass.getDeclaredMethods();
                    for (Method method : methods) {
                        McpTool mcpTool = method.getAnnotation(McpTool.class);
                        if (mcpTool != null) {
                            String toolName = mcpTool.name().isEmpty() ?
                                    method.getName() : mcpTool.name();

                            // 统一使用API友好的命名方式（下划线分隔）
                            String toolId = generateApiFunctionName(serviceName, toolName);

                            McpToolInfo toolInfo = new McpToolInfo(
                                    toolId, mcpTool.description(), serviceClass, method);
                            mcpToolCache.put(toolId, toolInfo);

                            log.debug("注册本地MCP工具: {}", toolId);
                        }
                    }
                } catch (Exception e) {
                    log.error("处理MCP服务类失败: {}", serviceClass.getName(), e);
                }
            }

            log.info("扫描本地MCP工具完成: {}个", mcpToolCache.size());
        } catch (Exception e) {
            log.error("扫描本地MCP工具失败", e);
        }
    }

    /**
     * 获取工具实体对象
     */
    public static Tool getToolEntity(String functionName) {
        if (functionName == null || functionName.trim().isEmpty()) {
            return null;
        }

        try {
            Tool.Function functionEntity = getFunctionEntity(functionName);
            if (functionEntity != null) {
                Tool tool = new Tool();
                tool.setType("function");
                tool.setFunction(functionEntity);
                return tool;
            }
        } catch (Exception e) {
            log.error("创建工具实体失败: {}", functionName, e);
        }
        return null;
    }


    /**
     * 创建本地MCP工具实体
     */
    private static Tool createMcpToolEntity(McpToolInfo mcpTool) {
        try {
            Tool.Function function = new Tool.Function();
            function.setName(mcpTool.toolId);  // 使用工具ID作为函数名
            function.setDescription(mcpTool.description);

            // 生成参数定义
            Map<String, Tool.Function.Property> parameters = new HashMap<>();
            List<String> requiredParameters = new ArrayList<>();

            Parameter[] methodParams = mcpTool.method.getParameters();
            for (Parameter param : methodParams) {
                McpParameter mcpParam = param.getAnnotation(McpParameter.class);
                String paramName = getParameterName(param);

                Tool.Function.Property property = createPropertyFromType(param.getType(),
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

        } catch (Exception e) {
            log.error("创建MCP工具实体失败: {}", mcpTool.toolId, e);
            return null;
        }
    }

    /**
     * 获取Function实体定义
     */
    public static Tool.Function getFunctionEntity(String functionName) {
        if (functionName == null || functionName.trim().isEmpty()) {
            return null;
        }

        try {
            Set<Class<?>> functionSet = reflections.getTypesAnnotatedWith(FunctionCall.class);

            for (Class<?> functionClass : functionSet) {
                try {
                    FunctionCall functionCall = functionClass.getAnnotation(FunctionCall.class);
                    if (functionCall != null && functionCall.name().equals(functionName)) {
                        Tool.Function function = new Tool.Function();
                        function.setName(functionCall.name());
                        function.setDescription(functionCall.description());

                        setFunctionParameters(function, functionClass);
                        toolClassMap.put(functionName, functionClass);
                        return function;
                    }
                } catch (Exception e) {
                    log.error("处理Function类失败: {}", functionClass.getName(), e);
                }
            }
        } catch (Exception e) {
            log.error("获取Function实体失败: {}", functionName, e);
        }
        return null;
    }



    /**
     * 生成符合OpenAI API规范的函数名
     * 规则：只能包含字母、数字、下划线和连字符，长度不超过64个字符
     */
    private static String generateApiFunctionName(String serviceName, String toolName) {
        // 将服务名和工具名组合，使用下划线连接
        String combined = serviceName + "_" + toolName;

        // 替换不符合规范的字符
        String normalized = combined
                .replaceAll("[^a-zA-Z0-9_-]", "_")  // 替换非法字符为下划线
                .replaceAll("_{2,}", "_")           // 多个连续下划线替换为单个
                .replaceAll("^_+|_+$", "");         // 移除开头和结尾的下划线

        // 确保长度不超过64个字符
        if (normalized.length() > 64) {
            normalized = normalized.substring(0, 64);
        }

        // 确保不为空且以字母开头
        if (normalized.isEmpty() || !Character.isLetter(normalized.charAt(0))) {
            normalized = "tool_" + normalized;
        }

        return normalized;
    }

    /**
     * 获取参数名称
     */
    private static String getParameterName(Parameter param) {
        McpParameter mcpParam = param.getAnnotation(McpParameter.class);
        if (mcpParam != null && !mcpParam.name().isEmpty()) {
            return mcpParam.name();
        }
        return param.getName();
    }

    /**
     * 参数值类型转换
     */
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
        } else if (targetType == Integer.class || targetType == int.class) {
            return Integer.valueOf(strValue);
        } else if (targetType == Long.class || targetType == long.class) {
            return Long.valueOf(strValue);
        } else if (targetType == Boolean.class || targetType == boolean.class) {
            return Boolean.valueOf(strValue);
        } else if (targetType == Double.class || targetType == double.class) {
            return Double.valueOf(strValue);
        } else if (targetType == Float.class || targetType == float.class) {
            return Float.valueOf(strValue);
        }

        return value;
    }

    /**
     * 设置Function参数定义
     */
    private static void setFunctionParameters(Tool.Function function, Class<?> functionClass) {
        try {
            Class<?>[] classes = functionClass.getDeclaredClasses();
            Map<String, Tool.Function.Property> parameters = new HashMap<>();
            List<String> requiredParameters = new ArrayList<>();

            for (Class<?> clazz : classes) {
                FunctionRequest request = clazz.getAnnotation(FunctionRequest.class);
                if (request == null) {
                    continue;
                }

                toolRequestMap.put(function.getName(), clazz);

                Field[] fields = clazz.getDeclaredFields();
                for (Field field : fields) {
                    FunctionParameter parameter = field.getAnnotation(FunctionParameter.class);
                    if (parameter == null) {
                        continue;
                    }

                    Tool.Function.Property property = createPropertyFromType(field.getType(), parameter.description());
                    parameters.put(field.getName(), property);

                    if (parameter.required()) {
                        requiredParameters.add(field.getName());
                    }
                }
            }

            Tool.Function.Parameter parameter = new Tool.Function.Parameter("object", parameters, requiredParameters);
            function.setParameters(parameter);

        } catch (Exception e) {
            log.error("设置Function参数失败: {}", function.getName(), e);
            throw new RuntimeException("设置Function参数失败: " + function.getName(), e);
        }
    }

    /**
     * 从类型创建属性对象
     */
    private static Tool.Function.Property createPropertyFromType(Class<?> fieldType, String description) {
        Tool.Function.Property property = new Tool.Function.Property();

        if (fieldType.isEnum()) {
            property.setType("string");
            property.setEnumValues(getEnumValues(fieldType));
        } else if (fieldType.equals(String.class)) {
            property.setType("string");
        } else if (fieldType.equals(int.class) || fieldType.equals(Integer.class) ||
                fieldType.equals(long.class) || fieldType.equals(Long.class) ||
                fieldType.equals(short.class) || fieldType.equals(Short.class)) {
            property.setType("integer");
        } else if (fieldType.equals(float.class) || fieldType.equals(Float.class) ||
                fieldType.equals(double.class) || fieldType.equals(Double.class)) {
            property.setType("number");
        } else if (fieldType.equals(boolean.class) || fieldType.equals(Boolean.class)) {
            property.setType("boolean");
        } else if (fieldType.isArray() || Collection.class.isAssignableFrom(fieldType)) {
            property.setType("array");

            Tool.Function.Property items = new Tool.Function.Property();
            Class<?> elementType = getArrayElementType(fieldType);
            if (elementType != null) {
                if (elementType == String.class) {
                    items.setType("string");
                } else if (elementType == Integer.class || elementType == int.class ||
                           elementType == Long.class || elementType == long.class) {
                    items.setType("integer");
                } else if (elementType == Double.class || elementType == double.class ||
                           elementType == Float.class || elementType == float.class) {
                    items.setType("number");
                } else if (elementType == Boolean.class || elementType == boolean.class) {
                    items.setType("boolean");
                } else {
                    items.setType("object");
                }
            } else {
                items.setType("object");
            }
            property.setItems(items);
        } else if (Map.class.isAssignableFrom(fieldType)) {
            property.setType("object");
        } else {
            property.setType("object");
        }

        property.setDescription(description);
        return property;
    }

    /**
     * 获取数组元素类型
     */
    private static Class<?> getArrayElementType(Class<?> arrayType) {
        if (arrayType.isArray()) {
            return arrayType.getComponentType();
        } else if (Collection.class.isAssignableFrom(arrayType)) {
            return null; // 泛型擦除，无法获取确切类型
        }
        return null;
    }

    /**
     * 获取枚举类型的所有可能值
     */
    private static List<String> getEnumValues(Class<?> enumType) {
        List<String> enumValues = new ArrayList<>();
        for (Object enumConstant : enumType.getEnumConstants()) {
            enumValues.add(enumConstant.toString());
        }
        return enumValues;
    }

    // ========== 向后兼容方法 ==========

    /**
     * 向后兼容的统一工具调用方法
     * @deprecated 请使用 invoke(String, String) 方法
     */
    @Deprecated
    public static String invokeUnified(String functionName, String argument) {
        return invoke(functionName, argument);
    }
}
