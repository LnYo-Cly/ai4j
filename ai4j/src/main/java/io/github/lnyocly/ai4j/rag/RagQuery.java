package io.github.lnyocly.ai4j.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import io.github.lnyocly.ai4j.memory.ChatMemoryItem;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagQuery {

    private String query;

    private String dataset;

    private String embeddingModel;

    @Builder.Default
    private Integer topK = 5;

    private Integer finalTopK;

    private Map<String, Object> filter;

    /**
     * Optional conversation history for conversational RAG query rewrite.
     */
    private List<ChatMemoryItem> history;

    @Builder.Default
    private String delimiter = "\n\n";

    @Builder.Default
    private boolean includeCitations = true;

    @Builder.Default
    private boolean includeTrace = true;
}
