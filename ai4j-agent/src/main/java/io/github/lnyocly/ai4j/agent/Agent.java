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

    /**
     * Returns the immutable-by-convention base context used by this Agent.
     * Harness integrations use {@link AgentContext#toBuilder()} to create a
     * per-execution overlay; callers should not mutate the returned context in
     * place while a run is active.
     */
    public AgentContext getContext() {
        return baseContext;
    }

    public void runStream(AgentRequest request, AgentListener listener) throws Exception {
        runtime.runStream(baseContext, request, listener);
    }

    public AgentResult runStreamResult(AgentRequest request, AgentListener listener) throws Exception {
        return runtime.runStreamResult(baseContext, request, listener);
    }

    public AgentSession newSession() {
        return newSession(baseContext, AgentSessionMetadata.create(), null);
    }

    /**
     * Creates a session from a context overlay while retaining the Agent's
     * configured memory supplier and runtime. This is intentionally additive:
     * existing callers continue to use {@link #newSession()}.
     */
    public AgentSession newSessionWithContext(AgentContext context) {
        return newSession(context == null ? baseContext : context, AgentSessionMetadata.create(), null);
    }

    /**
     * Creates a fresh session with a host-selected stable identity. This is
     * useful for business conversations whose id is owned by the application;
     * it does not bind that identity to a Harness task.
     */
    public AgentSession newSessionWithIdentity(String sessionId,
                                               String runId,
                                               AgentContext context) {
        AgentSessionMetadata metadata = new AgentSessionMetadata(sessionId, 0L, 0L, null);
        return newSession(context == null ? baseContext : context, metadata, runId);
    }

    private AgentSession newSession(AgentContext context, AgentSessionMetadata metadata, String runId) {
        AgentContext sourceContext = context == null ? baseContext : context;
        if (sourceContext == null) {
            throw new IllegalStateException("agent context is required");
        }
        AgentMemory memory = memorySupplier == null ? sourceContext.getMemory() : memorySupplier.get();
        AgentSessionMetadata sessionMetadata = metadata == null ? AgentSessionMetadata.create() : metadata.copy();
        AgentSessionEventLog eventLog = new InMemoryAgentSessionEventLog();
        AgentContext sessionContext = sourceContext.toBuilder()
                .memory(memory)
                .sessionId(sessionMetadata.getSessionId())
                .eventPublisher(sessionEventPublisher(eventLog, sourceContext))
                .build();
        return new AgentSession(runtime, sessionContext, sessionMetadata, eventLog, sessionStore, runId);
    }

    public AgentSession newSession(AgentSessionSnapshot snapshot) {
        return newSession(snapshot, baseContext);
    }

    /**
     * Restores a session using a per-execution context overlay. The snapshot
     * remains the source of session state; the overlay only supplies runtime
     * facilities such as Harness tools, listeners, and bounded options.
     */
    public AgentSession newSession(AgentSessionSnapshot snapshot, AgentContext context) {
        AgentSession session = newSession(
                context == null ? baseContext : context,
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

    private AgentEventPublisher sessionEventPublisher(final AgentSessionEventLog eventLog,
                                                       AgentContext sourceContext) {
        AgentEventPublisher basePublisher = sourceContext == null ? null : sourceContext.getEventPublisher();
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
