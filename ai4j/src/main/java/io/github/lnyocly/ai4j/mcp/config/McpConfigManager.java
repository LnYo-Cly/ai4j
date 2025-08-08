package io.github.lnyocly.ai4j.mcp.config;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.mcp.config.McpServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * MCP配置管理器 - 支持动态配置和事件通知
 */
public class McpConfigManager {
    private static final Logger log = LoggerFactory.getLogger(McpConfigManager.class);

    private final Map<String, McpServerConfig.McpServerInfo> configs = new ConcurrentHashMap<>();
    private final List<ConfigChangeListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * 配置变更监听器
     */
    public interface ConfigChangeListener {
        void onConfigAdded(String serverId, McpServerConfig.McpServerInfo config);
        void onConfigRemoved(String serverId);
        void onConfigUpdated(String serverId, McpServerConfig.McpServerInfo config);
    }

    /**
     * 添加配置变更监听器
     */
    public void addConfigChangeListener(ConfigChangeListener listener) {
        listeners.add(listener);
    }

    /**
     * 移除配置变更监听器
     */
    public void removeConfigChangeListener(ConfigChangeListener listener) {
        listeners.remove(listener);
    }

    /**
     * 添加MCP服务器配置（支持运行时动态添加）
     */
    public void addConfig(String serverId, McpServerConfig.McpServerInfo config) {
        McpServerConfig.McpServerInfo oldConfig = configs.put(serverId, config);
        log.info("添加MCP服务器配置: {}", serverId);

        // 通知监听器
        if (oldConfig == null) {
            notifyConfigAdded(serverId, config);
        } else {
            notifyConfigUpdated(serverId, config);
        }
    }
    
    /**
     * 删除MCP服务器配置（支持运行时动态删除）
     */
    public void removeConfig(String serverId) {
        McpServerConfig.McpServerInfo removedConfig = configs.remove(serverId);
        if (removedConfig != null) {
            log.info("删除MCP服务器配置: {}", serverId);
            notifyConfigRemoved(serverId);
        }
    }
    
    /**
     * 获取MCP服务器配置
     */
    public McpServerConfig.McpServerInfo getConfig(String serverId) {
        return configs.get(serverId);
    }

    /**
     * 获取所有配置
     */
    public Map<String, McpServerConfig.McpServerInfo> getAllConfigs() {
        return new ConcurrentHashMap<>(configs);
    }

    /**
     * 更新配置
     */
    public void updateConfig(String serverId, McpServerConfig.McpServerInfo config) {
        configs.put(serverId, config);
        log.info("更新MCP服务器配置: {}", serverId);
    }
    
    /**
     * 检查配置是否存在
     */
    public boolean hasConfig(String serverId) {
        return configs.containsKey(serverId);
    }

    /**
     * 验证配置有效性
     */
    public boolean validateConfig(McpServerConfig.McpServerInfo config) {
        if (config == null) return false;
        if (config.getName() == null || config.getName().trim().isEmpty()) return false;
        if (config.getTransport() == null) return false;

        // 根据传输类型验证特定配置
        switch (config.getTransport()) {
            case "stdio":
                return config.getCommand() != null && !config.getCommand().trim().isEmpty();
            case "http":
                return config.getUrl() != null && !config.getUrl().trim().isEmpty();
            default:
                return false;
        }
    }

    // 通知方法
    private void notifyConfigAdded(String serverId, McpServerConfig.McpServerInfo config) {
        for (ConfigChangeListener listener : listeners) {
            try {
                listener.onConfigAdded(serverId, config);
            } catch (Exception e) {
                log.error("通知配置添加事件失败: {}", serverId, e);
            }
        }
    }

    private void notifyConfigRemoved(String serverId) {
        for (ConfigChangeListener listener : listeners) {
            try {
                listener.onConfigRemoved(serverId);
            } catch (Exception e) {
                log.error("通知配置删除事件失败: {}", serverId, e);
            }
        }
    }

    private void notifyConfigUpdated(String serverId, McpServerConfig.McpServerInfo config) {
        for (ConfigChangeListener listener : listeners) {
            try {
                listener.onConfigUpdated(serverId, config);
            } catch (Exception e) {
                log.error("通知配置更新事件失败: {}", serverId, e);
            }
        }
    }
}
