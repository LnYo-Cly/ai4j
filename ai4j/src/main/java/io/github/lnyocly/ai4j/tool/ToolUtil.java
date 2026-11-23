package io.github.lnyocly.ai4j.tool;

/**
 * @Author cly
 * @Description 统一工具管理器，支持 built-in tool、传统Function工具、本地MCP工具和远程MCP服务
 */
import io.github.lnyocly.ai4j.annotation.FunctionCall;
import io.github.lnyocly.ai4j.annotation.FunctionParameter;
import io.github.lnyocly.ai4j.annotation.FunctionRequest;
import io.github.lnyocly.ai4j.codec.JsonCodec;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ToolUtil {

    private static final Logger log = LoggerFactory.getLogger(ToolUtil.class);
    private static final String SCAN_BASE_PACKAGE = "io.github.lnyocly.ai4j";

    // === 传统Function工具缓存 ===
    public static final Map<String, Tool> toolEntityMap = new ConcurrentHashMap<>();
    public static final Map<String, Class<?>> toolClassMap = new ConcurrentHashMap<>();
    public static final Map<String, Class<?>> toolRequestMap = new ConcurrentHashMap<>();

    // === 可选 MCP 桥接 ===
    private static volatile McpToolBridge mcpToolBridge;

    // === 初始化状态 ===
    private static volatile boolean initialized = false;

    private static final ThreadLocal<Deque<BuiltInToolContext>> builtInToolContextStack =
            new ThreadLocal<Deque<BuiltInToolContext>>();

    /**
     * 统一工具调用入口（支持自动识别用户上下文）
     */
    public static String invoke(String functionName, String argument) {
        return invoke(functionName, argument, currentBuiltInToolContext());
    }

    public static String invoke(String functionName, String argument, BuiltInToolContext builtInToolContext) {
        ensureInitialized();

        try {
            String builtInResult = BuiltInToolExecutor.invoke(functionName, argument, builtInToolContext);
            if (builtInResult != null) {
                return builtInResult;
            }

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

    public static void pushBuiltInToolContext(BuiltInToolContext context) {
        if (context == null) {
            return;
        }
        Deque<BuiltInToolContext> stack = builtInToolContextStack.get();
        if (stack == null) {
            stack = new ArrayDeque<BuiltInToolContext>();
            builtInToolContextStack.set(stack);
        }
        stack.push(context);
    }

    public static void popBuiltInToolContext() {
        Deque<BuiltInToolContext> stack = builtInToolContextStack.get();
        if (stack == null || stack.isEmpty()) {
            builtInToolContextStack.remove();
            return;
        }
        stack.pop();
        if (stack.isEmpty()) {
            builtInToolContextStack.remove();
        }
    }

    private static BuiltInToolContext currentBuiltInToolContext() {
        Deque<BuiltInToolContext> stack = builtInToolContextStack.get();
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return stack.peek();
    }

    private static McpToolBridge findMcpToolBridge() {
        McpToolBridge cached = mcpToolBridge;
        if (cached != null) {
            return cached;
        }
        synchronized (ToolUtil.class) {
            if (mcpToolBridge != null) {
                return mcpToolBridge;
            }
            McpToolBridge loaded = loadMcpToolBridge(Thread.currentThread().getContextClassLoader());
            if (loaded == null) {
                loaded = loadMcpToolBridge(ToolUtil.class.getClassLoader());
            }
            if (loaded != null) {
                mcpToolBridge = loaded;
            }
            return mcpToolBridge;
        }
    }

    private static McpToolBridge loadMcpToolBridge(ClassLoader classLoader) {
        ServiceLoader<McpToolBridge> loader = classLoader != null
                ? ServiceLoader.load(McpToolBridge.class, classLoader)
                : ServiceLoader.load(McpToolBridge.class);
        Iterator<McpToolBridge> iterator = loader.iterator();
        while (iterator.hasNext()) {
            try {
                McpToolBridge candidate = iterator.next();
                if (candidate != null) {
                    return candidate;
                }
            } catch (ServiceConfigurationError e) {
                log.debug("Failed to load McpToolBridge provider", e);
            }
        }
        return null;
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
        McpToolBridge bridge = findMcpToolBridge();
        if (bridge == null || !bridge.isGatewayInitialized()) {
            throw new RuntimeException("MCP网关未初始化");
        }
        return bridge.invokeUserMcpTool(userId, toolName, argument);
    }

    /**
     * 调用全局工具
     */
    private static String callGlobalTool(String functionName, String argument) {
        McpToolBridge bridge = findMcpToolBridge();

        // 1. 优先检查本地MCP工具
        if (bridge != null && bridge.hasLocalMcpTool(functionName)) {
            return bridge.invokeLocalMcpTool(functionName, argument);
        }

        // 2. 检查传统Function工具
        if (toolClassMap.containsKey(functionName) && toolRequestMap.containsKey(functionName)) {
            return invokeFunctionTool(functionName, argument);
        }

        // 3. 尝试调用全局MCP服务
        if (bridge != null && bridge.isGatewayInitialized()) {
            return bridge.invokeGlobalMcpTool(functionName, argument);
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

        McpToolBridge bridge = findMcpToolBridge();
        int localMcpToolCount = bridge != null ? bridge.getLocalMcpTools().size() : 0;

        log.info("工具扫描完成 - Function工具: {}, 本地MCP工具: {}",
                toolClassMap.size(), localMcpToolCount);
    }

    /**
     * 调用传统Function工具
     */
    private static String invokeFunctionTool(String functionName, String argument) {
        Class<?> functionClass = toolClassMap.get(functionName);
        Class<?> functionRequestClass = toolRequestMap.get(functionName);

        log.info("Call Function Name: {}, arguments: {}", functionName, argument);

        try {
            // 获取调用函数
            Method apply = functionClass.getMethod("apply", functionRequestClass);

            // 解析参数
            Object arg = (argument == null || argument.trim().isEmpty())
                    ? null
                    : JsonCodec.fromJsonLenient(argument, functionRequestClass);

            // 调用函数
            Object functionInstance = functionClass.getDeclaredConstructor().newInstance();
            Object result = apply.invoke(functionInstance, arg);

            String response = JsonCodec.toJson(result);
            log.info("Call Function Success: {} -> {}", functionName, response);
            return response;

        } catch (Exception e) {
            log.error("Call Function Name: {} failed", functionName, e);
            throw new RuntimeException("Call Function Error: " + functionName, e);
        }
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

        log.debug("获取{}个Function工具", functionList.size());

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

        log.info("获取Function工具完成: 请求{}个，成功{}个", functionList.size(), tools.size());
        return tools;
    }

    /**
     * 根据MCP服务ID列表获取所有MCP工具
     *
     * @param mcpServerIds MCP服务ID列表
     * @return 工具列表
     */
    public static List<Tool> getAllMcpTools(List<String> mcpServerIds) {
        if (mcpServerIds == null || mcpServerIds.isEmpty()) {
            return new ArrayList<>();
        }
        McpToolBridge bridge = findMcpToolBridge();
        if (bridge == null) {
            return new ArrayList<>();
        }
        return bridge.getAllMcpTools(mcpServerIds);
    }

    /**
     * 获取所有工具（自动识别用户上下文）
     */
    public static List<Tool> getAllTools(List<String> functionList, List<String> mcpServerIds) {
        // fix 启动MCP服务工具未初始化
        ensureInitialized();

        List<Tool> allTools = new ArrayList<>();

        if (functionList != null && !functionList.isEmpty()) {
            allTools.addAll(getAllFunctionTools(functionList));
        }

        if (mcpServerIds != null && !mcpServerIds.isEmpty()) {
            String userId = extractUserIdFromServiceIds(mcpServerIds);
            if (userId != null) {
                List<String> actualServiceIds = extractActualServiceIds(mcpServerIds);
                allTools.addAll(getUserMcpTools(actualServiceIds, userId));
            } else {
                allTools.addAll(getGlobalMcpTools(mcpServerIds));
            }
        }

        return allTools;
    }

    /**
     * 显式指定用户ID的工具获取
     */
    public static List<Tool> getAllTools(List<String> functionList, List<String> mcpServerIds, String userId) {
        ensureInitialized();
        List<Tool> allTools = new ArrayList<>();

        if (functionList != null && !functionList.isEmpty()) {
            allTools.addAll(getAllFunctionTools(functionList));
        }

        if (mcpServerIds != null && !mcpServerIds.isEmpty()) {
            if (userId != null && !userId.isEmpty()) {
                allTools.addAll(getUserMcpTools(mcpServerIds, userId));
            } else {
                allTools.addAll(getGlobalMcpTools(mcpServerIds));
            }
        }

        return allTools;
    }

    /**
     * Get all local MCP tools.
     */
    public static List<Tool> getLocalMcpTools() {
        ensureInitialized();
        McpToolBridge bridge = findMcpToolBridge();
        if (bridge == null) {
            return new ArrayList<>();
        }
        return bridge.getLocalMcpTools();
    }

    /**
     * 获取全局MCP工具
     */
    private static List<Tool> getGlobalMcpTools(List<String> serviceIds) {
        McpToolBridge bridge = findMcpToolBridge();
        if (bridge == null) {
            return new ArrayList<>();
        }
        return bridge.getGlobalMcpTools(serviceIds);
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
        McpToolBridge bridge = findMcpToolBridge();
        if (bridge == null) {
            return new ArrayList<>();
        }
        return bridge.getUserMcpTools(serviceIds, userId);
    }

    /**
     * 扫描传统Function工具
     */
    private static void scanFunctionTools() {
        try {
            Set<Class<?>> functionSet = ClassPathAnnotationScanner.findTypesAnnotatedWith(
                    SCAN_BASE_PACKAGE, FunctionCall.class);

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

                        log.debug("注册Function工具: {}", functionName);
                    }
                } catch (Exception e) {
                    log.error("处理Function类失败: {}", functionClass.getName(), e);
                }
            }

            log.info("扫描Function工具完成: {}个", toolClassMap.size());
        } catch (Exception e) {
            log.error("扫描Function工具失败", e);
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
     * 获取Function实体定义
     */
    public static Tool.Function getFunctionEntity(String functionName) {
        if (functionName == null || functionName.trim().isEmpty()) {
            return null;
        }

        try {
            Set<Class<?>> functionSet = ClassPathAnnotationScanner.findTypesAnnotatedWith(
                    SCAN_BASE_PACKAGE, FunctionCall.class);

            for (Class<?> functionClass : functionSet) {
                try {
                    FunctionCall functionCall = functionClass.getAnnotation(FunctionCall.class);
                    if (functionCall != null && functionCall.name().equals(functionName)) {
                        Tool.Function function = new Tool.Function();
                        function.setName(functionCall.name());
                        function.setDescription(functionCall.description());

                        setFunctionParameters(function, functionClass);
                        if (functionCall.strict()) {
                            applyStrictMode(function);
                        }
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
     * 开启严格模式：调整 schema 以满足 OpenAI 的硬性要求
     * （{@code additionalProperties=false}、所有字段列入 {@code required}、
     * 可选字段标为可空），并把 {@code strict} 置为 {@code true}。
     */
    private static void applyStrictMode(Tool.Function function) {
        Tool.Function.Parameter params = function.getParameters();
        if (params != null) {
            params.enforceStrictSchema();
        }
        function.setStrict(Boolean.TRUE);
    }

    /**
     * 从类型创建属性对象
     */
    public static Tool.Function.Property createPropertyFromType(Class<?> fieldType, String description) {
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
