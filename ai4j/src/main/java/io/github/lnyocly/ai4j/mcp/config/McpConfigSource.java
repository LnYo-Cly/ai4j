package io.github.lnyocly.ai4j.mcp.config;

import java.util.Map;

/**
 * @Author cly
 * @Description MCP配置源接口，支持多种配置来源（文件、数据库、Redis等）
 */
public interface McpConfigSource {
    
    /**
     * 获取所有配置
     */
    Map<String, McpServerConfig.McpServerInfo> getAllConfigs();
    
    /**
     * 获取指定配置
     */
    McpServerConfig.McpServerInfo getConfig(String serverId);
    
    /**
     * 添加配置变更监听器
     */
    void addConfigChangeListener(ConfigChangeListener listener);
    
    /**
     * 移除配置变更监听器
     */
    void removeConfigChangeListener(ConfigChangeListener listener);
    
    /**
     * 配置变更监听器
     */
    interface ConfigChangeListener {
        /**
         * 配置添加事件
         */
        void onConfigAdded(String serverId, McpServerConfig.McpServerInfo config);
        
        /**
         * 配置移除事件
         */
        void onConfigRemoved(String serverId);
        
        /**
         * 配置更新事件
         */
        void onConfigUpdated(String serverId, McpServerConfig.McpServerInfo config);
    }
}
