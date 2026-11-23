package io.github.lnyocly.ai4j.dx;

import io.github.lnyocly.ai4j.model.ModelItem;

import java.util.List;

/**
 * Session-scoped conversation history store for {@link AiClient} and {@link AiService}.
 */
public interface AiMemoryStore {

    List<ModelItem> load(String sessionId);

    void append(String sessionId, List<ModelItem> items);

    void save(String sessionId, List<ModelItem> items);

    void clear(String sessionId);
}
