package io.github.lnyocly.ai4j.rag;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.lnyocly.ai4j.client.ModelClient;
import io.github.lnyocly.ai4j.codec.JsonCodec;
import io.github.lnyocly.ai4j.codec.JsonCodecException;
import io.github.lnyocly.ai4j.model.Message;
import io.github.lnyocly.ai4j.model.ModelRequest;
import io.github.lnyocly.ai4j.model.ModelResult;
import io.github.lnyocly.ai4j.model.Protocol;

import java.util.Collections;
import java.util.List;

/**
 * {@link RagJudge} implementation backed by the next-generation {@link ModelClient}.
 */
public class ModelClientRagJudge implements RagJudge {

    private final ModelClient modelClient;
    private final String model;
    private final int maxContextChars;
    private final double temperature;
    private final int maxOutputTokens;

    public ModelClientRagJudge(ModelClient modelClient, String model) {
        this(builder().modelClient(modelClient).model(model));
    }

    private ModelClientRagJudge(Builder builder) {
        if (builder.modelClient == null) {
            throw new IllegalArgumentException("modelClient must not be null");
        }
        String normalizedModel = trimToNull(builder.model);
        if (normalizedModel == null) {
            throw new IllegalArgumentException("model must not be blank");
        }
        this.modelClient = builder.modelClient;
        this.model = normalizedModel;
        this.maxContextChars = Math.max(256, builder.maxContextChars);
        this.temperature = builder.temperature;
        this.maxOutputTokens = Math.max(64, builder.maxOutputTokens);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public RagJudgeEvaluation judge(RagJudgeRequest request) throws Exception {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        String query = trimToNull(request.getQuery());
        if (query == null) {
            throw new IllegalArgumentException("request.query must not be blank");
        }
        String answer = trimToNull(request.getAnswer());
        if (answer == null) {
            throw new IllegalArgumentException("request.answer must not be blank");
        }

        String contextText = resolveContextText(request);
        ModelRequest.Builder requestBuilder = ModelRequest.builder()
                .model(model)
                .message(Message.system().text(buildSystemPrompt()).build())
                .message(Message.user().text(buildUserPrompt(query, contextText, answer)).build())
                .temperature(Double.valueOf(temperature))
                .maxOutputTokens(Integer.valueOf(maxOutputTokens));
        if (modelClient.endpoint() != null && modelClient.endpoint().getProtocol() == Protocol.CHAT_COMPLETIONS) {
            requestBuilder.providerOption("response_format", Collections.singletonMap("type", "json_object"));
        }

        ModelResult result = modelClient.create(requestBuilder.build());
        String rawOutput = ModelClientRagQueryPlanner.extractAssistantText(result);
        if (rawOutput == null) {
            throw new IllegalStateException("ModelClient returned empty judge output");
        }

        JsonNode root;
        try {
            root = JsonCodec.readTreeLenient(rawOutput);
        } catch (JsonCodecException e) {
            throw new IllegalStateException("Unable to parse JSON judge output: " + rawOutput, e);
        }
        if (root == null || !root.isObject()) {
            throw new IllegalStateException("Unable to parse JSON judge output: " + rawOutput);
        }

        Double faithfulness = scoreField(root, "faithfulnessScore", "faithfulness", "faithfulness_score");
        Double contextRelevance = scoreField(root, "contextRelevanceScore", "contextRelevance",
                "context_relevance", "context_relevance_score");
        Double answerRelevance = scoreField(root, "answerRelevanceScore", "answerRelevance",
                "answer_relevance", "answer_relevance_score");
        if (faithfulness == null && contextRelevance == null && answerRelevance == null) {
            throw new IllegalStateException("Judge JSON output contains no numeric score fields: " + rawOutput);
        }

        return RagJudgeEvaluation.builder()
                .faithfulnessScore(faithfulness)
                .contextRelevanceScore(contextRelevance)
                .answerRelevanceScore(answerRelevance)
                .reason(textField(root, "reason", "explanation", "rationale"))
                .rawOutput(rawOutput)
                .build();
    }

    private String resolveContextText(RagJudgeRequest request) {
        String direct = trimToNull(request.getContext());
        if (direct != null) {
            return truncate(direct, maxContextChars);
        }
        List<RagHit> hits = request.getHits();
        if (hits == null || hits.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hits.size(); i++) {
            RagHit hit = hits.get(i);
            if (hit == null || trimToNull(hit.getContent()) == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            sb.append('[').append(i + 1).append("] ");
            if (trimToNull(hit.getSourceName()) != null) {
                sb.append(hit.getSourceName().trim()).append(": ");
            }
            sb.append(hit.getContent().trim());
        }
        return truncate(sb.toString(), maxContextChars);
    }

    private static String buildSystemPrompt() {
        return "You are a strict RAG answer quality judge. "
                + "Score faithfulness (grounding of the answer in retrieved context), "
                + "contextRelevance (relevance of retrieved context to the query), "
                + "and answerRelevance (how well the answer addresses the query), each in [0.0, 1.0]. "
                + "Return JSON only without markdown fences: "
                + "{\"faithfulnessScore\": number, \"contextRelevanceScore\": number, \"answerRelevanceScore\": number, \"reason\": string}.";
    }

    private static String buildUserPrompt(String query, String contextText, String answer) {
        StringBuilder sb = new StringBuilder();
        sb.append("Query:\n").append(query).append("\n\n");
        sb.append("Retrieved Context:\n").append(contextText.isEmpty() ? "(none)" : contextText).append("\n\n");
        sb.append("Answer:\n").append(answer);
        return sb.toString();
    }

    private static Double scoreField(JsonNode root, String... names) {
        if (root == null || names == null) {
            return null;
        }
        for (String name : names) {
            JsonNode child = root.get(name);
            if (child == null || child.isNull()) {
                continue;
            }
            double value;
            if (child.isNumber()) {
                value = child.asDouble();
            } else if (child.isTextual()) {
                try {
                    value = Double.parseDouble(child.asText().trim());
                } catch (NumberFormatException ignored) {
                    continue;
                }
            } else {
                continue;
            }
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                continue;
            }
            return Double.valueOf(clampUnit(value));
        }
        return null;
    }

    private static double clampUnit(double value) {
        if (value < 0.0) {
            return 0.0;
        }
        if (value > 1.0) {
            return 1.0;
        }
        return value;
    }

    private static String textField(JsonNode root, String... names) {
        if (root == null || names == null) {
            return null;
        }
        for (String name : names) {
            JsonNode child = root.get(name);
            if (child != null && child.isTextual()) {
                String value = trimToNull(child.asText());
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    private static String truncate(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return text == null ? "" : text;
        }
        return text.substring(0, maxChars);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static final class Builder {

        private ModelClient modelClient;
        private String model;
        private int maxContextChars = 8000;
        private double temperature = 0.0;
        private int maxOutputTokens = 400;

        private Builder() {
        }

        public Builder modelClient(ModelClient modelClient) {
            this.modelClient = modelClient;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder maxContextChars(int maxContextChars) {
            this.maxContextChars = maxContextChars;
            return this;
        }

        public Builder temperature(double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder maxOutputTokens(int maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
            return this;
        }

        public ModelClientRagJudge build() {
            return new ModelClientRagJudge(this);
        }
    }
}
