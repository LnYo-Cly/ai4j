package io.github.lnyocly.ai4j.agent.memory;

import java.util.List;

public interface AgentMemory {

    void addUserInput(Object input);

    void addOutputItems(List<Object> items);

    void addToolOutput(String callId, String output);

    List<Object> getItems();

    String getSummary();

    void clear();
}
