package io.github.lnyocly.ai4j;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @Author cly
 * @Description DeepSeek配置文件
 * @Date 2024/8/29 15:01
 */
@ConfigurationProperties(prefix = "ai.deepseek")
public class DeepSeekConfigProperties {

    private String apiHost = "https://api.deepseek.com/";
    private String apiKey = "";
    private String chatCompletionUrl = "chat/completions";

    public String getApiHost() {
        return apiHost;
    }

    public void setApiHost(String apiHost) {
        this.apiHost = apiHost;
    }

    public String getChatCompletionUrl() {
        return chatCompletionUrl;
    }

    public void setChatCompletionUrl(String chatCompletionUrl) {
        this.chatCompletionUrl = chatCompletionUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
}
