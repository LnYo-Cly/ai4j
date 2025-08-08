package io.github.lnyocly.ai4j.mcp.util;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.mcp.annotation.McpPrompt;
import io.github.lnyocly.ai4j.mcp.annotation.McpPromptParameter;
import io.github.lnyocly.ai4j.mcp.annotation.McpService;
import io.github.lnyocly.ai4j.mcp.entity.McpPromptResult;
import lombok.extern.slf4j.Slf4j;
import org.reflections.Reflections;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Author cly
 * @Description MCP提示词适配器，用于管理和调用MCP提示词
 */
@Slf4j
public class McpPromptAdapter {
    
    private static final Reflections reflections = new Reflections("io.github.lnyocly");
    
    // 提示词缓存：提示词名称 -> 提示词定义
    private static final Map<String, io.github.lnyocly.ai4j.mcp.entity.McpPrompt> mcpPromptCache = new ConcurrentHashMap<>();
    
    // 提示词类映射：提示词名称 -> 服务类
    private static final Map<String, Class<?>> promptClassMap = new ConcurrentHashMap<>();
    
    // 提示词方法映射：提示词名称 -> 方法
    private static final Map<String, Method> promptMethodMap = new ConcurrentHashMap<>();
    
    /**
     * 扫描并注册所有MCP服务中的提示词
     */
    public static void scanAndRegisterMcpPrompts() {
        // 扫描@McpService注解的类
        Set<Class<?>> mcpServiceClasses = reflections.getTypesAnnotatedWith(McpService.class);
        
        for (Class<?> serviceClass : mcpServiceClasses) {
            registerMcpServicePrompts(serviceClass);
        }
        
        log.info("MCP提示词扫描完成，共注册 {} 个提示词", mcpPromptCache.size());
    }
    
    /**
     * 注册指定服务类中的提示词
     */
    private static void registerMcpServicePrompts(Class<?> serviceClass) {
        McpService mcpService = serviceClass.getAnnotation(McpService.class);
        String serviceName = mcpService.name();
        
        // 扫描类中标记了@McpPrompt的方法
        Method[] methods = serviceClass.getDeclaredMethods();
        for (Method method : methods) {
            McpPrompt mcpPrompt = method.getAnnotation(McpPrompt.class);
            if (mcpPrompt != null) {
                String promptName = mcpPrompt.name().isEmpty() ? method.getName() : mcpPrompt.name();
                String fullPromptName = serviceName + "." + promptName;
                
                io.github.lnyocly.ai4j.mcp.entity.McpPrompt promptDefinition = 
                    createPromptDefinitionFromMethod(method, mcpPrompt, serviceClass, fullPromptName);
                
                mcpPromptCache.put(fullPromptName, promptDefinition);
                promptClassMap.put(fullPromptName, serviceClass);
                promptMethodMap.put(fullPromptName, method);
                
                log.debug("注册MCP提示词: {} -> {}", fullPromptName, method.getName());
            }
        }
    }
    
    /**
     * 从方法创建提示词定义
     */
    private static io.github.lnyocly.ai4j.mcp.entity.McpPrompt createPromptDefinitionFromMethod(
            Method method, McpPrompt mcpPrompt, Class<?> serviceClass, String fullPromptName) {
        
        // 构建参数Schema
        Map<String, Object> arguments = new HashMap<>();
        Parameter[] parameters = method.getParameters();
        
        for (Parameter parameter : parameters) {
            McpPromptParameter promptParam = parameter.getAnnotation(McpPromptParameter.class);
            if (promptParam != null) {
                String paramName = promptParam.name().isEmpty() ? parameter.getName() : promptParam.name();

                Map<String, Object> paramSchema = new HashMap<>();
                paramSchema.put("name", paramName);
                paramSchema.put("description", promptParam.description());
                paramSchema.put("required", promptParam.required());
                paramSchema.put("type", getJsonSchemaType(parameter.getType()));

                if (!promptParam.defaultValue().isEmpty()) {
                    paramSchema.put("default", promptParam.defaultValue());
                }

                arguments.put(paramName, paramSchema);
            }
        }
        
        return io.github.lnyocly.ai4j.mcp.entity.McpPrompt.builder()
                .name(fullPromptName)
                .description(mcpPrompt.description())
                .arguments(arguments)
                .build();
    }
    
    /**
     * 获取所有MCP提示词列表
     */
    public static List<io.github.lnyocly.ai4j.mcp.entity.McpPrompt> getAllMcpPrompts() {
        return new ArrayList<>(mcpPromptCache.values());
    }
    
    /**
     * 获取指定提示词
     */
    public static McpPromptResult getMcpPrompt(String promptName, Map<String, Object> arguments) {
        try {
            // 检查提示词是否存在
            if (!mcpPromptCache.containsKey(promptName)) {
                throw new IllegalArgumentException("提示词不存在: " + promptName);
            }
            
            // 获取提示词方法和类
            Method method = promptMethodMap.get(promptName);
            Class<?> promptClass = promptClassMap.get(promptName);
            
            if (method == null || promptClass == null) {
                throw new IllegalArgumentException("提示词方法未找到: " + promptName);
            }
            
            // 调用提示词方法
            Object result = invokeMcpPromptMethod(promptClass, method, arguments);
            
            // 构建提示词结果
            io.github.lnyocly.ai4j.mcp.entity.McpPrompt promptDef = mcpPromptCache.get(promptName);
            
            return McpPromptResult.builder()
                    .name(promptName)
                    .content(result != null ? result.toString() : "")
                    .description(promptDef.getDescription())
                    .build();
                    
        } catch (Exception e) {
            log.error("获取MCP提示词失败: {}", promptName, e);
            throw new RuntimeException("获取提示词失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 调用MCP提示词方法
     */
    private static Object invokeMcpPromptMethod(Class<?> promptClass, Method method, Map<String, Object> arguments) 
            throws Exception {
        
        // 创建服务实例
        Object serviceInstance = promptClass.newInstance();
        
        // 准备方法参数
        Object[] methodArgs = preparePromptMethodArguments(method, arguments);
        
        // 调用方法
        method.setAccessible(true);
        return method.invoke(serviceInstance, methodArgs);
    }
    
    /**
     * 准备提示词方法参数
     */
    private static Object[] preparePromptMethodArguments(Method method, Map<String, Object> arguments) {
        Class<?>[] paramTypes = method.getParameterTypes();
        Parameter[] parameters = method.getParameters();
        Object[] result = new Object[paramTypes.length];
        
        for (int i = 0; i < parameters.length; i++) {
            McpPromptParameter promptParam = parameters[i].getAnnotation(McpPromptParameter.class);
            String paramName = promptParam != null && !promptParam.name().isEmpty()
                    ? promptParam.name()
                    : parameters[i].getName();
            
            Object value = arguments != null ? arguments.get(paramName) : null;
            
            // 如果没有提供值，使用默认值
            if (value == null && promptParam != null && !promptParam.defaultValue().isEmpty()) {
                value = promptParam.defaultValue();
            }
            
            result[i] = convertValue(value, paramTypes[i]);
        }
        
        return result;
    }
    
    /**
     * 获取Java类型对应的JSON Schema类型
     */
    private static String getJsonSchemaType(Class<?> javaType) {
        if (javaType == String.class) {
            return "string";
        } else if (javaType == Integer.class || javaType == int.class ||
                   javaType == Long.class || javaType == long.class) {
            return "integer";
        } else if (javaType == Double.class || javaType == double.class ||
                   javaType == Float.class || javaType == float.class) {
            return "number";
        } else if (javaType == Boolean.class || javaType == boolean.class) {
            return "boolean";
        } else if (javaType.isArray() || java.util.List.class.isAssignableFrom(javaType)) {
            return "array";
        } else {
            return "object";
        }
    }

    /**
     * 转换参数值类型
     */
    private static Object convertValue(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }

        String stringValue = value.toString();

        if (targetType == String.class) {
            return stringValue;
        } else if (targetType == Integer.class || targetType == int.class) {
            return Integer.parseInt(stringValue);
        } else if (targetType == Long.class || targetType == long.class) {
            return Long.parseLong(stringValue);
        } else if (targetType == Boolean.class || targetType == boolean.class) {
            return Boolean.parseBoolean(stringValue);
        } else if (targetType == Double.class || targetType == double.class) {
            return Double.parseDouble(stringValue);
        } else {
            // 尝试JSON反序列化
            return JSON.parseObject(stringValue, targetType);
        }
    }
    
    /**
     * 检查提示词是否存在
     */
    public static boolean promptExists(String promptName) {
        return mcpPromptCache.containsKey(promptName);
    }
    
    /**
     * 获取提示词定义
     */
    public static io.github.lnyocly.ai4j.mcp.entity.McpPrompt getPromptDefinition(String promptName) {
        return mcpPromptCache.get(promptName);
    }
}
