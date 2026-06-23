package io.github.lnyocly.ai4j.agent;

import io.github.lnyocly.ai4j.agent.event.AgentEvent;
import io.github.lnyocly.ai4j.agent.event.AgentEventPublisher;
import io.github.lnyocly.ai4j.agent.event.AgentListener;
import io.github.lnyocly.ai4j.agent.memory.AgentMemory;
import io.github.lnyocly.ai4j.agent.session.AgentSessionEventLog;
import io.github.lnyocly.ai4j.agent.session.AgentSessionMetadata;
import io.github.lnyocly.ai4j.agent.session.AgentSessionSnapshot;
import io.github.lnyocly.ai4j.agent.session.AgentSessionStore;
import io.github.lnyocly.ai4j.agent.session.InMemoryAgentSessionEventLog;

import java.util.List;
import java.util.function.Supplier;

public class Agent {

    private final AgentRuntime runtime;
    private final AgentContext baseContext;
    private final Supplier<AgentMemory> memorySupplier;
    private final AgentSessionStore sessionStore;

    public Agent(AgentRuntime runtime, AgentContext baseContext, Supplier<AgentMemory> memorySupplier) {
        this(runtime, baseContext, memorySupplier, null);
    }

    public Agent(AgentRuntime runtime, AgentContext baseContext, Supplier<AgentMemory> memorySupplier, AgentSessionStore sessionStore) {
        this.runtime = runtime;
        this.baseContext = baseContext;
        this.memorySupplier = memorySupplier;
        this.sessionStore = sessionStore;
    }

    public AgentResult run(AgentRequest request) throws Exception {
        return runtime.run(baseContext, request);
    }

    public void runStream(AgentRequest request, AgentListener listener) throws Exception {
        runtime.runStream(baseContext, request, listener);
    }

    public AgentResult runStreamResult(AgentRequest request, AgentListener listener) throws Exception {
        return runtime.runStreamResult(baseContext, request, listener);
    }

    public AgentSession newSession() {
        return newSession(AgentSessionMetadata.create(), null);
    }

    private AgentSession newSession(AgentSessionMetadata metadata, String runId) {
        AgentMemory memory = memorySupplier == null ? baseContext.getMemory() : memorySupplier.get();
        AgentSessionMetadata sessionMetadata = metadata == null ? AgentSessionMetadata.create() : metadata.copy();
        AgentSessionEventLog eventLog = new InMemoryAgentSessionEventLog();
        AgentContext sessionContext = baseContext.toBuilder()
                .memory(memory)
                .sessionId(sessionMetadata.getSessionId())
                .eventPublisher(sessionEventPublisher(eventLog))
                .build();
        return new AgentSession(runtime, sessionContext, sessionMetadata, eventLog, sessionStore, runId);
    }

    public AgentSession newSession(AgentSessionSnapshot snapshot) {
        AgentSession session = newSession(
                snapshot == null ? null : snapshot.getMetadata(),
                snapshot == null ? null : snapshot.getRunId()
        );
        session.restore(snapshot);
        return session;
    }

    public AgentSession resumeSession(String sessionId) {
        if (sessionStore == null) {
            throw new IllegalStateException("sessionStore is required to resume a session by id");
        }
        AgentSessionSnapshot snapshot = sessionStore.load(sessionId);
        if (snapshot == null) {
            throw new IllegalArgumentException("Agent session not found: " + sessionId);
        }
        return newSession(snapshot);
    }

    public AgentSessionStore getSessionStore() {
        return sessionStore;
    }

    private AgentEventPublisher sessionEventPublisher(final AgentSessionEventLog eventLog) {
        AgentEventPublisher basePublisher = baseContext == null ? null : baseContext.getEventPublisher();
        List<AgentListener> baseListeners = basePublisher == null ? null : basePublisher.getListeners();
        AgentEventPublisher publisher = new AgentEventPublisher(baseListeners);
        publisher.addListener(new AgentListener() {
            @Override
            public void onEvent(AgentEvent event) {
                eventLog.append(event);
            }
        });
        return publisher;
    }
}
