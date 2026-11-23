package io.github.lnyocly.ai4j.mcp.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @Author cly
 * @Description MCP资源注解，用于标记MCP服务中的资源方法
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface McpResource {
    
    /**
     * 资源URI模板，支持参数占位符
     * 例如: "file://{path}", "database://users/{id}"
     */
    String uri();
    
    /**
     * 资源名称
     */
    String name();
    
    /**
     * 资源描述
     */
    String description() default "";
    
    /**
     * 资源MIME类型
     */
    String mimeType() default "";
    
    /**
     * 是否支持订阅变更通知
     */
    boolean subscribable() default false;
    
    /**
     * 资源大小（字节），-1表示未知
     */
    long size() default -1L;
}
