package io.github.lnyocly.ai4j.coding.tool;

import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.AgentToolExecution;
import io.github.lnyocly.ai4j.agent.tool.AsyncToolExecutor;
import io.github.lnyocly.ai4j.agent.tool.AsyncToolExecutors;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RoutingToolExecutor implements AsyncToolExecutor {

    private final List<Route> routes;
    private final ToolExecutor fallbackExecutor;

    public RoutingToolExecutor(List<Route> routes, ToolExecutor fallbackExecutor) {
        if (routes == null) {
            this.routes = Collections.emptyList();
        } else {
            this.routes = new ArrayList<>(routes);
        }
        this.fallbackExecutor = fallbackExecutor;
    }

    @Override
    public String execute(AgentToolCall call) throws Exception {
        String toolName = call == null ? null : call.getName();
        for (Route route : routes) {
            if (route.supports(toolName)) {
                return route.getExecutor().execute(call);
            }
        }
        if (fallbackExecutor != null) {
            return fallbackExecutor.execute(call);
        }
        throw new IllegalArgumentException("No tool executor found for tool: " + toolName);
    }

    @Override
    public AgentToolExecution start(AgentToolCall call) throws Exception {
        String toolName = call == null ? null : call.getName();
        ToolExecutor executor = resolve(toolName);
        return AsyncToolExecutors.start(executor, call);
    }

    private ToolExecutor resolve(String toolName) {
        for (Route route : routes) {
            if (route.supports(toolName)) {
                return route.getExecutor();
            }
        }
        if (fallbackExecutor != null) {
            return fallbackExecutor;
        }
        throw new IllegalArgumentException("No tool executor found for tool: " + toolName);
    }

    public static Route route(Set<String> toolNames, ToolExecutor executor) {
        return new Route(toolNames, executor);
    }

    public static class Route {

        private final Set<String> toolNames;
        private final ToolExecutor executor;

        public Route(Set<String> toolNames, ToolExecutor executor) {
            if (toolNames == null) {
                this.toolNames = Collections.emptySet();
            } else {
                this.toolNames = new HashSet<>(toolNames);
            }
            this.executor = executor;
        }

        public boolean supports(String toolName) {
            return toolName != null && toolNames.contains(toolName);
        }

        public ToolExecutor getExecutor() {
            return executor;
        }
    }
}
