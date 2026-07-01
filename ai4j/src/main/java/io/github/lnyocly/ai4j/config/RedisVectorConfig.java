package io.github.lnyocly.ai4j.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Arrays;
import java.util.List;

/**
 * Configuration for the Redis Stack (RediSearch) vector store. Requires a Redis instance
 * with the RediSearch module (Redis Stack). Fields marked as tag/numeric are indexed for
 * metadata filtering; all other metadata is stored but not indexed.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RedisVectorConfig {

    private boolean enabled = false;

    private String host = "localhost";

    private int port = 6379;

    private String password = "";

    private int database = 0;

    /** Hash key prefix; documents are stored as {@code <keyPrefix><dataset>:<id>}. */
    private String keyPrefix = "ai4j:vector:";

    private String indexName = "ai4j_vector_index";

    private int vectorDim = 1024;

    /** One of COSINE / L2 / IP. */
    private String distanceMetric = "COSINE";

    /** Index algorithm: HNSW (default, scalable) or FLAT (exact, small datasets). */
    private String algorithm = "HNSW";

    private int m = 16;

    private int efConstruction = 200;

    private String vectorField = "vector";

    private String contentField = "content";

    /** Fields indexed as TAG (equality / multi-value filtering). Always includes {@code dataset}. */
    private List<String> tagFields = Arrays.asList("dataset");

    /** Fields indexed as NUMERIC (range filtering), e.g. documentVersion. */
    private List<String> numericFields = Arrays.asList();

    private int connectTimeoutMillis = 2000;

    private int readTimeoutMillis = 5000;
}
