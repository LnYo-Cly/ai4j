package io.github.lnyocly.ai4j.vector.store.redis;

import io.github.lnyocly.ai4j.config.RedisVectorConfig;
import io.github.lnyocly.ai4j.test.LiveProviderTest;
import io.github.lnyocly.ai4j.vector.store.VectorDeleteRequest;
import io.github.lnyocly.ai4j.vector.store.VectorRecord;
import io.github.lnyocly.ai4j.vector.store.VectorSearchRequest;
import io.github.lnyocly.ai4j.vector.store.VectorSearchResult;
import io.github.lnyocly.ai4j.vector.store.VectorUpsertRequest;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Live integration test — requires a running Redis Stack instance with the RediSearch
 * module. Excluded from the default suite via {@link LiveProviderTest}; enable with
 * {@code -Plive-provider-tests} and provide REDIS_HOST/REDIS_PORT/REDIS_PASSWORD env.
 * <p>
 * Mirrors PgVectorStore (no unit test, validated against the real backend): correctness
 * of the wire protocol is Jedis's responsibility, so we validate the end-to-end
 * upsert/search/delete behaviour against real Redis Stack.
 */
@Category(LiveProviderTest.class)
public class RedisVectorStoreTest {

    @Test
    public void shouldUpsertSearchAndDeleteAgainstRedisStack() throws Exception {
        RedisVectorConfig config = new RedisVectorConfig();
        config.setHost(env("REDIS_HOST", "localhost"));
        config.setPort(Integer.parseInt(env("REDIS_PORT", "6379")));
        String password = System.getenv("REDIS_PASSWORD");
        if (password != null && !password.trim().isEmpty()) {
            config.setPassword(password);
        }
        // unique per run so leftover indexes never collide
        config.setKeyPrefix("ai4j:test:" + System.nanoTime() + ":");
        config.setIndexName("ai4j_test_idx_" + System.nanoTime());
        config.setVectorDim(3);
        config.setTagFields(Arrays.asList("dataset", "category"));
        config.setNumericFields(Arrays.asList("version"));

        RedisVectorStore store = new RedisVectorStore(config);

        int inserted = store.upsert(VectorUpsertRequest.builder()
                .dataset("demo")
                .records(Arrays.asList(
                        VectorRecord.builder().id("a").vector(vec(1f, 0f, 0f)).content("alpha")
                                .metadata(mapOf("category", "x", "version", 1)).build(),
                        VectorRecord.builder().id("b").vector(vec(0f, 1f, 0f)).content("beta")
                                .metadata(mapOf("category", "y", "version", 2)).build(),
                        VectorRecord.builder().id("c").vector(vec(0.99f, 0.01f, 0f)).content("gamma")
                                .metadata(mapOf("category", "x", "version", 3)).build()))
                .build());
        Assert.assertEquals(3, inserted);

        // nearest to (1,0,0) -> a and c should rank above b
        List<VectorSearchResult> hits = store.search(VectorSearchRequest.builder()
                .dataset("demo").vector(vec(1f, 0f, 0f)).topK(3).build());
        Assert.assertFalse("expected at least one hit", hits.isEmpty());
        Assert.assertTrue("a or c should beat b",
                hits.get(0).getId().equals("a") || hits.get(0).getId().equals("c"));

        // filtered search: only category=x
        List<VectorSearchResult> filtered = store.search(VectorSearchRequest.builder()
                .dataset("demo").vector(vec(1f, 0f, 0f)).topK(10)
                .filter(mapOf("category", "x")).build());
        Assert.assertFalse("expected filtered hits", filtered.isEmpty());
        for (VectorSearchResult hit : filtered) {
            Assert.assertEquals("x", hit.getMetadata().get("category"));
        }

        // delete by filter, then verify those docs no longer surface
        store.delete(VectorDeleteRequest.builder()
                .dataset("demo").filter(mapOf("category", "x")).build());
        List<VectorSearchResult> after = store.search(VectorSearchRequest.builder()
                .dataset("demo").vector(vec(1f, 0f, 0f)).topK(10)
                .filter(mapOf("category", "x")).build());
        Assert.assertTrue("category=x should be gone after delete", after.isEmpty());
    }

    private static List<Float> vec(float... values) {
        List<Float> list = new ArrayList<Float>();
        for (float value : values) {
            list.add(value);
        }
        return list;
    }

    private static Map<String, Object> mapOf(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isEmpty() ? fallback : value;
    }
}
