package io.github.lnyocly.ai4j.dx;

import io.github.lnyocly.ai4j.model.ModelItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory {@link AiMemoryStore}.
 */
public final class InMemoryAiMemoryStore implements AiMemoryStore {

    private final Map<String, List<ModelItem>> sessions = new ConcurrentHashMap<String, List<ModelItem>>();

    @Override
    public List<ModelItem> load(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<ModelItem> list = sessions.get(sessionId);
        if (list == null) {
            return Collections.emptyList();
        }
        synchronized (list) {
            return Collections.unmodifiableList(new ArrayList<ModelItem>(list));
        }
    }

    @Override
    public void append(String sessionId, List<ModelItem> items) {
        if (sessionId == null || sessionId.trim().isEmpty() || items == null || items.isEmpty()) {
            return;
        }
        List<ModelItem> list = sessions.get(sessionId);
        if (list == null) {
            List<ModelItem> created = new ArrayList<ModelItem>();
            List<ModelItem> existing = sessions.putIfAbsent(sessionId, created);
            list = existing == null ? created : existing;
        }
        synchronized (list) {
            list.addAll(items);
        }
    }

    @Override
    public void save(String sessionId, List<ModelItem> items) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return;
        }
        List<ModelItem> copy = items == null ? new ArrayList<ModelItem>() : new ArrayList<ModelItem>(items);
        sessions.put(sessionId, copy);
    }

    @Override
    public void clear(String sessionId) {
        if (sessionId != null) {
            sessions.remove(sessionId);
        }
    }
}
