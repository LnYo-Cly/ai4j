package io.github.lnyocly.ai4j.agent.runtime;

import io.github.lnyocly.ai4j.agent.event.AgentEventType;

public final class AgentToolExecutionScope {

    public interface EventEmitter {

        void emit(AgentEventType type, String message, Object payload);
    }

    public interface ScopeCallable<T> {

        T call() throws Exception;
    }

    private static final ThreadLocal<EventEmitter> CURRENT = new ThreadLocal<EventEmitter>();

    private AgentToolExecutionScope() {
    }

    public static <T> T runWithEmitter(EventEmitter emitter, ScopeCallable<T> callable) throws Exception {
        if (callable == null) {
            return null;
        }
        EventEmitter previous = CURRENT.get();
        if (emitter == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(emitter);
        }
        try {
            return callable.call();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    public static void emit(AgentEventType type, String message, Object payload) {
        EventEmitter emitter = CURRENT.get();
        if (emitter != null && type != null) {
            emitter.emit(type, message, payload);
        }
    }
}
