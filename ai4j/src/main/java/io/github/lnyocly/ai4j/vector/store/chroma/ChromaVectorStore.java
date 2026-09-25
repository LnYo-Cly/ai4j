package io.github.lnyocly.ai4j.vector.store.chroma;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.config.ChromaConfig;
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
import java.util.List;
import java.util.Map;

/**
 * {@link VectorStore} backed by a Chroma collection via the Chroma v2 REST API.
 *
 * <p>All records live in one collection ({@link ChromaConfig#getCollection()},
 * created lazily with cosine distance); the {@code dataset} request field is
 * stored as record metadata and always applied as a {@code where} condition,
 * so multiple datasets can share one collection.</p>
 *
 * <p>Query {@code distances} are cosine distances; the returned score is
 * {@code 1 - distance}, i.e. cosine similarity in [-1, 1].</p>
 */
public class ChromaVectorStore implements VectorStore {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.get(Constants.APPLICATION_JSON);
    private static final String DATASET_KEY = "dataset";

    private final ChromaConfig config;
    private final OkHttpClient okHttpClient;
    private volatile String collectionId;

    public ChromaVectorStore(Configuration configuration) {
        this(configuration, configuration == null ? null : configuration.getChromaConfig());
    }

    public ChromaVectorStore(Configuration configuration, ChromaConfig config) {
        if (configuration == null || configuration.getOkHttpClient() == null) {
            throw new IllegalArgumentException("OkHttpClient configuration is required");
        }
        if (config == null) {
            throw new IllegalArgumentException("chromaConfig is required");
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

        JSONArray ids = new JSONArray();
        JSONArray embeddings = new JSONArray();
        JSONArray documents = new JSONArray();
        JSONArray metadatas = new JSONArray();
        int index = 0;
        for (VectorRecord record : records) {
            if (record == null || record.getVector() == null || record.getVector().isEmpty()) {
                index++;
                continue;
            }
            ids.add(resolveId(record.getId(), index));
            embeddings.add(record.getVector());
            documents.add(record.getContent() == null ? "" : record.getContent());
            JSONObject metadata = new JSONObject();
            if (record.getMetadata() != null) {
                for (Map.Entry<String, Object> entry : record.getMetadata().entrySet()) {
                    if (entry != null && entry.getKey() != null && entry.getValue() != null) {
                        metadata.put(entry.getKey(), entry.getValue());
                    }
                }
            }
            metadata.put(DATASET_KEY, dataset);
            metadatas.add(metadata);
            index++;
        }
        if (ids.isEmpty()) {
            return 0;
        }

        JSONObject body = new JSONObject();
        body.put("ids", ids);
        body.put("embeddings", embeddings);
        body.put("documents", documents);
        body.put("metadatas", metadatas);
        executePost(collectionUrl("/upsert"), body);
        return ids.size();
    }

    @Override
    public List<VectorSearchResult> search(VectorSearchRequest request) throws Exception {
        String dataset = requiredDataset(request == null ? null : request.getDataset());
        if (request == null || request.getVector() == null || request.getVector().isEmpty()) {
            return Collections.emptyList();
        }

        JSONObject body = new JSONObject();
        JSONArray queryEmbeddings = new JSONArray();
        queryEmbeddings.add(request.getVector());
        body.put("query_embeddings", queryEmbeddings);
        body.put("n_results", request.getTopK() == null || request.getTopK() <= 0 ? 10 : request.getTopK());
        body.put("where", where(dataset, request.getFilter()));
        JSONArray include = new JSONArray();
        include.add("documents");
        include.add("metadatas");
        include.add("distances");
        if (Boolean.TRUE.equals(request.getIncludeVector())) {
            include.add("embeddings");
        }
        body.put("include", include);

        JSONObject response = executePost(collectionUrl("/query"), body);
        JSONArray ids = response == null ? null : response.getJSONArray("ids");
        if (ids == null || ids.isEmpty() || ids.getJSONArray(0) == null) {
            return Collections.emptyList();
        }
        JSONArray hitIds = ids.getJSONArray(0);
        JSONArray hitDocs = arrayAt(response, "documents");
        JSONArray hitMeta = arrayAt(response, "metadatas");
        JSONArray hitDist = arrayAt(response, "distances");
        JSONArray hitEmb = arrayAt(response, "embeddings");

        List<VectorSearchResult> results = new ArrayList<VectorSearchResult>();
        for (int i = 0; i < hitIds.size(); i++) {
            JSONObject metadata = hitMeta == null ? null : hitMeta.getJSONObject(i);
            if (metadata != null) {
                metadata.remove(DATASET_KEY);
            }
            Float distance = hitDist == null ? null : hitDist.getFloat(i);
            results.add(VectorSearchResult.builder()
                    .id(hitIds.getString(i))
                    .score(distance == null ? null : 1.0f - distance)
                    .content(hitDocs == null ? null : hitDocs.getString(i))
                    .metadata(Boolean.FALSE.equals(request.getIncludeMetadata()) || metadata == null
                            ? null
                            : metadata)
                    .vector(hitEmb == null ? null : vectorValue(hitEmb.getJSONArray(i)))
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

        JSONObject body = new JSONObject();
        if (hasIds) {
            body.put("ids", new JSONArray(request.getIds()));
        }
        body.put("where", where(dataset, request.getFilter()));
        executePost(collectionUrl("/delete"), body);
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

        JSONObject body = new JSONObject();
        if (hasIds) {
            body.put("ids", new JSONArray(request.getIds()));
        }
        body.put("where", where(dataset, request.getFilter()));
        body.put("limit", 1);
        body.put("include", new JSONArray());

        JSONObject response = executePost(collectionUrl("/get"), body);
        JSONArray ids = response == null ? null : response.getJSONArray("ids");
        return ids != null && !ids.isEmpty();
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

    private JSONObject where(String dataset, Map<String, Object> filter) {
        List<JSONObject> conditions = new ArrayList<JSONObject>();
        JSONObject datasetCond = new JSONObject();
        JSONObject eq = new JSONObject();
        eq.put("$eq", dataset);
        datasetCond.put(DATASET_KEY, eq);
        conditions.add(datasetCond);
        if (filter != null) {
            for (Map.Entry<String, Object> entry : filter.entrySet()) {
                if (entry == null || trimToNull(entry.getKey()) == null || entry.getValue() == null) {
                    continue;
                }
                JSONObject condition = new JSONObject();
                JSONObject op = new JSONObject();
                Object value = entry.getValue();
                if (value instanceof Collection) {
                    JSONArray in = new JSONArray();
                    for (Object item : (Collection<?>) value) {
                        if (item != null) {
                            in.add(item);
                        }
                    }
                    if (in.isEmpty()) {
                        continue;
                    }
                    op.put("$in", in);
                } else {
                    op.put("$eq", value);
                }
                condition.put(entry.getKey().trim(), op);
                conditions.add(condition);
            }
        }
        if (conditions.size() == 1) {
            return conditions.get(0);
        }
        JSONObject where = new JSONObject();
        where.put("$and", new JSONArray(conditions));
        return where;
    }

    private String collectionId() throws Exception {
        if (collectionId != null) {
            return collectionId;
        }
        JSONObject body = new JSONObject();
        body.put("name", config.getCollection());
        body.put("get_or_create", Boolean.TRUE);
        JSONObject metadata = new JSONObject();
        metadata.put("hnsw:space", "cosine");
        body.put("metadata", metadata);
        JSONObject response = executePost(baseUrl() + "/collections", body);
        String id = response == null ? null : response.getString("id");
        if (id == null || id.isEmpty()) {
            throw new IOException("Chroma collection lookup returned no id");
        }
        collectionId = id;
        return id;
    }

    private String baseUrl() {
        return UrlUtils.concatUrl(config.getHost(), "api/v2/tenants",
                config.getTenant(), "databases", config.getDatabase());
    }

    private String collectionUrl(String suffix) throws Exception {
        return UrlUtils.concatUrl(baseUrl(), "collections", collectionId() + suffix);
    }

    private JSONObject executePost(String url, JSONObject body) throws Exception {
        Request.Builder builder = new Request.Builder()
                .url(url)
                .post(RequestBody.create(body == null ? "{}" : body.toJSONString(), JSON_MEDIA_TYPE))
                .header("accept", Constants.APPLICATION_JSON)
                .header("content-type", Constants.APPLICATION_JSON);
        if (trimToNull(config.getToken()) != null) {
            builder.header("X-Chroma-Token", config.getToken().trim());
        }

        try (Response response = okHttpClient.newCall(builder.build()).execute()) {
            ResponseBody responseBody = response.body();
            String text = responseBody == null ? "" : responseBody.string();
            if (!response.isSuccessful()) {
                throw new IOException("Chroma request failed: " + response.message()
                        + " " + abbreviate(text));
            }
            return text.isEmpty() ? new JSONObject() : JSON.parseObject(text);
        }
    }

    private JSONArray arrayAt(JSONObject object, String key) {
        JSONArray outer = object == null ? null : object.getJSONArray(key);
        return outer == null || outer.isEmpty() ? null : outer.getJSONArray(0);
    }

    private List<Float> vectorValue(JSONArray values) {
        if (values == null) {
            return null;
        }
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
