package io.github.lnyocly.ai4j.agent;

import io.github.lnyocly.ai4j.agent.compact.CompactPolicy;
import io.github.lnyocly.ai4j.agent.compact.CompactResult;
import io.github.lnyocly.ai4j.agent.compact.SessionCompactPlan;
import io.github.lnyocly.ai4j.agent.compact.SessionCompactReport;
import io.github.lnyocly.ai4j.agent.event.AgentListener;
import io.github.lnyocly.ai4j.agent.event.AgentEvent;
import io.github.lnyocly.ai4j.agent.event.AgentEventType;
import io.github.lnyocly.ai4j.agent.lifecycle.AgentLifecycleHookDispatcher;
import io.github.lnyocly.ai4j.agent.memory.AgentMemory;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSession;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxStatus;
import io.github.lnyocly.ai4j.agent.session.AgentSessionEventLog;
import io.github.lnyocly.ai4j.agent.session.AgentSessionMetadata;
import io.github.lnyocly.ai4j.agent.session.AgentSessionSandboxBinding;
import io.github.lnyocly.ai4j.agent.session.AgentSessionSnapshot;
import io.github.lnyocly.ai4j.agent.session.AgentSessionStore;
import io.github.lnyocly.ai4j.agent.session.InMemoryAgentSessionEventLog;

import java.util.Map;

import io.github.lnyocly.ai4j.extension.lifecycle.AgentLifecycleEventType;

public class AgentSession {

    private final AgentRuntime runtime;
    private final AgentContext context;
    private final AgentSessionMetadata metadata;
    private final AgentSessionEventLog eventLog;
    private final AgentSessionStore store;
    private CompactResult lastCompactResult;
    private AgentSessionSandboxBinding sandboxBinding;

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
                eventLog.getEvents(),
                lastCompactResult,
                sandboxBinding
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
        lastCompactResult = snapshot.getCompactResult();
        sandboxBinding = snapshot.getSandboxBinding();
        return this;
    }

    public AgentSession compact(CompactPolicy policy) {
        compactAndReport(policy);
        return this;
    }

    public SessionCompactReport compact(SessionCompactPlan plan) {
        if (plan == null) {
            return SessionCompactReport.skipped(getSessionId());
        }
        return compactAndReport(plan.toPolicy());
    }

    public SessionCompactReport compactAndReport(CompactPolicy policy) {
        if (policy == null) {
            return SessionCompactReport.skipped(getSessionId());
        }
        AgentMemory memory = context == null ? null : context.getMemory();
        if (memory == null) {
            return SessionCompactReport.skipped(getSessionId());
        }
        CompactResult result = policy.compact(memory.snapshot());
        if (result != null && result.getMemory() != null) {
            memory.restore(result.getMemory());
        }
        lastCompactResult = result == null ? null : result.copy();
        AgentLifecycleHookDispatcher dispatcher = context == null ? null : context.getLifecycleHooks();
        if (dispatcher != null) {
            dispatcher.dispatch(context, AgentLifecycleEventType.ON_COMPACT, "session", 0, "compact", lastCompactResult);
        }
        metadata.touch();
        return new SessionCompactReport(getSessionId(), result != null, lastCompactResult);
    }

    public CompactResult getLastCompactResult() {
        return lastCompactResult == null ? null : lastCompactResult.copy();
    }

    public AgentSession bindSandbox(SandboxSession sandboxSession) {
        return bindSandbox(AgentSessionSandboxBinding.from(sandboxSession));
    }

    public AgentSession bindSandbox(AgentSessionSandboxBinding binding) {
        sandboxBinding = binding == null ? null : binding.copy();
        metadata.touch();
        appendSessionEvent(AgentEventType.SANDBOX_BOUND, "sandbox bound", sandboxBinding);
        return this;
    }

    public AgentSession updateSandboxStatus(SandboxStatus status) {
        if (sandboxBinding == null) {
            return this;
        }
        sandboxBinding = sandboxBinding.withStatus(status);
        metadata.touch();
        appendSessionEvent(AgentEventType.SANDBOX_UPDATED, "sandbox updated", sandboxBinding);
        return this;
    }

    public AgentSession clearSandbox() {
        AgentSessionSandboxBinding previous = sandboxBinding;
        sandboxBinding = null;
        metadata.touch();
        appendSessionEvent(AgentEventType.SANDBOX_CLEARED, "sandbox cleared", previous);
        return this;
    }

    public AgentSessionSandboxBinding getSandboxBinding() {
        return sandboxBinding == null ? null : sandboxBinding.copy();
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

    private void appendSessionEvent(AgentEventType type, String message, Object payload) {
        if (eventLog == null || type == null) {
            return;
        }
        eventLog.append(AgentEvent.builder()
                .type(type)
                .step(0)
                .message(message)
                .payload(payload)
                .build());
    }
}
