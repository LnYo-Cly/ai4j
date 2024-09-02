package io.github.lnyocly.ai4j;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @Author cly
 * @Description 腾讯混元配置文件
 * @Date 2024/9/2 19:13
 */
@ConfigurationProperties(prefix = "ai.hunyuan")
public class HunyuanConfigProperties {
    private String apiHost = "https://hunyuan.tencentcloudapi.com/";
    /**
     * apiKey 属于SecretId与SecretKey的拼接，格式为 {SecretId}.{SecretKey}
     */
    private String apiKey = "";

    public String getApiHost() {
        return apiHost;
    }

    public void setApiHost(String apiHost) {
        this.apiHost = apiHost;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
}
