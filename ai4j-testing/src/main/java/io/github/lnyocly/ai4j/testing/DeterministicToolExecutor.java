package io.github.lnyocly.ai4j.testing;

import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * A name-routed {@link ToolExecutor} for deterministic agent tests. Register one fixed output or
 * handler per tool name; every executed call is recorded for assertions.
 *
 * <pre>{@code
 * DeterministicToolExecutor tools = new DeterministicToolExecutor()
 *         .on("get_weather", "{\"temp\":26}")
 *         .on("get_time", call -> "{\"now\":\"noon\"}");
 *
 * Agent agent = Agents.react()
 *         .modelClient(model)
 *         .toolRegistry(registry)
 *         .toolExecutor(tools)
 *         .build();
 * }</pre>
 *
 * <p>Calls to unregistered tools throw {@link IllegalArgumentException} by default so a drifted
 * tool name fails loudly; supply {@link #otherwise(Function)} for a fallback handler.
 */
public class DeterministicToolExecutor implements ToolExecutor {

    private final Map<String, Function<AgentToolCall, String>> handlers =
            new LinkedHashMap<String, Function<AgentToolCall, String>>();
    private final List<AgentToolCall> calls = new ArrayList<AgentToolCall>();
    private Function<AgentToolCall, String> fallback;

    /** Fixed output for a tool name. */
    public DeterministicToolExecutor on(String toolName, String output) {
        handlers.put(toolName, call -> output);
        return this;
    }

    /** Dynamic output computed from the call (arguments, metadata). */
    public DeterministicToolExecutor on(String toolName, Function<AgentToolCall, String> handler) {
        handlers.put(toolName, handler);
        return this;
    }

    /** Fallback for tool names with no registered handler; without it unknown tools throw. */
    public DeterministicToolExecutor otherwise(Function<AgentToolCall, String> handler) {
        this.fallback = handler;
        return this;
    }

    @Override
    public String execute(AgentToolCall call) {
        calls.add(call);
        String name = call == null ? null : call.getName();
        Function<AgentToolCall, String> handler = handlers.get(name);
        if (handler == null) {
            handler = fallback;
        }
        if (handler == null) {
            throw new IllegalArgumentException("No scripted output for tool: " + name);
        }
        return handler.apply(call);
    }

    /** Every call that reached this executor, in order. */
    public List<AgentToolCall> getCalls() {
        return Collections.unmodifiableList(calls);
    }

    /** Calls received for one tool name. */
    public List<AgentToolCall> getCallsFor(String toolName) {
        List<AgentToolCall> matched = new ArrayList<AgentToolCall>();
        for (AgentToolCall call : calls) {
            if (call != null && call.getName() != null && call.getName().equals(toolName)) {
                matched.add(call);
            }
        }
        return matched;
    }

    public int countFor(String toolName) {
        return getCallsFor(toolName).size();
    }
}
