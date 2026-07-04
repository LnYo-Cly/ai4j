package io.github.lnyocly.ai4j.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletion;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletionResponse;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatMessage;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.Choice;
import io.github.lnyocly.ai4j.service.IChatService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM-backed RAG query planner for common pre-retrieval strategies.
 *
 * <p>This class only generates retrieval variants. DefaultRagService owns
 * execution, fusion, reranking, and context assembly.</p>
 */
public class ModelRagQueryPlanner implements RagQueryPlanner {

    private final IChatService chatService;
    private final String model;
    private final List<RagQueryVariantType> strategies;
    private final int maxVariants;
    private final boolean includeOriginal;
    private final ObjectMapper mapper = new ObjectMapper();

    public ModelRagQueryPlanner(IChatService chatService, String model) {
        this(chatService, model, null, 6, true);
    }

    public ModelRagQueryPlanner(IChatService chatService,
                                String model,
                                List<RagQueryVariantType> strategies,
                                Integer maxVariants,
                                Boolean includeOriginal) {
        if (chatService == null) {
            throw new IllegalArgumentException("chatService is required");
        }
        if (isBlank(model)) {
            throw new IllegalArgumentException("model is required");
        }
        this.chatService = chatService;
        this.model = model;
        this.strategies = normalizeStrategies(strategies);
        this.maxVariants = maxVariants == null || maxVariants <= 0 ? 6 : maxVariants;
        this.includeOriginal = includeOriginal == null || includeOriginal;
    }

    @Override
    public RagQueryPlan plan(RagQuery query) throws Exception {
        String originalQuery = query == null ? null : query.getQuery();
        if (isBlank(originalQuery)) {
            return RagQueryPlan.fallback(originalQuery, "query is blank");
        }
        List<RagQueryVariant> variants = new ArrayList<RagQueryVariant>();
        if (includeOriginal) {
            variants.add(RagQueryVariant.original(originalQuery));
        }

        boolean hasModelVariant = false;
        for (RagQueryVariantType strategy : strategies) {
            if (variants.size() >= maxVariants) {
                break;
            }
            ChatCompletionResponse response = chatService.chatCompletion(ChatCompletion.builder()
                    .model(model)
                    .message(ChatMessage.withSystem(systemPrompt(strategy)))
                    .message(ChatMessage.withUser(userPrompt(query, strategy, maxVariants - variants.size())))
                    .temperature(0f)
                    .maxCompletionTokens(800)
                    .responseFormat(jsonObjectResponseFormat())
                    .build());

            List<RagQueryVariant> modelVariants = parseVariants(originalQuery, extractContent(response));
            for (RagQueryVariant variant : modelVariants) {
                if (variant == null || variant.getType() != strategy || isBlank(variant.getQuery()) || contains(variants, variant.getQuery())) {
                    continue;
                }
                variants.add(variant);
                hasModelVariant = true;
                if (variants.size() >= maxVariants) {
                    break;
                }
            }
        }
        if (!hasModelVariant) {
            return RagQueryPlan.fallback(originalQuery, "model returned no usable query variant");
        }
        return RagQueryPlan.of(originalQuery, variants);
    }

    private List<RagQueryVariantType> normalizeStrategies(List<RagQueryVariantType> requested) {
        List<RagQueryVariantType> defaults = Collections.singletonList(RagQueryVariantType.REWRITE);
        List<RagQueryVariantType> source = requested == null || requested.isEmpty() ? defaults : requested;
        List<RagQueryVariantType> result = new ArrayList<RagQueryVariantType>();
        for (RagQueryVariantType type : source) {
            if (type == null || type == RagQueryVariantType.ORIGINAL || type == RagQueryVariantType.CUSTOM || result.contains(type)) {
                continue;
            }
            result.add(type);
        }
        return result.isEmpty() ? defaults : result;
    }

    private String systemPrompt(RagQueryVariantType strategy) {
        return "You are a RAG retrieval query planner. Return JSON only. "
                + "Run only the " + strategy.name() + " strategy. "
                + "Create retrieval text, not final user-facing answers. "
                + "Do not explain. Do not use markdown fences.";
    }

    private String userPrompt(RagQuery query, RagQueryVariantType strategy, int limit) {
        StringBuilder builder = new StringBuilder();
        builder.append("Original query:\n").append(query.getQuery()).append("\n\n");
        if (!isBlank(query.getDataset())) {
            builder.append("Dataset: ").append(query.getDataset()).append("\n");
        }
        builder.append("Max returned variants: ").append(Math.max(1, limit)).append("\n\n");
        if (strategy == RagQueryVariantType.REWRITE) {
            builder.append("Rewrite strategy: convert the original query into one standalone retrieval query.\n");
            builder.append("Return JSON: {\"rewrite\":\"standalone retrieval query\"}");
        } else if (strategy == RagQueryVariantType.MULTI_QUERY) {
            builder.append("Multi-query expansion strategy: create alternative retrieval queries for the same need.\n");
            builder.append("Return JSON: {\"multiQueries\":[\"alternative query 1\",\"alternative query 2\"]}");
        } else if (strategy == RagQueryVariantType.HYDE) {
            builder.append("HyDE strategy: write one short hypothetical answer/document paragraph for retrieval.\n");
            builder.append("Return JSON: {\"hyde\":\"short hypothetical document paragraph\"}");
        } else if (strategy == RagQueryVariantType.STEP_BACK) {
            builder.append("Step-back strategy: create one broader background retrieval query.\n");
            builder.append("Return JSON: {\"stepBack\":\"broader background retrieval query\"}");
        } else {
            builder.append("Return JSON: {\"variants\":[{\"type\":\"").append(strategy.name()).append("\",\"query\":\"retrieval query\"}]}");
        }
        builder.append("\nKeep it concise.");
        return builder.toString();
    }

    private Map<String, Object> jsonObjectResponseFormat() {
        Map<String, Object> responseFormat = new LinkedHashMap<String, Object>();
        responseFormat.put("type", "json_object");
        return responseFormat;
    }

    private String extractContent(ChatCompletionResponse response) {
        if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
            return null;
        }
        Choice choice = response.getChoices().get(0);
        if (choice == null || choice.getMessage() == null || choice.getMessage().getContent() == null) {
            return null;
        }
        return choice.getMessage().getContent().getText();
    }

    private List<RagQueryVariant> parseVariants(String originalQuery, String content) throws Exception {
        if (isBlank(content)) {
            return Collections.emptyList();
        }
        JsonNode root = mapper.readTree(extractJson(content));
        List<RagQueryVariant> variants = new ArrayList<RagQueryVariant>();
        parseVariantArray(root.get("variants"), variants);
        addText(root, variants, "rewrite", RagQueryVariantType.REWRITE);
        addText(root, variants, "rewrittenQuery", RagQueryVariantType.REWRITE);
        addText(root, variants, "rewritten_query", RagQueryVariantType.REWRITE);
        addArray(root, variants, "multiQueries", RagQueryVariantType.MULTI_QUERY);
        addArray(root, variants, "multi_queries", RagQueryVariantType.MULTI_QUERY);
        addArray(root, variants, "queries", RagQueryVariantType.MULTI_QUERY);
        addText(root, variants, "hyde", RagQueryVariantType.HYDE);
        addText(root, variants, "hypotheticalDocument", RagQueryVariantType.HYDE);
        addText(root, variants, "hypothetical_document", RagQueryVariantType.HYDE);
        addTextOrArray(root, variants, "stepBack", RagQueryVariantType.STEP_BACK);
        addTextOrArray(root, variants, "step_back", RagQueryVariantType.STEP_BACK);

        List<RagQueryVariant> deduped = new ArrayList<RagQueryVariant>();
        for (RagQueryVariant variant : variants) {
            if (variant == null || isBlank(variant.getQuery()) || contains(deduped, variant.getQuery())) {
                continue;
            }
            if (!includeOriginal && originalQuery != null && originalQuery.trim().equals(variant.getQuery().trim())) {
                continue;
            }
            deduped.add(variant);
        }
        return deduped;
    }

    private String extractJson(String content) {
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline >= 0 && lastFence > firstNewline) {
                trimmed = trimmed.substring(firstNewline + 1, lastFence).trim();
            }
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end >= start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    private void parseVariantArray(JsonNode node, List<RagQueryVariant> variants) {
        if (node == null || !node.isArray()) {
            return;
        }
        for (JsonNode item : node) {
            if (item == null) {
                continue;
            }
            if (item.isTextual()) {
                addVariant(variants, item.asText(), RagQueryVariantType.CUSTOM);
                continue;
            }
            String query = firstText(item, "query", "text", "content");
            RagQueryVariantType type = parseType(firstText(item, "type", "strategy"));
            addVariant(variants, query, type);
        }
    }

    private void addText(JsonNode root, List<RagQueryVariant> variants, String field, RagQueryVariantType type) {
        JsonNode node = root == null ? null : root.get(field);
        if (node != null && node.isTextual()) {
            addVariant(variants, node.asText(), type);
        }
    }

    private void addTextOrArray(JsonNode root, List<RagQueryVariant> variants, String field, RagQueryVariantType type) {
        JsonNode node = root == null ? null : root.get(field);
        if (node == null) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                if (item != null && item.isTextual()) {
                    addVariant(variants, item.asText(), type);
                }
            }
        } else if (node.isTextual()) {
            addVariant(variants, node.asText(), type);
        }
    }

    private void addArray(JsonNode root, List<RagQueryVariant> variants, String field, RagQueryVariantType type) {
        JsonNode node = root == null ? null : root.get(field);
        if (node == null || !node.isArray()) {
            return;
        }
        for (JsonNode item : node) {
            if (item != null && item.isTextual()) {
                addVariant(variants, item.asText(), type);
            }
        }
    }

    private void addVariant(List<RagQueryVariant> variants, String query, RagQueryVariantType type) {
        if (isBlank(query) || type == null || !strategies.contains(type)) {
            return;
        }
        variants.add(RagQueryVariant.of(query.trim(), type));
    }

    private String firstText(JsonNode node, String... fields) {
        if (node == null || fields == null) {
            return null;
        }
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && value.isTextual()) {
                return value.asText();
            }
        }
        return null;
    }

    private RagQueryVariantType parseType(String value) {
        if (isBlank(value)) {
            return RagQueryVariantType.CUSTOM;
        }
        String normalized = value.trim().replace('-', '_').replace(' ', '_').toUpperCase();
        if ("MULTI".equals(normalized) || "MULTIQUERY".equals(normalized)) {
            normalized = "MULTI_QUERY";
        }
        if ("STEPBACK".equals(normalized)) {
            normalized = "STEP_BACK";
        }
        try {
            return RagQueryVariantType.valueOf(normalized);
        } catch (Exception ignore) {
            return RagQueryVariantType.CUSTOM;
        }
    }

    private boolean contains(List<RagQueryVariant> variants, String query) {
        if (variants == null || isBlank(query)) {
            return false;
        }
        String normalized = query.trim();
        for (Iterator<RagQueryVariant> iterator = variants.iterator(); iterator.hasNext(); ) {
            RagQueryVariant variant = iterator.next();
            if (variant != null && variant.getQuery() != null && normalized.equals(variant.getQuery().trim())) {
                return true;
            }
        }
        return false;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

}
