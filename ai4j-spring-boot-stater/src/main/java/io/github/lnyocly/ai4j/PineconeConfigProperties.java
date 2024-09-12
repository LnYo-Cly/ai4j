package io.github.lnyocly.ai4j;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @Author cly
 * @Description Pinecone 向量数据配置文件
 * @Date 2024/8/16 16:37
 */

@Data
@ConfigurationProperties(prefix = "ai.vector.pinecone")
public class PineconeConfigProperties {
    private String url = "https://xxx.svc.xxx.pinecone.io";
    private String key = "";

    private String upsert = "/vectors/upsert";
    private String query = "/query";
    private String delete = "/vectors/delete";
}
