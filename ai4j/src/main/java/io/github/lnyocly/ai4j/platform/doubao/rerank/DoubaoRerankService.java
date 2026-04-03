package io.github.lnyocly.ai4j.platform.doubao.rerank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lnyocly.ai4j.config.DoubaoConfig;
import io.github.lnyocly.ai4j.constant.Constants;
import io.github.lnyocly.ai4j.exception.CommonException;
import io.github.lnyocly.ai4j.network.UrlUtils;
import io.github.lnyocly.ai4j.rerank.entity.RerankDocument;
import io.github.lnyocly.ai4j.rerank.entity.RerankRequest;
import io.github.lnyocly.ai4j.rerank.entity.RerankResponse;
import io.github.lnyocly.ai4j.rerank.entity.RerankResult;
import io.github.lnyocly.ai4j.rerank.entity.RerankUsage;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.IRerankService;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DoubaoRerankService implements IRerankService {

    private final DoubaoConfig doubaoConfig;
    private final OkHttpClient okHttpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DoubaoRerankService(Configuration configuration) {
        this(configuration, configuration == null ? null : configuration.getDoubaoConfig());
    }

    public DoubaoRerankService(Configuration configuration, DoubaoConfig doubaoConfig) {
        this.doubaoConfig = doubaoConfig;
        this.okHttpClient = configuration == null ? null : configuration.getOkHttpClient();
    }

    @Override
    public RerankResponse rerank(String baseUrl, String apiKey, RerankRequest request) throws Exception {
        String host = resolveBaseUrl(baseUrl);
        String key = resolveApiKey(apiKey);
        String path = resolveRerankUrl();

        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("rerank_model", request.getModel());
        if (StringUtils.isNotBlank(request.getInstruction())) {
            body.put("rerank_instruction", request.getInstruction());
        }
        List<Map<String, Object>> datas = new ArrayList<Map<String, Object>>();
        List<RerankDocument> documents = request.getDocuments() == null
                ? Collections.<RerankDocument>emptyList()
                : request.getDocuments();
        for (RerankDocument document : documents) {
            if (document == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("query", request.getQuery());
            String content = firstNonBlank(document.getContent(), document.getText());
            if (StringUtils.isNotBlank(content)) {
                item.put("content", content);
            }
            if (StringUtils.isNotBlank(document.getTitle())) {
                item.put("title", document.getTitle());
            }
            if (document.getImage() != null) {
                item.put("image", document.getImage());
            }
            datas.add(item);
        }
        body.put("datas", datas);
        if (request.getExtraBody() != null && !request.getExtraBody().isEmpty()) {
            body.putAll(request.getExtraBody());
        }

        Request.Builder builder = new Request.Builder()
                .url(UrlUtils.concatUrl(host, path))
                .post(RequestBody.create(objectMapper.writeValueAsString(body), MediaType.get(Constants.JSON_CONTENT_TYPE)));
        if (StringUtils.isNotBlank(key)) {
            builder.header("Authorization", "Bearer " + key);
        }

        try (okhttp3.Response response = okHttpClient.newCall(builder.build()).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                return parseResponse(objectMapper.readTree(response.body().string()), request);
            }
        }
        throw new CommonException("Doubao rerank request failed");
    }

    @Override
    public RerankResponse rerank(RerankRequest request) throws Exception {
        return rerank(null, null, request);
    }

    private RerankResponse parseResponse(JsonNode root, RerankRequest request) {
        JsonNode dataNode = root == null ? null : root.get("data");
        JsonNode scoreArray = dataNode;
        if (dataNode != null && dataNode.isObject() && dataNode.has("scores")) {
            scoreArray = dataNode.get("scores");
        }

        List<RerankResult> results = new ArrayList<RerankResult>();
        List<RerankDocument> documents = request.getDocuments() == null
                ? Collections.<RerankDocument>emptyList()
                : request.getDocuments();
        if (scoreArray != null && scoreArray.isArray()) {
            for (int i = 0; i < scoreArray.size(); i++) {
                JsonNode item = scoreArray.get(i);
                float relevanceScore;
                Integer index = i;
                if (item != null && item.isObject()) {
                    relevanceScore = item.has("score") ? (float) item.get("score").asDouble() : 0.0f;
                    if (item.has("index")) {
                        index = item.get("index").asInt();
                    }
                } else {
                    relevanceScore = item == null || item.isNull() ? 0.0f : (float) item.asDouble();
                }
                RerankDocument document = index != null && index >= 0 && index < documents.size()
                        ? documents.get(index)
                        : null;
                results.add(RerankResult.builder()
                        .index(index)
                        .relevanceScore(relevanceScore)
                        .document(document == null ? null : document.toBuilder().build())
                        .build());
            }
        }
        Collections.sort(results, new Comparator<RerankResult>() {
            @Override
            public int compare(RerankResult left, RerankResult right) {
                float l = left == null || left.getRelevanceScore() == null ? 0.0f : left.getRelevanceScore();
                float r = right == null || right.getRelevanceScore() == null ? 0.0f : right.getRelevanceScore();
                return Float.compare(r, l);
            }
        });

        return RerankResponse.builder()
                .id(text(root, "request_id"))
                .model(request == null ? null : request.getModel())
                .results(results)
                .usage(RerankUsage.builder()
                        .inputTokens(intValue(root == null ? null : root.get("token_usage")))
                        .build())
                .build();
    }

    private String resolveBaseUrl(String baseUrl) {
        if (StringUtils.isNotBlank(baseUrl)) {
            return baseUrl;
        }
        if (doubaoConfig != null && StringUtils.isNotBlank(doubaoConfig.getRerankApiHost())) {
            return doubaoConfig.getRerankApiHost();
        }
        if (doubaoConfig != null && StringUtils.isNotBlank(doubaoConfig.getApiHost())) {
            return doubaoConfig.getApiHost();
        }
        throw new IllegalArgumentException("doubao rerank apiHost is required");
    }

    private String resolveApiKey(String apiKey) {
        if (StringUtils.isNotBlank(apiKey)) {
            return apiKey;
        }
        return doubaoConfig == null ? null : doubaoConfig.getApiKey();
    }

    private String resolveRerankUrl() {
        if (doubaoConfig == null || StringUtils.isBlank(doubaoConfig.getRerankUrl())) {
            throw new IllegalArgumentException("doubao rerankUrl is required");
        }
        return doubaoConfig.getRerankUrl();
    }

    private String firstNonBlank(String... values) {
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

    private Integer intValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asInt();
    }

    private String text(JsonNode node, String fieldName) {
        if (node == null || fieldName == null || !node.has(fieldName) || node.get(fieldName).isNull()) {
            return null;
        }
        String text = node.get(fieldName).asText();
        return StringUtils.isBlank(text) ? null : text;
    }
}
