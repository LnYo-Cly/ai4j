package io.github.lnyocly.ai4j;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @Author cly
 * @Description TODO
 * @Date 2024/8/28 17:39
 */

@ConfigurationProperties(prefix = "ai.zhipu")
public class ZhipuConfigProperties {
    private String apiHost = "https://open.bigmodel.cn/api/paas/";
    private String apiKey = "";
    private String chatCompletionUrl = "v4/chat/completions";
    private String embeddingUrl= "v4/embeddings";

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

    public String getEmbeddingUrl() {
        return embeddingUrl;
    }

    public void setEmbeddingUrl(String embeddingUrl) {
        this.embeddingUrl = embeddingUrl;
    }
}
