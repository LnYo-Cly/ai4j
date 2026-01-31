package io.github.lnyocly.ai4j;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;



@Data
@ConfigurationProperties(prefix = "ai.doubao")
public class DoubaoConfigProperties {

    private String apiHost = "https://ark.cn-beijing.volces.com/api/v3/";
    private String apiKey = "";
    private String chatCompletionUrl = "chat/completions";
    private String imageGenerationUrl = "images/generations";
}
