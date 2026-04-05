package io.github.lnyocly.ai4j.vector.store.qdrant;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.github.lnyocly.ai4j.config.QdrantConfig;
import io.github.lnyocly.ai4j.service.Configuration;
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

public class QdrantVectorStoreTest {

    @Test
    public void shouldUpsertAndSearchAgainstQdrantHttpApi() throws Exception {
        AtomicReference<String> upsertBody = new AtomicReference<String>();
        AtomicReference<String> queryBody = new AtomicReference<String>();

        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/collections/demo/points", jsonHandler("{\"status\":\"ok\"}", upsertBody));
        server.createContext("/collections/demo/points/query", jsonHandler(
                "{\"result\":{\"points\":[{\"id\":\"pt-1\",\"score\":0.87,\"payload\":{\"content\":\"snippet\",\"sourceName\":\"manual.pdf\"}}]}}",
                queryBody));
        server.start();
        try {
            Configuration configuration = new Configuration();
            configuration.setOkHttpClient(new OkHttpClient());
            QdrantConfig qdrantConfig = new QdrantConfig();
            qdrantConfig.setHost("http://127.0.0.1:" + server.getAddress().getPort());
            configuration.setQdrantConfig(qdrantConfig);

            QdrantVectorStore store = new QdrantVectorStore(configuration);
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

            Assert.assertEquals(1, inserted);
            Assert.assertTrue(upsertBody.get().contains("\"points\""));
            Assert.assertTrue(upsertBody.get().contains("\"sourceName\":\"manual.pdf\""));
            Assert.assertTrue(queryBody.get().contains("\"limit\":3"));
            Assert.assertTrue(queryBody.get().contains("\"tenant\""));
            Assert.assertEquals(1, results.size());
            Assert.assertEquals("pt-1", results.get(0).getId());
            Assert.assertEquals("snippet", results.get(0).getContent());
        } finally {
            server.stop(0);
        }
    }

    private HttpHandler jsonHandler(final String responseBody, final AtomicReference<String> requestBody) {
        return new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) {
                try {
                    requestBody.set(read(exchange.getRequestBody()));
                    byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, response.length);
                    OutputStream outputStream = exchange.getResponseBody();
                    try {
                        outputStream.write(response);
                    } finally {
                        outputStream.close();
                    }
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
        };
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
