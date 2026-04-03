package io.github.lnyocly.ai4j.mcp.config;

import com.alibaba.fastjson2.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @Author cly
 * @Description 基于文件的MCP配置源实现
 */
public class FileMcpConfigSource implements McpConfigSource {
    
    private static final Logger log = LoggerFactory.getLogger(FileMcpConfigSource.class);
    
    private final String configFile;
    private final List<ConfigChangeListener> listeners = new CopyOnWriteArrayList<>();
    private volatile Map<String, McpServerConfig.McpServerInfo> configs = new HashMap<>();
    
    public FileMcpConfigSource(String configFile) {
        this.configFile = configFile;
        loadConfigs();
    }
    
    @Override
    public Map<String, McpServerConfig.McpServerInfo> getAllConfigs() {
        return new HashMap<>(configs);
    }
    
    @Override
    public McpServerConfig.McpServerInfo getConfig(String serverId) {
        return configs.get(serverId);
    }
    
    @Override
    public void addConfigChangeListener(ConfigChangeListener listener) {
        listeners.add(listener);
    }
    
    @Override
    public void removeConfigChangeListener(ConfigChangeListener listener) {
        listeners.remove(listener);
    }
    
    /**
     * 重新加载配置文件（支持热更新）
     */
    public void reloadConfigs() {
        Map<String, McpServerConfig.McpServerInfo> oldConfigs = new HashMap<>(configs);
        loadConfigs();
        Map<String, McpServerConfig.McpServerInfo> newConfigs = configs;
        
        // 检测配置变更并通知监听器
        detectAndNotifyChanges(oldConfigs, newConfigs);
    }
    
    /**
     * 从文件加载配置
     */
    private void loadConfigs() {
        try {
            McpServerConfig serverConfig = McpConfigIO.loadServerConfig(configFile, getClass().getClassLoader());
            Map<String, McpServerConfig.McpServerInfo> enabledConfigs =
                    McpConfigIO.extractEnabledConfigs(serverConfig);
            if (!enabledConfigs.isEmpty()) {
                this.configs = enabledConfigs;
                log.info("成功加载MCP配置文件: {}, 共 {} 个启用的服务", configFile, enabledConfigs.size());
            } else {
                this.configs = new HashMap<>();
                if (serverConfig == null) {
                    log.info("未找到MCP配置文件: {}", configFile);
                } else {
                    log.warn("MCP配置文件为空或没有启用的服务: {}", configFile);
                }
            }
        } catch (Exception e) {
            this.configs = new HashMap<>();
            log.error("加载MCP配置文件失败: {}", configFile, e);
        }
    }
    
    /**
     * 检测配置变更并通知监听器
     */
    private void detectAndNotifyChanges(Map<String, McpServerConfig.McpServerInfo> oldConfigs, 
                                       Map<String, McpServerConfig.McpServerInfo> newConfigs) {
        
        // 检测新增的配置
        newConfigs.forEach((serverId, config) -> {
            if (!oldConfigs.containsKey(serverId)) {
                notifyConfigAdded(serverId, config);
            } else if (!configEquals(oldConfigs.get(serverId), config)) {
                notifyConfigUpdated(serverId, config);
            }
        });
        
        // 检测删除的配置
        oldConfigs.forEach((serverId, config) -> {
            if (!newConfigs.containsKey(serverId)) {
                notifyConfigRemoved(serverId);
            }
        });
    }
    
    /**
     * 比较两个配置是否相等
     */
    private boolean configEquals(McpServerConfig.McpServerInfo config1, McpServerConfig.McpServerInfo config2) {
        if (config1 == null && config2 == null) return true;
        if (config1 == null || config2 == null) return false;
        
        // 简单的JSON序列化比较
        try {
            String json1 = JSON.toJSONString(config1);
            String json2 = JSON.toJSONString(config2);
            return json1.equals(json2);
        } catch (Exception e) {
            log.warn("比较配置时发生错误", e);
            return false;
        }
    }
    
    private void notifyConfigAdded(String serverId, McpServerConfig.McpServerInfo config) {
        listeners.forEach(listener -> {
            try {
                listener.onConfigAdded(serverId, config);
            } catch (Exception e) {
                log.error("通知配置添加失败: {}", serverId, e);
            }
        });
    }
    
    private void notifyConfigRemoved(String serverId) {
        listeners.forEach(listener -> {
            try {
                listener.onConfigRemoved(serverId);
            } catch (Exception e) {
                log.error("通知配置删除失败: {}", serverId, e);
            }
        });
    }
    
    private void notifyConfigUpdated(String serverId, McpServerConfig.McpServerInfo config) {
        listeners.forEach(listener -> {
            try {
                listener.onConfigUpdated(serverId, config);
            } catch (Exception e) {
                log.error("通知配置更新失败: {}", serverId, e);
            }
        });
    }
}
