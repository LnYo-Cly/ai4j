package io.github.lnyocly.ai4j.coding.policy;

import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.AgentToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.StaticToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import io.github.lnyocly.ai4j.coding.definition.CodingAgentDefinition;
import io.github.lnyocly.ai4j.coding.definition.CodingIsolationMode;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class CodingToolPolicyResolver {

    /** Tools that are blocked when CodingIsolationMode is READ_ONLY. */
    static final Set<String> READ_ONLY_BLOCKED_TOOLS = Collections.unmodifiableSet(
            new LinkedHashSet<String>(Arrays.asList("bash", "write_file", "apply_patch", "edit"))
    );

    public CodingToolContextPolicy resolve(AgentToolRegistry baseRegistry,
                                           ToolExecutor baseExecutor,
                                           CodingAgentDefinition definition) {
        Set<String> allowedToolNames = normalize(definition == null ? null : definition.getAllowedToolNames());
        if (allowedToolNames.isEmpty()) {
            return CodingToolContextPolicy.builder()
                    .toolRegistry(baseRegistry == null ? StaticToolRegistry.empty() : baseRegistry)
                    .toolExecutor(baseExecutor)
                    .allowedToolNames(Collections.<String>emptySet())
                    .build();
        }
        allowedToolNames = applyIsolationMode(allowedToolNames, definition);
        return CodingToolContextPolicy.builder()
                .toolRegistry(filterRegistry(baseRegistry, allowedToolNames))
                .toolExecutor(wrapExecutor(baseExecutor, allowedToolNames))
                .allowedToolNames(allowedToolNames)
                .build();
    }

    /**
     * When the definition declares {@link CodingIsolationMode#READ_ONLY}, strip every tool that can
     * mutate state ({@code bash}, {@code write_file}, {@code apply_patch}, {@code edit}). This
     * ensures a READ_ONLY sub-agent cannot escape its isolation via a tool whitelist that was
     * assembled before isolation mode was evaluated.
     */
    private Set<String> applyIsolationMode(Set<String> allowedToolNames, CodingAgentDefinition definition) {
        if (allowedToolNames == null || allowedToolNames.isEmpty()) {
            return Collections.<String>emptySet();
        }
        if (definition == null || definition.getIsolationMode() != CodingIsolationMode.READ_ONLY) {
            return allowedToolNames;
        }
        Set<String> filtered = new LinkedHashSet<String>();
        for (String toolName : allowedToolNames) {
            if (!READ_ONLY_BLOCKED_TOOLS.contains(toolName)) {
                filtered.add(toolName);
            }
        }
        return filtered.isEmpty() ? Collections.<String>emptySet() : Collections.unmodifiableSet(filtered);
    }

    private AgentToolRegistry filterRegistry(AgentToolRegistry baseRegistry, Set<String> allowedToolNames) {
        if (baseRegistry == null) {
            return StaticToolRegistry.empty();
        }
        List<Object> filtered = new ArrayList<Object>();
        List<Object> tools = baseRegistry.getTools();
        if (tools != null) {
            for (Object tool : tools) {
                if (supports(tool, allowedToolNames)) {
                    filtered.add(tool);
                }
            }
        }
        return new StaticToolRegistry(filtered);
    }

    private boolean supports(Object tool, Set<String> allowedToolNames) {
        String toolName = extractToolName(tool);
        return toolName != null && allowedToolNames.contains(normalize(toolName));
    }

    private String extractToolName(Object tool) {
        if (!(tool instanceof Tool)) {
            return null;
        }
        Tool.Function function = ((Tool) tool).getFunction();
        return function == null ? null : function.getName();
    }

    private ToolExecutor wrapExecutor(final ToolExecutor baseExecutor, final Set<String> allowedToolNames) {
        return new ToolExecutor() {
            @Override
            public String execute(AgentToolCall call) throws Exception {
                String toolName = call == null ? null : call.getName();
                if (toolName == null || !allowedToolNames.contains(normalize(toolName))) {
                    throw new IllegalArgumentException("Tool is not allowed in this delegated coding session: " + toolName);
                }
                if (baseExecutor == null) {
                    throw new IllegalStateException("No tool executor available for delegated coding session");
                }
                return baseExecutor.execute(call);
            }
        };
    }

    private Set<String> normalize(Set<String> toolNames) {
        if (toolNames == null || toolNames.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> normalized = new LinkedHashSet<String>();
        for (String toolName : toolNames) {
            String value = normalize(toolName);
            if (value != null) {
                normalized.add(value);
            }
        }
        return normalized.isEmpty() ? Collections.<String>emptySet() : Collections.unmodifiableSet(normalized);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase(Locale.ROOT);
    }
}
