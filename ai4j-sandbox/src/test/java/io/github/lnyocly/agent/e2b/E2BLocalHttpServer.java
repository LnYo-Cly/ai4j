package io.github.lnyocly.agent.e2b;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * Minimal offline HTTP harness for E2B provider tests. Unlike a plain JSON stub it can return raw
 * Connect-framed bytes (for {@code process.Process/Start}) and records request bodies as bytes so
 * the Connect request envelope can be inspected.
 */
final class E2BLocalHttpServer implements AutoCloseable {

    private final HttpServer server;
    private final List<RequestRecord> records = Collections.synchronizedList(new ArrayList<RequestRecord>());
    private final List<Response> responses = Collections.synchronizedList(new ArrayList<Response>());

    private E2BLocalHttpServer(HttpServer server) {
        this.server = server;
    }

    static E2BLocalHttpServer start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        final E2BLocalHttpServer harness = new E2BLocalHttpServer(server);
        server.createContext("/", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                harness.handle(exchange);
            }
        });
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        return harness;
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    E2BLocalHttpServer enqueue(int status, String contentType, byte[] body) {
        responses.add(new Response(status, contentType, body));
        return this;
    }

    E2BLocalHttpServer enqueueJson(int status, String body) {
        return enqueue(status, "application/json", body.getBytes(StandardCharsets.UTF_8));
    }

    List<RequestRecord> records() {
        synchronized (records) {
            return new ArrayList<RequestRecord>(records);
        }
    }

    RequestRecord record(int index) {
        return records().get(index);
    }

    private void handle(HttpExchange exchange) throws IOException {
        byte[] body = read(exchange.getRequestBody());
        records.add(new RequestRecord(
                exchange.getRequestMethod(),
                exchange.getRequestURI().toString(),
                exchange.getRequestHeaders().getFirst("X-API-Key"),
                exchange.getRequestHeaders().getFirst("Authorization"),
                exchange.getRequestHeaders().getFirst("Content-Type"),
                body));

        Response response;
        synchronized (responses) {
            response = responses.isEmpty()
                    ? new Response(500, "application/json", "{\"message\":\"unexpected request\"}".getBytes(StandardCharsets.UTF_8))
                    : responses.remove(0);
        }
        Headers headers = exchange.getResponseHeaders();
        if (response.contentType != null) {
            headers.set("Content-Type", response.contentType);
        }
        exchange.sendResponseHeaders(response.status, response.body.length == 0 ? -1 : response.body.length);
        if (response.body.length > 0) {
            OutputStream output = exchange.getResponseBody();
            try {
                output.write(response.body);
            } finally {
                output.close();
            }
        } else {
            exchange.getResponseBody().close();
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private static byte[] read(InputStream input) throws IOException {
        if (input == null) {
            return new byte[0];
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int read;
        try {
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        } finally {
            input.close();
        }
        return output.toByteArray();
    }

    static final class RequestRecord {
        final String method;
        final String path;
        final String apiKey;
        final String authorization;
        final String contentType;
        final byte[] body;

        private RequestRecord(String method, String path, String apiKey, String authorization, String contentType, byte[] body) {
            this.method = method;
            this.path = path;
            this.apiKey = apiKey;
            this.authorization = authorization;
            this.contentType = contentType;
            this.body = body;
        }

        String bodyUtf8() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    private static final class Response {
        final int status;
        final String contentType;
        final byte[] body;

        private Response(int status, String contentType, byte[] body) {
            this.status = status;
            this.contentType = contentType;
            this.body = body == null ? new byte[0] : body;
        }
    }
}
