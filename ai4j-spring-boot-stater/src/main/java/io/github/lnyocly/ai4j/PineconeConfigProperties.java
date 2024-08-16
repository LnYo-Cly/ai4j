package io.github.lnyocly.ai4j;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @Author cly
 * @Description Pinecone向量数据配置文件
 * @Date 2024/8/16 16:37
 */


@ConfigurationProperties(prefix = "ai.vector.pinecone")
public class PineconeConfigProperties {
    private String url = "https://xxx.svc.xxx.pinecone.io";
    private String key = "";

    private String upsert = "/vectors/upsert";
    private String query = "/query";
    private String delete = "/vectors/delete";

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getDelete() {
        return delete;
    }

    public void setDelete(String delete) {
        this.delete = delete;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getUpsert() {
        return upsert;
    }

    public void setUpsert(String upsert) {
        this.upsert = upsert;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }
}
