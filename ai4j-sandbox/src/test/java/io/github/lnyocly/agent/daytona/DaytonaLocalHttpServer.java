package io.github.lnyocly.agent.daytona;

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

final class DaytonaLocalHttpServer implements AutoCloseable {

    private final HttpServer server;
    private final List<RequestRecord> records = Collections.synchronizedList(new ArrayList<RequestRecord>());
    private final List<Response> responses = Collections.synchronizedList(new ArrayList<Response>());

    private DaytonaLocalHttpServer(HttpServer server) {
        this.server = server;
    }

    static DaytonaLocalHttpServer start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        DaytonaLocalHttpServer harness = new DaytonaLocalHttpServer(server);
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

    DaytonaLocalHttpServer enqueue(int status, String body) {
        responses.add(new Response(status, body));
        return this;
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
        String body = read(exchange.getRequestBody());
        records.add(new RequestRecord(
                exchange.getRequestMethod(),
                exchange.getRequestURI().toString(),
                exchange.getRequestHeaders().getFirst("Authorization"),
                exchange.getRequestHeaders().getFirst("X-Daytona-Organization-ID"),
                body));

        Response response;
        synchronized (responses) {
            response = responses.isEmpty()
                    ? new Response(500, "{\"message\":\"unexpected request\"}")
                    : responses.remove(0);
        }
        byte[] bytes = response.body.getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json");
        exchange.sendResponseHeaders(response.status, bytes.length);
        OutputStream output = exchange.getResponseBody();
        try {
            output.write(bytes);
        } finally {
            output.close();
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private static String read(InputStream input) throws IOException {
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
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    static final class RequestRecord {
        final String method;
        final String path;
        final String authorization;
        final String organizationId;
        final String body;

        private RequestRecord(String method, String path, String authorization, String organizationId, String body) {
            this.method = method;
            this.path = path;
            this.authorization = authorization;
            this.organizationId = organizationId;
            this.body = body;
        }
    }

    private static final class Response {
        private final int status;
        private final String body;

        private Response(int status, String body) {
            this.status = status;
            this.body = body == null ? "" : body;
        }
    }
}
