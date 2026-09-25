package io.github.lnyocly.ai4j.vector.store.elasticsearch;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.github.lnyocly.ai4j.config.ElasticsearchConfig;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class ElasticsearchVectorStoreTest {

    @Test
    public void shouldCreateIndexUpsertSearchAndDelete() throws Exception {
        AtomicReference<String> bulkBody = new AtomicReference<String>();
        java.util.List<String> searchBodies = new java.util.concurrent.CopyOnWriteArrayList<String>();
        AtomicReference<String> deleteBody = new AtomicReference<String>();
        AtomicReference<String> mappingBody = new AtomicReference<String>();
        AtomicInteger headCalls = new AtomicInteger(0);

        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/ai4j_vectors", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws java.io.IOException {
                if ("HEAD".equals(exchange.getRequestMethod())) {
                    headCalls.incrementAndGet();
                    exchange.sendResponseHeaders(404, -1);
                    exchange.close();
                    return;
                }
                if ("PUT".equals(exchange.getRequestMethod())) {
                    try {
                        mappingBody.set(read(exchange.getRequestBody()));
                    } catch (Exception ex) {
                        throw new java.io.IOException(ex);
                    }
                    respond(exchange, "{}");
                    return;
                }
                respond(exchange, "{}");
            }
        });
        server.createContext("/ai4j_vectors/_bulk", jsonHandler("{\"errors\":false}", bulkBody));
        server.createContext("/ai4j_vectors/_search", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws java.io.IOException {
                String body;
                try {
                    body = read(exchange.getRequestBody());
                } catch (Exception ex) {
                    throw new java.io.IOException(ex);
                }
                searchBodies.add(body);
                String response = body.contains("\"knn\"")
                        ? "{\"hits\":{\"total\":{\"value\":1,\"relation\":\"eq\"},\"hits\":["
                          + "{\"_id\":\"doc-1\",\"_score\":0.93,\"_source\":{\"content\":\"hello\","
                          + "\"metadata\":{\"sourceName\":\"manual.pdf\"}}}]}}"
                        : "{\"hits\":{\"total\":{\"value\":1,\"relation\":\"eq\"},\"hits\":[]}}";
                respond(exchange, response);
            }
        });
        server.createContext("/ai4j_vectors/_delete_by_query", jsonHandler("{\"deleted\":1}", deleteBody));
        server.start();
        try {
            Configuration configuration = new Configuration();
            configuration.setOkHttpClient(new OkHttpClient());
            ElasticsearchConfig config = new ElasticsearchConfig();
            config.setHost("http://127.0.0.1:" + server.getAddress().getPort());
            config.setVectorDim(2);
            configuration.setElasticsearchConfig(config);

            ElasticsearchVectorStore store = new ElasticsearchVectorStore(configuration);
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
            Assert.assertEquals(1, headCalls.get());
            Assert.assertTrue(mappingBody.get().contains("dense_vector"));
            Assert.assertTrue(mappingBody.get().contains("flattened"));
            Assert.assertTrue(bulkBody.get().contains("\"index\""));
            Assert.assertTrue(bulkBody.get().contains("\"dataset\":\"demo\""));
            Assert.assertTrue(bulkBody.get().contains("\"sourceName\":\"manual.pdf\""));
            Assert.assertEquals(2, searchBodies.size());
            Assert.assertTrue(searchBodies.get(0).contains("\"knn\""));
            Assert.assertTrue(searchBodies.get(0).contains("\"metadata.tenant\""));
            Assert.assertTrue(searchBodies.get(1).contains("\"terminate_after\""));
            Assert.assertTrue(searchBodies.get(1).contains("\"metadata.contentHash\""));
            Assert.assertTrue(exists);
            Assert.assertTrue(deleted);
            Assert.assertTrue(deleteBody.get().contains("\"terms\""));
            Assert.assertTrue(deleteBody.get().contains("\"_id\""));
            Assert.assertEquals(1, results.size());
            Assert.assertEquals("doc-1", results.get(0).getId());
            Assert.assertEquals("hello", results.get(0).getContent());
            Assert.assertEquals("manual.pdf", results.get(0).getMetadata().get("sourceName"));
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
