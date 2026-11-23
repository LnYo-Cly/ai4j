package io.github.lnyocly.ai4j.mcp.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @Author cly
 * @Description MCP资源参数注解，用于标记MCP资源方法的参数
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface McpResourceParameter {
    
    /**
     * 参数名称，对应URI模板中的占位符
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
}
