package io.github.lnyocly.ai4j.vector.store.elasticsearch;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
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
            JSONObject action = new JSONObject();
            JSONObject meta = new JSONObject();
            meta.put("_id", resolveId(record.getId(), index));
            action.put("index", meta);
            ndjson.append(action.toJSONString()).append('\n');
            ndjson.append(document(dataset, record).toJSONString()).append('\n');
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
        JSONObject knn = new JSONObject();
        knn.put("field", "vector");
        knn.put("query_vector", request.getVector());
        knn.put("k", topK);
        knn.put("num_candidates", Math.max(topK * 4, NUM_CANDIDATES));
        JSONArray filters = filters(dataset, request.getFilter());
        if (!filters.isEmpty()) {
            knn.put("filter", filters);
        }

        JSONObject body = new JSONObject();
        body.put("size", topK);
        body.put("knn", knn);
        JSONArray source = new JSONArray();
        source.add("content");
        source.add("metadata");
        if (Boolean.TRUE.equals(request.getIncludeVector())) {
            source.add("vector");
        }
        body.put("_source", source);

        JSONObject response = executePost(url("/_search"), body);
        JSONObject hits = response == null ? null : response.getJSONObject("hits");
        JSONArray hitList = hits == null ? null : hits.getJSONArray("hits");
        if (hitList == null || hitList.isEmpty()) {
            return Collections.emptyList();
        }

        List<VectorSearchResult> results = new ArrayList<VectorSearchResult>();
        for (int i = 0; i < hitList.size(); i++) {
            JSONObject hit = hitList.getJSONObject(i);
            if (hit == null) {
                continue;
            }
            JSONObject src = hit.getJSONObject("_source");
            Map<String, Object> metadata = src == null ? null : src.getJSONObject("metadata");
            results.add(VectorSearchResult.builder()
                    .id(hit.getString("_id"))
                    .score(hit.getFloat("_score"))
                    .content(src == null ? null : src.getString("content"))
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

        JSONArray filter = filters(dataset, request.getFilter());
        if (hasIds) {
            JSONObject ids = new JSONObject();
            ids.put("_id", new JSONArray(request.getIds()));
            JSONObject terms = new JSONObject();
            terms.put("terms", ids);
            filter.add(terms);
        }

        JSONObject bool = new JSONObject();
        bool.put("filter", filter);
        JSONObject boolQuery = new JSONObject();
        boolQuery.put("bool", bool);
        JSONObject body = new JSONObject();
        body.put("query", boolQuery);
        execute(url("/_delete_by_query?refresh=true&conflicts=proceed"), body.toJSONString(), JSON_MEDIA_TYPE);
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

        JSONArray filter = filters(dataset, request.getFilter());
        if (hasIds) {
            JSONObject ids = new JSONObject();
            ids.put("_id", new JSONArray(request.getIds()));
            JSONObject terms = new JSONObject();
            terms.put("terms", ids);
            filter.add(terms);
        }

        JSONObject bool = new JSONObject();
        bool.put("filter", filter);
        JSONObject boolQuery = new JSONObject();
        boolQuery.put("bool", bool);
        JSONObject body = new JSONObject();
        body.put("size", 0);
        body.put("terminate_after", 1);
        body.put("track_total_hits", Boolean.TRUE);
        body.put("query", boolQuery);

        JSONObject response = executePost(url("/_search"), body);
        JSONObject hits = response == null ? null : response.getJSONObject("hits");
        JSONObject total = hits == null ? null : hits.getJSONObject("total");
        return total != null && total.getLongValue("value") > 0;
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

        JSONObject vector = new JSONObject();
        vector.put("type", "dense_vector");
        vector.put("dims", config.getVectorDim());
        vector.put("index", Boolean.TRUE);
        vector.put("similarity", "cosine");

        JSONObject content = new JSONObject();
        content.put("type", "text");
        content.put("index", Boolean.FALSE);

        JSONObject dataset = new JSONObject();
        dataset.put("type", "keyword");

        JSONObject metadata = new JSONObject();
        metadata.put("type", "flattened");

        JSONObject properties = new JSONObject();
        properties.put("vector", vector);
        properties.put("content", content);
        properties.put("dataset", dataset);
        properties.put("metadata", metadata);

        JSONObject mappings = new JSONObject();
        mappings.put("properties", properties);
        JSONObject body = new JSONObject();
        body.put("mappings", mappings);

        Request.Builder put = new Request.Builder()
                .url(indexUrl())
                .put(RequestBody.create(body.toJSONString(), JSON_MEDIA_TYPE));
        applyAuth(put);
        try (Response response = okHttpClient.newCall(put.build()).execute()) {
            if (!response.isSuccessful() && response.code() != 400) {
                throw new IOException("Elasticsearch index creation failed: " + response.message());
            }
        }
        indexEnsured = true;
    }

    private JSONObject document(String dataset, VectorRecord record) {
        JSONObject doc = new JSONObject();
        doc.put("vector", record.getVector());
        doc.put("dataset", dataset);
        doc.put("content", record.getContent());
        if (record.getMetadata() != null && !record.getMetadata().isEmpty()) {
            doc.put("metadata", record.getMetadata());
        }
        return doc;
    }

    private JSONArray filters(String dataset, Map<String, Object> filter) {
        JSONArray clauses = new JSONArray();
        JSONObject datasetTerm = new JSONObject();
        JSONObject term = new JSONObject();
        term.put("dataset", dataset);
        datasetTerm.put("term", term);
        clauses.add(datasetTerm);
        if (filter != null) {
            for (Map.Entry<String, Object> entry : filter.entrySet()) {
                if (entry == null || trimToNull(entry.getKey()) == null || entry.getValue() == null) {
                    continue;
                }
                String field = "metadata." + entry.getKey().trim();
                Object value = entry.getValue();
                JSONObject clause = new JSONObject();
                JSONObject body = new JSONObject();
                if (value instanceof Collection) {
                    JSONArray values = new JSONArray();
                    for (Object item : (Collection<?>) value) {
                        if (item != null) {
                            values.add(item);
                        }
                    }
                    if (values.isEmpty()) {
                        continue;
                    }
                    body.put(field, values);
                    clause.put("terms", body);
                } else {
                    body.put(field, value);
                    clause.put("term", body);
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

    private JSONObject executePost(String url, JSONObject body) throws Exception {
        String text = execute(url, body == null ? "{}" : body.toJSONString(), JSON_MEDIA_TYPE);
        return text == null || text.isEmpty() ? new JSONObject() : JSON.parseObject(text);
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

    private List<Float> vectorValue(Object rawVector) {
        if (!(rawVector instanceof JSONArray)) {
            return null;
        }
        JSONArray values = (JSONArray) rawVector;
        List<Float> vector = new ArrayList<Float>(values.size());
        for (int i = 0; i < values.size(); i++) {
            vector.add(values.getFloat(i));
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
