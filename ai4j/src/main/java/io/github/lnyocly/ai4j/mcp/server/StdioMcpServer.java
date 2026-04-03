package io.github.lnyocly.ai4j.mcp.server;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.mcp.entity.McpMessage;
import io.github.lnyocly.ai4j.mcp.entity.McpResponse;
import io.github.lnyocly.ai4j.mcp.util.McpMessageCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Stdio MCP服务器实现
 * 直接处理标准输入输出的MCP服务器，不使用传输层抽象
 *
 * @Author cly
 */
public class StdioMcpServer implements McpServer {

    private static final Logger log = LoggerFactory.getLogger(StdioMcpServer.class);

    private final String serverName;
    private final String serverVersion;
    private final AtomicBoolean running;
    private final McpServerSessionState sessionState;
    private final McpServerEngine serverEngine;

    public StdioMcpServer(String serverName, String serverVersion) {
        this.serverName = serverName;
        this.serverVersion = serverVersion;
        this.running = new AtomicBoolean(false);
        this.sessionState = new McpServerSessionState("stdio");
        this.serverEngine = new McpServerEngine(
                serverName,
                serverVersion,
                Collections.singletonList("2024-11-05"),
                "2024-11-05",
                false,
                false,
                false);

        log.info("Stdio MCP服务器已创建: {} v{}", serverName, serverVersion);
    }

    /**
     * 启动MCP服务器
     */
    public CompletableFuture<Void> start() {
        return CompletableFuture.runAsync(() -> {
            if (running.compareAndSet(false, true)) {
                log.info("启动Stdio MCP服务器: {} v{}", serverName, serverVersion);

                try {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                    String line;

                    log.info("Stdio MCP服务器启动成功，等待stdin输入...");

                    while (running.get() && (line = reader.readLine()) != null) {
                        try {
                            if (!line.trim().isEmpty()) {
                                McpMessage message = McpMessageCodec.parseMessage(line);
                                handleMessage(message);
                            }
                        } catch (Exception e) {
                            log.error("处理stdin消息失败: {}", line, e);
                            sendResponse(createInternalErrorResponse(null, e));
                        }
                    }

                } catch (Exception e) {
                    running.set(false);
                    log.error("启动Stdio MCP服务器失败", e);
                    throw new RuntimeException("启动Stdio MCP服务器失败", e);
                }
            }
        });
    }

    /**
     * 停止MCP服务器
     */
    public CompletableFuture<Void> stop() {
        return CompletableFuture.runAsync(() -> {
            if (running.compareAndSet(true, false)) {
                log.info("停止Stdio MCP服务器");
                sessionState.setInitialized(false);
                sessionState.getCapabilities().clear();
                log.info("Stdio MCP服务器已停止");
            }
        });
    }

    /**
     * 检查服务器是否正在运行
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * 获取服务器信息
     */
    public String getServerInfo() {
        return String.format("%s v%s (stdio)", serverName, serverVersion);
    }

    /**
     * 获取服务器名称
     */
    public String getServerName() {
        return serverName;
    }

    /**
     * 获取服务器版本
     */
    public String getServerVersion() {
        return serverVersion;
    }

    /**
     * 处理MCP消息
     */
    private void handleMessage(McpMessage message) {
        try {
            log.debug("处理Stdio消息: {}", message);
            McpMessage response = serverEngine.processMessage(message, sessionState);
            if (response != null) {
                sendResponse(response);
            }
        } catch (Exception e) {
            log.error("处理Stdio消息时发生错误", e);
            sendResponse(createInternalErrorResponse(message, e));
        }
    }

    /**
     * 发送响应
     */
    private void sendResponse(McpMessage response) {
        try {
            String jsonResponse = JSON.toJSONString(response);
            System.out.println(jsonResponse);
            System.out.flush();
            log.debug("发送响应到stdout: {}", jsonResponse);
        } catch (Exception e) {
            log.error("发送响应失败", e);
        }
    }

    private McpResponse createInternalErrorResponse(McpMessage originalMessage, Exception error) {
        McpResponse errorResponse = new McpResponse();
        if (originalMessage != null) {
            errorResponse.setId(originalMessage.getId());
        }

        io.github.lnyocly.ai4j.mcp.entity.McpError mcpError = new io.github.lnyocly.ai4j.mcp.entity.McpError();
        mcpError.setCode(-32603);
        mcpError.setMessage("Internal error: " + error.getMessage());
        errorResponse.setError(mcpError);
        return errorResponse;
    }

}
