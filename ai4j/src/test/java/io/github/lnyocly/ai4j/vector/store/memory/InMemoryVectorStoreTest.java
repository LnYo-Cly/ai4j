package io.github.lnyocly.ai4j.vector.store.memory;

import io.github.lnyocly.ai4j.vector.store.VectorDeleteRequest;
import io.github.lnyocly.ai4j.vector.store.VectorExistsRequest;
import io.github.lnyocly.ai4j.vector.store.VectorRecord;
import io.github.lnyocly.ai4j.vector.store.VectorSearchRequest;
import io.github.lnyocly.ai4j.vector.store.VectorSearchResult;
import io.github.lnyocly.ai4j.vector.store.VectorUpsertRequest;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InMemoryVectorStoreTest {

    @Test
    public void shouldRankByCosineSimilarityAndRespectTopK() throws Exception {
        InMemoryVectorStore store = new InMemoryVectorStore();
        store.upsert(upsert("kb", Arrays.asList(
                record("a", Arrays.asList(1f, 0f), "alpha", null),
                record("b", Arrays.asList(0f, 1f), "beta", null),
                record("c", Arrays.asList(0.9f, 0.1f), "gamma", null)
        )));

        List<VectorSearchResult> results = store.search(VectorSearchRequest.builder()
                .dataset("kb")
                .vector(Arrays.asList(1f, 0f))
                .topK(2)
                .build());

        Assert.assertEquals(2, results.size());
        Assert.assertEquals("a", results.get(0).getId());
        Assert.assertEquals("c", results.get(1).getId());
        Assert.assertTrue(results.get(0).getScore() > results.get(1).getScore());
        Assert.assertTrue(results.get(1).getScore() > 0.9f);
    }

    @Test
    public void shouldIsolateDatasets() throws Exception {
        InMemoryVectorStore store = new InMemoryVectorStore();
        store.upsert(upsert("kb1", Collections.singletonList(
                record("a", Arrays.asList(1f, 0f), "alpha", null))));
        store.upsert(upsert("kb2", Collections.singletonList(
                record("b", Arrays.asList(1f, 0f), "beta", null))));

        List<VectorSearchResult> results = store.search(VectorSearchRequest.builder()
                .dataset("kb2")
                .vector(Arrays.asList(1f, 0f))
                .topK(10)
                .build());

        Assert.assertEquals(1, results.size());
        Assert.assertEquals("b", results.get(0).getId());
    }

    @Test
    public void shouldApplyMetadataFilterEquality() throws Exception {
        InMemoryVectorStore store = new InMemoryVectorStore();
        Map<String, Object> hrMeta = new HashMap<String, Object>();
        hrMeta.put("tenant", "acme");
        hrMeta.put("dept", "hr");
        Map<String, Object> engMeta = new HashMap<String, Object>();
        engMeta.put("tenant", "acme");
        engMeta.put("dept", "eng");
        store.upsert(upsert("kb", Arrays.asList(
                record("hr-1", Arrays.asList(1f, 0f), "hr doc", hrMeta),
                record("eng-1", Arrays.asList(1f, 0f), "eng doc", engMeta)
        )));

        Map<String, Object> filter = new HashMap<String, Object>();
        filter.put("dept", "hr");
        List<VectorSearchResult> results = store.search(VectorSearchRequest.builder()
                .dataset("kb")
                .vector(Arrays.asList(1f, 0f))
                .filter(filter)
                .topK(10)
                .build());

        Assert.assertEquals(1, results.size());
        Assert.assertEquals("hr-1", results.get(0).getId());
    }

    @Test
    public void shouldReplaceOnDuplicateUpsertAndDeleteByIdsAndFilter() throws Exception {
        InMemoryVectorStore store = new InMemoryVectorStore();
        Map<String, Object> meta = new HashMap<String, Object>();
        meta.put("tag", "old");
        store.upsert(upsert("kb", Collections.singletonList(
                record("a", Arrays.asList(1f, 0f), "v1", meta))));

        Map<String, Object> meta2 = new HashMap<String, Object>();
        meta2.put("tag", "new");
        store.upsert(upsert("kb", Arrays.asList(
                record("a", Arrays.asList(0f, 1f), "v2", meta2),
                record("b", Arrays.asList(1f, 0f), "keep", meta)
        )));

        Assert.assertEquals(2, store.search(VectorSearchRequest.builder()
                .dataset("kb").vector(Arrays.asList(1f, 0f)).topK(10).build()).size());

        // delete by id
        Assert.assertTrue(store.delete(VectorDeleteRequest.builder()
                .dataset("kb").ids(Collections.singletonList("a")).build()));
        Assert.assertFalse(store.exists(VectorExistsRequest.builder()
                .dataset("kb").ids(Collections.singletonList("a")).build()));

        // delete by filter
        Map<String, Object> filter = new HashMap<String, Object>();
        filter.put("tag", "old");
        Assert.assertTrue(store.delete(VectorDeleteRequest.builder()
                .dataset("kb").filter(filter).build()));
        Assert.assertTrue(store.search(VectorSearchRequest.builder()
                .dataset("kb").vector(Arrays.asList(1f, 0f)).topK(10).build()).isEmpty());
    }

    @Test
    public void shouldSupportMetadataLookupExistsForContentHashSkip() throws Exception {
        InMemoryVectorStore store = new InMemoryVectorStore();
        Map<String, Object> meta = new HashMap<String, Object>();
        meta.put("contentHash", "abc123");
        store.upsert(upsert("kb", Collections.singletonList(
                record("a", Arrays.asList(1f, 0f), "doc", meta))));

        Map<String, Object> filter = new HashMap<String, Object>();
        filter.put("contentHash", "abc123");
        Assert.assertTrue(store.exists(VectorExistsRequest.builder()
                .dataset("kb").filter(filter).build()));
        filter.put("contentHash", "missing");
        Assert.assertFalse(store.exists(VectorExistsRequest.builder()
                .dataset("kb").filter(filter).build()));
        Assert.assertTrue(store.capabilities().isMetadataLookup());
        Assert.assertTrue(store.capabilities().isDeleteByFilter());
        Assert.assertTrue(store.capabilities().isReturnStoredVector());
    }

    @Test
    public void shouldReturnVectorWhenRequested() throws Exception {
        InMemoryVectorStore store = new InMemoryVectorStore();
        store.upsert(upsert("kb", Collections.singletonList(
                record("a", Arrays.asList(1f, 0f), "doc", null))));

        List<VectorSearchResult> withVector = store.search(VectorSearchRequest.builder()
                .dataset("kb").vector(Arrays.asList(1f, 0f))
                .includeVector(Boolean.TRUE).topK(1).build());
        Assert.assertEquals(Arrays.asList(1f, 0f), withVector.get(0).getVector());

        List<VectorSearchResult> withoutVector = store.search(VectorSearchRequest.builder()
                .dataset("kb").vector(Arrays.asList(1f, 0f)).topK(1).build());
        Assert.assertNull(withoutVector.get(0).getVector());
    }

    private static VectorUpsertRequest upsert(String dataset, List<VectorRecord> records) {
        return VectorUpsertRequest.builder().dataset(dataset).records(records).build();
    }

    private static VectorRecord record(String id, List<Float> vector, String content, Map<String, Object> metadata) {
        return VectorRecord.builder().id(id).vector(vector).content(content).metadata(metadata).build();
    }
}
