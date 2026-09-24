package io.github.lnyocly.ai4j.vector.store.memory;

import io.github.lnyocly.ai4j.vector.store.VectorDeleteRequest;
import io.github.lnyocly.ai4j.vector.store.VectorExistsRequest;
import io.github.lnyocly.ai4j.vector.store.VectorSearchRequest;
import io.github.lnyocly.ai4j.vector.store.VectorSearchResult;
import io.github.lnyocly.ai4j.vector.store.VectorStore;
import io.github.lnyocly.ai4j.vector.store.VectorStoreCapabilities;
import io.github.lnyocly.ai4j.vector.store.VectorUpsertRequest;
import io.github.lnyocly.ai4j.vector.store.VectorRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Dependency-free in-process {@link VectorStore} backed by JVM memory.
 *
 * <p>Intended for demos, unit tests, smoke tests, and small embedded
 * applications. Records live in the process heap and are lost on restart;
 * for production deployments use a durable backend such as Qdrant, Milvus,
 * pgvector, Pinecone, or Redis.</p>
 *
 * <p>Search uses exact cosine similarity over the stored vectors; records
 * without a vector (or with a dimension that does not match the query) are
 * skipped. {@code filter} maps are matched as equality on record metadata.
 * All declared capabilities are supported, including metadata lookup and
 * delete-by-filter, so {@code skipExistingContentHash} ingestion works.</p>
 */
public class InMemoryVectorStore implements VectorStore {

    private final Map<String, List<VectorRecord>> datasets = new ConcurrentHashMap<String, List<VectorRecord>>();

    @Override
    public int upsert(VectorUpsertRequest request) throws Exception {
        if (request == null || request.getRecords() == null) {
            return 0;
        }
        List<VectorRecord> records = dataset(request.getDataset());
        int count = 0;
        for (VectorRecord record : request.getRecords()) {
            if (record == null || isBlank(record.getId())) {
                continue;
            }
            for (int i = 0; i < records.size(); i++) {
                VectorRecord existing = records.get(i);
                if (existing != null && record.getId().equals(existing.getId())) {
                    records.set(i, record);
                    record = null;
                    break;
                }
            }
            if (record != null) {
                records.add(record);
            }
            count++;
        }
        return count;
    }

    @Override
    public List<VectorSearchResult> search(VectorSearchRequest request) throws Exception {
        if (request == null || request.getVector() == null || request.getVector().isEmpty()) {
            return Collections.emptyList();
        }
        List<VectorRecord> records = records(request.getDataset());
        if (records.isEmpty()) {
            return Collections.emptyList();
        }
        List<VectorSearchResult> scored = new ArrayList<VectorSearchResult>(records.size());
        for (VectorRecord record : records) {
            if (record == null || record.getVector() == null
                    || record.getVector().size() != request.getVector().size()) {
                continue;
            }
            if (!matches(record, request.getFilter())) {
                continue;
            }
            scored.add(VectorSearchResult.builder()
                    .id(record.getId())
                    .score(cosine(request.getVector(), record.getVector()))
                    .content(record.getContent())
                    .metadata(Boolean.FALSE.equals(request.getIncludeMetadata()) ? null : record.getMetadata())
                    .vector(Boolean.TRUE.equals(request.getIncludeVector()) ? record.getVector() : null)
                    .build());
        }
        scored.sort(new Comparator<VectorSearchResult>() {
            @Override
            public int compare(VectorSearchResult a, VectorSearchResult b) {
                return Float.compare(nullScore(b), nullScore(a));
            }
        });
        Integer topK = request.getTopK();
        if (topK != null && topK > 0 && scored.size() > topK) {
            return new ArrayList<VectorSearchResult>(scored.subList(0, topK));
        }
        return scored;
    }

    @Override
    public boolean delete(VectorDeleteRequest request) throws Exception {
        if (request == null) {
            return false;
        }
        List<VectorRecord> records = dataset(request.getDataset());
        if (request.isDeleteAll()) {
            boolean had = !records.isEmpty();
            records.clear();
            return had;
        }
        Set<String> ids = request.getIds() == null
                ? Collections.<String>emptySet()
                : new HashSet<String>(request.getIds());
        boolean hasFilter = request.getFilter() != null && !request.getFilter().isEmpty();
        boolean removed = false;
        for (int i = records.size() - 1; i >= 0; i--) {
            VectorRecord record = records.get(i);
            if (record == null) {
                continue;
            }
            if (ids.contains(record.getId()) || (hasFilter && matches(record, request.getFilter()))) {
                records.remove(i);
                removed = true;
            }
        }
        return removed;
    }

    @Override
    public boolean exists(VectorExistsRequest request) throws Exception {
        if (request == null) {
            return false;
        }
        List<VectorRecord> records = records(request.getDataset());
        if (records.isEmpty()) {
            return false;
        }
        Set<String> ids = request.getIds() == null
                ? Collections.<String>emptySet()
                : new HashSet<String>(request.getIds());
        boolean hasIds = !ids.isEmpty();
        boolean hasFilter = request.getFilter() != null && !request.getFilter().isEmpty();
        if (!hasIds && !hasFilter) {
            return false;
        }
        for (VectorRecord record : records) {
            if (record == null) {
                continue;
            }
            if (ids.contains(record.getId()) || (hasFilter && matches(record, request.getFilter()))) {
                return true;
            }
        }
        return false;
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

    private List<VectorRecord> dataset(String dataset) {
        String key = isBlank(dataset) ? "" : dataset;
        List<VectorRecord> records = datasets.get(key);
        if (records == null) {
            records = new CopyOnWriteArrayList<VectorRecord>();
            List<VectorRecord> previous = datasets.putIfAbsent(key, records);
            if (previous != null) {
                records = previous;
            }
        }
        return records;
    }

    private List<VectorRecord> records(String dataset) {
        List<VectorRecord> records = datasets.get(isBlank(dataset) ? "" : dataset);
        return records == null ? Collections.<VectorRecord>emptyList() : records;
    }

    private boolean matches(VectorRecord record, Map<String, Object> filter) {
        if (filter == null || filter.isEmpty()) {
            return true;
        }
        Map<String, Object> metadata = record.getMetadata();
        for (Map.Entry<String, Object> entry : filter.entrySet()) {
            Object expected = entry.getValue();
            Object actual = metadata == null ? null : metadata.get(entry.getKey());
            if (expected == null ? actual != null : !expected.equals(actual)) {
                return false;
            }
        }
        return true;
    }

    private float cosine(List<Float> a, List<Float> b) {
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.size(); i++) {
            double x = a.get(i) == null ? 0.0 : a.get(i);
            double y = b.get(i) == null ? 0.0 : b.get(i);
            dot += x * y;
            normA += x * x;
            normB += y * y;
        }
        if (normA == 0.0 || normB == 0.0) {
            return 0.0f;
        }
        return (float) (dot / (Math.sqrt(normA) * Math.sqrt(normB)));
    }

    private float nullScore(VectorSearchResult result) {
        return result.getScore() == null ? Float.NEGATIVE_INFINITY : result.getScore();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
