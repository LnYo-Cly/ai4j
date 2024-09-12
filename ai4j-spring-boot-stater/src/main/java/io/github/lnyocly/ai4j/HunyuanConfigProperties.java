package io.github.lnyocly.ai4j;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @Author cly
 * @Description 腾讯混元配置文件
 * @Date 2024/9/2 19:13
 */

@Data
@ConfigurationProperties(prefix = "ai.hunyuan")
public class HunyuanConfigProperties {
    private String apiHost = "https://hunyuan.tencentcloudapi.com/";
    /**
     * apiKey 属于SecretId与SecretKey的拼接，格式为 {SecretId}.{SecretKey}
     */
    private String apiKey = "";
}
