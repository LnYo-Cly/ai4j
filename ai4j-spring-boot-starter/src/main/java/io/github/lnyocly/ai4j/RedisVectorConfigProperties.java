package io.github.lnyocly.ai4j;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "ai.vector.redis")
public class RedisVectorConfigProperties {

    private boolean enabled = false;

    private String host = "localhost";

    private int port = 6379;

    private String password = "";

    private int database = 0;

    private String keyPrefix = "ai4j:vector:";

    private String indexName = "ai4j_vector_index";

    private int vectorDim = 1024;

    private String distanceMetric = "COSINE";

    private String algorithm = "HNSW";

    private int m = 16;

    private int efConstruction = 200;

    private String vectorField = "vector";

    private String contentField = "content";

    private List<String> tagFields = Arrays.asList("dataset");

    private List<String> numericFields = Arrays.asList();

    private int connectTimeoutMillis = 2000;

    private int readTimeoutMillis = 5000;
}
