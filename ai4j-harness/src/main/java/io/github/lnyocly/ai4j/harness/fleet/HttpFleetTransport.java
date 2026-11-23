package io.github.lnyocly.ai4j.harness.fleet;

import io.github.lnyocly.ai4j.harness.ContentPinnedConsentRecord;
import io.github.lnyocly.ai4j.harness.EscalationRecord;
import io.github.lnyocly.ai4j.harness.ExecutionRecord;
import io.github.lnyocly.ai4j.harness.HarnessConflictException;
import io.github.lnyocly.ai4j.harness.HarnessJson;
import io.github.lnyocly.ai4j.harness.HarnessStoreException;
import io.github.lnyocly.ai4j.harness.HarnessValidationException;
import io.github.lnyocly.ai4j.harness.SteerRecord;
import io.github.lnyocly.ai4j.harness.SupervisoryAgenda;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@link OkHttpClient}-backed HTTP/JSON {@link FleetTransport} connecting edge
 * and proxy nodes to a remote {@link HttpFleetServer}.
 */
public class HttpFleetTransport implements FleetTransport {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

    private final String baseUrl;
    private final OkHttpClient client;
    private final boolean ownClient;

    public HttpFleetTransport(String baseUrl) {
        this(baseUrl, new OkHttpClient(), true);
    }

    public HttpFleetTransport(String baseUrl, OkHttpClient client) {
        this(baseUrl, client, false);
    }

    private HttpFleetTransport(String baseUrl, OkHttpClient client, boolean ownClient) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Fleet baseUrl is required");
        }
        String trimmed = baseUrl.trim();
        this.baseUrl = trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
        this.client = client == null ? new OkHttpClient() : client;
        this.ownClient = client == null || ownClient;
    }

    @Override
    public EdgeJoinResponse edgeJoin(EdgeJoinRequest request) {
        return post("/fleet/edge/join", null, null, request, EdgeJoinResponse.class);
    }

    @Override
    public EdgeSyncResponse edgeSync(EdgeSyncRequest request) {
        return post("/fleet/edge/sync", null, null, request, EdgeSyncResponse.class);
    }

    @Override
    public ClaimPullResponse pullClaimableExecutions(ClaimPullRequest request) {
        return post("/fleet/executions/pull", null, null, request, ClaimPullResponse.class);
    }

    @Override
    public ExecutionRecord claimExecution(ExecutionClaimRequest request) {
        return post("/fleet/executions/claim", null, null, request, ExecutionRecord.class);
    }

    @Override
    public ExecutionRecord persistOutcome(ExecutionOutcomeRequest request) {
        return post("/fleet/executions/outcome", null, null, request, ExecutionRecord.class);
    }

    @Override
    public SupervisoryAgenda fetchAgenda(String nodeId, String credentialToken, String actorId, String scopeKey) {
        StringBuilder path = new StringBuilder("/fleet/agenda");
        String sep = "?";
        if (actorId != null && !actorId.trim().isEmpty()) {
            path.append(sep).append("actorId=").append(encodeUtf8(actorId));
            sep = "&";
        }
        if (scopeKey != null && !scopeKey.trim().isEmpty()) {
            path.append(sep).append("scopeKey=").append(encodeUtf8(scopeKey));
        }
        return get(path.toString(), nodeId, credentialToken, SupervisoryAgenda.class);
    }

    @Override
    public SteerRecord steer(String nodeId, String credentialToken, SteerRequest request) {
        return post("/fleet/steer", nodeId, credentialToken, request, SteerRecord.class);
    }

    @Override
    public ContentPinnedConsentRecord reviewConsent(String nodeId, String credentialToken, ConsentRequest request) {
        return post("/fleet/consent", nodeId, credentialToken, request, ContentPinnedConsentRecord.class);
    }

    @Override
    public EscalationRecord answerEscalation(String nodeId, String credentialToken, EscalationAnswerRequest request) {
        return post("/fleet/escalations/answer", nodeId, credentialToken, request, EscalationRecord.class);
    }

    @Override
    public UnregisterNodeResult unregisterNode(UnregisterNodeRequest request) {
        return post("/fleet/nodes/unregister", null, null, request, UnregisterNodeResult.class);
    }

    @Override
    public List<OrphanHandoffExport> listOrphanExports(String scopeKey) {
        String path = "/fleet/orphans";
        if (scopeKey != null && !scopeKey.trim().isEmpty()) {
            path += "?scopeKey=" + encodeUtf8(scopeKey);
        }
        String raw = execute(buildGetRequest(path, null, null));
        List<Object> items = HarnessJson.parseList(raw);
        List<OrphanHandoffExport> result = new ArrayList<OrphanHandoffExport>();
        if (items != null) {
            for (Object item : items) {
                result.add(HarnessJson.convert(item, OrphanHandoffExport.class));
            }
        }
        return result;
    }

    @Override
    public ExecutionRecord takeoverOrphan(TakeoverOrphanRequest request) {
        return post("/fleet/orphans/takeover", null, null, request, ExecutionRecord.class);
    }

    @Override
    public void close() {
        if (ownClient) {
            client.dispatcher().executorService().shutdown();
            client.connectionPool().evictAll();
        }
    }

    private <T> T post(String path, String nodeId, String credentialToken, Object bodyObj, Class<T> responseType) {
        String json = HarnessJson.toJson(bodyObj);
        RequestBody body = RequestBody.create(json, JSON_MEDIA_TYPE);
        Request.Builder builder = new Request.Builder()
                .url(baseUrl + path)
                .post(body);
        applyNodeHeaders(builder, nodeId, credentialToken);
        String raw = execute(builder.build());
        return HarnessJson.fromJson(raw, responseType);
    }

    private <T> T get(String path, String nodeId, String credentialToken, Class<T> responseType) {
        String raw = execute(buildGetRequest(path, nodeId, credentialToken));
        return HarnessJson.fromJson(raw, responseType);
    }

    private Request buildGetRequest(String path, String nodeId, String credentialToken) {
        Request.Builder builder = new Request.Builder()
                .url(baseUrl + path)
                .get();
        applyNodeHeaders(builder, nodeId, credentialToken);
        return builder.build();
    }

    private static void applyNodeHeaders(Request.Builder builder, String nodeId, String credentialToken) {
        if (nodeId != null) {
            builder.header(HttpFleetServer.HEADER_NODE_ID, nodeId);
        }
        if (credentialToken != null) {
            builder.header(HttpFleetServer.HEADER_CREDENTIAL_TOKEN, credentialToken);
        }
    }

    private String execute(Request request) {
        try (Response response = client.newCall(request).execute()) {
            ResponseBody body = response.body();
            String text = body == null ? "" : body.string();
            if (response.isSuccessful()) {
                return text;
            }
            throw translateError(response.code(), text);
        } catch (IOException ex) {
            throw new HarnessStoreException("FLEET_HTTP_IO_ERROR",
                    "fleet HTTP request failed: " + ex.getMessage(), ex);
        }
    }

    private static RuntimeException translateError(int httpCode, String rawBody) {
        String errorCode = null;
        String errorType = null;
        String message = rawBody;
        try {
            if (rawBody != null && !rawBody.trim().isEmpty()) {
                Map<String, Object> parsed = HarnessJson.parseMap(rawBody);
                if (parsed != null) {
                    Object ec = parsed.get("errorCode");
                    Object et = parsed.get("errorType");
                    Object msg = parsed.get("message");
                    errorCode = ec == null ? null : String.valueOf(ec);
                    errorType = et == null ? null : String.valueOf(et);
                    message = msg == null ? rawBody : String.valueOf(msg);
                }
            }
        } catch (RuntimeException ignored) {
            // Preserve rawBody as message
        }
        if (httpCode == 409 || "conflict".equals(errorType)) {
            return errorCode == null
                    ? new HarnessConflictException(message)
                    : new HarnessConflictException(errorCode, message);
        }
        if (httpCode == 400 || httpCode == 401 || httpCode == 403 || httpCode == 405 || "validation".equals(errorType)) {
            return errorCode == null
                    ? new HarnessValidationException(message)
                    : new HarnessValidationException(errorCode, message);
        }
        return new HarnessStoreException(errorCode, message, null);
    }

    private static String encodeUtf8(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalStateException("UTF-8 unsupported", ex);
        }
    }
}
