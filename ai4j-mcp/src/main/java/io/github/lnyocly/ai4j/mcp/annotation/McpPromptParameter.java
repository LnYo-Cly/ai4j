package io.github.lnyocly.ai4j.mcp.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @Author cly
 * @Description MCP提示词参数注解，用于标记MCP提示词方法的参数
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface McpPromptParameter {
    
    /**
     * 参数名称
     */
    String name();
    
    /**
     * 参数描述
     */
    String description() default "";
    
    /**
     * 是否必需
     */
    boolean required() default true;

    /**
     * 默认值
     */
    String defaultValue() default "";
}
