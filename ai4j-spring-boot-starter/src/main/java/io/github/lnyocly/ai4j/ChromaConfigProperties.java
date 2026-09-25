package io.github.lnyocly.ai4j;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "ai.vector.chroma")
public class ChromaConfigProperties {

    private boolean enabled = false;

    private String host = "http://localhost:8000";

    private String tenant = "default_tenant";

    private String database = "default_database";

    private String collection = "ai4j_vectors";

    private String token = "";
}
