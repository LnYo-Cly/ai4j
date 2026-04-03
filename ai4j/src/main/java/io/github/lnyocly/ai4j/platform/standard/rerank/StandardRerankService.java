package io.github.lnyocly.ai4j.platform.standard.rerank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lnyocly.ai4j.constant.Constants;
import io.github.lnyocly.ai4j.exception.CommonException;
import io.github.lnyocly.ai4j.network.UrlUtils;
import io.github.lnyocly.ai4j.rerank.entity.RerankDocument;
import io.github.lnyocly.ai4j.rerank.entity.RerankRequest;
import io.github.lnyocly.ai4j.rerank.entity.RerankResponse;
import io.github.lnyocly.ai4j.rerank.entity.RerankResult;
import io.github.lnyocly.ai4j.rerank.entity.RerankUsage;
import io.github.lnyocly.ai4j.service.IRerankService;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class StandardRerankService implements IRerankService {

    private final OkHttpClient okHttpClient;
    private final String apiHost;
    private final String apiKey;
    private final String rerankUrl;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public StandardRerankService(OkHttpClient okHttpClient, String apiHost, String apiKey, String rerankUrl) {
        this.okHttpClient = okHttpClient;
        this.apiHost = apiHost;
        this.apiKey = apiKey;
        this.rerankUrl = rerankUrl;
    }

    @Override
    public RerankResponse rerank(String baseUrl, String apiKey, RerankRequest request) throws Exception {
        String host = resolveBaseUrl(baseUrl);
        String path = resolveRerankUrl();
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("model", request.getModel());
        body.put("query", request.getQuery());
        body.put("documents", toRequestDocuments(request.getDocuments()));
        if (request.getTopN() != null) {
            body.put("top_n", request.getTopN());
        }
        if (request.getReturnDocuments() != null) {
            body.put("return_documents", request.getReturnDocuments());
        }
        if (StringUtils.isNotBlank(request.getInstruction())) {
            body.put("instruction", request.getInstruction());
        }
        if (request.getExtraBody() != null && !request.getExtraBody().isEmpty()) {
            body.putAll(request.getExtraBody());
        }

        Request.Builder builder = new Request.Builder()
                .url(UrlUtils.concatUrl(host, path))
                .post(RequestBody.create(objectMapper.writeValueAsString(body), MediaType.get(Constants.JSON_CONTENT_TYPE)));
        String key = resolveApiKey(apiKey);
        if (StringUtils.isNotBlank(key)) {
            builder.header("Authorization", "Bearer " + key);
        }

        try (okhttp3.Response response = okHttpClient.newCall(builder.build()).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                JsonNode root = objectMapper.readTree(response.body().string());
                return toResponse(root, request);
            }
        }
        throw new CommonException("Standard rerank request failed");
    }

    @Override
    public RerankResponse rerank(RerankRequest request) throws Exception {
        return rerank(null, null, request);
    }

    protected RerankResponse toResponse(JsonNode root, RerankRequest request) {
        List<RerankResult> results = new ArrayList<RerankResult>();
        JsonNode resultsNode = root == null ? null : root.get("results");
        if (resultsNode != null && resultsNode.isArray()) {
            for (JsonNode item : resultsNode) {
                if (item == null || item.isNull()) {
                    continue;
                }
                results.add(RerankResult.builder()
                        .index(item.has("index") ? item.get("index").asInt() : null)
                        .relevanceScore(item.has("relevance_score") ? (float) item.get("relevance_score").asDouble() : null)
                        .document(parseDocument(item.get("document"), request))
                        .build());
            }
        }
        return RerankResponse.builder()
                .id(text(root, "id"))
                .model(firstNonBlank(text(root, "model"), request == null ? null : request.getModel()))
                .results(results)
                .usage(parseUsage(root == null ? null : root.get("usage")))
                .build();
    }

    protected RerankUsage parseUsage(JsonNode usageNode) {
        if (usageNode == null || usageNode.isNull()) {
            return null;
        }
        return RerankUsage.builder()
                .promptTokens(intValue(usageNode.get("prompt_tokens")))
                .totalTokens(intValue(usageNode.get("total_tokens")))
                .inputTokens(intValue(usageNode.get("input_tokens")))
                .build();
    }

    protected RerankDocument parseDocument(JsonNode documentNode, RerankRequest request) {
        if (documentNode == null || documentNode.isNull()) {
            return null;
        }
        if (documentNode.isTextual()) {
            String text = documentNode.asText();
            return RerankDocument.builder().text(text).content(text).build();
        }
        Map<String, Object> metadata = new LinkedHashMap<String, Object>();
        JsonNode metadataNode = documentNode.get("metadata");
        if (metadataNode != null && metadataNode.isObject()) {
            java.util.Iterator<Map.Entry<String, JsonNode>> iterator = metadataNode.fields();
            while (iterator.hasNext()) {
                Map.Entry<String, JsonNode> entry = iterator.next();
                metadata.put(entry.getKey(), parseJsonValue(entry.getValue()));
            }
        }
        String text = firstNonBlank(text(documentNode, "text"), text(documentNode, "content"));
        return RerankDocument.builder()
                .id(text(documentNode, "id"))
                .text(text)
                .content(text)
                .title(text(documentNode, "title"))
                .metadata(metadata.isEmpty() ? null : metadata)
                .build();
    }

    protected List<Object> toRequestDocuments(List<RerankDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            return Collections.emptyList();
        }
        List<Object> payload = new ArrayList<Object>(documents.size());
        for (RerankDocument document : documents) {
            if (document == null) {
                continue;
            }
            String text = firstNonBlank(document.getText(), document.getContent());
            if (StringUtils.isNotBlank(text)
                    && StringUtils.isBlank(document.getId())
                    && StringUtils.isBlank(document.getTitle())
                    && (document.getMetadata() == null || document.getMetadata().isEmpty())
                    && document.getImage() == null) {
                payload.add(text);
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            if (StringUtils.isNotBlank(document.getId())) {
                item.put("id", document.getId());
            }
            if (StringUtils.isNotBlank(text)) {
                item.put("text", text);
            }
            if (StringUtils.isNotBlank(document.getTitle())) {
                item.put("title", document.getTitle());
            }
            if (document.getImage() != null) {
                item.put("image", document.getImage());
            }
            if (document.getMetadata() != null && !document.getMetadata().isEmpty()) {
                item.put("metadata", document.getMetadata());
            }
            payload.add(item);
        }
        return payload;
    }

    protected String resolveBaseUrl(String baseUrl) {
        String host = StringUtils.isBlank(baseUrl) ? apiHost : baseUrl;
        if (StringUtils.isBlank(host)) {
            throw new IllegalArgumentException("rerank apiHost is required");
        }
        return host;
    }

    protected String resolveApiKey(String baseUrlApiKey) {
        return StringUtils.isBlank(baseUrlApiKey) ? apiKey : baseUrlApiKey;
    }

    protected String resolveRerankUrl() {
        if (StringUtils.isBlank(rerankUrl)) {
            throw new IllegalArgumentException("rerankUrl is required");
        }
        return rerankUrl;
    }

    protected String text(JsonNode node, String fieldName) {
        if (node == null || fieldName == null || !node.has(fieldName) || node.get(fieldName).isNull()) {
            return null;
        }
        String text = node.get(fieldName).asText();
        return StringUtils.isBlank(text) ? null : text;
    }

    protected Integer intValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asInt();
    }

    protected Object parseJsonValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isInt() || node.isLong()) {
            return node.asLong();
        }
        if (node.isFloat() || node.isDouble() || node.isBigDecimal()) {
            return node.asDouble();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isArray()) {
            List<Object> values = new ArrayList<Object>();
            for (JsonNode child : node) {
                values.add(parseJsonValue(child));
            }
            return values;
        }
        if (node.isObject()) {
            Map<String, Object> value = new LinkedHashMap<String, Object>();
            java.util.Iterator<Map.Entry<String, JsonNode>> iterator = node.fields();
            while (iterator.hasNext()) {
                Map.Entry<String, JsonNode> entry = iterator.next();
                value.put(entry.getKey(), parseJsonValue(entry.getValue()));
            }
            return value;
        }
        return node.asText();
    }

    protected String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
