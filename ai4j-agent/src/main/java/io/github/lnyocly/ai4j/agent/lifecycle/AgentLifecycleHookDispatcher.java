package io.github.lnyocly.ai4j.agent.lifecycle;

import io.github.lnyocly.ai4j.agent.AgentContext;
import io.github.lnyocly.ai4j.agent.event.AgentEvent;
import io.github.lnyocly.ai4j.agent.event.AgentEventPublisher;
import io.github.lnyocly.ai4j.agent.event.AgentEventType;
import io.github.lnyocly.ai4j.extension.lifecycle.AgentLifecycleEvent;
import io.github.lnyocly.ai4j.extension.lifecycle.AgentLifecycleEventType;
import io.github.lnyocly.ai4j.extension.lifecycle.AgentLifecycleHook;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AgentLifecycleHookDispatcher {

    private final List<AgentLifecycleHook> hooks;

    public AgentLifecycleHookDispatcher(List<AgentLifecycleHook> hooks) {
        if (hooks == null || hooks.isEmpty()) {
            this.hooks = Collections.emptyList();
        } else {
            List<AgentLifecycleHook> copy = new ArrayList<AgentLifecycleHook>();
            for (AgentLifecycleHook hook : hooks) {
                if (hook != null) {
                    copy.add(hook);
                }
            }
            this.hooks = Collections.unmodifiableList(copy);
        }
    }

    public static AgentLifecycleHookDispatcher empty() {
        return new AgentLifecycleHookDispatcher(null);
    }

    public boolean isEmpty() {
        return hooks.isEmpty();
    }

    public List<AgentLifecycleHook> getHooks() {
        return hooks;
    }

    public void dispatch(AgentContext context,
                         AgentLifecycleEventType type,
                         String runtime,
                         int step,
                         String message,
                         Object payload) {
        if (type == null || hooks.isEmpty()) {
            return;
        }
        AgentLifecycleEvent event = AgentLifecycleEvent.builder(type)
                .runtime(runtime)
                .sessionId(context == null ? null : context.getSessionId())
                .step(step)
                .message(message)
                .payload(payload)
                .build();
        for (AgentLifecycleHook hook : hooks) {
            if (hook == null) {
                continue;
            }
            try {
                hook.onEvent(event);
            } catch (Exception ex) {
                publishHookError(context, hook, event, ex);
            }
        }
    }

    private void publishHookError(AgentContext context,
                                  AgentLifecycleHook hook,
                                  AgentLifecycleEvent event,
                                  Exception error) {
        if (context == null || context.getEventPublisher() == null) {
            return;
        }
        String hookName;
        try {
            hookName = hook == null ? "unknown" : hook.name();
        } catch (Exception ignored) {
            hookName = "unknown";
        }
        String message = "lifecycle hook failed: " + hookName;
        AgentEvent agentEvent = AgentEvent.builder()
                .type(AgentEventType.ERROR)
                .step(event == null || event.getStep() == null ? 0 : event.getStep())
                .message(message)
                .payload(new AgentLifecycleHookError(hookName, event, error))
                .build();
        AgentEventPublisher publisher = context.getEventPublisher();
        publisher.publish(agentEvent);
    }
}
