package io.github.lnyocly.ai4j.mcp.util;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.mcp.annotation.McpParameter;
import io.github.lnyocly.ai4j.mcp.annotation.McpService;
import io.github.lnyocly.ai4j.mcp.annotation.McpTool;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Author cly
 * @Description Local MCP tool adapter for scanning, registering and invoking local MCP tools
 */
public class McpToolAdapter {

    private static final Logger log = LoggerFactory.getLogger(McpToolAdapter.class);
    
    // Reflection tool for scanning annotations
    private static final Reflections reflections = new Reflections(new ConfigurationBuilder()
            .setUrls(ClasspathHelper.forPackage(""))
            .setScanners(Scanners.TypesAnnotated, Scanners.MethodsAnnotated));
    
    // Local MCP tool cache
    private static final Map<String, Tool> localMcpToolCache = new ConcurrentHashMap<>();
    private static final Map<String, Method> localMcpMethodCache = new ConcurrentHashMap<>();
    private static final Map<String, Class<?>> localMcpClassCache = new ConcurrentHashMap<>();
    
    // Initialization flag
    private static volatile boolean initialized = false;
    private static final Object initLock = new Object();

    /**
     * Scan and register all local MCP tools
     */
    public static void scanAndRegisterMcpTools() {
        if (initialized) {
            return;
        }
        
        synchronized (initLock) {
            if (initialized) {
                return;
            }
            
            log.info("Starting to scan local MCP tools...");
            long startTime = System.currentTimeMillis();
            
            try {
                // Get all classes marked with @McpService
                Set<Class<?>> mcpServiceClasses = reflections.getTypesAnnotatedWith(McpService.class);
                
                int toolCount = 0;
                for (Class<?> serviceClass : mcpServiceClasses) {
                    McpService mcpService = serviceClass.getAnnotation(McpService.class);
                    log.debug("Found MCP service: {} - {}", mcpService.name(), serviceClass.getName());
                    
                    // Scan tool methods in service class
                    Method[] methods = serviceClass.getDeclaredMethods();
                    for (Method method : methods) {
                        McpTool mcpTool = method.getAnnotation(McpTool.class);
                        if (mcpTool != null) {
                            String toolName = mcpTool.name().isEmpty() ? method.getName() : mcpTool.name();
                            
                            // Create Tool object
                            Tool tool = createToolFromMethod(method, mcpTool, serviceClass);
                            if (tool != null) {
                                // Cache tool information
                                localMcpToolCache.put(toolName, tool);
                                localMcpMethodCache.put(toolName, method);
                                localMcpClassCache.put(toolName, serviceClass);
                                
                                toolCount++;
                                log.debug("Registered local MCP tool: {} -> {}.{}", toolName, serviceClass.getSimpleName(), method.getName());
                            }
                        }
                    }
                }
                
                initialized = true;
                log.info("Local MCP tool scanning completed, registered {} tools, took {} ms", 
                        toolCount, System.currentTimeMillis() - startTime);
                
            } catch (Exception e) {
                log.error("Failed to scan local MCP tools", e);
                throw new RuntimeException("Failed to scan local MCP tools", e);
            }
        }
    }

    /**
     * Get all local MCP tools
     */
    public static List<Tool> getAllMcpTools() {
        ensureInitialized();
        return new ArrayList<>(localMcpToolCache.values());
    }

    /**
     * Invoke local MCP tool
     */
    public static String invokeMcpTool(String toolName, String arguments) {
        ensureInitialized();
        
        long startTime = System.currentTimeMillis();
        log.info("Invoking local MCP tool: {}, arguments: {}", toolName, arguments);
        
        try {
            Method method = localMcpMethodCache.get(toolName);
            Class<?> serviceClass = localMcpClassCache.get(toolName);
            
            if (method == null || serviceClass == null) {
                throw new IllegalArgumentException("Local MCP tool not found: " + toolName);
            }
            
            // Create service instance
            Object serviceInstance = serviceClass.newInstance();
            
            // Parse arguments
            Object[] methodArgs = parseMethodArguments(method, arguments);
            
            // Invoke method
            Object result = method.invoke(serviceInstance, methodArgs);
            
            // Convert result to JSON string
            String response = JSON.toJSONString(result);
            log.info("Local MCP tool invocation successful: {}, response: {}, took: {} ms", 
                    toolName, response, System.currentTimeMillis() - startTime);
            
            return response;
            
        } catch (Exception e) {
            log.error("Local MCP tool invocation failed: {} - {}", toolName, e.getMessage(), e);
            throw new RuntimeException("Local MCP tool invocation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Check if local MCP tool exists
     */
    public static boolean mcpToolExists(String toolName) {
        ensureInitialized();
        return localMcpToolCache.containsKey(toolName);
    }

    /**
     * Get detailed information of local MCP tool
     */
    public static Tool getMcpTool(String toolName) {
        ensureInitialized();
        return localMcpToolCache.get(toolName);
    }

    /**
     * Get all local MCP tool names list
     */
    public static Set<String> getAllMcpToolNames() {
        ensureInitialized();
        return new HashSet<>(localMcpToolCache.keySet());
    }

    /**
     * Clear cache and rescan
     */
    public static void refresh() {
        synchronized (initLock) {
            localMcpToolCache.clear();
            localMcpMethodCache.clear();
            localMcpClassCache.clear();
            initialized = false;
        }
        scanAndRegisterMcpTools();
    }

    /**
     * Ensure initialized
     */
    private static void ensureInitialized() {
        if (!initialized) {
            scanAndRegisterMcpTools();
        }
    }

    /**
     * Create Tool object from method
     */
    private static Tool createToolFromMethod(Method method, McpTool mcpTool, Class<?> serviceClass) {
        try {
            String toolName = mcpTool.name().isEmpty() ? method.getName() : mcpTool.name();
            String description = mcpTool.description();
            
            // Create Function object
            Tool.Function function = new Tool.Function();
            function.setName(toolName);
            function.setDescription(description);
            
            // Create parameter definition
            Tool.Function.Parameter parameters = createParametersFromMethod(method);
            function.setParameters(parameters);
            
            // Create Tool object
            Tool tool = new Tool();
            tool.setType("function");
            tool.setFunction(function);
            
            return tool;
            
        } catch (Exception e) {
            log.error("Failed to create tool definition: {}.{} - {}", serviceClass.getSimpleName(), method.getName(), e.getMessage());
            return null;
        }
    }

    /**
     * Create parameter definition from method
     */
    private static Tool.Function.Parameter createParametersFromMethod(Method method) {
        Map<String, Tool.Function.Property> properties = new HashMap<>();
        List<String> requiredParameters = new ArrayList<>();
        
        Parameter[] parameters = method.getParameters();
        for (Parameter parameter : parameters) {
            McpParameter mcpParam = parameter.getAnnotation(McpParameter.class);
            
            String paramName = (mcpParam != null && !mcpParam.name().isEmpty()) 
                    ? mcpParam.name() 
                    : parameter.getName();
            
            String paramDescription = (mcpParam != null) 
                    ? mcpParam.description() 
                    : "";
            
            boolean required = (mcpParam == null) || mcpParam.required();
            
            // Create property object
            Tool.Function.Property property = createPropertyFromParameter(parameter.getType(), paramDescription);
            properties.put(paramName, property);
            
            if (required) {
                requiredParameters.add(paramName);
            }
        }
        
        return new Tool.Function.Parameter("object", properties, requiredParameters);
    }

    /**
     * Create property object from parameter type
     */
    private static Tool.Function.Property createPropertyFromParameter(Class<?> paramType, String description) {
        Tool.Function.Property property = new Tool.Function.Property();
        property.setDescription(description);
        
        if (paramType.isEnum()) {
            property.setType("string");
            property.setEnumValues(getEnumValues(paramType));
        } else if (paramType.equals(String.class)) {
            property.setType("string");
        } else if (paramType.equals(int.class) || paramType.equals(Integer.class) ||
                paramType.equals(long.class) || paramType.equals(Long.class) ||
                paramType.equals(short.class) || paramType.equals(Short.class)) {
            property.setType("integer");
        } else if (paramType.equals(float.class) || paramType.equals(Float.class) ||
                paramType.equals(double.class) || paramType.equals(Double.class)) {
            property.setType("number");
        } else if (paramType.equals(boolean.class) || paramType.equals(Boolean.class)) {
            property.setType("boolean");
        } else if (paramType.isArray() || Collection.class.isAssignableFrom(paramType)) {
            property.setType("array");
            // Add items definition for array type
            Tool.Function.Property items = new Tool.Function.Property();
            items.setType("object"); // Default to object type
            property.setItems(items);
        } else if (Map.class.isAssignableFrom(paramType)) {
            property.setType("object");
        } else {
            property.setType("object");
        }
        
        return property;
    }

    /**
     * Get all possible values of enum type
     */
    private static List<String> getEnumValues(Class<?> enumType) {
        List<String> enumValues = new ArrayList<>();
        for (Object enumConstant : enumType.getEnumConstants()) {
            enumValues.add(enumConstant.toString());
        }
        return enumValues;
    }

    /**
     * Parse method arguments
     */
    private static Object[] parseMethodArguments(Method method, String arguments) {
        Parameter[] parameters = method.getParameters();
        if (parameters.length == 0) {
            return new Object[0];
        }
        
        try {
            // Parse JSON arguments
            Map<String, Object> argMap = JSON.parseObject(arguments, Map.class);
            Object[] methodArgs = new Object[parameters.length];
            
            for (int i = 0; i < parameters.length; i++) {
                Parameter parameter = parameters[i];
                McpParameter mcpParam = parameter.getAnnotation(McpParameter.class);
                
                String paramName = (mcpParam != null && !mcpParam.name().isEmpty()) 
                        ? mcpParam.name() 
                        : parameter.getName();
                
                Object value = argMap.get(paramName);
                
                // If parameter is null and has default value, use default value
                if (value == null && mcpParam != null && !mcpParam.defaultValue().isEmpty()) {
                    value = mcpParam.defaultValue();
                }
                
                // Type conversion
                methodArgs[i] = convertValue(value, parameter.getType());
            }
            
            return methodArgs;
            
        } catch (Exception e) {
            log.error("Failed to parse method arguments: {} - {}", method.getName(), e.getMessage());
            throw new RuntimeException("Failed to parse method arguments: " + e.getMessage(), e);
        }
    }

    /**
     * Value type conversion
     */
    private static Object convertValue(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }
        
        if (targetType.isAssignableFrom(value.getClass())) {
            return value;
        }
        
        try {
            if (targetType == String.class) {
                return value.toString();
            } else if (targetType == int.class || targetType == Integer.class) {
                return Integer.valueOf(value.toString());
            } else if (targetType == long.class || targetType == Long.class) {
                return Long.valueOf(value.toString());
            } else if (targetType == double.class || targetType == Double.class) {
                return Double.valueOf(value.toString());
            } else if (targetType == float.class || targetType == Float.class) {
                return Float.valueOf(value.toString());
            } else if (targetType == boolean.class || targetType == Boolean.class) {
                return Boolean.valueOf(value.toString());
            } else {
                // For complex objects, try JSON conversion
                return JSON.parseObject(JSON.toJSONString(value), targetType);
            }
        } catch (Exception e) {
            log.warn("Type conversion failed: {} -> {}, using original value", value.getClass(), targetType);
            return value;
        }
    }
}