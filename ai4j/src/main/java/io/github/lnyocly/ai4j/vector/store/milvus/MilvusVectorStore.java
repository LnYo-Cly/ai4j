package io.github.lnyocly.ai4j.vector.store.milvus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.lnyocly.ai4j.codec.JsonCodec;
import io.github.lnyocly.ai4j.config.MilvusConfig;
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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MilvusVectorStore implements VectorStore {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.get(Constants.APPLICATION_JSON);

    private final MilvusConfig config;
    private final OkHttpClient okHttpClient;

    public MilvusVectorStore(Configuration configuration) {
        this(configuration, configuration == null ? null : configuration.getMilvusConfig());
    }

    public MilvusVectorStore(Configuration configuration, MilvusConfig config) {
        if (configuration == null || configuration.getOkHttpClient() == null) {
            throw new IllegalArgumentException("OkHttpClient configuration is required");
        }
        if (config == null) {
            throw new IllegalArgumentException("milvusConfig is required");
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

        ArrayNode rows = JsonCodec.createArrayNode();
        int index = 0;
        for (VectorRecord record : records) {
            if (record == null || record.getVector() == null || record.getVector().isEmpty()) {
                index++;
                continue;
            }
            ObjectNode row = JsonCodec.createObjectNode();
            row.put(config.getIdField(), resolveId(record.getId(), index));
            row.set(config.getVectorField(), JsonCodec.valueToTree(record.getVector()));
            String content = trimToNull(record.getContent());
            if (content != null) {
                row.put(config.getContentField(), content);
                row.put(Constants.METADATA_KEY, content);
            }
            if (record.getMetadata() != null) {
                for (Map.Entry<String, Object> entry : record.getMetadata().entrySet()) {
                    if (entry != null && entry.getKey() != null && entry.getValue() != null) {
                        row.set(entry.getKey(), JsonCodec.valueToTree(entry.getValue()));
                    }
                }
            }
            rows.add(row);
            index++;
        }
        if (rows.isEmpty()) {
            return 0;
        }

        ObjectNode body = JsonCodec.createObjectNode();
        applyCollectionScope(body, dataset);
        body.set("data", rows);
        executePost(config.getUpsert(), body);
        return rows.size();
    }

    @Override
    public List<VectorSearchResult> search(VectorSearchRequest request) throws Exception {
        String dataset = requiredDataset(request == null ? null : request.getDataset());
        if (request == null || request.getVector() == null || request.getVector().isEmpty()) {
            return Collections.emptyList();
        }

        ObjectNode body = JsonCodec.createObjectNode();
        applyCollectionScope(body, dataset);
        body.set("data", JsonCodec.valueToTree(Collections.singletonList(request.getVector())));
        body.put("annsField", config.getVectorField());
        body.put("limit", request.getTopK() == null || request.getTopK() <= 0 ? 10 : request.getTopK().intValue());
        body.set("outputFields", JsonCodec.valueToTree(config.getOutputFields()));
        String filter = toFilterExpression(request.getFilter());
        if (filter != null) {
            body.put("filter", filter);
        }

        ObjectNode response = executePost(config.getSearch(), body);
        JsonNode results = response == null ? null : response.get("data");
        if (results == null || !results.isArray() || results.isEmpty()) {
            return Collections.emptyList();
        }
        List<VectorSearchResult> vectorResults = new ArrayList<VectorSearchResult>();
        for (int i = 0; i < results.size(); i++) {
            JsonNode item = results.get(i);
            if (item == null || !item.isObject()) {
                continue;
            }
            Map<String, Object> metadata = metadataFromResult(item);
            Object idValue = metadata.remove(config.getIdField());
            vectorResults.add(VectorSearchResult.builder()
                    .id(stringValue(idValue))
                    .score(scoreValue(item))
                    .content(firstNonBlank(
                            stringValue(metadata.get(config.getContentField())),
                            stringValue(metadata.get(Constants.METADATA_KEY))))
                    .metadata(metadata)
                    .build());
        }
        return vectorResults;
    }

    @Override
    public boolean exists(VectorExistsRequest request) throws Exception {
        String dataset = requiredDataset(request == null ? null : request.getDataset());
        String filter = toFilterExpression(request == null ? null : request.getFilter());
        if (filter == null) {
            return false;
        }

        ObjectNode body = JsonCodec.createObjectNode();
        applyCollectionScope(body, dataset);
        body.put("filter", filter);
        body.put("limit", 1);
        body.set("outputFields", JsonCodec.valueToTree(Collections.singletonList(config.getIdField())));

        ObjectNode response = executePost(config.getQuery(), body);
        JsonNode results = response == null ? null : response.get("data");
        return results != null && results.isArray() && !results.isEmpty();
    }

    @Override
    public boolean delete(VectorDeleteRequest request) throws Exception {
        String dataset = requiredDataset(request == null ? null : request.getDataset());
        if (request == null) {
            return false;
        }
        ObjectNode body = JsonCodec.createObjectNode();
        applyCollectionScope(body, dataset);
        if (request.getIds() != null && !request.getIds().isEmpty()) {
            body.set("ids", JsonCodec.valueToTree(request.getIds()));
        } else {
            String filter = toFilterExpression(request.getFilter());
            if (filter == null && request.isDeleteAll()) {
                filter = config.getIdField() + " != \"\"";
            }
            if (filter != null) {
                body.put("filter", filter);
            }
        }
        executePost(config.getDelete(), body);
        return true;
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

    private void applyCollectionScope(ObjectNode body, String dataset) {
        body.put("collectionName", dataset);
        if (trimToNull(config.getPartitionName()) != null) {
            body.put("partitionName", config.getPartitionName().trim());
        }
        if (trimToNull(config.getDbName()) != null) {
            body.put("dbName", config.getDbName().trim());
        }
    }

    private ObjectNode executePost(String path, ObjectNode payload) throws Exception {
        Request.Builder builder = new Request.Builder()
                .url(UrlUtils.concatUrl(config.getHost(), path))
                .post(RequestBody.create(JsonCodec.toJson(payload), JSON_MEDIA_TYPE))
                .header("accept", Constants.APPLICATION_JSON)
                .header("content-type", Constants.APPLICATION_JSON);
        if (trimToNull(config.getToken()) != null) {
            builder.header("Authorization", "Bearer " + config.getToken().trim());
        }

        try (Response response = okHttpClient.newCall(builder.build()).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Milvus request failed: " + response.message());
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

    private Map<String, Object> metadataFromResult(JsonNode item) {
        Map<String, Object> metadata = new LinkedHashMap<String, Object>();
        JsonNode entity = item.get("entity");
        if (entity != null && entity.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = entity.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                metadata.put(entry.getKey(), toPlainValue(entry.getValue()));
            }
        }
        Iterator<Map.Entry<String, JsonNode>> fields = item.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String key = entry.getKey();
            if ("distance".equals(key) || "score".equals(key) || "id".equals(key) || "entity".equals(key)) {
                continue;
            }
            metadata.put(key, toPlainValue(entry.getValue()));
        }
        if (!metadata.containsKey(config.getIdField()) && item.has("id")) {
            metadata.put(config.getIdField(), toPlainValue(item.get("id")));
        }
        return metadata;
    }

    private Object toPlainValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return JsonCodec.mapper().convertValue(node, Object.class);
    }

    private Float scoreValue(JsonNode item) {
        if (item.hasNonNull("score")) {
            return Float.valueOf(item.get("score").floatValue());
        }
        if (item.hasNonNull("distance")) {
            return Float.valueOf(1.0f - item.get("distance").floatValue());
        }
        return null;
    }

    private String toFilterExpression(Map<String, Object> filter) {
        if (filter == null || filter.isEmpty()) {
            return null;
        }
        List<String> clauses = new ArrayList<String>();
        for (Map.Entry<String, Object> entry : filter.entrySet()) {
            if (entry == null || trimToNull(entry.getKey()) == null || entry.getValue() == null) {
                continue;
            }
            String key = entry.getKey().trim();
            Object value = entry.getValue();
            if (value instanceof Collection) {
                List<String> items = new ArrayList<String>();
                for (Object item : (Collection<?>) value) {
                    items.add(formatFilterValue(item));
                }
                clauses.add(key + " in [" + join(items) + "]");
            } else {
                clauses.add(key + " == " + formatFilterValue(value));
            }
        }
        return clauses.isEmpty() ? null : joinWithAnd(clauses);
    }

    private String formatFilterValue(Object value) {
        if (value == null) {
            return "\"\"";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        return "\"" + String.valueOf(value).replace("\"", "\\\"") + "\"";
    }

    private String join(List<String> values) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(values.get(i));
        }
        return builder.toString();
    }

    private String joinWithAnd(List<String> values) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(" and ");
            }
            builder.append(values.get(i));
        }
        return builder.toString();
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

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
