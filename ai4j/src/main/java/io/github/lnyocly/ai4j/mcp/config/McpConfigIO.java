package io.github.lnyocly.ai4j.mcp.config;

import com.alibaba.fastjson2.JSON;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * MCP 配置读取辅助类
 */
public final class McpConfigIO {

    private McpConfigIO() {
    }

    public static McpServerConfig loadServerConfig(String configFile, ClassLoader classLoader) throws IOException {
        String configContent = loadConfigContent(configFile, classLoader);
        if (configContent == null || configContent.trim().isEmpty()) {
            return null;
        }
        return JSON.parseObject(configContent, McpServerConfig.class);
    }

    public static Map<String, McpServerConfig.McpServerInfo> loadEnabledConfigs(
            String configFile,
            ClassLoader classLoader) throws IOException {
        return extractEnabledConfigs(loadServerConfig(configFile, classLoader));
    }

    public static Map<String, McpServerConfig.McpServerInfo> extractEnabledConfigs(McpServerConfig serverConfig) {
        Map<String, McpServerConfig.McpServerInfo> enabledConfigs =
                new HashMap<String, McpServerConfig.McpServerInfo>();

        if (serverConfig == null || serverConfig.getMcpServers() == null) {
            return enabledConfigs;
        }

        serverConfig.getMcpServers().forEach((serverId, serverInfo) -> {
            if (serverInfo.getEnabled() == null || serverInfo.getEnabled()) {
                enabledConfigs.put(serverId, serverInfo);
            }
        });
        return enabledConfigs;
    }

    public static String loadConfigContent(String configFile, ClassLoader classLoader) throws IOException {
        InputStream inputStream = classLoader.getResourceAsStream(configFile);
        if (inputStream != null) {
            try {
                byte[] bytes = readAllBytes(inputStream);
                return new String(bytes, StandardCharsets.UTF_8);
            } finally {
                inputStream.close();
            }
        }

        Path configPath = Paths.get(configFile);
        if (Files.exists(configPath)) {
            return new String(Files.readAllBytes(configPath), StandardCharsets.UTF_8);
        }

        return null;
    }

    private static byte[] readAllBytes(InputStream inputStream) throws IOException {
        byte[] buffer = new byte[8192];
        int bytesRead;
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }
        return outputStream.toByteArray();
    }
}
