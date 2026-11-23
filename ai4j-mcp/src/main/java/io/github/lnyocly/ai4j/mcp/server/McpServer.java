package io.github.lnyocly.ai4j.mcp.server;

import java.util.concurrent.CompletableFuture;

/**
 * MCP服务器公共接口
 * 定义所有MCP服务器的基本操作
 * 
 * @Author cly
 */
public interface McpServer {
    
    /**
     * 启动MCP服务器
     * 
     * @return CompletableFuture，完成时表示服务器启动完成
     */
    CompletableFuture<Void> start();
    
    /**
     * 停止MCP服务器
     * 
     * @return CompletableFuture，完成时表示服务器停止完成
     */
    CompletableFuture<Void> stop();
    
    /**
     * 检查服务器是否正在运行
     * 
     * @return true表示服务器正在运行，false表示已停止
     */
    boolean isRunning();
    
    /**
     * 获取服务器信息
     * 
     * @return 服务器信息字符串，包含名称、版本等
     */
    String getServerInfo();
    
    /**
     * 获取服务器名称
     * 
     * @return 服务器名称
     */
    String getServerName();
    
    /**
     * 获取服务器版本
     * 
     * @return 服务器版本
     */
    String getServerVersion();
}
