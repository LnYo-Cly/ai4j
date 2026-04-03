package io.github.lnyocly.ai4j.coding;

import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentSession;
import io.github.lnyocly.ai4j.agent.event.AgentListener;
import io.github.lnyocly.ai4j.agent.subagent.HandoffPolicy;
import io.github.lnyocly.ai4j.agent.subagent.SubAgentRegistry;
import io.github.lnyocly.ai4j.agent.tool.AgentToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import io.github.lnyocly.ai4j.coding.definition.CodingAgentDefinitionRegistry;
import io.github.lnyocly.ai4j.coding.delegate.CodingDelegateRequest;
import io.github.lnyocly.ai4j.coding.delegate.CodingDelegateResult;
import io.github.lnyocly.ai4j.coding.process.SessionProcessRegistry;
import io.github.lnyocly.ai4j.coding.runtime.CodingRuntime;
import io.github.lnyocly.ai4j.coding.session.CodingSessionLink;
import io.github.lnyocly.ai4j.coding.task.CodingTask;
import io.github.lnyocly.ai4j.coding.workspace.WorkspaceContext;

import java.util.List;
import java.util.UUID;

public class CodingAgent {

    private final Agent delegate;
    private final WorkspaceContext workspaceContext;
    private final CodingAgentOptions options;
    private final AgentToolRegistry customToolRegistry;
    private final ToolExecutor customToolExecutor;
    private final CodingRuntime runtime;
    private final SubAgentRegistry subAgentRegistry;
    private final HandoffPolicy handoffPolicy;

    public CodingAgent(Agent delegate,
                       WorkspaceContext workspaceContext,
                       CodingAgentOptions options,
                       AgentToolRegistry customToolRegistry,
                       ToolExecutor customToolExecutor,
                       CodingRuntime runtime,
                       SubAgentRegistry subAgentRegistry,
                       HandoffPolicy handoffPolicy) {
        this.delegate = delegate;
        this.workspaceContext = workspaceContext;
        this.options = options;
        this.customToolRegistry = customToolRegistry;
        this.customToolExecutor = customToolExecutor;
        this.runtime = runtime;
        this.subAgentRegistry = subAgentRegistry;
        this.handoffPolicy = handoffPolicy;
    }

    public CodingAgentResult run(String input) throws Exception {
        try (CodingSession session = newSession()) {
            return session.run(input);
        }
    }

    public CodingAgentResult run(CodingAgentRequest request) throws Exception {
        try (CodingSession session = newSession()) {
            return session.run(request);
        }
    }

    public CodingAgentResult runStream(CodingAgentRequest request, AgentListener listener) throws Exception {
        try (CodingSession session = newSession()) {
            return session.runStream(request, listener);
        }
    }

    public CodingSession newSession() {
        return newSession((String) null, null);
    }

    public CodingSession newSession(CodingSessionState state) {
        return newSession(state == null ? null : state.getSessionId(), state);
    }

    public CodingSession newSession(String sessionId, CodingSessionState state) {
        AgentSession rawSession = delegate.newSession();
        SessionProcessRegistry processRegistry = new SessionProcessRegistry(workspaceContext, options);
        CodingAgentDefinitionRegistry definitionRegistry = getDefinitionRegistry();
        AgentToolRegistry builtInRegistry = CodingAgentBuilder.createBuiltInRegistry(options, definitionRegistry);
        ToolExecutor builtInExecutor = CodingAgentBuilder.createBuiltInToolExecutor(
                workspaceContext,
                options,
                processRegistry,
                runtime,
                definitionRegistry
        );
        AgentToolRegistry mergedBaseRegistry = CodingAgentBuilder.mergeToolRegistry(
                builtInRegistry,
                customToolRegistry
        );
        ToolExecutor mergedBaseExecutor = CodingAgentBuilder.mergeToolExecutor(
                builtInRegistry,
                builtInExecutor,
                customToolRegistry,
                customToolExecutor
        );
        AgentToolRegistry mergedRegistry = CodingAgentBuilder.mergeSubAgentToolRegistry(
                mergedBaseRegistry,
                subAgentRegistry
        );
        ToolExecutor mergedExecutor = CodingAgentBuilder.mergeSubAgentToolExecutor(
                mergedBaseExecutor,
                subAgentRegistry,
                handoffPolicy
        );
        AgentSession session = new AgentSession(
                rawSession.getRuntime(),
                rawSession.getContext().toBuilder()
                        .toolRegistry(mergedRegistry)
                        .toolExecutor(mergedExecutor)
                        .build()
        );
        CodingSession codingSession = new CodingSession(
                isBlank(sessionId) ? UUID.randomUUID().toString() : sessionId,
                session,
                workspaceContext,
                options,
                processRegistry,
                runtime
        );
        if (state != null) {
            codingSession.restore(state);
        }
        return codingSession;
    }

    public Agent getDelegate() {
        return delegate;
    }

    public WorkspaceContext getWorkspaceContext() {
        return workspaceContext;
    }

    public CodingAgentOptions getOptions() {
        return options;
    }

    public CodingRuntime getRuntime() {
        return runtime;
    }

    public CodingAgentDefinitionRegistry getDefinitionRegistry() {
        return runtime == null ? null : runtime.getDefinitionRegistry();
    }

    public CodingDelegateResult delegate(CodingDelegateRequest request) throws Exception {
        try (CodingSession session = newSession()) {
            return session.delegate(request);
        }
    }

    public CodingTask getTask(String taskId) {
        return runtime == null ? null : runtime.getTask(taskId);
    }

    public List<CodingTask> listTasks() {
        return runtime == null ? java.util.Collections.<CodingTask>emptyList() : runtime.listTasks();
    }

    public List<CodingTask> listTasks(String parentSessionId) {
        return runtime == null
                ? java.util.Collections.<CodingTask>emptyList()
                : runtime.listTasksByParentSessionId(parentSessionId);
    }

    public List<CodingSessionLink> listSessionLinks(String parentSessionId) {
        return runtime == null
                ? java.util.Collections.<CodingSessionLink>emptyList()
                : runtime.listSessionLinks(parentSessionId);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
