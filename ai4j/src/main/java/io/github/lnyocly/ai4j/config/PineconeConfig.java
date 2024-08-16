package io.github.lnyocly.ai4j.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author cly
 * @Description Pinecone向量数据配置文件
 * @Date 2024/8/16 16:37
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PineconeConfig {
    private String url = "https://chatgpt-hxm5j0y.svc.aped-4627-b74a.pinecone.io";
    private String key = "c4e52c27-3fbb-462e-b0c7-a99753cb7b34";

    private String upsert = "/vectors/upsert";
    private String query = "/query";
    private String delete = "/vectors/delete";
}
