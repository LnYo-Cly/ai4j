package io.github.lnyocly.ai4j.mcp.util;

import io.github.lnyocly.ai4j.mcp.annotation.McpResource;
import io.github.lnyocly.ai4j.mcp.annotation.McpResourceParameter;
import io.github.lnyocly.ai4j.mcp.annotation.McpService;
import io.github.lnyocly.ai4j.mcp.entity.McpResourceContent;
import lombok.extern.slf4j.Slf4j;
import org.reflections.Reflections;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @Author cly
 * @Description MCP资源适配器，用于管理和调用MCP资源
 */
@Slf4j
public class McpResourceAdapter {
    
    private static final Reflections reflections = new Reflections("io.github.lnyocly");
    
    // 资源缓存：URI模板 -> 资源定义
    private static final Map<String, io.github.lnyocly.ai4j.mcp.entity.McpResource> mcpResourceCache = new ConcurrentHashMap<>();
    
    // 资源类映射：URI模板 -> 服务类
    private static final Map<String, Class<?>> resourceClassMap = new ConcurrentHashMap<>();
    
    // 资源方法映射：URI模板 -> 方法
    private static final Map<String, Method> resourceMethodMap = new ConcurrentHashMap<>();
    
    // URI模板参数提取正则
    private static final Pattern URI_PARAM_PATTERN = Pattern.compile("\\{([^}]+)\\}");
    
    /**
     * 扫描并注册所有MCP服务中的资源
     */
    public static void scanAndRegisterMcpResources() {
        // 扫描@McpService注解的类
        Set<Class<?>> mcpServiceClasses = reflections.getTypesAnnotatedWith(McpService.class);
        
        for (Class<?> serviceClass : mcpServiceClasses) {
            registerMcpServiceResources(serviceClass);
        }
        
        log.info("MCP资源扫描完成，共注册 {} 个资源", mcpResourceCache.size());
    }
    
    /**
     * 注册指定服务类中的资源
     */
    private static void registerMcpServiceResources(Class<?> serviceClass) {
        McpService mcpService = serviceClass.getAnnotation(McpService.class);
        String serviceName = mcpService.name();
        
        // 扫描类中标记了@McpResource的方法
        Method[] methods = serviceClass.getDeclaredMethods();
        for (Method method : methods) {
            McpResource mcpResource = method.getAnnotation(McpResource.class);
            if (mcpResource != null) {
                String uriTemplate = mcpResource.uri();
                
                io.github.lnyocly.ai4j.mcp.entity.McpResource resourceDefinition = 
                    createResourceDefinitionFromMethod(method, mcpResource, serviceClass, uriTemplate);
                
                mcpResourceCache.put(uriTemplate, resourceDefinition);
                resourceClassMap.put(uriTemplate, serviceClass);
                resourceMethodMap.put(uriTemplate, method);
                
                log.debug("注册MCP资源: {} -> {}", uriTemplate, method.getName());
            }
        }
    }
    
    /**
     * 从方法创建资源定义
     */
    private static io.github.lnyocly.ai4j.mcp.entity.McpResource createResourceDefinitionFromMethod(
            Method method, McpResource mcpResource, Class<?> serviceClass, String uriTemplate) {
        
        return io.github.lnyocly.ai4j.mcp.entity.McpResource.builder()
                .uri(uriTemplate)
                .name(mcpResource.name())
                .description(mcpResource.description())
                .mimeType(mcpResource.mimeType().isEmpty() ? null : mcpResource.mimeType())
                .size(mcpResource.size() == -1L ? null : mcpResource.size())
                .build();
    }
    
    /**
     * 获取所有MCP资源列表
     */
    public static List<io.github.lnyocly.ai4j.mcp.entity.McpResource> getAllMcpResources() {
        return new ArrayList<>(mcpResourceCache.values());
    }
    
    /**
     * 读取指定URI的资源内容
     */
    public static McpResourceContent readMcpResource(String uri) {
        try {
            // 查找匹配的URI模板
            String matchedTemplate = findMatchingTemplate(uri);
            if (matchedTemplate == null) {
                throw new IllegalArgumentException("资源不存在: " + uri);
            }
            
            // 获取资源方法和类
            Method method = resourceMethodMap.get(matchedTemplate);
            Class<?> resourceClass = resourceClassMap.get(matchedTemplate);
            
            if (method == null || resourceClass == null) {
                throw new IllegalArgumentException("资源方法未找到: " + uri);
            }
            
            // 提取URI参数
            Map<String, String> uriParams = extractUriParameters(matchedTemplate, uri);
            
            // 调用资源方法
            Object result = invokeMcpResourceMethod(resourceClass, method, uriParams);
            
            // 构建资源内容
            io.github.lnyocly.ai4j.mcp.entity.McpResource resourceDef = mcpResourceCache.get(matchedTemplate);
            
            return McpResourceContent.builder()
                    .uri(uri)
                    .contents(result)
                    .mimeType(resourceDef.getMimeType())
                    .build();
                    
        } catch (Exception e) {
            log.error("读取MCP资源失败: {}", uri, e);
            throw new RuntimeException("读取资源失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 查找匹配URI的模板
     */
    private static String findMatchingTemplate(String uri) {
        for (String template : mcpResourceCache.keySet()) {
            if (uriMatchesTemplate(uri, template)) {
                return template;
            }
        }
        return null;
    }
    
    /**
     * 检查URI是否匹配模板
     */
    private static boolean uriMatchesTemplate(String uri, String template) {
        // 将模板转换为正则表达式
        String regex = template.replaceAll("\\{[^}]+\\}", "[^/]+");
        return uri.matches(regex);
    }
    
    /**
     * 从URI中提取参数
     */
    private static Map<String, String> extractUriParameters(String template, String uri) {
        Map<String, String> params = new HashMap<>();
        
        // 提取模板中的参数名
        List<String> paramNames = new ArrayList<>();
        Matcher matcher = URI_PARAM_PATTERN.matcher(template);
        while (matcher.find()) {
            paramNames.add(matcher.group(1));
        }
        
        // 构建正则表达式来提取参数值
        String regex = template;
        for (String paramName : paramNames) {
            regex = regex.replace("{" + paramName + "}", "([^/]+)");
        }
        
        // 提取参数值
        Pattern pattern = Pattern.compile(regex);
        Matcher uriMatcher = pattern.matcher(uri);
        if (uriMatcher.matches()) {
            for (int i = 0; i < paramNames.size(); i++) {
                params.put(paramNames.get(i), uriMatcher.group(i + 1));
            }
        }
        
        return params;
    }
    
    /**
     * 调用MCP资源方法
     */
    private static Object invokeMcpResourceMethod(Class<?> resourceClass, Method method, Map<String, String> uriParams) 
            throws Exception {
        
        // 创建服务实例
        Object serviceInstance = resourceClass.newInstance();
        
        // 准备方法参数
        Object[] methodArgs = prepareResourceMethodArguments(method, uriParams);
        
        // 调用方法
        method.setAccessible(true);
        return method.invoke(serviceInstance, methodArgs);
    }
    
    /**
     * 准备资源方法参数
     */
    private static Object[] prepareResourceMethodArguments(Method method, Map<String, String> uriParams) {
        Class<?>[] paramTypes = method.getParameterTypes();
        Parameter[] parameters = method.getParameters();
        Object[] result = new Object[paramTypes.length];
        
        for (int i = 0; i < parameters.length; i++) {
            McpResourceParameter resourceParam = parameters[i].getAnnotation(McpResourceParameter.class);
            String paramName = resourceParam != null && !resourceParam.name().isEmpty()
                    ? resourceParam.name()
                    : parameters[i].getName();
            
            String value = uriParams.get(paramName);
            result[i] = convertValue(value, paramTypes[i]);
        }
        
        return result;
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
            return com.alibaba.fastjson2.JSON.parseObject(stringValue, targetType);
        }
    }
    
    /**
     * 检查资源是否存在
     */
    public static boolean resourceExists(String uri) {
        return findMatchingTemplate(uri) != null;
    }
    
    /**
     * 获取资源定义
     */
    public static io.github.lnyocly.ai4j.mcp.entity.McpResource getResourceDefinition(String uri) {
        String template = findMatchingTemplate(uri);
        return template != null ? mcpResourceCache.get(template) : null;
    }
}
