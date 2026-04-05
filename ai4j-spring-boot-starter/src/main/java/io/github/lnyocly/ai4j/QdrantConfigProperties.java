package io.github.lnyocly.ai4j;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "ai.vector.qdrant")
public class QdrantConfigProperties {

    private boolean enabled = false;

    private String host = "http://localhost:6333";

    private String apiKey = "";

    private String vectorName = "";

    private String upsert = "/collections/%s/points";

    private String query = "/collections/%s/points/query";

    private String delete = "/collections/%s/points/delete";
}
