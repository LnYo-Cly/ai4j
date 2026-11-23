package io.github.lnyocly.ai4j.rag;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.lnyocly.ai4j.client.ModelClient;
import io.github.lnyocly.ai4j.codec.JsonCodec;
import io.github.lnyocly.ai4j.codec.JsonCodecException;
import io.github.lnyocly.ai4j.memory.ChatMemoryItem;
import io.github.lnyocly.ai4j.model.Message;
import io.github.lnyocly.ai4j.model.MessageRole;
import io.github.lnyocly.ai4j.model.ModelItem;
import io.github.lnyocly.ai4j.model.ModelRequest;
import io.github.lnyocly.ai4j.model.ModelResult;
import io.github.lnyocly.ai4j.model.Protocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * {@link RagQueryPlanner} implementation backed by the next-generation {@link ModelClient}.
 */
public class ModelClientRagQueryPlanner implements RagQueryPlanner {

    private static final Logger log = LoggerFactory.getLogger(ModelClientRagQueryPlanner.class);

    private final ModelClient modelClient;
    private final String model;
    private final boolean rewriteEnabled;
    private final boolean multiQueryEnabled;
    private final boolean hydeEnabled;
    private final int maxVariants;
    private final int maxHistoryItems;
    private final double temperature;
    private final int maxOutputTokens;

    public ModelClientRagQueryPlanner(ModelClient modelClient, String model) {
        this(builder().modelClient(modelClient).model(model));
    }

    private ModelClientRagQueryPlanner(Builder builder) {
        if (builder.modelClient == null) {
            throw new IllegalArgumentException("modelClient must not be null");
        }
        String normalizedModel = trimToNull(builder.model);
        if (normalizedModel == null) {
            throw new IllegalArgumentException("model must not be blank");
        }
        this.modelClient = builder.modelClient;
        this.model = normalizedModel;
        this.rewriteEnabled = builder.rewriteEnabled;
        this.multiQueryEnabled = builder.multiQueryEnabled;
        this.hydeEnabled = builder.hydeEnabled;
        this.maxVariants = Math.max(1, builder.maxVariants);
        this.maxHistoryItems = Math.max(0, builder.maxHistoryItems);
        this.temperature = builder.temperature;
        this.maxOutputTokens = Math.max(1, builder.maxOutputTokens);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public RagQueryPlan plan(RagQuery query) {
        if (query == null) {
            throw new IllegalArgumentException("query must not be null");
        }
        String normalizedQuery = collapseWhitespace(query.getQuery());
        if (normalizedQuery == null) {
            return RagQueryPlan.fallback("", "blank_query");
        }
        if (!rewriteEnabled && !multiQueryEnabled && !hydeEnabled) {
            return RagQueryPlan.of(normalizedQuery, Collections.singletonList(RagQueryVariant.original(normalizedQuery)));
        }

        try {
            ModelRequest.Builder requestBuilder = ModelRequest.builder()
                    .model(model)
                    .message(Message.system().text(buildSystemPrompt()).build())
                    .message(Message.user().text(buildUserPrompt(query, normalizedQuery)).build())
                    .temperature(Double.valueOf(temperature))
                    .maxOutputTokens(Integer.valueOf(maxOutputTokens));
            if (modelClient.endpoint() != null && modelClient.endpoint().getProtocol() == Protocol.CHAT_COMPLETIONS) {
                requestBuilder.providerOption("response_format", Collections.singletonMap("type", "json_object"));
            }

            ModelResult result = modelClient.create(requestBuilder.build());
            String raw = extractAssistantText(result);
            if (raw == null) {
                return RagQueryPlan.fallback(normalizedQuery, "empty_planner_output");
            }
            return parsePlan(normalizedQuery, raw);
        } catch (Exception e) {
            log.debug("ModelClientRagQueryPlanner failed; falling back to original query", e);
            String message = trimToNull(e.getMessage());
            return RagQueryPlan.fallback(
                    normalizedQuery,
                    message == null ? "planner_error" : "planner_error: " + message);
        }
    }

    private RagQueryPlan parsePlan(String originalQuery, String rawOutput) {
        JsonNode root;
        try {
            root = JsonCodec.readTreeLenient(rawOutput);
        } catch (JsonCodecException ignored) {
            return RagQueryPlan.fallback(originalQuery, "unparseable_planner_output");
        }
        if (root == null || !root.isObject()) {
            return RagQueryPlan.fallback(originalQuery, "unparseable_planner_output");
        }

        List<RagQueryVariant> variants = new ArrayList<RagQueryVariant>();
        Set<String> seen = new LinkedHashSet<String>();
        addVariant(variants, seen, RagQueryVariant.original(originalQuery));

        if (rewriteEnabled) {
            String rewrite = textField(root, "rewrite", "rewrittenQuery", "rewritten_query");
            if (rewrite != null) {
                addVariant(variants, seen, RagQueryVariant.of(rewrite, RagQueryVariantType.REWRITE));
            }
        }

        if (multiQueryEnabled) {
            JsonNode queriesNode = firstArray(root, "multiQueries", "multi_queries", "queries");
            if (queriesNode != null) {
                for (JsonNode item : queriesNode) {
                    if (item != null && item.isTextual()) {
                        addVariant(variants, seen, RagQueryVariant.of(item.asText(), RagQueryVariantType.MULTI_QUERY));
                    }
                }
            }
        }

        if (hydeEnabled) {
            String hyde = textField(root, "hyde", "hypotheticalDocument", "hypothetical_document");
            if (hyde != null) {
                addVariant(variants, seen, RagQueryVariant.of(hyde, RagQueryVariantType.HYDE));
            }
        }

        JsonNode extraVariants = firstArray(root, "variants");
        if (extraVariants != null) {
            for (JsonNode item : extraVariants) {
                if (item == null || !item.isObject()) {
                    continue;
                }
                String text = textField(item, "query", "text");
                if (text == null) {
                    continue;
                }
                RagQueryVariantType type = resolveType(textField(item, "type"));
                if (isTypeEnabled(type)) {
                    addVariant(variants, seen, RagQueryVariant.of(text, type));
                }
            }
        }

        return RagQueryPlan.of(originalQuery, variants);
    }

    private void addVariant(List<RagQueryVariant> variants, Set<String> seen, RagQueryVariant candidate) {
        if (candidate == null || variants.size() >= maxVariants) {
            return;
        }
        String normalized = collapseWhitespace(candidate.getQuery());
        if (normalized == null) {
            return;
        }
        String key = normalized.toLowerCase(Locale.ROOT);
        if (!seen.add(key)) {
            return;
        }
        variants.add(RagQueryVariant.of(normalized, candidate.getType()));
    }

    private boolean isTypeEnabled(RagQueryVariantType type) {
        if (type == RagQueryVariantType.REWRITE) {
            return rewriteEnabled;
        }
        if (type == RagQueryVariantType.MULTI_QUERY) {
            return multiQueryEnabled;
        }
        if (type == RagQueryVariantType.HYDE) {
            return hydeEnabled;
        }
        return true;
    }

    private static RagQueryVariantType resolveType(String rawType) {
        if (rawType == null) {
            return RagQueryVariantType.CUSTOM;
        }
        String normalized = rawType.trim().toUpperCase(Locale.ROOT);
        for (RagQueryVariantType candidate : RagQueryVariantType.values()) {
            if (candidate.name().equals(normalized)) {
                return candidate;
            }
        }
        return RagQueryVariantType.CUSTOM;
    }

    private String buildSystemPrompt() {
        return "You are a RAG retrieval query planner. "
                + "Generate retrieval queries based strictly on the user's question and conversation context. "
                + "Return JSON only without markdown fences using keys: "
                + "{\"rewrite\": string or null, \"multiQueries\": string[], \"hyde\": string or null}.";
    }

    private String buildUserPrompt(RagQuery query, String normalizedQuery) {
        StringBuilder sb = new StringBuilder();
        sb.append("Original query: ").append(normalizedQuery).append('\n');
        sb.append("Enabled strategies: ");
        List<String> strategies = new ArrayList<String>();
        if (rewriteEnabled) {
            strategies.add("rewrite (contextualized standalone query)");
        }
        if (multiQueryEnabled) {
            strategies.add("multiQueries (up to " + maxVariants + " diverse sub-queries)");
        }
        if (hydeEnabled) {
            strategies.add("hyde (concise hypothetical answer passage)");
        }
        for (int i = 0; i < strategies.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(strategies.get(i));
        }
        sb.append('\n');

        List<ChatMemoryItem> history = query.getHistory();
        if (history != null && !history.isEmpty() && maxHistoryItems > 0) {
            int start = Math.max(0, history.size() - maxHistoryItems);
            boolean headerWritten = false;
            for (int i = start; i < history.size(); i++) {
                ChatMemoryItem item = history.get(i);
                if (item == null || trimToNull(item.getText()) == null) {
                    continue;
                }
                if (!headerWritten) {
                    sb.append("Recent conversation history:\n");
                    headerWritten = true;
                }
                String label = item.isSummary()
                        ? "summary"
                        : (trimToNull(item.getRole()) == null ? "turn" : item.getRole().trim());
                sb.append("- ").append(label).append(": ").append(collapseWhitespace(item.getText())).append('\n');
            }
        }
        sb.append("Return JSON only.");
        return sb.toString();
    }

    static String extractAssistantText(ModelResult result) {
        if (result == null || result.getItems() == null) {
            return null;
        }
        String fallback = null;
        for (ModelItem item : result.getItems()) {
            if (!(item instanceof Message)) {
                continue;
            }
            Message message = (Message) item;
            String text = trimToNull(message.textContent());
            if (text == null) {
                continue;
            }
            if (message.getRole() == MessageRole.ASSISTANT) {
                return text;
            }
            if (fallback == null) {
                fallback = text;
            }
        }
        return fallback;
    }

    private static String textField(JsonNode node, String... names) {
        if (node == null || names == null) {
            return null;
        }
        for (String name : names) {
            JsonNode child = node.get(name);
            if (child != null && child.isTextual()) {
                String value = trimToNull(child.asText());
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    private static JsonNode firstArray(JsonNode node, String... names) {
        if (node == null || names == null) {
            return null;
        }
        for (String name : names) {
            JsonNode child = node.get(name);
            if (child != null && child.isArray()) {
                return child;
            }
        }
        return null;
    }

    private static String collapseWhitespace(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        return trimmed.replaceAll("\\s+", " ");
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
        private boolean rewriteEnabled = true;
        private boolean multiQueryEnabled = false;
        private boolean hydeEnabled = false;
        private int maxVariants = 4;
        private int maxHistoryItems = 6;
        private double temperature = 0.0;
        private int maxOutputTokens = 512;

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

        public Builder rewriteEnabled(boolean rewriteEnabled) {
            this.rewriteEnabled = rewriteEnabled;
            return this;
        }

        public Builder multiQueryEnabled(boolean multiQueryEnabled) {
            this.multiQueryEnabled = multiQueryEnabled;
            return this;
        }

        public Builder hydeEnabled(boolean hydeEnabled) {
            this.hydeEnabled = hydeEnabled;
            return this;
        }

        public Builder maxVariants(int maxVariants) {
            this.maxVariants = maxVariants;
            return this;
        }

        public Builder maxHistoryItems(int maxHistoryItems) {
            this.maxHistoryItems = maxHistoryItems;
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

        public ModelClientRagQueryPlanner build() {
            return new ModelClientRagQueryPlanner(this);
        }
    }
}
