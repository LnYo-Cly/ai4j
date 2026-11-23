package io.github.lnyocly.ai4j.rag;

import io.github.lnyocly.ai4j.model.Message;
import io.github.lnyocly.ai4j.model.MessageRole;
import io.github.lnyocly.ai4j.model.ModelItem;
import io.github.lnyocly.ai4j.model.ModelRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Projects {@link RagResult} or {@link RagContext} into {@link Message} items and
 * immutable {@link ModelRequest} instances with non-wire citation metadata stored on
 * {@link Message#getApplicationMetadata()}.
 */
public class RagContextProjector {

    public static final String META_RAG_QUERY = "rag.query";
    public static final String META_RAG_HIT_COUNT = "rag.hitCount";
    public static final String META_RAG_CONTEXT_CHARS = "rag.contextChars";
    public static final String META_RAG_CITATIONS = "rag.citations";

    private static final String DEFAULT_INSTRUCTION =
            "Answer using the grounded context below when relevant and preserve citation ids when cited.";

    private final String instructionHeader;
    private final int maxContextChars;
    private final int maxCitations;
    private final boolean injectAsSystemMessage;

    public RagContextProjector() {
        this(builder());
    }

    private RagContextProjector(Builder builder) {
        this.instructionHeader = trimToNull(builder.instructionHeader) == null
                ? DEFAULT_INSTRUCTION
                : builder.instructionHeader.trim();
        this.maxContextChars = builder.maxContextChars;
        this.maxCitations = builder.maxCitations;
        this.injectAsSystemMessage = builder.injectAsSystemMessage;
    }

    public static Builder builder() {
        return new Builder();
    }

    public ModelRequest project(ModelRequest request, RagService ragService) throws Exception {
        return project(request, ragService, null);
    }

    public ModelRequest project(ModelRequest request, RagService ragService, RagQuery queryOverride) throws Exception {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        if (ragService == null) {
            throw new IllegalArgumentException("ragService must not be null");
        }
        RagQuery resolvedQuery = queryOverride;
        if (resolvedQuery == null) {
            String userQuery = extractLatestUserQuery(request);
            if (userQuery == null) {
                return request;
            }
            resolvedQuery = RagQuery.builder().query(userQuery).build();
        }
        RagResult result = ragService.search(resolvedQuery);
        return project(request, result);
    }

    public ModelRequest project(ModelRequest request, RagResult result) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        if (result == null) {
            return request;
        }
        int hitCount = result.getHits() == null ? 0 : result.getHits().size();
        Message contextMessage = projectMessage(result.getQuery(), result.getContext(), result.getCitations(), hitCount);
        if (contextMessage == null) {
            return request;
        }
        return insertProjectedMessage(request, contextMessage);
    }

    public ModelRequest project(ModelRequest request, String query, RagContext context) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        if (context == null) {
            return request;
        }
        Message contextMessage = projectMessage(
                query,
                context.getText(),
                context.getCitations(),
                context.getCitations() == null ? 0 : context.getCitations().size());
        if (contextMessage == null) {
            return request;
        }
        return insertProjectedMessage(request, contextMessage);
    }

    public Message projectMessage(RagResult result) {
        if (result == null) {
            return null;
        }
        int hitCount = result.getHits() == null ? 0 : result.getHits().size();
        return projectMessage(result.getQuery(), result.getContext(), result.getCitations(), hitCount);
    }

    public Message projectMessage(String query, String contextText, List<RagCitation> citations, int hitCount) {
        String normalizedContext = trimToNull(contextText);
        List<Map<String, Object>> citationMetadata = toCitationMetadata(citations);
        if (normalizedContext == null && citationMetadata.isEmpty()) {
            return null;
        }

        String renderedBody = buildRenderedBody(normalizedContext, citations);
        Map<String, Object> metadata = new LinkedHashMap<String, Object>();
        if (trimToNull(query) != null) {
            metadata.put(META_RAG_QUERY, query.trim());
        }
        metadata.put(META_RAG_HIT_COUNT, Integer.valueOf(Math.max(0, hitCount)));
        metadata.put(META_RAG_CONTEXT_CHARS, Integer.valueOf(renderedBody.length()));
        if (!citationMetadata.isEmpty()) {
            metadata.put(META_RAG_CITATIONS, citationMetadata);
        }

        MessageRole targetRole = injectAsSystemMessage ? MessageRole.SYSTEM : MessageRole.USER;
        return Message.builder(targetRole)
                .text(renderedBody)
                .applicationMetadata(metadata)
                .build();
    }

    public List<Map<String, Object>> toCitationMetadata(List<RagCitation> citations) {
        if (citations == null || citations.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        int limit = maxCitations < 0 ? Integer.MAX_VALUE : maxCitations;
        for (RagCitation citation : citations) {
            if (citation == null) {
                continue;
            }
            if (rows.size() >= limit) {
                break;
            }
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            putIfNotBlank(row, "citationId", citation.getCitationId());
            putIfNotBlank(row, "sourceName", citation.getSourceName());
            putIfNotBlank(row, "sourcePath", citation.getSourcePath());
            putIfNotBlank(row, "sourceUri", citation.getSourceUri());
            if (citation.getPageNumber() != null) {
                row.put("pageNumber", citation.getPageNumber());
            }
            putIfNotBlank(row, "sectionTitle", citation.getSectionTitle());
            putIfNotBlank(row, "snippet", citation.getSnippet());
            if (!row.isEmpty()) {
                rows.add(Collections.unmodifiableMap(row));
            }
        }
        return rows.isEmpty()
                ? Collections.<Map<String, Object>>emptyList()
                : Collections.unmodifiableList(rows);
    }

    public static String extractLatestUserQuery(ModelRequest request) {
        if (request == null || request.getItems() == null) {
            return null;
        }
        List<ModelItem> items = request.getItems();
        for (int i = items.size() - 1; i >= 0; i--) {
            ModelItem item = items.get(i);
            if (!(item instanceof Message)) {
                continue;
            }
            Message message = (Message) item;
            if (message.getRole() == MessageRole.USER) {
                String text = trimToNull(message.textContent());
                if (text != null) {
                    return text;
                }
            }
        }
        return null;
    }

    private String buildRenderedBody(String normalizedContext, List<RagCitation> citations) {
        String body = normalizedContext;
        if (body == null) {
            StringBuilder fallback = new StringBuilder();
            if (citations != null) {
                for (RagCitation citation : citations) {
                    if (citation == null || trimToNull(citation.getSnippet()) == null) {
                        continue;
                    }
                    if (fallback.length() > 0) {
                        fallback.append('\n');
                    }
                    if (trimToNull(citation.getCitationId()) != null) {
                        fallback.append('[').append(citation.getCitationId().trim()).append("] ");
                    }
                    fallback.append(citation.getSnippet().trim());
                }
            }
            body = fallback.toString();
        }
        if (maxContextChars > 0 && body.length() > maxContextChars) {
            body = body.substring(0, maxContextChars);
        }
        return instructionHeader + "\n\n" + body;
    }

    private ModelRequest insertProjectedMessage(ModelRequest request, Message contextMessage) {
        List<ModelItem> sourceItems = request.getItems();
        List<ModelItem> projectedItems = new ArrayList<ModelItem>(sourceItems.size() + 1);
        int insertionIndex = resolveInsertionIndex(sourceItems);
        for (int i = 0; i < sourceItems.size(); i++) {
            if (i == insertionIndex) {
                projectedItems.add(contextMessage);
            }
            projectedItems.add(sourceItems.get(i));
        }
        if (insertionIndex >= sourceItems.size()) {
            projectedItems.add(contextMessage);
        }

        return ModelRequest.builder()
                .protocol(request.getProtocol())
                .model(request.getModel())
                .items(projectedItems)
                .tools(request.getTools())
                .toolChoice(request.getToolChoice())
                .parallelToolCalls(request.getParallelToolCalls())
                .maxOutputTokens(request.getMaxOutputTokens())
                .temperature(request.getTemperature())
                .topP(request.getTopP())
                .stream(request.getStream())
                .applicationMetadata(request.getApplicationMetadata())
                .providerOptions(request.getProviderOptions())
                .execution(request.getExecution())
                .replayable(Boolean.valueOf(request.isReplayable()))
                .build();
    }

    private int resolveInsertionIndex(List<ModelItem> items) {
        if (injectAsSystemMessage) {
            int idx = 0;
            while (idx < items.size()) {
                ModelItem item = items.get(idx);
                if (item instanceof Message && ((Message) item).getRole() == MessageRole.SYSTEM) {
                    idx++;
                } else {
                    break;
                }
            }
            return idx;
        }
        for (int i = items.size() - 1; i >= 0; i--) {
            ModelItem item = items.get(i);
            if (item instanceof Message && ((Message) item).getRole() == MessageRole.USER) {
                return i;
            }
        }
        return items.size();
    }

    private static void putIfNotBlank(Map<String, Object> target, String key, String value) {
        String normalized = trimToNull(value);
        if (normalized != null) {
            target.put(key, normalized);
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static final class Builder {

        private String instructionHeader = DEFAULT_INSTRUCTION;
        private int maxContextChars = 12000;
        private int maxCitations = 16;
        private boolean injectAsSystemMessage = true;

        private Builder() {
        }

        public Builder instructionHeader(String instructionHeader) {
            this.instructionHeader = instructionHeader;
            return this;
        }

        public Builder maxContextChars(int maxContextChars) {
            this.maxContextChars = maxContextChars;
            return this;
        }

        public Builder maxCitations(int maxCitations) {
            this.maxCitations = maxCitations;
            return this;
        }

        public Builder injectAsSystemMessage(boolean injectAsSystemMessage) {
            this.injectAsSystemMessage = injectAsSystemMessage;
            return this;
        }

        public RagContextProjector build() {
            return new RagContextProjector(this);
        }
    }
}
