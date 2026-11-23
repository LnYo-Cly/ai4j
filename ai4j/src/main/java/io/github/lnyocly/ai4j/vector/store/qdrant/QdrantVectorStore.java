package io.github.lnyocly.ai4j.vector.store.qdrant;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.lnyocly.ai4j.codec.JsonCodec;
import io.github.lnyocly.ai4j.config.QdrantConfig;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class QdrantVectorStore implements VectorStore {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.get(Constants.APPLICATION_JSON);
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE =
            new TypeReference<LinkedHashMap<String, Object>>() {
            };

    private final QdrantConfig config;
    private final OkHttpClient okHttpClient;

    public QdrantVectorStore(Configuration configuration) {
        this(configuration, configuration == null ? null : configuration.getQdrantConfig());
    }

    public QdrantVectorStore(Configuration configuration, QdrantConfig config) {
        if (configuration == null || configuration.getOkHttpClient() == null) {
            throw new IllegalArgumentException("OkHttpClient configuration is required");
        }
        if (config == null) {
            throw new IllegalArgumentException("qdrantConfig is required");
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

        ArrayNode points = JsonCodec.createArrayNode();
        int index = 0;
        for (VectorRecord record : records) {
            if (record == null || record.getVector() == null || record.getVector().isEmpty()) {
                index++;
                continue;
            }
            ObjectNode point = JsonCodec.createObjectNode();
            point.put("id", resolveId(record.getId(), index));
            point.set("vector", namedVector(record.getVector()));
            point.set("payload", payload(record));
            points.add(point);
            index++;
        }
        if (points.isEmpty()) {
            return 0;
        }

        ObjectNode body = JsonCodec.createObjectNode();
        body.set("points", points);
        executePost(url(config.getUpsert(), dataset), body);
        return points.size();
    }

    @Override
    public List<VectorSearchResult> search(VectorSearchRequest request) throws Exception {
        String dataset = requiredDataset(request == null ? null : request.getDataset());
        if (request == null || request.getVector() == null || request.getVector().isEmpty()) {
            return Collections.emptyList();
        }

        ObjectNode body = JsonCodec.createObjectNode();
        body.set("query", JsonCodec.valueToTree(request.getVector()));
        body.put("limit", request.getTopK() == null || request.getTopK() <= 0 ? 10 : request.getTopK().intValue());
        body.put("with_payload", request.getIncludeMetadata() == null ? Boolean.TRUE : request.getIncludeMetadata());
        body.put("with_vector", request.getIncludeVector() == null ? Boolean.FALSE : request.getIncludeVector());
        if (trimToNull(config.getVectorName()) != null) {
            body.put("using", trimToNull(config.getVectorName()));
        }
        ObjectNode filter = toFilter(request.getFilter());
        if (filter != null) {
            body.set("filter", filter);
        }

        ObjectNode response = executePost(url(config.getQuery(), dataset), body);
        JsonNode result = response == null ? null : response.get("result");
        JsonNode points = result == null ? null : result.get("points");
        if (points == null || !points.isArray() || points.isEmpty()) {
            return Collections.emptyList();
        }

        List<VectorSearchResult> results = new ArrayList<VectorSearchResult>();
        for (int i = 0; i < points.size(); i++) {
            JsonNode point = points.get(i);
            if (point == null || !point.isObject()) {
                continue;
            }
            Map<String, Object> metadata = payloadMap(point.get("payload"));
            JsonNode idNode = point.get("id");
            JsonNode scoreNode = point.get("score");
            results.add(VectorSearchResult.builder()
                    .id(idNode == null || idNode.isNull() ? null : stringValue(idNode.isValueNode() ? idNode.asText() : idNode.toString()))
                    .score(scoreNode == null || scoreNode.isNull() ? null : Float.valueOf(scoreNode.floatValue()))
                    .content(stringValue(metadata.get(Constants.METADATA_KEY)))
                    .vector(Boolean.TRUE.equals(request.getIncludeVector()) ? vectorValue(point.get("vector")) : null)
                    .metadata(metadata)
                    .build());
        }
        return results;
    }

    @Override
    public boolean exists(VectorExistsRequest request) throws Exception {
        String dataset = requiredDataset(request == null ? null : request.getDataset());
        ObjectNode filter = toFilter(request == null ? null : request.getFilter());
        if (filter == null) {
            return false;
        }

        ObjectNode body = JsonCodec.createObjectNode();
        body.set("filter", filter);
        body.put("limit", 1);
        body.put("with_payload", false);
        body.put("with_vector", false);

        ObjectNode response = executePost(url(config.getScroll(), dataset), body);
        JsonNode result = response == null ? null : response.get("result");
        JsonNode points = result == null ? null : result.get("points");
        return points != null && points.isArray() && !points.isEmpty();
    }

    @Override
    public boolean delete(VectorDeleteRequest request) throws Exception {
        String dataset = requiredDataset(request == null ? null : request.getDataset());
        if (request == null) {
            return false;
        }

        ObjectNode body = JsonCodec.createObjectNode();
        if (request.getIds() != null && !request.getIds().isEmpty()) {
            body.set("points", JsonCodec.valueToTree(request.getIds()));
        } else {
            ObjectNode filter = toFilter(request.getFilter());
            if (filter == null && request.isDeleteAll()) {
                filter = JsonCodec.createObjectNode();
                filter.set("must", JsonCodec.createArrayNode());
            }
            if (filter != null) {
                body.set("filter", filter);
            }
        }
        executePost(url(config.getDelete(), dataset), body);
        return true;
    }

    @Override
    public VectorStoreCapabilities capabilities() {
        return VectorStoreCapabilities.builder()
                .dataset(true)
                .metadataFilter(true)
                .metadataLookup(true)
                .deleteByFilter(true)
                .returnStoredVector(true)
                .build();
    }

    private ObjectNode payload(VectorRecord record) {
        ObjectNode payload = JsonCodec.createObjectNode();
        if (record.getMetadata() != null) {
            for (Map.Entry<String, Object> entry : record.getMetadata().entrySet()) {
                if (entry != null && entry.getKey() != null && entry.getValue() != null) {
                    payload.set(entry.getKey(), JsonCodec.valueToTree(entry.getValue()));
                }
            }
        }
        String content = trimToNull(record.getContent());
        if (content != null && !payload.has(Constants.METADATA_KEY)) {
            payload.put(Constants.METADATA_KEY, content);
        }
        return payload;
    }

    private JsonNode namedVector(List<Float> vector) {
        if (trimToNull(config.getVectorName()) == null) {
            return JsonCodec.valueToTree(vector);
        }
        ObjectNode named = JsonCodec.createObjectNode();
        named.set(config.getVectorName().trim(), JsonCodec.valueToTree(vector));
        return named;
    }

    private ObjectNode toFilter(Map<String, Object> filter) {
        if (filter == null || filter.isEmpty()) {
            return null;
        }
        ArrayNode must = JsonCodec.createArrayNode();
        for (Map.Entry<String, Object> entry : filter.entrySet()) {
            if (entry == null || trimToNull(entry.getKey()) == null || entry.getValue() == null) {
                continue;
            }
            ObjectNode condition = JsonCodec.createObjectNode();
            condition.put("key", entry.getKey());
            ObjectNode match = JsonCodec.createObjectNode();
            Object value = entry.getValue();
            if (value instanceof Collection) {
                ArrayNode any = JsonCodec.createArrayNode();
                for (Object item : (Collection<?>) value) {
                    any.add(JsonCodec.valueToTree(item));
                }
                match.set("any", any);
            } else {
                match.set("value", JsonCodec.valueToTree(value));
            }
            condition.set("match", match);
            must.add(condition);
        }
        if (must.isEmpty()) {
            return null;
        }
        ObjectNode result = JsonCodec.createObjectNode();
        result.set("must", must);
        return result;
    }

    private ObjectNode executePost(String url, ObjectNode payload) throws Exception {
        Request.Builder builder = new Request.Builder()
                .url(url)
                .post(RequestBody.create(JsonCodec.toJson(payload), JSON_MEDIA_TYPE))
                .header("accept", Constants.APPLICATION_JSON)
                .header("content-type", Constants.APPLICATION_JSON);
        if (trimToNull(config.getApiKey()) != null) {
            builder.header("api-key", config.getApiKey().trim());
        }

        try (Response response = okHttpClient.newCall(builder.build()).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Qdrant request failed: " + response.message());
            }
            ResponseBody responseBody = response.body();
            String body = responseBody == null ? "{}" : responseBody.string();
            if (body.trim().isEmpty()) {
                return JsonCodec.createObjectNode();
            }
            JsonNode node = JsonCodec.readTree(body);
            return node != null && node.isObject() ? (ObjectNode) node : JsonCodec.createObjectNode();
        }
    }

    private List<Float> vectorValue(JsonNode rawVector) {
        if (rawVector == null || rawVector.isNull()) {
            return null;
        }
        JsonNode values = null;
        if (rawVector.isArray()) {
            values = rawVector;
        } else if (rawVector.isObject() && trimToNull(config.getVectorName()) != null) {
            values = rawVector.get(config.getVectorName().trim());
        }
        if (values == null || !values.isArray()) {
            return null;
        }
        List<Float> vector = new ArrayList<Float>(values.size());
        for (int i = 0; i < values.size(); i++) {
            vector.add(values.get(i).floatValue());
        }
        return vector;
    }

    private Map<String, Object> payloadMap(JsonNode payload) {
        if (payload == null || !payload.isObject() || payload.size() == 0) {
            return Collections.emptyMap();
        }
        return JsonCodec.mapper().convertValue(payload, MAP_TYPE);
    }

    private String url(String template, String dataset) {
        return UrlUtils.concatUrl(config.getHost(), String.format(template, dataset));
    }

    private String requiredDataset(String dataset) {
        String value = trimToNull(dataset);
        if (value == null) {
            throw new IllegalArgumentException("dataset is required");
        }
        return value;
    }

    private String resolveId(String id, int index) {
        String value = trimToNull(id);
        return value == null ? "id_" + index : value;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }
}
