package io.github.lnyocly.ai4j.vector.store.pinecone;

import io.github.lnyocly.ai4j.constant.Constants;
import io.github.lnyocly.ai4j.vector.pinecone.PineconeDelete;
import io.github.lnyocly.ai4j.vector.pinecone.PineconeInsert;
import io.github.lnyocly.ai4j.vector.pinecone.PineconeQuery;
import io.github.lnyocly.ai4j.vector.pinecone.PineconeQueryResponse;
import io.github.lnyocly.ai4j.vector.pinecone.PineconeVectors;
import io.github.lnyocly.ai4j.vector.service.PineconeService;
import io.github.lnyocly.ai4j.vector.store.VectorDeleteRequest;
import io.github.lnyocly.ai4j.vector.store.VectorRecord;
import io.github.lnyocly.ai4j.vector.store.VectorSearchRequest;
import io.github.lnyocly.ai4j.vector.store.VectorSearchResult;
import io.github.lnyocly.ai4j.vector.store.VectorStore;
import io.github.lnyocly.ai4j.vector.store.VectorStoreCapabilities;
import io.github.lnyocly.ai4j.vector.store.VectorUpsertRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PineconeVectorStore implements VectorStore {

    private final PineconeService pineconeService;

    public PineconeVectorStore(PineconeService pineconeService) {
        if (pineconeService == null) {
            throw new IllegalArgumentException("pineconeService is required");
        }
        this.pineconeService = pineconeService;
    }

    @Override
    public int upsert(VectorUpsertRequest request) {
        String dataset = requiredDataset(request == null ? null : request.getDataset());
        List<VectorRecord> records = request == null || request.getRecords() == null
                ? Collections.<VectorRecord>emptyList()
                : request.getRecords();
        if (records.isEmpty()) {
            return 0;
        }

        List<PineconeVectors> vectors = new ArrayList<PineconeVectors>();
        int index = 0;
        for (VectorRecord record : records) {
            if (record == null || record.getVector() == null || record.getVector().isEmpty()) {
                index++;
                continue;
            }
            Map<String, String> metadata = stringifyMetadata(record.getMetadata());
            String content = trimToNull(record.getContent());
            if (content != null && !metadata.containsKey(Constants.METADATA_KEY)) {
                metadata.put(Constants.METADATA_KEY, content);
            }
            vectors.add(PineconeVectors.builder()
                    .id(resolveId(record.getId(), index))
                    .values(record.getVector())
                    .metadata(metadata)
                    .build());
            index++;
        }
        if (vectors.isEmpty()) {
            return 0;
        }
        return pineconeService.insert(new PineconeInsert(vectors, dataset));
    }

    @Override
    public List<VectorSearchResult> search(VectorSearchRequest request) {
        String dataset = requiredDataset(request == null ? null : request.getDataset());
        if (request == null || request.getVector() == null || request.getVector().isEmpty()) {
            return Collections.emptyList();
        }
        PineconeQueryResponse response = pineconeService.query(PineconeQuery.builder()
                .namespace(dataset)
                .topK(request.getTopK() == null || request.getTopK() <= 0 ? 10 : request.getTopK())
                .filter(stringifyMetadata(request.getFilter()))
                .includeMetadata(request.getIncludeMetadata() == null ? Boolean.TRUE : request.getIncludeMetadata())
                .includeValues(request.getIncludeVector() == null ? Boolean.FALSE : request.getIncludeVector())
                .vector(request.getVector())
                .build());
        if (response == null || response.getMatches() == null || response.getMatches().isEmpty()) {
            return Collections.emptyList();
        }
        List<VectorSearchResult> results = new ArrayList<VectorSearchResult>();
        for (PineconeQueryResponse.Match match : response.getMatches()) {
            if (match == null) {
                continue;
            }
            Map<String, Object> metadata = objectMetadata(match.getMetadata());
            String content = metadata == null ? null : stringValue(metadata.get(Constants.METADATA_KEY));
            results.add(VectorSearchResult.builder()
                    .id(match.getId())
                    .score(match.getScore())
                    .content(content)
                    .vector(match.getValues())
                    .metadata(metadata)
                    .build());
        }
        return results;
    }

    @Override
    public boolean delete(VectorDeleteRequest request) {
        String dataset = requiredDataset(request == null ? null : request.getDataset());
        if (request == null) {
            return false;
        }
        return pineconeService.delete(PineconeDelete.builder()
                .ids(request.getIds())
                .deleteAll(request.isDeleteAll())
                .namespace(dataset)
                .filter(stringifyMetadata(request.getFilter()))
                .build());
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

    private Map<String, String> stringifyMetadata(Map<String, ?> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        Map<String, String> result = new LinkedHashMap<String, String>();
        for (Map.Entry<String, ?> entry : metadata.entrySet()) {
            if (entry == null || entry.getKey() == null) {
                continue;
            }
            String value = stringValue(entry.getValue());
            if (value != null) {
                result.put(entry.getKey(), value);
            }
        }
        return result.isEmpty() ? null : result;
    }

    private Map<String, Object> objectMetadata(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            if (entry != null && entry.getKey() != null) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
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
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }
}
