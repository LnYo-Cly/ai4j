package io.github.lnyocly.ai4j.vector.store.milvus;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.config.MilvusConfig;
import io.github.lnyocly.ai4j.constant.Constants;
import io.github.lnyocly.ai4j.network.UrlUtils;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.vector.store.VectorDeleteRequest;
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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

        JSONArray rows = new JSONArray();
        int index = 0;
        for (VectorRecord record : records) {
            if (record == null || record.getVector() == null || record.getVector().isEmpty()) {
                index++;
                continue;
            }
            JSONObject row = new JSONObject();
            row.put(config.getIdField(), resolveId(record.getId(), index));
            row.put(config.getVectorField(), record.getVector());
            String content = trimToNull(record.getContent());
            if (content != null) {
                row.put(config.getContentField(), content);
                row.put(Constants.METADATA_KEY, content);
            }
            if (record.getMetadata() != null) {
                for (Map.Entry<String, Object> entry : record.getMetadata().entrySet()) {
                    if (entry != null && entry.getKey() != null && entry.getValue() != null) {
                        row.put(entry.getKey(), entry.getValue());
                    }
                }
            }
            rows.add(row);
            index++;
        }
        if (rows.isEmpty()) {
            return 0;
        }

        JSONObject body = new JSONObject();
        applyCollectionScope(body, dataset);
        body.put("data", rows);
        executePost(config.getUpsert(), body);
        return rows.size();
    }

    @Override
    public List<VectorSearchResult> search(VectorSearchRequest request) throws Exception {
        String dataset = requiredDataset(request == null ? null : request.getDataset());
        if (request == null || request.getVector() == null || request.getVector().isEmpty()) {
            return Collections.emptyList();
        }

        JSONObject body = new JSONObject();
        applyCollectionScope(body, dataset);
        body.put("data", Collections.singletonList(request.getVector()));
        body.put("annsField", config.getVectorField());
        body.put("limit", request.getTopK() == null || request.getTopK() <= 0 ? 10 : request.getTopK());
        body.put("outputFields", config.getOutputFields());
        String filter = toFilterExpression(request.getFilter());
        if (filter != null) {
            body.put("filter", filter);
        }

        JSONObject response = executePost(config.getSearch(), body);
        JSONArray results = response == null ? null : response.getJSONArray("data");
        if (results == null || results.isEmpty()) {
            return Collections.emptyList();
        }
        List<VectorSearchResult> vectorResults = new ArrayList<VectorSearchResult>();
        for (int i = 0; i < results.size(); i++) {
            JSONObject item = results.getJSONObject(i);
            if (item == null) {
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
    public boolean delete(VectorDeleteRequest request) throws Exception {
        String dataset = requiredDataset(request == null ? null : request.getDataset());
        if (request == null) {
            return false;
        }
        JSONObject body = new JSONObject();
        applyCollectionScope(body, dataset);
        if (request.getIds() != null && !request.getIds().isEmpty()) {
            body.put("ids", request.getIds());
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
                .deleteByFilter(true)
                .returnStoredVector(false)
                .build();
    }

    private void applyCollectionScope(JSONObject body, String dataset) {
        body.put("collectionName", dataset);
        if (trimToNull(config.getPartitionName()) != null) {
            body.put("partitionName", config.getPartitionName().trim());
        }
        if (trimToNull(config.getDbName()) != null) {
            body.put("dbName", config.getDbName().trim());
        }
    }

    private JSONObject executePost(String path, JSONObject payload) throws Exception {
        Request.Builder builder = new Request.Builder()
                .url(UrlUtils.concatUrl(config.getHost(), path))
                .post(RequestBody.create(JSON.toJSONString(payload), JSON_MEDIA_TYPE))
                .header("accept", Constants.APPLICATION_JSON)
                .header("content-type", Constants.APPLICATION_JSON);
        if (trimToNull(config.getToken()) != null) {
            builder.header("Authorization", "Bearer " + config.getToken().trim());
        }

        try (Response response = okHttpClient.newCall(builder.build()).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Milvus request failed: " + response.message());
            }
            String body = response.body() == null ? "{}" : response.body().string();
            return JSON.parseObject(body);
        }
    }

    private Map<String, Object> metadataFromResult(JSONObject item) {
        Map<String, Object> metadata = new LinkedHashMap<String, Object>();
        JSONObject entity = item.getJSONObject("entity");
        if (entity != null) {
            for (String key : entity.keySet()) {
                metadata.put(key, entity.get(key));
            }
        }
        for (String key : item.keySet()) {
            if ("distance".equals(key) || "score".equals(key) || "id".equals(key) || "entity".equals(key)) {
                continue;
            }
            metadata.put(key, item.get(key));
        }
        if (!metadata.containsKey(config.getIdField()) && item.containsKey("id")) {
            metadata.put(config.getIdField(), item.get("id"));
        }
        return metadata;
    }

    private Float scoreValue(JSONObject item) {
        if (item.containsKey("score")) {
            return item.getFloat("score");
        }
        if (item.containsKey("distance")) {
            Float distance = item.getFloat("distance");
            return distance == null ? null : 1.0f - distance;
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
