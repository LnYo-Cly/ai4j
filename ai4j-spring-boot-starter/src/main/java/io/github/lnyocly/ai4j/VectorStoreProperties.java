package io.github.lnyocly.ai4j;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "ai.vector")
public class VectorStoreProperties {

    private String primary;

}
