package io.github.lnyocly.ai4j.vector.store.elasticsearch;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.lnyocly.ai4j.codec.JsonCodec;
import io.github.lnyocly.ai4j.config.ElasticsearchConfig;
import io.github.lnyocly.ai4j.constant.Constants;
import io.github.lnyocly.ai4j.network.UrlUtils;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.vector.store.VectorDeleteRequest;
import io.github.lnyocly.ai4j.vector.store.VectorExistsRequest;
import io.github.lnyocly.ai4j.vector.store.VectorRecord;
import io.github.lnyocly.ai4j.vector.store.VectorSearchRequest;
import io.github.lnyocly.ai4j.vector.store.VectorSearchResult;
import io.github.lnyocly.ai4j.vector.store.VectorStore;
import io.github.lnyocly.ai4j.vector.store.VectorStoreCapabilities;
import io.github.lnyocly.ai4j.vector.store.VectorUpsertRequest;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link VectorStore} backed by Elasticsearch dense-vector search (ES 8.4+).
 *
 * <p>All records live in a single index ({@link ElasticsearchConfig#getIndexName()});
 * the {@code dataset} request field is stored as a keyword field and always
 * applied as a filter, so multiple datasets can share one index. The index is
 * created lazily on the first upsert with this mapping:</p>
 *
 * <pre>
 * vector   : dense_vector(dims = vectorDim, index = true, similarity = cosine)
 * content  : text (not indexed)
 * dataset  : keyword
 * metadata : flattened (leaf values term-queryable as metadata.&lt;key&gt;)
 * </pre>
 *
 * <p>Writes use {@code refresh=true} so upserts and deletes are immediately
 * visible to search/exists; this matches the read-after-write expectations of
 * ingestion pipelines.</p>
 */
public class ElasticsearchVectorStore implements VectorStore {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.get(Constants.APPLICATION_JSON);
    private static final MediaType NDJSON_MEDIA_TYPE = MediaType.get("application/x-ndjson");
    private static final int NUM_CANDIDATES = 100;
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE =
            new TypeReference<LinkedHashMap<String, Object>>() {
            };

    private final ElasticsearchConfig config;
    private final OkHttpClient okHttpClient;
    private volatile boolean indexEnsured = false;

    public ElasticsearchVectorStore(Configuration configuration) {
        this(configuration, configuration == null ? null : configuration.getElasticsearchConfig());
    }

    public ElasticsearchVectorStore(Configuration configuration, ElasticsearchConfig config) {
        if (configuration == null || configuration.getOkHttpClient() == null) {
            throw new IllegalArgumentException("OkHttpClient configuration is required");
        }
        if (config == null) {
            throw new IllegalArgumentException("elasticsearchConfig is required");
        }
        this.okHttpClient = configuration.getOkHttpClient();
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
        ensureIndex();

        StringBuilder ndjson = new StringBuilder();
        int count = 0;
        int index = 0;
        for (VectorRecord record : records) {
            if (record == null || record.getVector() == null || record.getVector().isEmpty()) {
                index++;
                continue;
            }
            ObjectNode action = JsonCodec.createObjectNode();
            ObjectNode meta = JsonCodec.createObjectNode();
            meta.put("_id", resolveId(record.getId(), index));
            action.set("index", meta);
            ndjson.append(JsonCodec.toJson(action)).append('\n');
            ndjson.append(JsonCodec.toJson(document(dataset, record))).append('\n');
            count++;
            index++;
        }
        if (count == 0) {
            return 0;
        }
        execute(url("/_bulk?refresh=true"), ndjson.toString(), NDJSON_MEDIA_TYPE);
        return count;
    }

    @Override
    public List<VectorSearchResult> search(VectorSearchRequest request) throws Exception {
        String dataset = requiredDataset(request == null ? null : request.getDataset());
        if (request == null || request.getVector() == null || request.getVector().isEmpty()) {
            return Collections.emptyList();
        }

        int topK = request.getTopK() == null || request.getTopK() <= 0 ? 10 : request.getTopK();
        ObjectNode knn = JsonCodec.createObjectNode();
        knn.put("field", "vector");
        knn.set("query_vector", JsonCodec.valueToTree(request.getVector()));
        knn.put("k", topK);
        knn.put("num_candidates", Math.max(topK * 4, NUM_CANDIDATES));
        ArrayNode filters = filters(dataset, request.getFilter());
        if (!filters.isEmpty()) {
            knn.set("filter", filters);
        }

        ObjectNode body = JsonCodec.createObjectNode();
        body.put("size", topK);
        body.set("knn", knn);
        ArrayNode source = JsonCodec.createArrayNode();
        source.add("content");
        source.add("metadata");
        if (Boolean.TRUE.equals(request.getIncludeVector())) {
            source.add("vector");
        }
        body.set("_source", source);

        ObjectNode response = executePost(url("/_search"), body);
        JsonNode hits = response == null ? null : response.get("hits");
        JsonNode hitList = hits == null ? null : hits.get("hits");
        if (hitList == null || !hitList.isArray() || hitList.isEmpty()) {
            return Collections.emptyList();
        }

        List<VectorSearchResult> results = new ArrayList<VectorSearchResult>();
        for (int i = 0; i < hitList.size(); i++) {
            JsonNode hit = hitList.get(i);
            if (hit == null || !hit.isObject()) {
                continue;
            }
            JsonNode src = hit.get("_source");
            JsonNode metaNode = src == null ? null : src.get("metadata");
            Map<String, Object> metadata = metaNode != null && metaNode.isObject()
                    ? JsonCodec.mapper().convertValue(metaNode, MAP_TYPE)
                    : null;
            JsonNode idNode = hit.get("_id");
            JsonNode scoreNode = hit.get("_score");
            JsonNode contentNode = src == null ? null : src.get("content");
            results.add(VectorSearchResult.builder()
                    .id(idNode == null || idNode.isNull() ? null : idNode.asText())
                    .score(scoreNode == null || scoreNode.isNull() ? null : Float.valueOf(scoreNode.floatValue()))
                    .content(contentNode == null || contentNode.isNull() ? null : contentNode.asText())
                    .metadata(Boolean.FALSE.equals(request.getIncludeMetadata())
                            ? null
                            : (metadata == null ? Collections.<String, Object>emptyMap() : metadata))
                    .vector(Boolean.TRUE.equals(request.getIncludeVector()) && src != null
                            ? vectorValue(src.get("vector"))
                            : null)
                    .build());
        }
        return results;
    }

    @Override
    public boolean delete(VectorDeleteRequest request) throws Exception {
        String dataset = requiredDataset(request == null ? null : request.getDataset());
        if (request == null) {
            return false;
        }
        boolean hasIds = request.getIds() != null && !request.getIds().isEmpty();
        boolean hasFilter = request.getFilter() != null && !request.getFilter().isEmpty();
        if (!request.isDeleteAll() && !hasIds && !hasFilter) {
            return false;
        }

        ArrayNode filter = filters(dataset, request.getFilter());
        if (hasIds) {
            ObjectNode ids = JsonCodec.createObjectNode();
            ids.set("_id", JsonCodec.valueToTree(request.getIds()));
            ObjectNode terms = JsonCodec.createObjectNode();
            terms.set("terms", ids);
            filter.add(terms);
        }

        ObjectNode bool = JsonCodec.createObjectNode();
        bool.set("filter", filter);
        ObjectNode boolQuery = JsonCodec.createObjectNode();
        boolQuery.set("bool", bool);
        ObjectNode body = JsonCodec.createObjectNode();
        body.set("query", boolQuery);
        execute(url("/_delete_by_query?refresh=true&conflicts=proceed"), JsonCodec.toJson(body), JSON_MEDIA_TYPE);
        return true;
    }

    @Override
    public boolean exists(VectorExistsRequest request) throws Exception {
        String dataset = requiredDataset(request == null ? null : request.getDataset());
        if (request == null) {
            return false;
        }
        boolean hasIds = request.getIds() != null && !request.getIds().isEmpty();
        boolean hasFilter = request.getFilter() != null && !request.getFilter().isEmpty();
        if (!hasIds && !hasFilter) {
            return false;
        }

        ArrayNode filter = filters(dataset, request.getFilter());
        if (hasIds) {
            ObjectNode ids = JsonCodec.createObjectNode();
            ids.set("_id", JsonCodec.valueToTree(request.getIds()));
            ObjectNode terms = JsonCodec.createObjectNode();
            terms.set("terms", ids);
            filter.add(terms);
        }

        ObjectNode bool = JsonCodec.createObjectNode();
        bool.set("filter", filter);
        ObjectNode boolQuery = JsonCodec.createObjectNode();
        boolQuery.set("bool", bool);
        ObjectNode body = JsonCodec.createObjectNode();
        body.put("size", 0);
        body.put("terminate_after", 1);
        body.put("track_total_hits", Boolean.TRUE);
        body.set("query", boolQuery);

        ObjectNode response = executePost(url("/_search"), body);
        JsonNode hits = response == null ? null : response.get("hits");
        JsonNode total = hits == null ? null : hits.get("total");
        return total != null && total.isObject() && total.path("value").asLong() > 0L;
    }

    @Override
    public VectorStoreCapabilities capabilities() {
        return VectorStoreCapabilities.builder()
                .dataset(true)
                .metadataFilter(true)
                .metadataLookup(true)
                .idLookup(true)
                .deleteByFilter(true)
                .returnStoredVector(true)
                .build();
    }

    private void ensureIndex() throws Exception {
        if (indexEnsured) {
            return;
        }
        Request.Builder head = new Request.Builder().url(indexUrl()).head();
        applyAuth(head);
        try (Response response = okHttpClient.newCall(head.build()).execute()) {
            if (response.isSuccessful()) {
                indexEnsured = true;
                return;
            }
            if (response.code() != 404) {
                throw new IOException("Elasticsearch index check failed: " + response.message());
            }
        }

        ObjectNode vector = JsonCodec.createObjectNode();
        vector.put("type", "dense_vector");
        vector.put("dims", config.getVectorDim());
        vector.put("index", Boolean.TRUE);
        vector.put("similarity", "cosine");

        ObjectNode content = JsonCodec.createObjectNode();
        content.put("type", "text");
        content.put("index", Boolean.FALSE);

        ObjectNode dataset = JsonCodec.createObjectNode();
        dataset.put("type", "keyword");

        ObjectNode metadata = JsonCodec.createObjectNode();
        metadata.put("type", "flattened");

        ObjectNode properties = JsonCodec.createObjectNode();
        properties.set("vector", vector);
        properties.set("content", content);
        properties.set("dataset", dataset);
        properties.set("metadata", metadata);

        ObjectNode mappings = JsonCodec.createObjectNode();
        mappings.set("properties", properties);
        ObjectNode body = JsonCodec.createObjectNode();
        body.set("mappings", mappings);

        Request.Builder put = new Request.Builder()
                .url(indexUrl())
                .put(RequestBody.create(JsonCodec.toJson(body), JSON_MEDIA_TYPE));
        applyAuth(put);
        try (Response response = okHttpClient.newCall(put.build()).execute()) {
            if (!response.isSuccessful() && response.code() != 400) {
                throw new IOException("Elasticsearch index creation failed: " + response.message());
            }
        }
        indexEnsured = true;
    }

    private ObjectNode document(String dataset, VectorRecord record) {
        ObjectNode doc = JsonCodec.createObjectNode();
        doc.set("vector", JsonCodec.valueToTree(record.getVector()));
        doc.put("dataset", dataset);
        doc.put("content", record.getContent());
        if (record.getMetadata() != null && !record.getMetadata().isEmpty()) {
            doc.set("metadata", JsonCodec.valueToTree(record.getMetadata()));
        }
        return doc;
    }

    private ArrayNode filters(String dataset, Map<String, Object> filter) {
        ArrayNode clauses = JsonCodec.createArrayNode();
        ObjectNode datasetTerm = JsonCodec.createObjectNode();
        ObjectNode term = JsonCodec.createObjectNode();
        term.put("dataset", dataset);
        datasetTerm.set("term", term);
        clauses.add(datasetTerm);
        if (filter != null) {
            for (Map.Entry<String, Object> entry : filter.entrySet()) {
                if (entry == null || trimToNull(entry.getKey()) == null || entry.getValue() == null) {
                    continue;
                }
                String field = "metadata." + entry.getKey().trim();
                Object value = entry.getValue();
                ObjectNode clause = JsonCodec.createObjectNode();
                ObjectNode body = JsonCodec.createObjectNode();
                if (value instanceof Collection) {
                    ArrayNode values = JsonCodec.createArrayNode();
                    for (Object item : (Collection<?>) value) {
                        if (item != null) {
                            values.add(JsonCodec.valueToTree(item));
                        }
                    }
                    if (values.isEmpty()) {
                        continue;
                    }
                    body.set(field, values);
                    clause.set("terms", body);
                } else {
                    body.set(field, JsonCodec.valueToTree(value));
                    clause.set("term", body);
                }
                clauses.add(clause);
            }
        }
        return clauses;
    }

    private String indexUrl() {
        return UrlUtils.concatUrl(config.getHost(), config.getIndexName());
    }

    private String url(String suffix) {
        return UrlUtils.concatUrl(indexUrl(), suffix);
    }

    private void applyAuth(Request.Builder builder) {
        if (trimToNull(config.getApiKey()) != null) {
            builder.header("Authorization", "ApiKey " + config.getApiKey().trim());
        } else if (trimToNull(config.getUsername()) != null) {
            String credentials = config.getUsername().trim() + ":"
                    + (config.getPassword() == null ? "" : config.getPassword());
            builder.header("Authorization", "Basic "
                    + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
        }
    }

    private ObjectNode executePost(String url, ObjectNode body) throws Exception {
        String text = execute(url, body == null ? "{}" : JsonCodec.toJson(body), JSON_MEDIA_TYPE);
        if (text == null || text.trim().isEmpty()) {
            return JsonCodec.createObjectNode();
        }
        JsonNode node = JsonCodec.readTree(text);
        return node != null && node.isObject() ? (ObjectNode) node : JsonCodec.createObjectNode();
    }

    private String execute(String url, String payload, MediaType mediaType) throws Exception {
        Request.Builder builder = new Request.Builder()
                .url(url)
                .post(RequestBody.create(payload, mediaType))
                .header("accept", Constants.APPLICATION_JSON)
                .header("content-type", mediaType.toString());
        applyAuth(builder);
        try (Response response = okHttpClient.newCall(builder.build()).execute()) {
            ResponseBody responseBody = response.body();
            String text = responseBody == null ? "" : responseBody.string();
            if (!response.isSuccessful()) {
                throw new IOException("Elasticsearch request failed: " + response.message()
                        + " " + abbreviate(text));
            }
            return text;
        }
    }

    private List<Float> vectorValue(JsonNode rawVector) {
        if (rawVector == null || !rawVector.isArray()) {
            return null;
        }
        List<Float> vector = new ArrayList<Float>(rawVector.size());
        for (int i = 0; i < rawVector.size(); i++) {
            vector.add(rawVector.get(i).floatValue());
        }
        return vector;
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

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 200 ? text : text.substring(0, 200);
    }
}
