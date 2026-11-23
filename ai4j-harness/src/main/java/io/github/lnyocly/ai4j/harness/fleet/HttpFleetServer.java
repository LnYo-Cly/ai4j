package io.github.lnyocly.ai4j.harness.fleet;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.github.lnyocly.ai4j.harness.HarnessConflictException;
import io.github.lnyocly.ai4j.harness.HarnessJson;
import io.github.lnyocly.ai4j.harness.HarnessStoreException;
import io.github.lnyocly.ai4j.harness.HarnessValidationException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Embedded HTTP/JSON Fleet center server over JDK {@link HttpServer} exposing
 * {@link FleetCenterService} endpoints for {@link HttpFleetTransport} clients.
 */
public class HttpFleetServer implements AutoCloseable {

    public static final String HEADER_NODE_ID = "X-Fleet-Node-Id";
    public static final String HEADER_CREDENTIAL_TOKEN = "X-Fleet-Token";

    private final FleetCenterService centerService;
    private final HttpServer server;
    private final ExecutorService executor;

    public HttpFleetServer(FleetCenterService centerService) throws IOException {
        this(centerService, "127.0.0.1", 0);
    }

    public HttpFleetServer(FleetCenterService centerService, int port) throws IOException {
        this(centerService, "127.0.0.1", port);
    }

    public HttpFleetServer(FleetCenterService centerService, String host, int port) throws IOException {
        if (centerService == null) {
            throw new IllegalArgumentException("FleetCenterService is required");
        }
        this.centerService = centerService;
        String bindHost = (host == null || host.trim().isEmpty()) ? "127.0.0.1" : host.trim();
        this.server = HttpServer.create(new InetSocketAddress(bindHost, port), 64);
        this.executor = Executors.newCachedThreadPool(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "ai4j-fleet-http");
                thread.setDaemon(true);
                return thread;
            }
        });
        this.server.setExecutor(this.executor);
        registerRoutes();
    }

    private void registerRoutes() {
        postJson("/fleet/edge/join", EdgeJoinRequest.class, new RequestHandler<EdgeJoinRequest>() {
            @Override
            public Object handle(HttpExchange exchange, EdgeJoinRequest req) {
                return centerService.edgeJoin(req);
            }
        });
        postJson("/fleet/edge/sync", EdgeSyncRequest.class, new RequestHandler<EdgeSyncRequest>() {
            @Override
            public Object handle(HttpExchange exchange, EdgeSyncRequest req) {
                return centerService.edgeSync(req);
            }
        });
        postJson("/fleet/executions/pull", ClaimPullRequest.class, new RequestHandler<ClaimPullRequest>() {
            @Override
            public Object handle(HttpExchange exchange, ClaimPullRequest req) {
                return centerService.pullClaimableExecutions(req);
            }
        });
        postJson("/fleet/executions/claim", ExecutionClaimRequest.class, new RequestHandler<ExecutionClaimRequest>() {
            @Override
            public Object handle(HttpExchange exchange, ExecutionClaimRequest req) {
                return centerService.claimExecution(req);
            }
        });
        postJson("/fleet/executions/outcome", ExecutionOutcomeRequest.class, new RequestHandler<ExecutionOutcomeRequest>() {
            @Override
            public Object handle(HttpExchange exchange, ExecutionOutcomeRequest req) {
                return centerService.persistOutcome(req);
            }
        });
        postJson("/fleet/steer", SteerRequest.class, new RequestHandler<SteerRequest>() {
            @Override
            public Object handle(HttpExchange exchange, SteerRequest req) {
                return centerService.steer(header(exchange, HEADER_NODE_ID), header(exchange, HEADER_CREDENTIAL_TOKEN), req);
            }
        });
        postJson("/fleet/consent", ConsentRequest.class, new RequestHandler<ConsentRequest>() {
            @Override
            public Object handle(HttpExchange exchange, ConsentRequest req) {
                return centerService.reviewConsent(header(exchange, HEADER_NODE_ID), header(exchange, HEADER_CREDENTIAL_TOKEN), req);
            }
        });
        postJson("/fleet/escalations/answer", EscalationAnswerRequest.class, new RequestHandler<EscalationAnswerRequest>() {
            @Override
            public Object handle(HttpExchange exchange, EscalationAnswerRequest req) {
                return centerService.answerEscalation(header(exchange, HEADER_NODE_ID), header(exchange, HEADER_CREDENTIAL_TOKEN), req);
            }
        });
        postJson("/fleet/nodes/unregister", UnregisterNodeRequest.class, new RequestHandler<UnregisterNodeRequest>() {
            @Override
            public Object handle(HttpExchange exchange, UnregisterNodeRequest req) {
                return centerService.unregisterNode(req);
            }
        });
        postJson("/fleet/orphans/takeover", TakeoverOrphanRequest.class, new RequestHandler<TakeoverOrphanRequest>() {
            @Override
            public Object handle(HttpExchange exchange, TakeoverOrphanRequest req) {
                return centerService.takeoverOrphan(req);
            }
        });
        getJson("/fleet/agenda", new GetHandler() {
            @Override
            public Object handle(HttpExchange exchange, Map<String, String> query) {
                return centerService.fetchAgenda(
                        header(exchange, HEADER_NODE_ID),
                        header(exchange, HEADER_CREDENTIAL_TOKEN),
                        query.get("actorId"),
                        query.get("scopeKey"));
            }
        });
        getJson("/fleet/orphans", new GetHandler() {
            @Override
            public Object handle(HttpExchange exchange, Map<String, String> query) {
                return centerService.listOrphanExports(query.get("scopeKey"));
            }
        });
    }

    public void start() {
        server.start();
    }

    public int getPort() {
        return server.getAddress().getPort();
    }

    public String getBaseUrl() {
        InetSocketAddress addr = server.getAddress();
        return "http://" + addr.getHostString() + ":" + addr.getPort();
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    private <T> void postJson(String path, final Class<T> requestType, final RequestHandler<T> handler) {
        server.createContext(path, new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                handleSafely(exchange, "POST", new ExchangeWork() {
                    @Override
                    public Object execute() throws IOException {
                        String body = readBody(exchange);
                        T request = body.trim().isEmpty() ? null : HarnessJson.fromJson(body, requestType);
                        return handler.handle(exchange, request);
                    }
                });
            }
        });
    }

    private void getJson(String path, final GetHandler handler) {
        server.createContext(path, new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                handleSafely(exchange, "GET", new ExchangeWork() {
                    @Override
                    public Object execute() {
                        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
                        return handler.handle(exchange, query);
                    }
                });
            }
        });
    }

    private void handleSafely(HttpExchange exchange, String expectedMethod, ExchangeWork work) throws IOException {
        try {
            if (!expectedMethod.equalsIgnoreCase(exchange.getRequestMethod())) {
                writeJson(exchange, 405, errorPayload("METHOD_NOT_ALLOWED", "validation",
                        "expected " + expectedMethod + " but got " + exchange.getRequestMethod()));
                return;
            }
            Object result = work.execute();
            writeJson(exchange, 200, result);
        } catch (HarnessConflictException ex) {
            writeJson(exchange, 409, errorPayload(ex.getErrorCode(), "conflict", ex.getMessage()));
        } catch (HarnessValidationException ex) {
            int status = FleetCenterService.ERROR_UNAUTHORIZED.equals(ex.getErrorCode()) ? 401 : 400;
            writeJson(exchange, status, errorPayload(ex.getErrorCode(), "validation", ex.getMessage()));
        } catch (HarnessStoreException ex) {
            writeJson(exchange, 500, errorPayload(ex.getErrorCode(), "store", ex.getMessage()));
        } catch (RuntimeException ex) {
            writeJson(exchange, 500, errorPayload("INTERNAL_ERROR", "runtime", ex.getMessage()));
        } finally {
            exchange.close();
        }
    }

    private static Map<String, Object> errorPayload(String errorCode, String errorType, String message) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("errorCode", errorCode);
        payload.put("errorType", errorType);
        payload.put("message", message);
        return payload;
    }

    private static String header(HttpExchange exchange, String name) {
        return exchange.getRequestHeaders().getFirst(name);
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        try (InputStream in = exchange.getRequestBody()) {
            int read;
            while ((read = in.read(buf)) >= 0) {
                if (read > 0) {
                    out.write(buf, 0, read);
                }
            }
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private static void writeJson(HttpExchange exchange, int status, Object payload) throws IOException {
        byte[] bytes = HarnessJson.toJson(payload).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> map = new LinkedHashMap<String, String>();
        if (rawQuery == null || rawQuery.trim().isEmpty()) {
            return map;
        }
        String[] pairs = rawQuery.split("&");
        for (String pair : pairs) {
            if (pair == null || pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String rawKey = eq >= 0 ? pair.substring(0, eq) : pair;
            String rawVal = eq >= 0 ? pair.substring(eq + 1) : "";
            map.put(decodeUtf8(rawKey), decodeUtf8(rawVal));
        }
        return map;
    }

    private static String decodeUtf8(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalStateException("UTF-8 unsupported", ex);
        }
    }

    private interface RequestHandler<T> {
        Object handle(HttpExchange exchange, T request);
    }

    private interface GetHandler {
        Object handle(HttpExchange exchange, Map<String, String> query);
    }

    private interface ExchangeWork {
        Object execute() throws IOException;
    }
}
