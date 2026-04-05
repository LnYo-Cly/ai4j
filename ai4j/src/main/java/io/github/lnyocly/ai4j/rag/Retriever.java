package io.github.lnyocly.ai4j.rag;

import java.util.List;

public interface Retriever {

    List<RagHit> retrieve(RagQuery query) throws Exception;

    default String retrieverSource() {
        String simpleName = getClass().getSimpleName();
        if (simpleName == null || simpleName.trim().isEmpty()) {
            return "retriever";
        }
        return simpleName;
    }
}
