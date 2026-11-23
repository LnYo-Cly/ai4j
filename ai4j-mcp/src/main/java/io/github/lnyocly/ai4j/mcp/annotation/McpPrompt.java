package io.github.lnyocly.ai4j.mcp.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @Author cly
 * @Description MCP提示词注解，用于标记MCP服务中的提示词方法
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface McpPrompt {
    
    /**
     * 提示词名称
     */
    String name();
    
    /**
     * 提示词描述
     */
    String description() default "";
    
    /**
     * 提示词类型 (user, assistant, system)
     */
    String role() default "user";
    
    /**
     * 是否支持动态参数
     */
    boolean dynamic() default true;
}
