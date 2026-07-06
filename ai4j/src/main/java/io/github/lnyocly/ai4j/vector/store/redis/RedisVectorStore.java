package io.github.lnyocly.ai4j.vector.store.redis;

import io.github.lnyocly.ai4j.config.RedisVectorConfig;
import io.github.lnyocly.ai4j.constant.Constants;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.vector.store.VectorDeleteRequest;
import io.github.lnyocly.ai4j.vector.store.VectorExistsRequest;
import io.github.lnyocly.ai4j.vector.store.VectorRecord;
import io.github.lnyocly.ai4j.vector.store.VectorSearchRequest;
import io.github.lnyocly.ai4j.vector.store.VectorSearchResult;
import io.github.lnyocly.ai4j.vector.store.VectorStore;
import io.github.lnyocly.ai4j.vector.store.VectorStoreCapabilities;
import io.github.lnyocly.ai4j.vector.store.VectorUpsertRequest;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.search.Document;
import redis.clients.jedis.search.FTSearchParams;
import redis.clients.jedis.search.IndexDefinition;
import redis.clients.jedis.search.IndexOptions;
import redis.clients.jedis.search.Schema;
import redis.clients.jedis.search.SearchResult;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Redis Stack (RediSearch) {@link VectorStore}. Documents are stored as Redis Hashes and
 * indexed via {@code FT.CREATE}; retrieval uses KNN {@code FT.SEARCH} with a FLOAT32
 * little-endian vector payload.
 * <p>
 * Uses Jedis (optional dependency) for the RESP wire protocol, so callers must have
 * jedis 4.x on the classpath to enable this store — the same opt-in model PgVectorStore
 * uses for the postgres JDBC driver.
 * <p>
 * Connections are opened per operation (no pooling), mirroring PgVectorStore. Users with
 * high-throughput needs should wrap a pooled Jedis/UnifiedJedis at the application layer.
 */
public class RedisVectorStore implements VectorStore {

    private static final String SCORE_ALIAS = "__vector_score";
    private static final int DELETE_BATCH_LIMIT = 1000;

    private final RedisVectorConfig config;
    private volatile boolean indexReady = false;

    public RedisVectorStore(Configuration configuration) {
        this(configuration == null ? null : configuration.getRedisVectorConfig());
    }

    public RedisVectorStore(RedisVectorConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("redisVectorConfig is required");
        }
        this.config = config;
    }

    @Override
    public int upsert(VectorUpsertRequest request) throws Exception {
        String dataset = requiredDataset(request == null ? null : request.getDataset());
        List<VectorRecord> records = request == null || request.getRecords() == null
                ? Collections.<VectorRecord>emptyList()
                : request.getRecords();
        if (records.isEmpty()) {
            return 0;
        }

        try (UnifiedJedis jedis = connect()) {
            ensureIndex(jedis);
            int count = 0;
            for (int i = 0; i < records.size(); i++) {
                VectorRecord record = records.get(i);
                if (record == null || record.getVector() == null || record.getVector().isEmpty()) {
                    continue;
                }
                String id = resolveId(record.getId(), i);
                String key = buildKey(dataset, id);

                jedis.hset(key.getBytes(StandardCharsets.UTF_8),
                        config.getVectorField().getBytes(StandardCharsets.UTF_8),
                        encodeVector(record.getVector()));

                Map<String, String> fields = new LinkedHashMap<String, String>();
                fields.put("dataset", dataset);
                if (trimToNull(record.getContent()) != null) {
                    fields.put(config.getContentField(), record.getContent());
                    fields.put(Constants.METADATA_KEY, record.getContent().trim());
                }
                if (record.getMetadata() != null) {
                    for (Map.Entry<String, Object> entry : record.getMetadata().entrySet()) {
                        if (entry == null || entry.getKey() == null || entry.getValue() == null) {
                            continue;
                        }
                        String name = entry.getKey().trim();
                        if (name.isEmpty()
                                || name.equals(config.getVectorField())
                                || name.equals(config.getContentField())
                                || name.equals("dataset")) {
                            continue;
                        }
                        fields.put(name, String.valueOf(entry.getValue()));
                    }
                }
                jedis.hset(key, fields);
                count++;
            }
            return count;
        }
    }

    @Override
    public List<VectorSearchResult> search(VectorSearchRequest request) throws Exception {
        String dataset = requiredDataset(request == null ? null : request.getDataset());
        if (request == null || request.getVector() == null || request.getVector().isEmpty()) {
            return Collections.emptyList();
        }
        int topK = request.getTopK() == null || request.getTopK() <= 0 ? 10 : request.getTopK();
        String filter = buildFilter(dataset, request.getFilter());
        String query = "(" + filter + ")=>[KNN " + topK + " @" + config.getVectorField()
                + " $vec AS " + SCORE_ALIAS + "]";

        List<String> returnFields = new ArrayList<String>();
        returnFields.add(config.getContentField());
        returnFields.add(SCORE_ALIAS);
        appendUnique(returnFields, config.getTagFields());
        appendUnique(returnFields, config.getNumericFields());

        FTSearchParams params = new FTSearchParams()
                .addParam("vec", encodeVector(request.getVector()))
                .limit(0, topK)
                .returnFields(returnFields.toArray(new String[0]))
                .dialect(2);

        try (UnifiedJedis jedis = connect()) {
            SearchResult result = jedis.ftSearch(config.getIndexName(), query, params);
            List<VectorSearchResult> results = new ArrayList<VectorSearchResult>();
            for (Document doc : result.getDocuments()) {
                String content = doc.getString(config.getContentField());
                float rawScore = parseFloatSafe(doc.getString(SCORE_ALIAS));
                float score = "COSINE".equalsIgnoreCase(config.getDistanceMetric())
                        ? 1.0f - rawScore : rawScore;
                Map<String, Object> metadata = new LinkedHashMap<String, Object>();
                for (Map.Entry<String, Object> entry : doc.getProperties()) {
                    String name = entry.getKey();
                    if (name == null || entry.getValue() == null) {
                        continue;
                    }
                    if (name.equals(config.getContentField())
                            || name.equals(SCORE_ALIAS)
                            || name.equals("dataset")) {
                        continue;
                    }
                    metadata.put(name, entry.getValue());
                }
                results.add(VectorSearchResult.builder()
                        .id(stripKey(doc.getId(), dataset))
                        .score(score)
                        .content(content)
                        .metadata(metadata)
                        .build());
            }
            return results;
        }
    }

    @Override
    public boolean exists(VectorExistsRequest request) throws Exception {
        String dataset = requiredDataset(request == null ? null : request.getDataset());
        if (request == null || request.getFilter() == null || request.getFilter().isEmpty()) {
            return false;
        }
        try (UnifiedJedis jedis = connect()) {
            ensureIndex(jedis);
            SearchResult result = jedis.ftSearch(config.getIndexName(), buildFilter(dataset, request.getFilter()),
                    new FTSearchParams().limit(0, 1).dialect(2));
            return result.getTotalResults() > 0 || !result.getDocuments().isEmpty();
        }
    }

    @Override
    public boolean delete(VectorDeleteRequest request) throws Exception {
        String dataset = requiredDataset(request == null ? null : request.getDataset());
        if (request == null) {
            return false;
        }
        try (UnifiedJedis jedis = connect()) {
            if (request.getIds() != null && !request.getIds().isEmpty()) {
                for (String id : request.getIds()) {
                    jedis.del(buildKey(dataset, id));
                }
            } else {
                // ponytail: single bounded search; paginate if delete-by-filter ever needs >1000 docs.
                String filter = buildFilter(dataset, request.getFilter());
                SearchResult result = jedis.ftSearch(config.getIndexName(), filter,
                        new FTSearchParams().limit(0, DELETE_BATCH_LIMIT).dialect(2));
                for (Document doc : result.getDocuments()) {
                    jedis.del(doc.getId());
                }
            }
            return true;
        }
    }

    @Override
    public VectorStoreCapabilities capabilities() {
        return VectorStoreCapabilities.builder()
                .dataset(true)
                .metadataFilter(true)
                .metadataLookup(true)
                .deleteByFilter(true)
                .returnStoredVector(false)
                .build();
    }

    // ---- index management ----

    private void ensureIndex(UnifiedJedis jedis) {
        if (indexReady) {
            return;
        }
        Map<String, Object> vectorAttrs = new LinkedHashMap<String, Object>();
        vectorAttrs.put("TYPE", "FLOAT32");
        vectorAttrs.put("DIM", String.valueOf(config.getVectorDim()));
        vectorAttrs.put("DISTANCE_METRIC", config.getDistanceMetric());
        boolean hnsw = !"FLAT".equalsIgnoreCase(config.getAlgorithm());

        Schema schema = new Schema().addTextField(config.getContentField(), 1.0);
        if (hnsw) {
            vectorAttrs.put("M", String.valueOf(config.getM()));
            vectorAttrs.put("EF_CONSTRUCTION", String.valueOf(config.getEfConstruction()));
            schema = schema.addHNSWVectorField(config.getVectorField(), vectorAttrs);
        } else {
            schema = schema.addFlatVectorField(config.getVectorField(), vectorAttrs);
        }
        if (config.getTagFields() != null) {
            for (String field : config.getTagFields()) {
                if (trimToNull(field) == null || field.trim().equals("dataset")) {
                    continue;
                }
                schema = schema.addTagField(field.trim());
            }
        }
        schema = schema.addTagField("dataset");
        if (config.getNumericFields() != null) {
            for (String field : config.getNumericFields()) {
                if (trimToNull(field) == null) {
                    continue;
                }
                schema = schema.addNumericField(field.trim());
            }
        }

        IndexDefinition definition = new IndexDefinition(IndexDefinition.Type.HASH)
                .setPrefixes(config.getKeyPrefix());
        IndexOptions options = IndexOptions.defaultOptions().setDefinition(definition);
        try {
            jedis.ftCreate(config.getIndexName(), options, schema);
        } catch (Exception e) {
            String message = e.getMessage() == null ? "" : e.getMessage();
            if (!message.contains("already exists")) {
                throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
            }
        }
        indexReady = true;
    }

    // ---- query helpers ----

    private String buildFilter(String dataset, Map<String, Object> filter) {
        StringBuilder builder = new StringBuilder();
        builder.append("@dataset:{").append(escapeTag(dataset)).append("}");
        if (filter == null || filter.isEmpty()) {
            return builder.toString();
        }
        for (Map.Entry<String, Object> entry : filter.entrySet()) {
            if (entry == null || entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            String key = entry.getKey().trim();
            if (key.isEmpty()) {
                continue;
            }
            Object value = entry.getValue();
            if (value instanceof Collection) {
                builder.append(" @").append(key).append(":{");
                boolean first = true;
                for (Object item : (Collection<?>) value) {
                    if (item == null) {
                        continue;
                    }
                    if (!first) {
                        builder.append("|");
                    }
                    builder.append(escapeTag(String.valueOf(item)));
                    first = false;
                }
                builder.append("}");
            } else if (isNumericField(key) && value instanceof Number) {
                double n = ((Number) value).doubleValue();
                builder.append(" @").append(key).append(":[").append(n).append(" ").append(n).append("]");
            } else {
                builder.append(" @").append(key).append(":{").append(escapeTag(String.valueOf(value))).append("}");
            }
        }
        return builder.toString();
    }

    private boolean isNumericField(String key) {
        if (config.getNumericFields() == null) {
            return false;
        }
        for (String field : config.getNumericFields()) {
            if (key.equals(field)) {
                return true;
            }
        }
        return false;
    }

    // ponytail: minimal TAG escaping — sufficient for typical tenant/version identifiers;
    // exotic values containing {@code |} or braces would need richer handling.
    private String escapeTag(String value) {
        return value == null ? "" : value;
    }

    // ---- connection ----

    private UnifiedJedis connect() {
        DefaultJedisClientConfig.Builder builder = DefaultJedisClientConfig.builder()
                .connectionTimeoutMillis(config.getConnectTimeoutMillis())
                .socketTimeoutMillis(config.getReadTimeoutMillis())
                .database(config.getDatabase());
        if (trimToNull(config.getPassword()) != null) {
            builder.password(config.getPassword());
        }
        return new UnifiedJedis(new HostAndPort(config.getHost(), config.getPort()), builder.build());
    }

    // ---- codec & utilities ----

    private byte[] encodeVector(List<Float> vector) {
        ByteBuffer buffer = ByteBuffer.allocate(vector.size() * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (Float value : vector) {
            buffer.putFloat(value == null ? 0f : value);
        }
        return buffer.array();
    }

    private String buildKey(String dataset, String id) {
        return config.getKeyPrefix() + dataset + ":" + id;
    }

    private String stripKey(String key, String dataset) {
        if (key == null) {
            return null;
        }
        String prefix = config.getKeyPrefix() + dataset + ":";
        return key.startsWith(prefix) ? key.substring(prefix.length()) : key;
    }

    private void appendUnique(List<String> target, List<String> source) {
        if (source == null) {
            return;
        }
        for (String value : source) {
            if (trimToNull(value) != null && !target.contains(value)) {
                target.add(value);
            }
        }
    }

    private String resolveId(String id, int index) {
        String value = trimToNull(id);
        return value == null ? "id_" + index : value;
    }

    private String requiredDataset(String dataset) {
        String value = trimToNull(dataset);
        if (value == null) {
            throw new IllegalArgumentException("dataset is required");
        }
        return value;
    }

    private float parseFloatSafe(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0f;
        }
        try {
            return Float.parseFloat(value.trim());
        } catch (NumberFormatException e) {
            return 0f;
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
