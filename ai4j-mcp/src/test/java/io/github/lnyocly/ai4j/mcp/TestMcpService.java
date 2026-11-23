package io.github.lnyocly.ai4j.mcp;

import io.github.lnyocly.ai4j.mcp.annotation.McpService;
import io.github.lnyocly.ai4j.mcp.annotation.McpTool;
import io.github.lnyocly.ai4j.mcp.annotation.McpParameter;

/**
 * 测试用的MCP服务
 */
@McpService(name = "TestService", description = "测试MCP服务")
public class TestMcpService {

    /**
     * 简单的问候工具
     */
    @McpTool(name = "greet", description = "向指定的人问候")
    public String greet(@McpParameter(name = "name", description = "要问候的人的名字", required = true) String name) {
        return "Hello, " + name + "! Welcome to MCP service!";
    }

    /**
     * 数学计算工具
     */
    @McpTool(name = "add", description = "计算两个数字的和")
    public int add(
            @McpParameter(name = "a", description = "第一个数字", required = true) int a,
            @McpParameter(name = "b", description = "第二个数字", required = true) int b) {
        return a + b;
    }

    /**
     * 字符串处理工具
     */
    @McpTool(name = "reverse", description = "反转字符串")
    public String reverse(@McpParameter(name = "text", description = "要反转的文本", required = true) String text) {
        if (text == null) {
            return "";
        }
        return new StringBuilder(text).reverse().toString();
    }

    /**
     * 获取系统信息工具
     */
    @McpTool(name = "systemInfo", description = "获取系统信息")
    public SystemInfo getSystemInfo() {
        SystemInfo info = new SystemInfo();
        info.javaVersion = System.getProperty("java.version");
        info.osName = System.getProperty("os.name");
        info.osVersion = System.getProperty("os.version");
        info.timestamp = System.currentTimeMillis();
        return info;
    }

    /**
     * 系统信息类
     */
    public static class SystemInfo {
        public String javaVersion;
        public String osName;
        public String osVersion;
        public long timestamp;
    }
}
