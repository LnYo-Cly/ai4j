package io.github.lnyocly.ai4j.mcp.annotation;

import java.lang.annotation.*;

/**
 * @Author cly
 * @Description MCP参数注解，用于标记MCP工具方法的参数
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpParameter {

    /**
     * 参数名称，如果不指定则使用参数名
     */
    String name() default "";

    /**
     * 参数描述
     */
    String description() default "";

    /**
     * 是否必需参数
     */
    boolean required() default true;

    /**
     * 参数默认值
     * 在MCP工具调用时使用
     */
    String defaultValue() default "";
}
