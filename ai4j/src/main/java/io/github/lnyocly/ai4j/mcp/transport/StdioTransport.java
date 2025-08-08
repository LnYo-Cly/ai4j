package io.github.lnyocly.ai4j.mcp.transport;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.mcp.entity.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @Author cly
 * @Description 进程Stdio传输层 - 启动外部MCP服务器进程
 */
public class StdioTransport implements McpTransport {
    
    private static final Logger log = LoggerFactory.getLogger(StdioTransport.class);
    
    private final String command;
    private final List<String> args;
    private final Map<String, String> env;
    
    private Process mcpProcess;
    private BufferedReader reader;
    private PrintWriter writer;
    private final ExecutorService executor;
    private final AtomicBoolean running;
    private McpMessageHandler messageHandler;
    
    public StdioTransport(String command, List<String> args, Map<String, String> env) {
        this.command = command;
        this.args = args;
        this.env = env;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "MCP-Process-Stdio-Transport");
            t.setDaemon(true);
            return t;
        });
        this.running = new AtomicBoolean(false);
    }
    
    @Override
    public CompletableFuture<Void> start() {
        return CompletableFuture.runAsync(() -> {
            if (running.compareAndSet(false, true)) {
                log.info("启动进程Stdio传输层");
                
                try {
                    // 启动MCP服务器进程
                    startMcpProcess();
                    
                    if (messageHandler != null) {
                        messageHandler.onConnected();
                    }
                    
                    // 启动消息读取循环
                    executor.submit(this::messageReadLoop);
                    
                } catch (Exception e) {
                    log.error("启动MCP进程失败", e);
                    running.set(false);
                    if (messageHandler != null) {
                        messageHandler.onError(e);
                    }
                    throw new RuntimeException("启动MCP进程失败", e);
                }
            }
        });
    }
    
    /**
     * 启动MCP服务器进程
     */
    private void startMcpProcess() throws IOException {
        log.info("启动MCP服务器进程: {} {}", command, args);

        ProcessBuilder pb = new ProcessBuilder();

        // 构建完整命令
        List<String> fullCommand = new ArrayList<>();

        // Windows系统需要特殊处理
        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            if ("npx".equals(command)) {
                // 在Windows上使用cmd包装npx命令
                fullCommand.add("cmd");
                fullCommand.add("/c");
                fullCommand.add("npx");
                if (args != null) {
                    fullCommand.addAll(args);
                }
            } else {
                fullCommand.add(command);
                if (args != null) {
                    fullCommand.addAll(args);
                }
            }
        } else {
            fullCommand.add(command);
            if (args != null) {
                fullCommand.addAll(args);
            }
        }

        pb.command(fullCommand);

        log.debug("完整命令: {}", fullCommand);

        // 设置环境变量
        if (env != null && !env.isEmpty()) {
            Map<String, String> processEnv = pb.environment();
            processEnv.putAll(env);
        }
        
        // 重定向错误流到标准输出
        pb.redirectErrorStream(true);
        
        // 启动进程
        mcpProcess = pb.start();
        
        // 设置输入输出流
        reader = new BufferedReader(new InputStreamReader(mcpProcess.getInputStream(), "UTF-8"));
        writer = new PrintWriter(new OutputStreamWriter(mcpProcess.getOutputStream(), "UTF-8"), true);
        
        log.info("MCP服务器进程已启动，PID: {}", getProcessId());

        // 检查进程是否立即退出
        try {
            Thread.sleep(100); // 等待100ms
            if (!mcpProcess.isAlive()) {
                int exitCode = mcpProcess.exitValue();
                throw new IOException("MCP服务器进程立即退出，退出码: " + exitCode);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * 获取进程ID（尽力而为）
     */
    private String getProcessId() {
        try {
            if (mcpProcess != null) {
                // Java 9+ 有 pid() 方法，但我们在 JDK 8 环境
                return mcpProcess.toString();
            }
        } catch (Exception e) {
            // 忽略
        }
        return "unknown";
    }
    
    @Override
    public CompletableFuture<Void> stop() {
        return CompletableFuture.runAsync(() -> {
            if (running.compareAndSet(true, false)) {
                log.info("停止进程Stdio传输层");
                
                // 关闭流
                try {
                    if (writer != null) {
                        writer.close();
                    }
                    if (reader != null) {
                        reader.close();
                    }
                } catch (Exception e) {
                    log.warn("关闭流时发生错误", e);
                }
                
                // 终止进程
                if (mcpProcess != null) {
                    try {
                        mcpProcess.destroy();
                        
                        // 等待进程结束
                        boolean terminated = mcpProcess.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
                        if (!terminated) {
                            log.warn("进程未在5秒内结束，强制终止");
                            mcpProcess.destroyForcibly();
                        }
                        
                        log.info("MCP服务器进程已终止");
                    } catch (Exception e) {
                        log.error("终止MCP进程时发生错误", e);
                    }
                }
                
                // 关闭线程池
                executor.shutdown();
                
                if (messageHandler != null) {
                    messageHandler.onDisconnected("Transport stopped");
                }
            }
        });
    }
    
    @Override
    public CompletableFuture<Void> sendMessage(McpMessage message) {
        return CompletableFuture.runAsync(() -> {
            if (!running.get()) {
                throw new IllegalStateException("传输层未启动");
            }
            
            if (writer == null) {
                throw new IllegalStateException("输出流未初始化");
            }
            
            try {
                String jsonMessage = JSON.toJSONString(message);
                log.debug("发送消息: {}", jsonMessage);

                // 发送JSON消息并确保换行
                writer.println(jsonMessage);
                writer.flush();

                // 额外确保数据被发送
                if (writer.checkError()) {
                    throw new IOException("写入数据时发生错误");
                }
            } catch (Exception e) {
                log.error("发送消息失败", e);
                if (messageHandler != null) {
                    messageHandler.onError(e);
                }
                throw new RuntimeException("发送消息失败", e);
            }
        });
    }
    
    @Override
    public void setMessageHandler(McpMessageHandler handler) {
        this.messageHandler = handler;
    }
    
    @Override
    public boolean isConnected() {
        return running.get() && mcpProcess != null && mcpProcess.isAlive();
    }

    @Override
    public boolean needsHeartbeat() {
        return false;
    }

    @Override
    public String getTransportType() {
        return "process-stdio";
    }

    /**
     * 消息读取循环
     */
    private void messageReadLoop() {
        log.debug("开始消息读取循环");
        
        while (running.get()) {
            try {
                String line = reader.readLine();
                if (line == null) {
                    // 输入流结束，检查进程状态
                    if (mcpProcess != null && mcpProcess.isAlive()) {
                        log.warn("输入流结束但进程仍在运行，可能是通信问题");
                    } else if (mcpProcess != null) {
                        try {
                            int exitCode = mcpProcess.exitValue();
                            log.warn("MCP进程已退出，退出码: {}", exitCode);
                        } catch (IllegalThreadStateException e) {
                            log.warn("无法获取进程退出码");
                        }
                    }
                    log.info("输入流结束，停止传输层");
                    stop();
                    break;
                }
                
                if (line.trim().isEmpty()) {
                    continue;
                }

                log.debug("接收消息: {}", line);

                // 检查是否是JSON消息（以{开头）
                if (!line.trim().startsWith("{")) {
                    log.info("MCP服务器日志: {}", line);
                    continue;
                }

                // 解析JSON消息
                McpMessage message = parseMessage(line);
                if (message != null && messageHandler != null) {
                    messageHandler.handleMessage(message);
                }
                
            } catch (IOException e) {
                if (running.get()) {
                    log.error("读取消息时发生IO错误", e);
                    if (messageHandler != null) {
                        messageHandler.onError(e);
                    }
                }
                break;
            } catch (Exception e) {
                log.error("处理消息时发生错误", e);
                if (messageHandler != null) {
                    messageHandler.onError(e);
                }
            }
        }
        
        log.debug("消息读取循环结束");
    }
    
    /**
     * 解析JSON消息为McpMessage对象
     */
    private McpMessage parseMessage(String jsonString) {
        try {
            JSONObject jsonObject = JSON.parseObject(jsonString);
            
            // 判断消息类型
            if (jsonObject.containsKey("method")) {
                if (jsonObject.containsKey("id")) {
                    // 请求消息
                    return JSON.parseObject(jsonString, McpRequest.class);
                } else {
                    // 通知消息
                    return JSON.parseObject(jsonString, McpNotification.class);
                }
            } else if (jsonObject.containsKey("id")) {
                // 响应消息
                return JSON.parseObject(jsonString, McpResponse.class);
            }
            
            log.debug("无法识别的消息格式: {}", jsonString);
            return null;
        } catch (Exception e) {
            log.debug("解析消息失败: {}", jsonString, e);
            return null;
        }
    }
}
