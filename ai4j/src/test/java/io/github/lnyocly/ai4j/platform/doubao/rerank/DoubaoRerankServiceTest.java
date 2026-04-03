package io.github.lnyocly.ai4j.platform.doubao.rerank;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.github.lnyocly.ai4j.config.DoubaoConfig;
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
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

public class DoubaoRerankServiceTest {

    @Test
    public void shouldConvertDoubaoKnowledgeRerankApi() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<String>();
        AtomicReference<String> authorization = new AtomicReference<String>();

        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/knowledge/service/rerank", jsonHandler(
                "{\"request_id\":\"req-1\",\"token_usage\":12,\"data\":[0.12,0.82]}",
                requestBody,
                authorization));
        server.start();
        try {
            Configuration configuration = new Configuration();
            configuration.setOkHttpClient(new OkHttpClient());
            configuration.setDoubaoConfig(new DoubaoConfig(
                    "https://ark.cn-beijing.volces.com/api/v3/",
                    "ark-key",
                    "chat/completions",
                    "images/generations",
                    "responses",
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    "api/knowledge/service/rerank"
            ));

            DoubaoRerankService service = new DoubaoRerankService(configuration);
            RerankResponse response = service.rerank(RerankRequest.builder()
                    .model("doubao-rerank")
                    .query("vacation policy")
                    .documents(Arrays.asList(
                            RerankDocument.builder().content("doc-a").build(),
                            RerankDocument.builder().content("doc-b").build()
                    ))
                    .instruction("只判断相关性")
                    .build());

            Assert.assertEquals("Bearer ark-key", authorization.get());
            Assert.assertTrue(requestBody.get().contains("\"rerank_model\":\"doubao-rerank\""));
            Assert.assertTrue(requestBody.get().contains("\"rerank_instruction\":\"只判断相关性\""));
            Assert.assertTrue(requestBody.get().contains("\"query\":\"vacation policy\""));
            Assert.assertTrue(requestBody.get().contains("\"content\":\"doc-a\""));
            Assert.assertEquals(2, response.getResults().size());
            Assert.assertEquals(Integer.valueOf(1), response.getResults().get(0).getIndex());
            Assert.assertEquals(0.82d, response.getResults().get(0).getRelevanceScore(), 0.0001d);
            Assert.assertEquals(Integer.valueOf(12), response.getUsage().getInputTokens());
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
