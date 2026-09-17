package io.github.lnyocly.ai4j.agent.tool;

import io.github.lnyocly.ai4j.platform.openai.tool.Tool;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Controls which registered tools are included in a model request.
 *
 * <p>This is deliberately a model-facing view only. It does not alter the
 * registry, executor, permission policy, or host authorization boundary. A
 * host can therefore expose a small, stable tool set to a model while still
 * retaining the complete executor for explicitly submitted calls and durable
 * Harness operations.</p>
 */
public final class AgentToolVisibility {

    private final Predicate<Object> predicate;

    private AgentToolVisibility(Predicate<Object> predicate) {
        if (predicate == null) {
            throw new IllegalArgumentException("predicate is required");
        }
        this.predicate = predicate;
    }

    /** Returns a view that includes every registered item, including unnamed items. */
    public static AgentToolVisibility all() {
        return new AgentToolVisibility(new Predicate<Object>() {
            @Override
            public boolean test(Object tool) {
                return true;
            }
        });
    }

    /**
     * Returns a view containing only tools whose provider-visible name is in
     * {@code names}. Unnamed or unrecognized registry items are excluded.
     */
    public static AgentToolVisibility named(Collection<String> names) {
        final Set<String> allowed = normalizeNames(names);
        return new AgentToolVisibility(new Predicate<Object>() {
            @Override
            public boolean test(Object tool) {
                String name = toolName(tool);
                return name != null && allowed.contains(name);
            }
        });
    }

    /**
     * Returns a view that excludes tools whose provider-visible name is in
     * {@code names}. Unnamed or unrecognized registry items remain visible.
     */
    public static AgentToolVisibility excluding(Collection<String> names) {
        final Set<String> excluded = normalizeNames(names);
        return new AgentToolVisibility(new Predicate<Object>() {
            @Override
            public boolean test(Object tool) {
                String name = toolName(tool);
                return name == null || !excluded.contains(name);
            }
        });
    }

    /** Creates a visibility view from an application-defined item predicate. */
    public static AgentToolVisibility custom(Predicate<Object> predicate) {
        return new AgentToolVisibility(predicate);
    }

    /** Alias for {@link #custom(Predicate)} for callers that prefer predicate terminology. */
    public static AgentToolVisibility predicate(Predicate<Object> predicate) {
        return custom(predicate);
    }

    /** Combines two views with logical AND. A null view is treated as no extra filter. */
    public AgentToolVisibility and(final AgentToolVisibility other) {
        if (other == null) {
            return this;
        }
        final AgentToolVisibility self = this;
        return custom(new Predicate<Object>() {
            @Override
            public boolean test(Object tool) {
                return self.includes(tool) && other.includes(tool);
            }
        });
    }

    /** Combines two views with logical OR. A null view is ignored. */
    public AgentToolVisibility or(final AgentToolVisibility other) {
        if (other == null) {
            return this;
        }
        final AgentToolVisibility self = this;
        return custom(new Predicate<Object>() {
            @Override
            public boolean test(Object tool) {
                return self.includes(tool) || other.includes(tool);
            }
        });
    }

    /** Returns whether one registry item is visible under this view. */
    public boolean includes(Object tool) {
        return predicate.test(tool);
    }

    /**
     * Filters a registry snapshot while preserving its order and returning a
     * new mutable list. A null input remains null to preserve the existing
     * registry contract used by model clients.
     */
    public List<Object> filter(List<Object> tools) {
        if (tools == null) {
            return null;
        }
        List<Object> visible = new ArrayList<Object>();
        for (Object tool : tools) {
            if (includes(tool)) {
                visible.add(tool);
            }
        }
        return visible;
    }

    /**
     * Extracts the provider-visible name from the SDK's Tool value or a
     * serialized OpenAI/Anthropic-style map. Unknown representations return
     * null and are intentionally left available to custom predicates.
     */
    public static String toolName(Object tool) {
        if (tool instanceof Tool) {
            Tool.Function function = ((Tool) tool).getFunction();
            return normalizeName(function == null ? null : function.getName());
        }
        if (tool instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) tool;
            Object function = map.get("function");
            if (function instanceof Map) {
                String name = normalizeName(((Map<?, ?>) function).get("name"));
                if (name != null) {
                    return name;
                }
            }
            return normalizeName(map.get("name"));
        }
        return null;
    }

    private static Set<String> normalizeNames(Collection<String> names) {
        if (names == null || names.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> normalized = new LinkedHashSet<String>();
        for (String name : names) {
            String value = normalizeName(name);
            if (value != null) {
                normalized.add(value);
            }
        }
        return Collections.unmodifiableSet(normalized);
    }

    private static String normalizeName(Object value) {
        if (value == null) {
            return null;
        }
        String name = String.valueOf(value).trim();
        return name.isEmpty() ? null : name;
    }
}
