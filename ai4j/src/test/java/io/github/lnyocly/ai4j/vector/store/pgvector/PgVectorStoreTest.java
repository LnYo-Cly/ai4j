package io.github.lnyocly.ai4j.vector.store.pgvector;

import io.github.lnyocly.ai4j.config.PgVectorConfig;
import io.github.lnyocly.ai4j.test.LiveProviderTest;
import io.github.lnyocly.ai4j.vector.store.VectorDeleteRequest;
import io.github.lnyocly.ai4j.vector.store.VectorRecord;
import io.github.lnyocly.ai4j.vector.store.VectorSearchRequest;
import io.github.lnyocly.ai4j.vector.store.VectorSearchResult;
import io.github.lnyocly.ai4j.vector.store.VectorUpsertRequest;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Live integration test against a real PostgreSQL + pgvector instance. Excluded from
 * the default suite via {@link LiveProviderTest}; enable with {@code -Plive-provider-tests}
 * and override PGVECTOR_JDBC / PGVECTOR_USER / PGVECTOR_PASSWORD env if needed.
 * <p>
 * PgVectorStore assumes the target table already exists (it does not auto-create it),
 * so this test creates a per-run table with a {@code vector(3)} column and drops it after.
 */
@Category(LiveProviderTest.class)
public class PgVectorStoreTest {

    @Test
    public void shouldUpsertSearchAndDeleteAgainstRealPgVector() throws Exception {
        String jdbc = env("PGVECTOR_JDBC", "jdbc:postgresql://localhost:5432/postgres");
        String user = env("PGVECTOR_USER", "postgres");
        String pass = env("PGVECTOR_PASSWORD", "postgres");
        final String table = "ai4j_test_vec_" + System.nanoTime();

        // setup: PgVectorStore expects the table + vector column to pre-exist
        try (Connection c = DriverManager.getConnection(jdbc, user, pass);
             Statement s = c.createStatement()) {
            s.execute("CREATE TABLE " + table
                    + " (id text PRIMARY KEY, dataset text, content text, metadata jsonb, embedding vector(3))");
        }

        try {
            PgVectorConfig config = new PgVectorConfig();
            config.setJdbcUrl(jdbc);
            config.setUsername(user);
            config.setPassword(pass);
            config.setTableName(table);
            config.setDistanceOperator("<=>");

            PgVectorStore store = new PgVectorStore(config);

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

            // nearest to (1,0,0): a and c should rank above b
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

            // delete by filter, then verify category=x no longer surfaces
            store.delete(VectorDeleteRequest.builder()
                    .dataset("demo").filter(mapOf("category", "x")).build());
            List<VectorSearchResult> after = store.search(VectorSearchRequest.builder()
                    .dataset("demo").vector(vec(1f, 0f, 0f)).topK(10)
                    .filter(mapOf("category", "x")).build());
            Assert.assertTrue("category=x should be gone after delete", after.isEmpty());
        } finally {
            try (Connection c = DriverManager.getConnection(jdbc, user, pass);
                 Statement s = c.createStatement()) {
                s.execute("DROP TABLE IF EXISTS " + table);
            }
        }
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
