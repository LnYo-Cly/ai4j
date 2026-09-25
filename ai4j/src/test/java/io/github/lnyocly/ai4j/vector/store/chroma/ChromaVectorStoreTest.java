package io.github.lnyocly.ai4j.vector.store.chroma;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.github.lnyocly.ai4j.config.ChromaConfig;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.vector.store.VectorDeleteRequest;
import io.github.lnyocly.ai4j.vector.store.VectorExistsRequest;
import io.github.lnyocly.ai4j.vector.store.VectorRecord;
import io.github.lnyocly.ai4j.vector.store.VectorSearchRequest;
import io.github.lnyocly.ai4j.vector.store.VectorSearchResult;
import io.github.lnyocly.ai4j.vector.store.VectorUpsertRequest;
import okhttp3.OkHttpClient;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class ChromaVectorStoreTest {

    @Test
    public void shouldResolveCollectionUpsertSearchAndDelete() throws Exception {
        AtomicReference<String> createBody = new AtomicReference<String>();
        AtomicReference<String> upsertBody = new AtomicReference<String>();
        AtomicReference<String> queryBody = new AtomicReference<String>();
        AtomicReference<String> deleteBody = new AtomicReference<String>();
        AtomicReference<String> getBody = new AtomicReference<String>();

        String base = "/api/v2/tenants/default_tenant/databases/default_database";
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(base + "/collections", jsonHandler(
                "{\"id\":\"col-uuid-1\",\"name\":\"ai4j_vectors\"}", createBody));
        server.createContext(base + "/collections/col-uuid-1/upsert", jsonHandler("{}", upsertBody));
        server.createContext(base + "/collections/col-uuid-1/query", jsonHandler(
                "{\"ids\":[[\"doc-1\"]],\"documents\":[[\"hello\"]],"
                        + "\"metadatas\":[[{\"dataset\":\"demo\",\"sourceName\":\"manual.pdf\"}]],"
                        + "\"distances\":[[0.07]]}",
                queryBody));
        server.createContext(base + "/collections/col-uuid-1/get", jsonHandler(
                "{\"ids\":[\"doc-1\"]}", getBody));
        server.createContext(base + "/collections/col-uuid-1/delete", jsonHandler("{}", deleteBody));
        server.start();
        try {
            Configuration configuration = new Configuration();
            configuration.setOkHttpClient(new OkHttpClient());
            ChromaConfig config = new ChromaConfig();
            config.setHost("http://127.0.0.1:" + server.getAddress().getPort());
            configuration.setChromaConfig(config);

            ChromaVectorStore store = new ChromaVectorStore(configuration);
            int inserted = store.upsert(VectorUpsertRequest.builder()
                    .dataset("demo")
                    .records(Collections.singletonList(VectorRecord.builder()
                            .id("doc-1")
                            .vector(Arrays.asList(0.1f, 0.2f))
                            .content("hello")
                            .metadata(mapOf("sourceName", "manual.pdf"))
                            .build()))
                    .build());

            List<VectorSearchResult> results = store.search(VectorSearchRequest.builder()
                    .dataset("demo")
                    .vector(Arrays.asList(0.2f, 0.3f))
                    .topK(3)
                    .filter(mapOf("tenant", "acme"))
                    .build());

            boolean exists = store.exists(VectorExistsRequest.builder()
                    .dataset("demo")
                    .filter(mapOf("contentHash", "hash-1"))
                    .build());

            boolean deleted = store.delete(VectorDeleteRequest.builder()
                    .dataset("demo")
                    .ids(Collections.singletonList("doc-1"))
                    .build());

            Assert.assertEquals(1, inserted);
            Assert.assertTrue(createBody.get().contains("get_or_create"));
            Assert.assertTrue(createBody.get().contains("hnsw:space"));
            Assert.assertTrue(upsertBody.get().contains("\"embeddings\""));
            Assert.assertTrue(upsertBody.get().contains("\"dataset\":\"demo\""));
            Assert.assertTrue(upsertBody.get().contains("\"sourceName\":\"manual.pdf\""));
            Assert.assertTrue(queryBody.get().contains("\"n_results\":3"));
            Assert.assertTrue(queryBody.get().contains("\"tenant\""));
            Assert.assertTrue(queryBody.get().contains("\"$and\""));
            Assert.assertTrue(exists);
            Assert.assertTrue(getBody.get().contains("\"contentHash\""));
            Assert.assertTrue(deleted);
            Assert.assertTrue(deleteBody.get().contains("\"doc-1\""));
            Assert.assertEquals(1, results.size());
            Assert.assertEquals("doc-1", results.get(0).getId());
            Assert.assertEquals("hello", results.get(0).getContent());
            Assert.assertEquals("manual.pdf", results.get(0).getMetadata().get("sourceName"));
            Assert.assertEquals(0.93f, results.get(0).getScore(), 0.001f);
        } finally {
            server.stop(0);
        }
    }

    private HttpHandler jsonHandler(final String responseBody, final AtomicReference<String> requestBody) {
        return new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws java.io.IOException {
                try {
                    requestBody.set(read(exchange.getRequestBody()));
                } catch (Exception ex) {
                    throw new java.io.IOException(ex);
                }
                respond(exchange, responseBody);
            }
        };
    }

    private void respond(HttpExchange exchange, String responseBody) throws java.io.IOException {
        byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.length);
        OutputStream outputStream = exchange.getResponseBody();
        try {
            outputStream.write(response);
        } finally {
            outputStream.close();
        }
    }

    private String read(InputStream inputStream) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[256];
        int len;
        while ((len = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, len);
        }
        return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
    }

    private static Map<String, Object> mapOf(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return map;
    }
}
