package io.github.lnyocly.ai4j.agent.interceptor;

import io.github.lnyocly.ai4j.extension.lifecycle.AgentLifecycleEvent;

/**
 * Functional interface for an observe (side-effect) handler — the void notification shape, so the
 * {@link AgentHooks} facade can accept a lambda for {@code stop} / {@code preCompact} /
 * {@code sessionStart} / {@code sessionEnd} without implementing the two-method
 * {@code AgentLifecycleHook} ({@code name()} + {@code onEvent()}).
 */
@FunctionalInterface
public interface ObserveHook {
    void onEvent(AgentLifecycleEvent event);
}
