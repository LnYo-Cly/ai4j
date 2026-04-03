package io.github.lnyocly.ai4j.platform.ollama.rerank;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.github.lnyocly.ai4j.config.OllamaConfig;
import io.github.lnyocly.ai4j.rerank.entity.RerankDocument;
import io.github.lnyocly.ai4j.rerank.entity.RerankRequest;
import io.github.lnyocly.ai4j.rerank.entity.RerankResponse;
import io.github.lnyocly.ai4j.service.Configuration;
import okhttp3.OkHttpClient;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

public class OllamaRerankServiceTest {

    @Test
    public void shouldCallOllamaRerankEndpointWithoutAuthWhenApiKeyMissing() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<String>();
        AtomicReference<String> authorization = new AtomicReference<String>();

        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/rerank", jsonHandler(
                "{\"model\":\"bge-reranker-v2-m3\",\"results\":[{\"index\":0,\"relevance_score\":0.77,\"document\":\"document-a\"}]}",
                requestBody,
                authorization));
        server.start();
        try {
            Configuration configuration = new Configuration();
            configuration.setOkHttpClient(new OkHttpClient());
            configuration.setOllamaConfig(new OllamaConfig(
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    "",
                    "api/chat",
                    "api/embed",
                    "api/rerank"
            ));

            OllamaRerankService service = new OllamaRerankService(configuration);
            RerankResponse response = service.rerank(RerankRequest.builder()
                    .model("bge-reranker-v2-m3")
                    .query("vacation policy")
                    .documents(Collections.singletonList(
                            RerankDocument.builder().text("document-a").content("document-a").build()
                    ))
                    .topN(1)
                    .build());

            Assert.assertNull(authorization.get());
            Assert.assertTrue(requestBody.get().contains("\"model\":\"bge-reranker-v2-m3\""));
            Assert.assertEquals(1, response.getResults().size());
            Assert.assertEquals(0.77d, response.getResults().get(0).getRelevanceScore(), 0.0001d);
        } finally {
            server.stop(0);
        }
    }

    private HttpHandler jsonHandler(final String responseBody,
                                    final AtomicReference<String> requestBody,
                                    final AtomicReference<String> authorization) {
        return new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) {
                try {
                    requestBody.set(read(exchange.getRequestBody()));
                    authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
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
}
