package io.github.lnyocly.ai4j.mcp;

import io.github.lnyocly.ai4j.mcp.client.McpClient;
import io.github.lnyocly.ai4j.mcp.entity.McpToolDefinition;
import io.github.lnyocly.ai4j.mcp.gateway.McpGateway;
import io.github.lnyocly.ai4j.mcp.transport.McpTransport;
import io.github.lnyocly.ai4j.mcp.transport.McpTransportFactory;
import io.github.lnyocly.ai4j.mcp.transport.TransportConfig;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Author cly
 * @Description TODO
 * @Date 2025/7/24 16:57
 */
public class Test {

/*    public static void main(String[] args) {
        // 创建并初始化MCP网关
        //McpGateway gateway = new McpGateway();
       // gateway.initialize("mcp-servers-config.json").join();

        List<String> objects = new ArrayList<>();
        objects.add("-y");
        objects.add("12306-mcp");

        McpTransport transport = McpTransportFactory.createTransport("stdio", TransportConfig.stdio("npx", objects));
        McpClient mcpClient = new McpClient("a","1.0.1", transport);
// 获取可用工具
        mcpClient.connect().join();
        List<McpToolDefinition> join = mcpClient.getAvailableTools().join();
        System.out.println(join);

        //List<Tool.Function> tools = gateway.getAvailableTools().join();


        //System.out.println(tools);
        //gateway.shutdown().join();
    }*/

    public static void main(String[] args) {
        // 创建并初始化MCP网关
        McpGateway gateway = new McpGateway();
        gateway.initialize("mcp-servers-config.json").join();


        List<Tool.Function> tools = gateway.getAvailableTools().join();


        System.out.println(tools);
        gateway.shutdown().join();
    }
}
