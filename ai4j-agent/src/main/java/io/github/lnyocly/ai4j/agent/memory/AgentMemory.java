package io.github.lnyocly.ai4j.agent.memory;

import java.util.List;

public interface AgentMemory {

    void addUserInput(Object input);

    void addOutputItems(List<Object> items);

    void addToolOutput(String callId, String output);

    List<Object> getItems();

    String getSummary();

    /**
     * Sets the compacted summary text. Implementations should store it so that subsequent
     * {@link #getSummary()} / {@link #snapshot()} calls return it. Default is a no-op so the
     * interface stays backwards-compatible with implementations that don't track a summary.
     */
    default void setSummary(String summary) {
        // no-op default; implementations that support compaction override this
    }

    default MemorySnapshot snapshot() {
        return MemorySnapshot.from(getItems(), getSummary());
    }

    default void restore(MemorySnapshot snapshot) {
        clear();
        if (snapshot == null) {
            return;
        }
        if (snapshot.getItems() != null) {
            addOutputItems(snapshot.getItems());
        }
        // Preserve the compacted summary so it survives a restore cycle.
        if (snapshot.getSummary() != null) {
            setSummary(snapshot.getSummary());
        }
    }

    void clear();
}
