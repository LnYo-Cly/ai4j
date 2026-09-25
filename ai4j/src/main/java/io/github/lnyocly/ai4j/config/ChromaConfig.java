package io.github.lnyocly.ai4j.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Configuration for the Chroma vector store (Chroma v2 REST API).
 *
 * <p>{@code token} is sent as the {@code X-Chroma-Token} header; leave it
 * empty for unauthenticated local deployments.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChromaConfig {

    private boolean enabled = false;

    private String host = "http://localhost:8000";

    private String tenant = "default_tenant";

    private String database = "default_database";

    private String collection = "ai4j_vectors";

    private String token = "";

}
