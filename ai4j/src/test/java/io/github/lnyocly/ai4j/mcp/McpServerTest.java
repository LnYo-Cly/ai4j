package io.github.lnyocly.ai4j.mcp;

import io.github.lnyocly.ai4j.mcp.server.McpServer;
import io.github.lnyocly.ai4j.mcp.server.McpServerFactory;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import io.github.lnyocly.ai4j.utils.ToolUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * MCP服务器测试类
 * 测试创建MCP服务器并提供工具服务
 */
public class McpServerTest {
    
    private static final Logger log = LoggerFactory.getLogger(McpServerTest.class);
    
    public static void main(String[] args) {
        log.info("开始MCP服务器测试...");
        
        try {
            // 测试1: 创建并启动SSE MCP服务器
            //testSseMcpServer();
            
            // 测试2: 创建并启动Stdio MCP服务器
            testStdioMcpServer();
            
            // 测试3: 创建并启动Streamable HTTP MCP服务器
            //testStreamableHttpMcpServer();
            
            log.info("所有MCP服务器测试完成！");
            
        } catch (Exception e) {
            log.error("MCP服务器测试失败", e);
        }
    }
    
    /**
     * 测试SSE MCP服务器
     */
    private static void testSseMcpServer() {
        log.info("=== 测试1: SSE MCP服务器 ===");
        
        try {
            // 创建SSE服务器
            McpServer server = McpServerFactory.createServer("sse", "name", "1.0.0", 5000);
            
            log.info("创建SSE MCP服务器: {}", server.getServerInfo());
            
            // 启动服务器
            CompletableFuture<Void> startFuture = server.start();
            startFuture.get(5, TimeUnit.SECONDS);
            
            if (server.isRunning()) {
                log.info("SSE MCP服务器启动成功，端口: 8080");
                log.info("SSE端点: http://localhost:8080/sse");
                log.info("消息端点: http://localhost:8080/message");
                
                // 显示可用工具
                showAvailableTools();
                
                // 运行一段时间后停止
                Thread.sleep(2000);
                
                // 停止服务器
                CompletableFuture<Void> stopFuture = server.stop();
                stopFuture.get(5, TimeUnit.SECONDS);
                log.info("SSE MCP服务器已停止");
            } else {
                log.error("SSE MCP服务器启动失败");
            }
            
        } catch (Exception e) {
            log.error("SSE MCP服务器测试失败", e);
        }
    }
    
    /**
     * 测试Stdio MCP服务器
     */
    private static void testStdioMcpServer() {
        log.info("=== 测试2: Stdio MCP服务器 ===");
        
        try {
            // 创建Stdio服务器
            McpServer server = McpServerFactory.createServer("stdio", "name", "1.0.0");
            
            log.info("创建Stdio MCP服务器: {}", server.getServerInfo());
            
            // 注意：Stdio服务器通常在后台运行，这里只是演示创建过程
            log.info("Stdio MCP服务器创建成功，可以通过标准输入输出进行通信");
            
            // 显示可用工具
            showAvailableTools();
            
        } catch (Exception e) {
            log.error("Stdio MCP服务器测试失败", e);
        }
    }
    
    /**
     * 测试Streamable HTTP MCP服务器
     */
    private static void testStreamableHttpMcpServer() {
        log.info("=== 测试3: Streamable HTTP MCP服务器 ===");
        
        try {
            // 创建Streamable HTTP服务器
            McpServer server = McpServerFactory.createServer("http", "name", "1.0.0", 8081);
            
            log.info("创建Streamable HTTP MCP服务器: {}", server.getServerInfo());
            
            // 启动服务器
            CompletableFuture<Void> startFuture = server.start();
            startFuture.get(5, TimeUnit.SECONDS);
            
            if (server.isRunning()) {
                log.info("Streamable HTTP MCP服务器启动成功，端口: 8081");
                log.info("MCP端点: http://localhost:8081/mcp");
                
                // 显示可用工具
                showAvailableTools();
                
                // 运行一段时间后停止
                Thread.sleep(2000);
                
                // 停止服务器
                CompletableFuture<Void> stopFuture = server.stop();
                stopFuture.get(5, TimeUnit.SECONDS);
                log.info("Streamable HTTP MCP服务器已停止");
            } else {
                log.error("Streamable HTTP MCP服务器启动失败");
            }
            
        } catch (Exception e) {
            log.error("Streamable HTTP MCP服务器测试失败", e);
        }
    }
    
    /**
     * 显示可用工具
     */
    private static void showAvailableTools() {
        try {
            log.info("--- 可用工具列表 ---");
            
            // 获取所有工具
            List<Tool> allTools = ToolUtil.getAllTools(new ArrayList<>(), new ArrayList<>());
            
            log.info("总计 {} 个工具:", allTools.size());
            for (Tool tool : allTools) {
                if (tool.getFunction() != null) {
                    log.info("- {}: {}", 
                            tool.getFunction().getName(), 
                            tool.getFunction().getDescription());
                }
            }
            
            // 测试调用几个工具
            log.info("--- 工具调用测试 ---");
            testToolCall("TestService_greet", "{\"name\":\"MCP测试用户\"}");
            testToolCall("TestService_add", "{\"a\":100,\"b\":200}");
            
        } catch (Exception e) {
            log.error("显示可用工具失败", e);
        }
    }
    
    /**
     * 测试工具调用
     */
    private static void testToolCall(String toolName, String arguments) {
        try {
            log.info("调用工具: {} 参数: {}", toolName, arguments);
            String result = ToolUtil.invoke(toolName, arguments);
            log.info("调用结果: {}", result);
        } catch (Exception e) {
            log.error("工具调用失败: {} - {}", toolName, e.getMessage());
        }
    }
}
