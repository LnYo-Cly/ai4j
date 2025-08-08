package io.github.lnyocly.ai4j.mcp.util;

import io.github.lnyocly.ai4j.mcp.annotation.McpParameter;
import io.github.lnyocly.ai4j.mcp.annotation.McpService;
import io.github.lnyocly.ai4j.mcp.annotation.McpTool;

/**
 * @Author cly
 * @Description 测试用的MCP服务
 * @Date 2024/12/29 02:30
 */
@McpService(
    name = "test-service",
    description = "测试MCP服务",
    version = "1.0.0"
)
public class TestMcpService {

    @McpTool(
        name = "add_numbers",
        description = "计算两个数字的和"
    )
    public int addNumbers(
            @McpParameter(name = "a", description = "第一个数字", required = true) int a,
            @McpParameter(name = "b", description = "第二个数字", required = true) int b
    ) {
        return a + b;
    }

    @McpTool(
        name = "greet_user",
        description = "向用户问候"
    )
    public String greetUser(
            @McpParameter(name = "name", description = "用户名称", required = true) String name,
            @McpParameter(name = "greeting", description = "问候语", required = false, defaultValue = "Hello") String greeting
    ) {
        return greeting + ", " + name + "!";
    }

    @McpTool(
        description = "获取当前时间戳"
    )
    public long getCurrentTimestamp() {
        return System.currentTimeMillis();
    }
}