package io.github.lnyocly.ai4j;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @Author cly
 * @Description 零一万物配置文件
 * @Date 2024/9/9 23:31
 */
@ConfigurationProperties(prefix = "ai.lingyi")
public class LingyiConfigProperties {
    private String apiHost = "https://api.lingyiwanwu.com/";
    private String apiKey = "";
    private String chatCompletionUrl = "v1/chat/completions";

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

    public String getChatCompletionUrl() {
        return chatCompletionUrl;
    }

    public void setChatCompletionUrl(String chatCompletionUrl) {
        this.chatCompletionUrl = chatCompletionUrl;
    }
}
