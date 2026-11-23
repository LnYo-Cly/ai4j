package io.github.lnyocly.ai4j.mcp.annotation;

import java.lang.annotation.*;

/**
 * @Author cly
 * @Description MCP工具注解，用于标记MCP服务中的工具方法
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpTool {

    /**
     * 工具名称，如果不指定则使用方法名
     */
    String name() default "";

    /**
     * 工具描述
     */
    String description() default "";

    /**
     * 工具输入Schema的JSON字符串
     * 如果不指定，将根据方法参数自动生成
     */
    String inputSchema() default "";
}
