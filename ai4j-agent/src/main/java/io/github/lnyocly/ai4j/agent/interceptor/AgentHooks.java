package io.github.lnyocly.ai4j.agent.interceptor;

import io.github.lnyocly.ai4j.agent.AgentBuilder;
import io.github.lnyocly.ai4j.agent.AgentContext;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.extension.lifecycle.AgentLifecycleEvent;
import io.github.lnyocly.ai4j.extension.lifecycle.AgentLifecycleEventType;
import io.github.lnyocly.ai4j.extension.lifecycle.AgentLifecycleHook;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Named per-event facade over the typed interceptors — the pi-like "one place, all events,
 * discoverable" sugar. Each method accepts the right typed functional interface (compile-time
 * safe), and {@link #applyTo} composes them into the single interceptor slots the runtime exposes.
 *
 * <p>This is pure sugar: the underlying {@link ToolInterceptor} / {@link PromptInterceptor} /
 * {@code AgentLifecycleHook} stay the source of truth. Use via {@code AgentBuilder.hooks(...)}:</p>
 * <pre>
 * Agents.react()
 *     .hooks(h -&gt; h
 *         .preToolUse((call, ctx) -&gt; isDangerous(call) ? ToolCallDecision.block("no") : ToolCallDecision.allow())
 *         .postToolUse((call, out, ctx) -&gt; leaks(out) ? ToolCallDecision.block("secret") : ToolCallDecision.allow())
 *         .userPromptSubmit((input, ctx) -&gt; PromptDecision.allow())
 *         .stop(ev -&gt; metrics.turnEnd())
 *         .sessionStart(ev -&gt; log.info("session start")))
 *     ...
 * </pre>
 *
 * <p>Semantics: for interception events the first non-allow decision wins (pre/post/prompt); observe
 * handlers all run (side-effects). Pre and Post compose into one {@link ToolInterceptor} (the runtime
 * takes one), so you can register both without conflict.</p>
 */
public final class AgentHooks {

    private final List<ToolInterceptor> preToolUse = new ArrayList<ToolInterceptor>();
    private final List<PostToolUseHook> postToolUse = new ArrayList<PostToolUseHook>();
    private final List<PromptInterceptor> userPromptSubmit = new ArrayList<PromptInterceptor>();
    private final List<ModelRequestHook> modelRequestHooks = new ArrayList<ModelRequestHook>();
    private final Map<AgentLifecycleEventType, List<ObserveHook>> observe = new LinkedHashMap<AgentLifecycleEventType, List<ObserveHook>>();

    public AgentHooks preToolUse(ToolInterceptor hook) {
        if (hook != null) {
            preToolUse.add(hook);
        }
        return this;
    }

    public AgentHooks postToolUse(PostToolUseHook hook) {
        if (hook != null) {
            postToolUse.add(hook);
        }
        return this;
    }

    public AgentHooks userPromptSubmit(PromptInterceptor hook) {
        if (hook != null) {
            userPromptSubmit.add(hook);
        }
        return this;
    }

    public AgentHooks beforeModelRequest(ModelRequestHook hook) {
        if (hook != null) {
            modelRequestHooks.add(hook);
        }
        return this;
    }

    public AgentHooks stop(ObserveHook hook) {
        return observe(AgentLifecycleEventType.AFTER_TURN, hook);
    }

    public AgentHooks preCompact(ObserveHook hook) {
        return observe(AgentLifecycleEventType.ON_COMPACT, hook);
    }

    public AgentHooks sessionStart(ObserveHook hook) {
        return observe(AgentLifecycleEventType.SESSION_START, hook);
    }

    public AgentHooks sessionEnd(ObserveHook hook) {
        return observe(AgentLifecycleEventType.SESSION_END, hook);
    }

    private AgentHooks observe(AgentLifecycleEventType type, ObserveHook hook) {
        if (hook != null) {
            List<ObserveHook> list = observe.get(type);
            if (list == null) {
                list = new ArrayList<ObserveHook>();
                observe.put(type, list);
            }
            list.add(hook);
        }
        return this;
    }

    /** Apply the collected hooks to the builder (composing into the runtime's interceptor slots). */
    public void applyTo(AgentBuilder builder) {
        if (builder == null) {
            return;
        }
        if (!preToolUse.isEmpty() || !postToolUse.isEmpty()) {
            builder.toolInterceptor(composeToolInterceptor());
        }
        if (!userPromptSubmit.isEmpty()) {
            builder.promptInterceptor(composePromptInterceptor());
        }
        if (!modelRequestHooks.isEmpty()) {
            builder.modelRequestHook(composeModelRequestHook());
        }
        if (!observe.isEmpty()) {
            builder.lifecycleHook(composeLifecycleHook());
        }
    }

    boolean isEmpty() {
        return preToolUse.isEmpty() && postToolUse.isEmpty() && userPromptSubmit.isEmpty()
                && modelRequestHooks.isEmpty() && observe.isEmpty();
    }

    private ToolInterceptor composeToolInterceptor() {
        final List<ToolInterceptor> pre = new ArrayList<ToolInterceptor>(preToolUse);
        final List<PostToolUseHook> post = new ArrayList<PostToolUseHook>(postToolUse);
        return new ToolInterceptor() {
            @Override
            public ToolCallDecision beforeToolCall(AgentToolCall call, AgentContext context) {
                for (ToolInterceptor h : pre) {
                    ToolCallDecision d = h.beforeToolCall(call, context);
                    if (d != null && d.getType() != ToolCallDecision.Type.ALLOW) {
                        return d; // first block/modify/routeTo wins
                    }
                }
                return ToolCallDecision.allow();
            }

            @Override
            public ToolCallDecision afterToolCall(AgentToolCall call, String output, AgentContext context) {
                for (PostToolUseHook h : post) {
                    ToolCallDecision d = h.afterToolCall(call, output, context);
                    if (d != null && d.getType() == ToolCallDecision.Type.BLOCK) {
                        return d; // first block wins
                    }
                }
                return ToolCallDecision.allow();
            }
        };
    }

    private PromptInterceptor composePromptInterceptor() {
        final List<PromptInterceptor> hooks = new ArrayList<PromptInterceptor>(userPromptSubmit);
        return new PromptInterceptor() {
            @Override
            public PromptDecision beforePrompt(String input, AgentContext context) {
                for (PromptInterceptor h : hooks) {
                    PromptDecision d = h.beforePrompt(input, context);
                    if (d != null && d.getType() != PromptDecision.Type.ALLOW) {
                        return d; // first block/modify wins
                    }
                }
                return PromptDecision.allow();
            }
        };
    }

    private ModelRequestHook composeModelRequestHook() {
        final List<ModelRequestHook> hooks = new ArrayList<ModelRequestHook>(modelRequestHooks);
        return new ModelRequestHook() {
            @Override
            public AgentPrompt beforeModelRequest(AgentPrompt prompt, AgentContext context) {
                AgentPrompt current = prompt;
                for (ModelRequestHook h : hooks) {
                    AgentPrompt modified = h.beforeModelRequest(current, context);
                    if (modified != null) {
                        current = modified;
                    }
                }
                return current;
            }
        };
    }

    private AgentLifecycleHook composeLifecycleHook() {
        final Map<AgentLifecycleEventType, List<ObserveHook>> snapshot = new LinkedHashMap<AgentLifecycleEventType, List<ObserveHook>>();
        for (Map.Entry<AgentLifecycleEventType, List<ObserveHook>> e : observe.entrySet()) {
            snapshot.put(e.getKey(), new ArrayList<ObserveHook>(e.getValue()));
        }
        return new AgentLifecycleHook() {
            @Override
            public String name() {
                return "agent-hooks-observe";
            }

            @Override
            public void onEvent(AgentLifecycleEvent event) {
                if (event == null || event.getType() == null) {
                    return;
                }
                List<ObserveHook> hooks = snapshot.get(event.getType());
                if (hooks == null) {
                    return;
                }
                for (ObserveHook h : hooks) {
                    try {
                        h.onEvent(event);
                    } catch (Exception ignored) {
                        // an observe hook must not break the run
                    }
                }
            }
        };
    }
}
