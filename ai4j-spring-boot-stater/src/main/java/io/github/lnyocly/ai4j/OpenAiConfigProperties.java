package io.github.lnyocly.ai4j;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @Author cly
 * @Description TODO
 * @Date 2024/8/9 23:17
 */
@ConfigurationProperties(prefix = "ai.openai")
public class OpenAiConfigProperties {
    private String apiHost = "https://api.openai.com/";

    private String apiKey = "";

    private String v1_chat_completions = "v1/chat/completions";
    private String v1_embeddings = "v1/embeddings";

    public OpenAiConfigProperties() {
    }

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

    public String getV1_chat_completions() {
        return v1_chat_completions;
    }

    public void setV1_chat_completions(String v1_chat_completions) {
        this.v1_chat_completions = v1_chat_completions;
    }

    public String getV1_embeddings() {
        return v1_embeddings;
    }

    public void setV1_embeddings(String v1_embeddings) {
        this.v1_embeddings = v1_embeddings;
    }
}
