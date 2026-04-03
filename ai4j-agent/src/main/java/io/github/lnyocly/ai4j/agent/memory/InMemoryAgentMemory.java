package io.github.lnyocly.ai4j.agent.memory;

import io.github.lnyocly.ai4j.agent.util.AgentInputItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class InMemoryAgentMemory implements AgentMemory {

    private final List<Object> items = new ArrayList<>();
    private String summary;
    private MemoryCompressor compressor;

    public InMemoryAgentMemory() {
    }

    public InMemoryAgentMemory(MemoryCompressor compressor) {
        this.compressor = compressor;
    }

    public void setCompressor(MemoryCompressor compressor) {
        this.compressor = compressor;
    }

    @Override
    public void addUserInput(Object input) {
        if (input == null) {
            return;
        }
        if (input instanceof String) {
            items.add(AgentInputItem.userMessage((String) input));
        } else {
            items.add(input);
        }
        maybeCompress();
    }

    @Override
    public void addOutputItems(List<Object> outputItems) {
        if (outputItems == null || outputItems.isEmpty()) {
            return;
        }
        items.addAll(outputItems);
        maybeCompress();
    }

    @Override
    public void addToolOutput(String callId, String output) {
        if (callId == null) {
            return;
        }
        items.add(AgentInputItem.functionCallOutput(callId, output));
        maybeCompress();
    }

    @Override
    public List<Object> getItems() {
        if (summary == null || summary.trim().isEmpty()) {
            return new ArrayList<>(items);
        }
        List<Object> merged = new ArrayList<>();
        merged.add(AgentInputItem.systemMessage(summary));
        merged.addAll(items);
        return merged;
    }

    @Override
    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public MemorySnapshot snapshot() {
        return MemorySnapshot.from(items, summary);
    }

    public void restore(MemorySnapshot snapshot) {
        items.clear();
        if (snapshot != null && snapshot.getItems() != null) {
            items.addAll(snapshot.getItems());
        }
        summary = snapshot == null ? null : snapshot.getSummary();
    }

    @Override
    public void clear() {
        items.clear();
        summary = null;
    }

    private void maybeCompress() {
        if (compressor == null) {
            return;
        }
        MemorySnapshot snapshot = compressor.compress(MemorySnapshot.from(items, summary));
        items.clear();
        if (snapshot.getItems() != null) {
            items.addAll(snapshot.getItems());
        }
        summary = snapshot.getSummary();
    }
}
