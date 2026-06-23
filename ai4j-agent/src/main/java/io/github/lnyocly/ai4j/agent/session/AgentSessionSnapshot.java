package io.github.lnyocly.ai4j.agent.session;

import io.github.lnyocly.ai4j.agent.compact.CompactResult;
import io.github.lnyocly.ai4j.agent.memory.MemorySnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * A portable snapshot of one AgentSession runtime state.
 */
public class AgentSessionSnapshot {

    private AgentSessionMetadata metadata;
    private MemorySnapshot memory;
    private List<AgentSessionEvent> events;
    private CompactResult compactResult;
    private AgentSessionSandboxBinding sandboxBinding;
    private String runId;

    public AgentSessionSnapshot() {
        this(null, null, null, null, null, null);
    }

    public AgentSessionSnapshot(AgentSessionMetadata metadata, MemorySnapshot memory, List<AgentSessionEvent> events) {
        this(metadata, memory, events, null, null, null);
    }

    public AgentSessionSnapshot(AgentSessionMetadata metadata, MemorySnapshot memory, List<AgentSessionEvent> events, CompactResult compactResult) {
        this(metadata, memory, events, compactResult, null, null);
    }

    public AgentSessionSnapshot(AgentSessionMetadata metadata,
                                MemorySnapshot memory,
                                List<AgentSessionEvent> events,
                                CompactResult compactResult,
                                AgentSessionSandboxBinding sandboxBinding) {
        this(metadata, memory, events, compactResult, sandboxBinding, null);
    }

    public AgentSessionSnapshot(AgentSessionMetadata metadata,
                                MemorySnapshot memory,
                                List<AgentSessionEvent> events,
                                CompactResult compactResult,
                                AgentSessionSandboxBinding sandboxBinding,
                                String runId) {
        this.metadata = metadata == null ? AgentSessionMetadata.create() : metadata.copy();
        this.memory = copyMemory(memory);
        this.events = copyEvents(events);
        this.compactResult = compactResult == null ? null : compactResult.copy();
        this.sandboxBinding = sandboxBinding == null ? null : sandboxBinding.copy();
        this.runId = trimToNull(runId);
    }

    public String getSessionId() {
        return metadata == null ? null : metadata.getSessionId();
    }

    public AgentSessionMetadata getMetadata() {
        return metadata == null ? null : metadata.copy();
    }

    public void setMetadata(AgentSessionMetadata metadata) {
        this.metadata = metadata == null ? AgentSessionMetadata.create() : metadata.copy();
    }

    public MemorySnapshot getMemory() {
        return copyMemory(memory);
    }

    public void setMemory(MemorySnapshot memory) {
        this.memory = copyMemory(memory);
    }

    public List<AgentSessionEvent> getEvents() {
        return copyEvents(events);
    }

    public void setEvents(List<AgentSessionEvent> events) {
        this.events = copyEvents(events);
    }

    public CompactResult getCompactResult() {
        return compactResult == null ? null : compactResult.copy();
    }

    public void setCompactResult(CompactResult compactResult) {
        this.compactResult = compactResult == null ? null : compactResult.copy();
    }

    public AgentSessionSandboxBinding getSandboxBinding() {
        return sandboxBinding == null ? null : sandboxBinding.copy();
    }

    public void setSandboxBinding(AgentSessionSandboxBinding sandboxBinding) {
        this.sandboxBinding = sandboxBinding == null ? null : sandboxBinding.copy();
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = trimToNull(runId);
    }

    private static MemorySnapshot copyMemory(MemorySnapshot source) {
        return source == null ? MemorySnapshot.from(new ArrayList<Object>(), null) : MemorySnapshot.from(source.getItems(), source.getSummary());
    }

    private static List<AgentSessionEvent> copyEvents(List<AgentSessionEvent> source) {
        List<AgentSessionEvent> copy = new ArrayList<AgentSessionEvent>();
        if (source != null) {
            for (AgentSessionEvent event : source) {
                if (event != null) {
                    copy.add(event.copy());
                }
            }
        }
        return copy;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
