package io.github.lnyocly.ai4j.agent.memory;

import java.util.List;

public interface AgentMemory {

    void addUserInput(Object input);

    void addOutputItems(List<Object> items);

    void addToolOutput(String callId, String output);

    List<Object> getItems();

    String getSummary();

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
    }

    void clear();
}
