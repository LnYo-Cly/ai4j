package io.github.lnyocly.ai4j.agent.blueprint;

import io.github.lnyocly.ai4j.agent.AgentOptions;
import io.github.lnyocly.ai4j.agent.context.ContextBudget;
import io.github.lnyocly.ai4j.agent.context.ContextProjector;
import io.github.lnyocly.ai4j.agent.event.AgentEventPublisher;
import io.github.lnyocly.ai4j.agent.memory.AgentMemory;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.permission.AgentExecutionEnvironment;
import io.github.lnyocly.ai4j.agent.permission.AgentPermissionPolicy;
import io.github.lnyocly.ai4j.agent.session.AgentSessionStore;
import io.github.lnyocly.ai4j.agent.tool.AgentToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Host-provided dependencies used by {@link AgentFactory}.
 */
public class AgentFactoryContext {

    private AgentModelClient modelClient;
    private AgentToolRegistry toolRegistry;
    private ToolExecutor toolExecutor;
    private Supplier<AgentMemory> memorySupplier;
    private AgentOptions baseOptions;
    private AgentPermissionPolicy permissionPolicy;
    private AgentExecutionEnvironment executionEnvironment;
    private ContextProjector contextProjector;
    private ContextBudget contextBudget;
    private AgentEventPublisher eventPublisher;
    private AgentSessionStore sessionStore;
    private Map<String, Object> extraBody;
    private boolean allowSandboxDeclaration;

    public static Builder builder() {
        return new Builder();
    }

    public AgentModelClient getModelClient() {
        return modelClient;
    }

    public AgentToolRegistry getToolRegistry() {
        return toolRegistry;
    }

    public ToolExecutor getToolExecutor() {
        return toolExecutor;
    }

    public Supplier<AgentMemory> getMemorySupplier() {
        return memorySupplier;
    }

    public AgentOptions getBaseOptions() {
        return baseOptions;
    }

    public AgentPermissionPolicy getPermissionPolicy() {
        return permissionPolicy;
    }

    public AgentExecutionEnvironment getExecutionEnvironment() {
        return executionEnvironment;
    }

    public ContextProjector getContextProjector() {
        return contextProjector;
    }

    public ContextBudget getContextBudget() {
        return contextBudget;
    }

    public AgentEventPublisher getEventPublisher() {
        return eventPublisher;
    }

    public AgentSessionStore getSessionStore() {
        return sessionStore;
    }

    public Map<String, Object> getExtraBody() {
        return extraBody;
    }

    public boolean isAllowSandboxDeclaration() {
        return allowSandboxDeclaration;
    }

    public static class Builder {
        private final AgentFactoryContext context = new AgentFactoryContext();

        public Builder modelClient(AgentModelClient modelClient) {
            context.modelClient = modelClient;
            return this;
        }

        public Builder toolRegistry(AgentToolRegistry toolRegistry) {
            context.toolRegistry = toolRegistry;
            return this;
        }

        public Builder toolExecutor(ToolExecutor toolExecutor) {
            context.toolExecutor = toolExecutor;
            return this;
        }

        public Builder memorySupplier(Supplier<AgentMemory> memorySupplier) {
            context.memorySupplier = memorySupplier;
            return this;
        }

        public Builder baseOptions(AgentOptions baseOptions) {
            context.baseOptions = baseOptions;
            return this;
        }

        public Builder permissionPolicy(AgentPermissionPolicy permissionPolicy) {
            context.permissionPolicy = permissionPolicy;
            return this;
        }

        public Builder executionEnvironment(AgentExecutionEnvironment executionEnvironment) {
            context.executionEnvironment = executionEnvironment;
            return this;
        }

        public Builder contextProjector(ContextProjector contextProjector) {
            context.contextProjector = contextProjector;
            return this;
        }

        public Builder contextBudget(ContextBudget contextBudget) {
            context.contextBudget = contextBudget;
            return this;
        }

        public Builder eventPublisher(AgentEventPublisher eventPublisher) {
            context.eventPublisher = eventPublisher;
            return this;
        }

        public Builder sessionStore(AgentSessionStore sessionStore) {
            context.sessionStore = sessionStore;
            return this;
        }

        public Builder extraBody(Map<String, Object> extraBody) {
            context.extraBody = extraBody;
            return this;
        }

        public Builder allowSandboxDeclaration(boolean allowSandboxDeclaration) {
            context.allowSandboxDeclaration = allowSandboxDeclaration;
            return this;
        }

        public AgentFactoryContext build() {
            return context;
        }
    }
}
