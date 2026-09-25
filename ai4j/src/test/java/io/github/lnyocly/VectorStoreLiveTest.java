package io.github.lnyocly;

import io.github.lnyocly.ai4j.config.ChromaConfig;
import io.github.lnyocly.ai4j.config.ElasticsearchConfig;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.test.LiveProviderTest;
import io.github.lnyocly.ai4j.vector.store.VectorDeleteRequest;
import io.github.lnyocly.ai4j.vector.store.VectorExistsRequest;
import io.github.lnyocly.ai4j.vector.store.VectorRecord;
import io.github.lnyocly.ai4j.vector.store.VectorSearchRequest;
import io.github.lnyocly.ai4j.vector.store.VectorSearchResult;
import io.github.lnyocly.ai4j.vector.store.VectorStore;
import io.github.lnyocly.ai4j.vector.store.VectorUpsertRequest;
import io.github.lnyocly.ai4j.vector.store.chroma.ChromaVectorStore;
import io.github.lnyocly.ai4j.vector.store.elasticsearch.ElasticsearchVectorStore;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 真实向量库冒烟测试，覆盖 SDK 与真实服务端的协议往返，默认跳过。
 *
 * <ul>
 *   <li>Chroma：{@code CHROMA_LIVE_TEST=1} 启用，可选 {@code CHROMA_LIVE_BASE_URL}
 *       （默认 http://localhost:8000）。本地起服：{@code chroma run --path <dir>}。</li>
 *   <li>Elasticsearch：{@code ES_LIVE_TEST=1} 启用，可选 {@code ES_LIVE_BASE_URL}
 *       （默认 http://localhost:9200）与 {@code ES_LIVE_API_KEY}。</li>
 * </ul>
 */
@Category(LiveProviderTest.class)
public class VectorStoreLiveTest {

    @Test
    public void chromaUpsertSearchExistsDelete() throws Exception {
        LiveProviderTestSupport.requireEnv("Skip Chroma live test", "CHROMA_LIVE_TEST");
        String host = LiveProviderTestSupport.firstEnv("CHROMA_LIVE_BASE_URL");
        if (LiveProviderTestSupport.isBlank(host)) {
            host = "http://localhost:8000";
        }

        ChromaConfig config = new ChromaConfig();
        config.setHost(host);
        config.setCollection("ai4j_smoke_" + System.currentTimeMillis());
        VectorStore store = new ChromaVectorStore(new Configuration(), config);
        smokeUpsertSearchExistsDelete(store, "chroma");
    }

    @Test
    public void elasticsearchUpsertSearchExistsDelete() throws Exception {
        LiveProviderTestSupport.requireEnv("Skip Elasticsearch live test", "ES_LIVE_TEST");
        String host = LiveProviderTestSupport.firstEnv("ES_LIVE_BASE_URL");
        if (LiveProviderTestSupport.isBlank(host)) {
            host = "http://localhost:9200";
        }

        ElasticsearchConfig config = new ElasticsearchConfig();
        config.setHost(host);
        String apiKey = LiveProviderTestSupport.firstEnv("ES_LIVE_API_KEY");
        if (!LiveProviderTestSupport.isBlank(apiKey)) {
            config.setApiKey(apiKey);
        }
        config.setIndexName("ai4j-smoke-" + System.currentTimeMillis());
        config.setVectorDim(3);
        VectorStore store = new ElasticsearchVectorStore(new Configuration(), config);
        smokeUpsertSearchExistsDelete(store, "elasticsearch");
    }

    private void smokeUpsertSearchExistsDelete(VectorStore store, String tag) throws Exception {
        String datasetA = "smoke_a_" + tag;
        String datasetB = "smoke_b_" + tag;

        VectorUpsertRequest upsert = new VectorUpsertRequest();
        upsert.setDataset(datasetA);
        upsert.setRecords(Arrays.asList(
                record("r1", Arrays.asList(1.0f, 0.0f, 0.0f), "refund policy doc", "tag", "policy"),
                record("r2", Arrays.asList(0.0f, 1.0f, 0.0f), "order tracking doc", "tag", "order"),
                record("r3", Arrays.asList(0.9f, 0.1f, 0.0f), "refund time limit doc", "tag", "policy")));
        Assert.assertEquals(3, store.upsert(upsert));

        VectorUpsertRequest other = new VectorUpsertRequest();
        other.setDataset(datasetB);
        other.setRecords(Collections.singletonList(
                record("b1", Arrays.asList(1.0f, 0.0f, 0.0f), "other dataset doc", "tag", "policy")));
        Assert.assertEquals(1, store.upsert(other));

        VectorSearchRequest search = new VectorSearchRequest();
        search.setDataset(datasetA);
        search.setVector(Arrays.asList(1.0f, 0.0f, 0.0f));
        search.setTopK(3);
        search.setIncludeMetadata(true);
        List<VectorSearchResult> hits = store.search(search);
        Assert.assertEquals(3, hits.size());
        Assert.assertEquals("r1", hits.get(0).getId());
        Assert.assertNotNull(hits.get(0).getScore());
        Assert.assertTrue(hits.get(0).getScore() > 0.9f);
        Assert.assertEquals("policy", hits.get(0).getMetadata().get("tag"));

        search.setFilter(Collections.<String, Object>singletonMap("tag", "order"));
        hits = store.search(search);
        Assert.assertEquals(1, hits.size());
        Assert.assertEquals("r2", hits.get(0).getId());

        search.setDataset(datasetB);
        search.setFilter(null);
        hits = store.search(search);
        Assert.assertEquals(1, hits.size());
        Assert.assertEquals("b1", hits.get(0).getId());

        VectorExistsRequest exists = new VectorExistsRequest();
        exists.setDataset(datasetA);
        exists.setIds(Collections.singletonList("r1"));
        Assert.assertTrue(store.exists(exists));

        VectorDeleteRequest delete = new VectorDeleteRequest();
        delete.setDataset(datasetA);
        delete.setIds(Collections.singletonList("r1"));
        Assert.assertTrue(store.delete(delete));
        Assert.assertFalse(store.exists(exists));

        VectorDeleteRequest deleteAll = new VectorDeleteRequest();
        deleteAll.setDataset(datasetA);
        deleteAll.setDeleteAll(true);
        Assert.assertTrue(store.delete(deleteAll));
        search.setDataset(datasetA);
        Assert.assertTrue(store.search(search).isEmpty());
    }

    private VectorRecord record(String id, List<Float> vector, String content,
                                String metaKey, String metaValue) {
        Map<String, Object> metadata = new HashMap<String, Object>();
        metadata.put(metaKey, metaValue);
        return VectorRecord.builder()
                .id(id)
                .vector(vector)
                .content(content)
                .metadata(metadata)
                .build();
    }
}
