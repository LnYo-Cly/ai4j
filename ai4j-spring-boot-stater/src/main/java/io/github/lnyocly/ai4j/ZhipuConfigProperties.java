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
    private String chat_completion = "v4/chat/completions";
    private String embedding= "v4/embeddings";

    public String getApiHost() {
        return apiHost;
    }

    public void setApiHost(String apiHost) {
        this.apiHost = apiHost;
    }

    public String getEmbedding() {
        return embedding;
    }

    public void setEmbedding(String embedding) {
        this.embedding = embedding;
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
