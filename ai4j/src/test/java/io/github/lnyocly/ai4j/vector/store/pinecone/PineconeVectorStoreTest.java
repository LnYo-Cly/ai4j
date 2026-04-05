package io.github.lnyocly.ai4j.vector.store.pinecone;

import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.vector.pinecone.PineconeDelete;
import io.github.lnyocly.ai4j.vector.pinecone.PineconeInsert;
import io.github.lnyocly.ai4j.vector.pinecone.PineconeInsertResponse;
import io.github.lnyocly.ai4j.vector.pinecone.PineconeQuery;
import io.github.lnyocly.ai4j.vector.pinecone.PineconeQueryResponse;
import io.github.lnyocly.ai4j.vector.service.PineconeService;
import io.github.lnyocly.ai4j.vector.store.VectorDeleteRequest;
import io.github.lnyocly.ai4j.vector.store.VectorRecord;
import io.github.lnyocly.ai4j.vector.store.VectorSearchRequest;
import io.github.lnyocly.ai4j.vector.store.VectorSearchResult;
import io.github.lnyocly.ai4j.vector.store.VectorUpsertRequest;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PineconeVectorStoreTest {

    @Test
    public void shouldConvertUpsertRecordsToPineconeRequest() throws Exception {
        CapturingPineconeService pineconeService = new CapturingPineconeService();
        PineconeVectorStore store = new PineconeVectorStore(pineconeService);

        int inserted = store.upsert(VectorUpsertRequest.builder()
                .dataset("tenant_a")
                .records(Collections.singletonList(VectorRecord.builder()
                        .id("doc-1")
                        .vector(Arrays.asList(0.1f, 0.2f))
                        .content("hello rag")
                        .metadata(mapOf("sourceName", "manual.pdf"))
                        .build()))
                .build());

        Assert.assertEquals(1, inserted);
        Assert.assertNotNull(pineconeService.lastInsert);
        Assert.assertEquals("tenant_a", pineconeService.lastInsert.getNamespace());
        Assert.assertEquals("doc-1", pineconeService.lastInsert.getVectors().get(0).getId());
        Assert.assertEquals("hello rag", pineconeService.lastInsert.getVectors().get(0).getMetadata().get("content"));
        Assert.assertEquals("manual.pdf", pineconeService.lastInsert.getVectors().get(0).getMetadata().get("sourceName"));
    }

    @Test
    public void shouldConvertPineconeMatchesToVectorSearchResults() throws Exception {
        CapturingPineconeService pineconeService = new CapturingPineconeService();
        PineconeVectorStore store = new PineconeVectorStore(pineconeService);

        List<VectorSearchResult> results = store.search(VectorSearchRequest.builder()
                .dataset("tenant_b")
                .vector(Arrays.asList(0.3f, 0.4f))
                .topK(3)
                .filter(mapOf("tenant", "acme"))
                .build());

        Assert.assertNotNull(pineconeService.lastQuery);
        Assert.assertEquals("tenant_b", pineconeService.lastQuery.getNamespace());
        Assert.assertEquals(Integer.valueOf(3), pineconeService.lastQuery.getTopK());
        Assert.assertEquals("acme", pineconeService.lastQuery.getFilter().get("tenant"));
        Assert.assertEquals(1, results.size());
        Assert.assertEquals("match-1", results.get(0).getId());
        Assert.assertEquals("retrieved snippet", results.get(0).getContent());
    }

    @Test
    public void shouldConvertDeleteRequestToPineconeDelete() throws Exception {
        CapturingPineconeService pineconeService = new CapturingPineconeService();
        PineconeVectorStore store = new PineconeVectorStore(pineconeService);

        boolean deleted = store.delete(VectorDeleteRequest.builder()
                .dataset("tenant_c")
                .ids(Collections.singletonList("doc-1"))
                .build());

        Assert.assertTrue(deleted);
        Assert.assertNotNull(pineconeService.lastDelete);
        Assert.assertEquals("tenant_c", pineconeService.lastDelete.getNamespace());
        Assert.assertEquals("doc-1", pineconeService.lastDelete.getIds().get(0));
    }

    private static class CapturingPineconeService extends PineconeService {
        private PineconeInsert lastInsert;
        private PineconeQuery lastQuery;
        private PineconeDelete lastDelete;

        private CapturingPineconeService() {
            super(new Configuration());
        }

        @Override
        public Integer insert(PineconeInsert pineconeInsertReq) {
            this.lastInsert = pineconeInsertReq;
            return 1;
        }

        @Override
        public PineconeQueryResponse query(PineconeQuery pineconeQueryReq) {
            this.lastQuery = pineconeQueryReq;
            PineconeQueryResponse.Match match = new PineconeQueryResponse.Match();
            match.setId("match-1");
            match.setScore(0.92f);
            match.setMetadata(new LinkedHashMap<String, String>());
            match.getMetadata().put("content", "retrieved snippet");
            match.getMetadata().put("sourceName", "manual.pdf");

            PineconeQueryResponse response = new PineconeQueryResponse();
            response.setMatches(Collections.singletonList(match));
            return response;
        }

        @Override
        public Boolean delete(PineconeDelete pineconeDeleteReq) {
            this.lastDelete = pineconeDeleteReq;
            return true;
        }
    }

    private static Map<String, Object> mapOf(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return map;
    }
}
