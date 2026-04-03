package io.github.lnyocly.ai4j.vector.store.qdrant;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.config.QdrantConfig;
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

public class QdrantVectorStore implements VectorStore {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.get(Constants.APPLICATION_JSON);

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

        JSONArray points = new JSONArray();
        int index = 0;
        for (VectorRecord record : records) {
            if (record == null || record.getVector() == null || record.getVector().isEmpty()) {
                index++;
                continue;
            }
            JSONObject point = new JSONObject();
            point.put("id", resolveId(record.getId(), index));
            point.put("vector", namedVector(record.getVector()));
            point.put("payload", payload(record));
            points.add(point);
            index++;
        }
        if (points.isEmpty()) {
            return 0;
        }

        JSONObject body = new JSONObject();
        body.put("points", points);
        executePost(url(config.getUpsert(), dataset), body);
        return points.size();
    }

    @Override
    public List<VectorSearchResult> search(VectorSearchRequest request) throws Exception {
        String dataset = requiredDataset(request == null ? null : request.getDataset());
        if (request == null || request.getVector() == null || request.getVector().isEmpty()) {
            return Collections.emptyList();
        }

        JSONObject body = new JSONObject();
        body.put("query", request.getVector());
        body.put("limit", request.getTopK() == null || request.getTopK() <= 0 ? 10 : request.getTopK());
        body.put("with_payload", request.getIncludeMetadata() == null ? Boolean.TRUE : request.getIncludeMetadata());
        body.put("with_vector", request.getIncludeVector() == null ? Boolean.FALSE : request.getIncludeVector());
        if (trimToNull(config.getVectorName()) != null) {
            body.put("using", trimToNull(config.getVectorName()));
        }
        JSONObject filter = toFilter(request.getFilter());
        if (filter != null) {
            body.put("filter", filter);
        }

        JSONObject response = executePost(url(config.getQuery(), dataset), body);
        JSONObject result = response == null ? null : response.getJSONObject("result");
        JSONArray points = result == null ? null : result.getJSONArray("points");
        if (points == null || points.isEmpty()) {
            return Collections.emptyList();
        }

        List<VectorSearchResult> results = new ArrayList<VectorSearchResult>();
        for (int i = 0; i < points.size(); i++) {
            JSONObject point = points.getJSONObject(i);
            if (point == null) {
                continue;
            }
            Map<String, Object> metadata = payloadMap(point.getJSONObject("payload"));
            results.add(VectorSearchResult.builder()
                    .id(stringValue(point.get("id")))
                    .score(point.getFloat("score"))
                    .content(stringValue(metadata.get(Constants.METADATA_KEY)))
                    .vector(request.getIncludeVector() != null && request.getIncludeVector() ? vectorValue(point.get("vector")) : null)
                    .metadata(metadata)
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

        JSONObject body = new JSONObject();
        if (request.getIds() != null && !request.getIds().isEmpty()) {
            body.put("points", request.getIds());
        } else {
            JSONObject filter = toFilter(request.getFilter());
            if (filter == null && request.isDeleteAll()) {
                filter = new JSONObject();
                filter.put("must", new JSONArray());
            }
            if (filter != null) {
                body.put("filter", filter);
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
                .deleteByFilter(true)
                .returnStoredVector(true)
                .build();
    }

    private JSONObject payload(VectorRecord record) {
        JSONObject payload = new JSONObject();
        if (record.getMetadata() != null) {
            for (Map.Entry<String, Object> entry : record.getMetadata().entrySet()) {
                if (entry != null && entry.getKey() != null && entry.getValue() != null) {
                    payload.put(entry.getKey(), entry.getValue());
                }
            }
        }
        String content = trimToNull(record.getContent());
        if (content != null && !payload.containsKey(Constants.METADATA_KEY)) {
            payload.put(Constants.METADATA_KEY, content);
        }
        return payload;
    }

    private Object namedVector(List<Float> vector) {
        if (trimToNull(config.getVectorName()) == null) {
            return vector;
        }
        JSONObject named = new JSONObject();
        named.put(config.getVectorName().trim(), vector);
        return named;
    }

    private JSONObject toFilter(Map<String, Object> filter) {
        if (filter == null || filter.isEmpty()) {
            return null;
        }
        JSONArray must = new JSONArray();
        for (Map.Entry<String, Object> entry : filter.entrySet()) {
            if (entry == null || trimToNull(entry.getKey()) == null || entry.getValue() == null) {
                continue;
            }
            JSONObject condition = new JSONObject();
            condition.put("key", entry.getKey());
            JSONObject match = new JSONObject();
            Object value = entry.getValue();
            if (value instanceof Collection) {
                JSONArray any = new JSONArray();
                for (Object item : (Collection<?>) value) {
                    any.add(item);
                }
                match.put("any", any);
            } else {
                match.put("value", value);
            }
            condition.put("match", match);
            must.add(condition);
        }
        if (must.isEmpty()) {
            return null;
        }
        JSONObject result = new JSONObject();
        result.put("must", must);
        return result;
    }

    private JSONObject executePost(String url, JSONObject payload) throws Exception {
        Request.Builder builder = new Request.Builder()
                .url(url)
                .post(RequestBody.create(JSON.toJSONString(payload), JSON_MEDIA_TYPE))
                .header("accept", Constants.APPLICATION_JSON)
                .header("content-type", Constants.APPLICATION_JSON);
        if (trimToNull(config.getApiKey()) != null) {
            builder.header("api-key", config.getApiKey().trim());
        }

        try (Response response = okHttpClient.newCall(builder.build()).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Qdrant request failed: " + response.message());
            }
            String body = response.body() == null ? "{}" : response.body().string();
            return JSON.parseObject(body);
        }
    }

    private List<Float> vectorValue(Object rawVector) {
        if (rawVector == null) {
            return null;
        }
        JSONArray values = null;
        if (rawVector instanceof JSONArray) {
            values = (JSONArray) rawVector;
        } else if (rawVector instanceof JSONObject && trimToNull(config.getVectorName()) != null) {
            values = ((JSONObject) rawVector).getJSONArray(config.getVectorName().trim());
        }
        if (values == null) {
            return null;
        }
        List<Float> vector = new ArrayList<Float>();
        for (int i = 0; i < values.size(); i++) {
            vector.add(values.getFloat(i));
        }
        return vector;
    }

    private Map<String, Object> payloadMap(JSONObject payload) {
        if (payload == null || payload.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> metadata = new LinkedHashMap<String, Object>();
        for (String key : payload.keySet()) {
            metadata.put(key, payload.get(key));
        }
        return metadata;
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
