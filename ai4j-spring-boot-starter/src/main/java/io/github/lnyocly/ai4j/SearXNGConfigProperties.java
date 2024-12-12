package io.github.lnyocly.ai4j;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @Author cly
 * @Description TODO
 * @Date 2024/12/12 11:55
 */
@Data
@ConfigurationProperties(prefix = "ai.websearch.searxng")
public class SearXNGConfigProperties {
    private String url;
}
