package io.github.lnyocly.ai4j;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "ai.typesafe")
public class TypeSafeConfigProperties {

    private String apiHost = "https://api.typesafe.ai/";

    private String apiKey = "";

    private String systemOneUrl = "v1/systemone";

    private String modelsUrl = "v1/models";

    private String defaultModel = "jev-latest";
}
