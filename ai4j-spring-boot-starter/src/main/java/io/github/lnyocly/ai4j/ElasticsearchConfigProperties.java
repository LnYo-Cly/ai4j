package io.github.lnyocly.ai4j;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "ai.vector.elasticsearch")
public class ElasticsearchConfigProperties {

    private boolean enabled = false;

    private String host = "http://localhost:9200";

    private String apiKey = "";

    private String username = "";

    private String password = "";

    private String indexName = "ai4j_vectors";

    private int vectorDim = 1024;
}
