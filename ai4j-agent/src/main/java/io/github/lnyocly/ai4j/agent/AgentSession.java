package io.github.lnyocly.ai4j.agent;

import io.github.lnyocly.ai4j.agent.event.AgentListener;
import io.github.lnyocly.ai4j.agent.memory.AgentMemory;
import io.github.lnyocly.ai4j.agent.session.AgentSessionEventLog;
import io.github.lnyocly.ai4j.agent.session.AgentSessionMetadata;
import io.github.lnyocly.ai4j.agent.session.AgentSessionSnapshot;
import io.github.lnyocly.ai4j.agent.session.AgentSessionStore;
import io.github.lnyocly.ai4j.agent.session.InMemoryAgentSessionEventLog;

import java.util.Map;

public class AgentSession {

    private final AgentRuntime runtime;
    private final AgentContext context;
    private final AgentSessionMetadata metadata;
    private final AgentSessionEventLog eventLog;
    private final AgentSessionStore store;

    public AgentSession(AgentRuntime runtime, AgentContext context) {
        this(runtime, context, AgentSessionMetadata.create(), new InMemoryAgentSessionEventLog(), null);
    }

    public AgentSession(AgentRuntime runtime,
                        AgentContext context,
                        AgentSessionMetadata metadata,
                        AgentSessionEventLog eventLog,
                        AgentSessionStore store) {
        this.runtime = runtime;
        this.context = context;
        this.metadata = metadata == null ? AgentSessionMetadata.create() : metadata;
        this.eventLog = eventLog == null ? new InMemoryAgentSessionEventLog() : eventLog;
        this.store = store;
    }

    public AgentResult run(String input) throws Exception {
        return run(AgentRequest.builder().input(input).build());
    }

    public AgentResult run(AgentRequest request) throws Exception {
        try {
            return runtime.run(context, request);
        } finally {
            metadata.touch();
        }
    }

    public void runStream(AgentRequest request, AgentListener listener) throws Exception {
        try {
            runtime.runStream(context, request, listener);
        } finally {
            metadata.touch();
        }
    }

    public AgentResult runStreamResult(AgentRequest request, AgentListener listener) throws Exception {
        try {
            return runtime.runStreamResult(context, request, listener);
        } finally {
            metadata.touch();
        }
    }

    public AgentContext getContext() {
        return context;
    }

    public AgentRuntime getRuntime() {
        return runtime;
    }

    public String getSessionId() {
        return metadata.getSessionId();
    }

    public AgentSessionMetadata getMetadata() {
        return metadata.copy();
    }

    public Map<String, Object> getMetadataAttributes() {
        return metadata.getAttributes();
    }

    public AgentSession putMetadata(String key, Object value) {
        metadata.putAttribute(key, value);
        return this;
    }

    public Object getMetadata(String key) {
        return metadata.getAttribute(key);
    }

    public AgentSessionEventLog getEventLog() {
        return eventLog;
    }

    public AgentSessionSnapshot snapshot() {
        AgentMemory memory = context == null ? null : context.getMemory();
        return new AgentSessionSnapshot(
                metadata,
                memory == null ? null : memory.snapshot(),
                eventLog.getEvents()
        );
    }

    public AgentSession restore(AgentSessionSnapshot snapshot) {
        if (snapshot == null) {
            return this;
        }
        AgentMemory memory = context == null ? null : context.getMemory();
        if (memory != null) {
            memory.restore(snapshot.getMemory());
        }
        AgentSessionMetadata restoredMetadata = snapshot.getMetadata();
        if (restoredMetadata != null) {
            metadata.setSessionId(restoredMetadata.getSessionId());
            metadata.setCreatedAtEpochMs(restoredMetadata.getCreatedAtEpochMs());
            metadata.setAttributes(restoredMetadata.getAttributes());
            metadata.setUpdatedAtEpochMs(restoredMetadata.getUpdatedAtEpochMs());
        }
        eventLog.restore(snapshot.getEvents());
        return this;
    }

    public AgentSession save() {
        if (store != null) {
            store.save(snapshot());
        }
        return this;
    }

    public AgentSessionStore getStore() {
        return store;
    }
}
