package io.github.lnyocly.ai4j;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @Author cly
 * @Description TODO
 * @Date 2024/8/29 15:01
 */
@ConfigurationProperties(prefix = "ai.deepseek")
public class DeepSeekConfigProperties {

    private String apiHost = "https://api.deepseek.com/";
    private String apiKey = "";
    private String chat_completion = "chat/completions";

    public String getApiHost() {
        return apiHost;
    }

    public void setApiHost(String apiHost) {
        this.apiHost = apiHost;
    }

    public String getChat_completion() {
        return chat_completion;
    }

    public void setChat_completion(String chat_completion) {
        this.chat_completion = chat_completion;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
}
