package io.github.lnyocly.ai4j.mcp.annotation;

import java.lang.annotation.*;

/**
 * @Author cly
 * @Description MCP服务注解，用于标记MCP服务类
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpService {

    /**
     * 服务名称
     */
    String name() default "";

    /**
     * 服务版本
     */
    String version() default "1.0.0";

    /**
     * 服务描述
     */
    String description() default "";

    /**
     * 服务端口（仅HTTP传输时使用）
     */
    int port() default 3000;

    /**
     * 传输类型：stdio, sse, streamable_http
     */
    String transport() default "stdio";

    /**
     * 是否自动启动
     */
    boolean autoStart() default true;
}
