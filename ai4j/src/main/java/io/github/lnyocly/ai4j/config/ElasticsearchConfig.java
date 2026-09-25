package io.github.lnyocly.ai4j.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Configuration for the Elasticsearch vector store.
 *
 * <p>Authentication supports two modes: {@code apiKey} is sent as an
 * Elasticsearch {@code ApiKey} authorization header; otherwise
 * {@code username}/{@code password} are sent as HTTP Basic credentials.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ElasticsearchConfig {

    private boolean enabled = false;

    private String host = "http://localhost:9200";

    private String apiKey = "";

    private String username = "";

    private String password = "";

    private String indexName = "ai4j_vectors";

    private int vectorDim = 1024;

}
