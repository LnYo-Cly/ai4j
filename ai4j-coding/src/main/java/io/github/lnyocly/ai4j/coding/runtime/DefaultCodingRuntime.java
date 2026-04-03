package io.github.lnyocly.ai4j.coding.runtime;

import io.github.lnyocly.ai4j.agent.AgentContext;
import io.github.lnyocly.ai4j.agent.AgentSession;
import io.github.lnyocly.ai4j.agent.memory.InMemoryAgentMemory;
import io.github.lnyocly.ai4j.agent.subagent.HandoffPolicy;
import io.github.lnyocly.ai4j.agent.subagent.SubAgentRegistry;
import io.github.lnyocly.ai4j.agent.tool.AgentToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import io.github.lnyocly.ai4j.coding.CodingAgentBuilder;
import io.github.lnyocly.ai4j.coding.CodingAgentOptions;
import io.github.lnyocly.ai4j.coding.CodingAgentResult;
import io.github.lnyocly.ai4j.coding.CodingSession;
import io.github.lnyocly.ai4j.coding.CodingSessionState;
import io.github.lnyocly.ai4j.coding.definition.CodingAgentDefinition;
import io.github.lnyocly.ai4j.coding.definition.CodingAgentDefinitionRegistry;
import io.github.lnyocly.ai4j.coding.definition.CodingSessionMode;
import io.github.lnyocly.ai4j.coding.delegate.CodingDelegateRequest;
import io.github.lnyocly.ai4j.coding.delegate.CodingDelegateResult;
import io.github.lnyocly.ai4j.coding.policy.CodingToolContextPolicy;
import io.github.lnyocly.ai4j.coding.policy.CodingToolPolicyResolver;
import io.github.lnyocly.ai4j.coding.process.SessionProcessRegistry;
import io.github.lnyocly.ai4j.coding.session.CodingSessionLink;
import io.github.lnyocly.ai4j.coding.session.CodingSessionLinkStore;
import io.github.lnyocly.ai4j.coding.task.CodingTask;
import io.github.lnyocly.ai4j.coding.task.CodingTaskManager;
import io.github.lnyocly.ai4j.coding.task.CodingTaskProgress;
import io.github.lnyocly.ai4j.coding.task.CodingTaskStatus;
import io.github.lnyocly.ai4j.coding.workspace.WorkspaceContext;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class DefaultCodingRuntime implements CodingRuntime {

    private final WorkspaceContext workspaceContext;
    private final CodingAgentOptions options;
    private final AgentToolRegistry customToolRegistry;
    private final ToolExecutor customToolExecutor;
    private final CodingAgentDefinitionRegistry definitionRegistry;
    private final CodingTaskManager taskManager;
    private final CodingSessionLinkStore sessionLinkStore;
    private final CodingToolPolicyResolver toolPolicyResolver;
    private final SubAgentRegistry subAgentRegistry;
    private final HandoffPolicy handoffPolicy;
    private final ExecutorService executorService;
    private final CopyOnWriteArrayList<CodingRuntimeListener> listeners = new CopyOnWriteArrayList<CodingRuntimeListener>();

    public DefaultCodingRuntime(WorkspaceContext workspaceContext,
                                CodingAgentOptions options,
                                AgentToolRegistry customToolRegistry,
                                ToolExecutor customToolExecutor,
                                CodingAgentDefinitionRegistry definitionRegistry,
                                CodingTaskManager taskManager,
                                CodingSessionLinkStore sessionLinkStore,
                                CodingToolPolicyResolver toolPolicyResolver) {
        this(workspaceContext, options, customToolRegistry, customToolExecutor, definitionRegistry, taskManager, sessionLinkStore, toolPolicyResolver, null, null);
    }

    public DefaultCodingRuntime(WorkspaceContext workspaceContext,
                                CodingAgentOptions options,
                                AgentToolRegistry customToolRegistry,
                                ToolExecutor customToolExecutor,
                                CodingAgentDefinitionRegistry definitionRegistry,
                                CodingTaskManager taskManager,
                                CodingSessionLinkStore sessionLinkStore,
                                CodingToolPolicyResolver toolPolicyResolver,
                                SubAgentRegistry subAgentRegistry,
                                HandoffPolicy handoffPolicy) {
        this(workspaceContext, options, customToolRegistry, customToolExecutor, definitionRegistry, taskManager, sessionLinkStore, toolPolicyResolver,
                Executors.newCachedThreadPool(new ThreadFactory() {
                    @Override
                    public Thread newThread(Runnable runnable) {
                        Thread thread = new Thread(runnable, "ai4j-coding-runtime");
                        thread.setDaemon(true);
                        return thread;
                    }
                }),
                subAgentRegistry,
                handoffPolicy);
    }

    public DefaultCodingRuntime(WorkspaceContext workspaceContext,
                                CodingAgentOptions options,
                                AgentToolRegistry customToolRegistry,
                                ToolExecutor customToolExecutor,
                                CodingAgentDefinitionRegistry definitionRegistry,
                                CodingTaskManager taskManager,
                                CodingSessionLinkStore sessionLinkStore,
                                CodingToolPolicyResolver toolPolicyResolver,
                                ExecutorService executorService) {
        this(workspaceContext, options, customToolRegistry, customToolExecutor, definitionRegistry, taskManager, sessionLinkStore, toolPolicyResolver, executorService, null, null);
    }

    public DefaultCodingRuntime(WorkspaceContext workspaceContext,
                                CodingAgentOptions options,
                                AgentToolRegistry customToolRegistry,
                                ToolExecutor customToolExecutor,
                                CodingAgentDefinitionRegistry definitionRegistry,
                                CodingTaskManager taskManager,
                                CodingSessionLinkStore sessionLinkStore,
                                CodingToolPolicyResolver toolPolicyResolver,
                                ExecutorService executorService,
                                SubAgentRegistry subAgentRegistry,
                                HandoffPolicy handoffPolicy) {
        this.workspaceContext = workspaceContext;
        this.options = options == null ? CodingAgentOptions.builder().build() : options;
        this.customToolRegistry = customToolRegistry;
        this.customToolExecutor = customToolExecutor;
        this.definitionRegistry = definitionRegistry;
        this.taskManager = taskManager;
        this.sessionLinkStore = sessionLinkStore;
        this.toolPolicyResolver = toolPolicyResolver == null ? new CodingToolPolicyResolver() : toolPolicyResolver;
        this.subAgentRegistry = subAgentRegistry;
        this.handoffPolicy = handoffPolicy;
        this.executorService = executorService;
    }

    @Override
    public CodingDelegateResult delegate(final CodingSession parentSession, final CodingDelegateRequest request) throws Exception {
        if (parentSession == null) {
            throw new IllegalArgumentException("parentSession is required");
        }
        CodingAgentDefinition definition = requireDefinition(request);
        boolean background = request != null && request.getBackground() != null
                ? request.getBackground().booleanValue()
                : definition.isBackground();
        CodingSessionMode sessionMode = request != null && request.getSessionMode() != null
                ? request.getSessionMode()
                : defaultSessionMode(definition);
        String taskId = UUID.randomUUID().toString();
        String childSessionId = firstNonBlank(
                request == null ? null : request.getChildSessionId(),
                parentSession.getSessionId() + "-delegate-" + UUID.randomUUID().toString().substring(0, 8)
        );
        long now = System.currentTimeMillis();
        CodingSessionState seedState = resolveSeedState(parentSession, sessionMode);

        CodingTask queuedTask = persistInitialTask(CodingTask.builder()
                .taskId(taskId)
                .definitionName(definition.getName())
                .parentSessionId(parentSession.getSessionId())
                .childSessionId(childSessionId)
                .input(composeInput(request))
                .background(background)
                .status(CodingTaskStatus.QUEUED)
                .progress(progress("queued", "Task queued for execution.", 0, now))
                .createdAtEpochMs(now)
                .build());
        CodingSessionLink link = saveLink(CodingSessionLink.builder()
                .linkId(UUID.randomUUID().toString())
                .taskId(taskId)
                .definitionName(definition.getName())
                .parentSessionId(parentSession.getSessionId())
                .childSessionId(childSessionId)
                .sessionMode(sessionMode)
                .background(background)
                .createdAtEpochMs(now)
                .build());
        notifyTaskCreated(queuedTask, link);

        if (background) {
            executorService.submit(new Runnable() {
                @Override
                public void run() {
                    runTask(parentSession, request, definition, taskId, childSessionId, seedState);
                }
            });
            CodingTask task = taskManager.getTask(taskId);
            return buildDelegateResult(task, null);
        }

        return runTask(parentSession, request, definition, taskId, childSessionId, seedState);
    }

    @Override
    public CodingTask getTask(String taskId) {
        return taskManager == null ? null : taskManager.getTask(taskId);
    }

    @Override
    public void addListener(CodingRuntimeListener listener) {
        if (listener != null) {
            listeners.addIfAbsent(listener);
        }
    }

    @Override
    public void removeListener(CodingRuntimeListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    @Override
    public List<CodingTask> listTasks() {
        return taskManager == null ? Collections.<CodingTask>emptyList() : taskManager.listTasks();
    }

    @Override
    public List<CodingTask> listTasksByParentSessionId(String parentSessionId) {
        return taskManager == null ? Collections.<CodingTask>emptyList() : taskManager.listTasksByParentSessionId(parentSessionId);
    }

    @Override
    public List<CodingSessionLink> listSessionLinks(String parentSessionId) {
        return sessionLinkStore == null
                ? Collections.<CodingSessionLink>emptyList()
                : sessionLinkStore.listLinksByParentSessionId(parentSessionId);
    }

    @Override
    public CodingAgentDefinitionRegistry getDefinitionRegistry() {
        return definitionRegistry;
    }

    private CodingDelegateResult runTask(CodingSession parentSession,
                                         CodingDelegateRequest request,
                                         CodingAgentDefinition definition,
                                         String taskId,
                                         String childSessionId,
                                         CodingSessionState seedState) {
        long startedAt = System.currentTimeMillis();
        saveTask(updateTask(taskId, CodingTaskStatus.STARTING, progress("starting", "Preparing delegated session.", 10, startedAt), startedAt, 0L, null, null));
        CodingSession childSession = null;
        try {
            childSession = createChildSession(parentSession, definition, childSessionId, seedState);
            saveTask(updateTask(taskId, CodingTaskStatus.RUNNING, progress("running", "Delegated session is running.", 50, System.currentTimeMillis()), startedAt, 0L, null, null));
            CodingAgentResult result = childSession.run(composeInput(request));
            long endedAt = System.currentTimeMillis();
            CodingTask completed = saveTask(updateTask(taskId, CodingTaskStatus.COMPLETED,
                    progress("completed", "Delegated session completed.", 100, endedAt), startedAt, endedAt,
                    result == null ? null : result.getOutputText(), null));
            return buildDelegateResult(completed, null);
        } catch (Exception ex) {
            long endedAt = System.currentTimeMillis();
            CodingTask failed = saveTask(updateTask(taskId, CodingTaskStatus.FAILED,
                    progress("failed", safeMessage(ex), 100, endedAt), startedAt, endedAt,
                    null, safeMessage(ex)));
            return buildDelegateResult(failed, ex);
        } finally {
            if (childSession != null) {
                childSession.close();
            }
        }
    }

    private CodingSession createChildSession(CodingSession parentSession,
                                             CodingAgentDefinition definition,
                                             String childSessionId,
                                             CodingSessionState seedState) {
        AgentSession parentAgentSession = parentSession.getDelegate();
        if (parentAgentSession == null || parentAgentSession.getContext() == null) {
            throw new IllegalStateException("parent agent session context is unavailable");
        }
        SessionProcessRegistry processRegistry = new SessionProcessRegistry(workspaceContext, options);
        AgentToolRegistry builtInRegistry = CodingAgentBuilder.createBuiltInRegistry(options, definitionRegistry);
        ToolExecutor builtInExecutor = CodingAgentBuilder.createBuiltInToolExecutor(workspaceContext, options, processRegistry, this, definitionRegistry);
        AgentToolRegistry mergedRegistry = CodingAgentBuilder.mergeToolRegistry(builtInRegistry, customToolRegistry);
        ToolExecutor mergedExecutor = CodingAgentBuilder.mergeToolExecutor(
                builtInRegistry,
                builtInExecutor,
                customToolRegistry,
                customToolExecutor
        );
        mergedRegistry = CodingAgentBuilder.mergeSubAgentToolRegistry(mergedRegistry, subAgentRegistry);
        mergedExecutor = CodingAgentBuilder.mergeSubAgentToolExecutor(mergedExecutor, subAgentRegistry, handoffPolicy);
        CodingToolContextPolicy toolPolicy = toolPolicyResolver.resolve(mergedRegistry, mergedExecutor, definition);
        AgentContext parentContext = parentAgentSession.getContext();
        AgentContext childContext = parentContext.toBuilder()
                .memory(new InMemoryAgentMemory())
                .toolRegistry(toolPolicy.getToolRegistry())
                .toolExecutor(toolPolicy.getToolExecutor())
                .model(firstNonBlank(definition.getModel(), parentContext.getModel()))
                .instructions(mergeText(parentContext.getInstructions(), definition.getInstructions()))
                .systemPrompt(mergeText(parentContext.getSystemPrompt(), definition.getSystemPrompt()))
                .build();
        CodingSession childSession = new CodingSession(
                childSessionId,
                new AgentSession(parentAgentSession.getRuntime(), childContext),
                workspaceContext,
                options,
                processRegistry,
                this
        );
        if (seedState != null) {
            childSession.restore(seedState);
        }
        return childSession;
    }

    private CodingSessionState resolveSeedState(CodingSession parentSession, CodingSessionMode sessionMode) {
        if (parentSession == null || sessionMode == null || sessionMode == CodingSessionMode.NEW) {
            return null;
        }
        return parentSession.exportState();
    }

    private CodingAgentDefinition requireDefinition(CodingDelegateRequest request) {
        String name = request == null ? null : request.getDefinitionName();
        CodingAgentDefinition definition = definitionRegistry == null ? null : definitionRegistry.getDefinition(firstNonBlank(name, "general-purpose"));
        if (definition == null) {
            throw new IllegalArgumentException("Unknown coding agent definition: " + firstNonBlank(name, "general-purpose"));
        }
        return definition;
    }

    private CodingSessionMode defaultSessionMode(CodingAgentDefinition definition) {
        return definition == null || definition.getSessionMode() == null
                ? CodingSessionMode.FORK
                : definition.getSessionMode();
    }

    private String composeInput(CodingDelegateRequest request) {
        if (request == null) {
            return "";
        }
        String input = trimToNull(request.getInput());
        String context = trimToNull(request.getContext());
        if (input == null) {
            return context == null ? "" : context;
        }
        if (context == null) {
            return input;
        }
        return input + "\n\nContext:\n" + context;
    }

    private CodingTask saveTask(CodingTask task) {
        if (taskManager == null || task == null) {
            return task;
        }
        CodingTask saved = taskManager.save(task);
        notifyTaskUpdated(saved);
        return saved;
    }

    private CodingTask persistInitialTask(CodingTask task) {
        if (taskManager == null || task == null) {
            return task;
        }
        return taskManager.save(task);
    }

    private CodingSessionLink saveLink(CodingSessionLink link) {
        if (sessionLinkStore == null || link == null) {
            return link;
        }
        return sessionLinkStore.save(link);
    }

    private CodingTask updateTask(String taskId,
                                  CodingTaskStatus status,
                                  CodingTaskProgress progress,
                                  long startedAt,
                                  long endedAt,
                                  String outputText,
                                  String error) {
        CodingTask current = taskManager == null ? null : taskManager.getTask(taskId);
        if (current == null) {
            throw new IllegalStateException("Coding task not found: " + taskId);
        }
        return current.toBuilder()
                .status(status)
                .progress(progress)
                .startedAtEpochMs(startedAt > 0 ? startedAt : current.getStartedAtEpochMs())
                .endedAtEpochMs(endedAt > 0 ? endedAt : current.getEndedAtEpochMs())
                .outputText(outputText == null ? current.getOutputText() : outputText)
                .error(error == null ? current.getError() : error)
                .build();
    }

    private CodingTaskProgress progress(String phase, String message, Integer percent, long now) {
        return CodingTaskProgress.builder()
                .phase(phase)
                .message(message)
                .percent(percent)
                .updatedAtEpochMs(now)
                .build();
    }

    private CodingDelegateResult buildDelegateResult(CodingTask task, Exception error) {
        return CodingDelegateResult.builder()
                .taskId(task == null ? null : task.getTaskId())
                .definitionName(task == null ? null : task.getDefinitionName())
                .parentSessionId(task == null ? null : task.getParentSessionId())
                .childSessionId(task == null ? null : task.getChildSessionId())
                .background(task != null && task.isBackground())
                .status(task == null ? null : task.getStatus())
                .outputText(task == null ? null : task.getOutputText())
                .error(error == null ? (task == null ? null : task.getError()) : safeMessage(error))
                .build();
    }

    private String mergeText(String base, String extra) {
        if (isBlank(base)) {
            return extra;
        }
        if (isBlank(extra)) {
            return base;
        }
        return base + "\n\n" + extra;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                return trimmed;
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String safeMessage(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        Throwable current = throwable;
        String message = null;
        while (current != null) {
            if (!isBlank(current.getMessage())) {
                message = current.getMessage().trim();
            }
            current = current.getCause();
        }
        return isBlank(message) ? throwable.getClass().getSimpleName() : message;
    }

    private void notifyTaskCreated(CodingTask task, CodingSessionLink link) {
        for (CodingRuntimeListener listener : listeners) {
            try {
                listener.onTaskCreated(copyTask(task), copyLink(link));
            } catch (Exception ignored) {
            }
        }
    }

    private void notifyTaskUpdated(CodingTask task) {
        for (CodingRuntimeListener listener : listeners) {
            try {
                listener.onTaskUpdated(copyTask(task));
            } catch (Exception ignored) {
            }
        }
    }

    private CodingTask copyTask(CodingTask task) {
        return task == null ? null : task.toBuilder().build();
    }

    private CodingSessionLink copyLink(CodingSessionLink link) {
        return link == null ? null : link.toBuilder().build();
    }
}
