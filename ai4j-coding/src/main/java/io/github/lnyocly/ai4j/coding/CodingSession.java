package io.github.lnyocly.ai4j.coding;

import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.AgentSession;
import io.github.lnyocly.ai4j.agent.event.AgentListener;
import io.github.lnyocly.ai4j.agent.memory.AgentMemory;
import io.github.lnyocly.ai4j.agent.memory.InMemoryAgentMemory;
import io.github.lnyocly.ai4j.agent.memory.MemorySnapshot;
import io.github.lnyocly.ai4j.coding.loop.CodingAgentLoopController;
import io.github.lnyocly.ai4j.coding.loop.CodingLoopDecision;
import io.github.lnyocly.ai4j.coding.compact.CodingSessionCompactor;
import io.github.lnyocly.ai4j.coding.compact.CodingToolResultMicroCompactResult;
import io.github.lnyocly.ai4j.coding.compact.CodingToolResultMicroCompactor;
import io.github.lnyocly.ai4j.coding.delegate.CodingDelegateRequest;
import io.github.lnyocly.ai4j.coding.delegate.CodingDelegateResult;
import io.github.lnyocly.ai4j.coding.process.BashProcessLogChunk;
import io.github.lnyocly.ai4j.coding.process.BashProcessInfo;
import io.github.lnyocly.ai4j.coding.process.StoredProcessSnapshot;
import io.github.lnyocly.ai4j.coding.process.SessionProcessRegistry;
import io.github.lnyocly.ai4j.coding.runtime.CodingRuntime;
import io.github.lnyocly.ai4j.coding.workspace.WorkspaceContext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class CodingSession implements AutoCloseable {

    private static final CodingSessionCompactor COMPACTOR = new CodingSessionCompactor();
    private static final CodingToolResultMicroCompactor TOOL_RESULT_MICRO_COMPACTOR = new CodingToolResultMicroCompactor();
    private static final CodingAgentLoopController LOOP_CONTROLLER = new CodingAgentLoopController();

    private final String sessionId;
    private final AgentSession delegate;
    private final WorkspaceContext workspaceContext;
    private final CodingAgentOptions options;
    private final SessionProcessRegistry processRegistry;
    private final CodingRuntime runtime;
    private CodingSessionCheckpoint checkpoint;
    private CodingSessionCompactResult lastAutoCompactResult;
    private CodingSessionCompactResult latestCompactResult;
    private Exception lastAutoCompactError;
    private final List<CodingSessionCompactResult> pendingAutoCompactResults = new ArrayList<CodingSessionCompactResult>();
    private final List<Exception> pendingAutoCompactErrors = new ArrayList<Exception>();
    private final List<CodingLoopDecision> pendingLoopDecisions = new ArrayList<CodingLoopDecision>();
    private int consecutiveAutoCompactFailures;
    private boolean autoCompactCircuitBreakerOpen;

    public CodingSession(AgentSession delegate,
                         WorkspaceContext workspaceContext,
                         CodingAgentOptions options,
                         SessionProcessRegistry processRegistry) {
        this(UUID.randomUUID().toString(), delegate, workspaceContext, options, processRegistry, null);
    }

    public CodingSession(String sessionId,
                         AgentSession delegate,
                         WorkspaceContext workspaceContext,
                         CodingAgentOptions options,
                         SessionProcessRegistry processRegistry) {
        this(sessionId, delegate, workspaceContext, options, processRegistry, null);
    }

    public CodingSession(AgentSession delegate,
                         WorkspaceContext workspaceContext,
                         CodingAgentOptions options,
                         SessionProcessRegistry processRegistry,
                         CodingRuntime runtime) {
        this(UUID.randomUUID().toString(), delegate, workspaceContext, options, processRegistry, runtime);
    }

    public CodingSession(String sessionId,
                         AgentSession delegate,
                         WorkspaceContext workspaceContext,
                         CodingAgentOptions options,
                         SessionProcessRegistry processRegistry,
                         CodingRuntime runtime) {
        this.sessionId = sessionId;
        this.delegate = delegate;
        this.workspaceContext = workspaceContext;
        this.options = options;
        this.processRegistry = processRegistry;
        this.runtime = runtime;
    }

    public CodingAgentResult run(String input) throws Exception {
        return run(CodingAgentRequest.builder().input(input).build());
    }

    public CodingAgentResult run(CodingAgentRequest request) throws Exception {
        clearPendingLoopArtifacts();
        return LOOP_CONTROLLER.run(this, request);
    }

    public CodingAgentResult runStream(CodingAgentRequest request, AgentListener listener) throws Exception {
        clearPendingLoopArtifacts();
        return LOOP_CONTROLLER.runStream(this, request, listener);
    }

    public String getSessionId() {
        return sessionId;
    }

    public AgentSession getDelegate() {
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

    public CodingDelegateResult delegate(CodingDelegateRequest request) throws Exception {
        if (runtime == null) {
            throw new IllegalStateException("coding runtime is unavailable for this session");
        }
        return runtime.delegate(this, request);
    }

    public CodingSessionSnapshot snapshot() {
        MemorySnapshot snapshot = exportMemory();
        List<Object> items = snapshot == null || snapshot.getItems() == null
                ? Collections.<Object>emptyList()
                : snapshot.getItems();
        List<BashProcessInfo> processes = listProcessInfos();
        List<StoredProcessSnapshot> processSnapshots = exportProcessSnapshots();
        CodingSessionCheckpoint effectiveCheckpoint = resolveCheckpoint(snapshot, processSnapshots, items.size(), false);
        String renderedSummary = CodingSessionCheckpointFormatter.render(effectiveCheckpoint);
        return CodingSessionSnapshot.builder()
                .sessionId(sessionId)
                .workspaceRoot(workspaceContext == null ? null : workspaceContext.getRootPath())
                .memoryItemCount(items == null ? 0 : items.size())
                .summary(renderedSummary)
                .checkpointGoal(effectiveCheckpoint == null ? null : effectiveCheckpoint.getGoal())
                .checkpointGeneratedAtEpochMs(effectiveCheckpoint == null ? 0L : effectiveCheckpoint.getGeneratedAtEpochMs())
                .checkpointSplitTurn(effectiveCheckpoint != null && effectiveCheckpoint.isSplitTurn())
                .processCount(processes.size())
                .activeProcessCount(processRegistry == null ? 0 : processRegistry.activeCount())
                .restoredProcessCount(processRegistry == null ? 0 : processRegistry.restoredCount())
                .estimatedContextTokens(COMPACTOR.estimateContextTokens(items, renderedSummary))
                .lastCompactMode(resolveCompactMode(latestCompactResult))
                .lastCompactBeforeItemCount(latestCompactResult == null ? 0 : latestCompactResult.getBeforeItemCount())
                .lastCompactAfterItemCount(latestCompactResult == null ? 0 : latestCompactResult.getAfterItemCount())
                .lastCompactTokensBefore(latestCompactResult == null ? 0 : latestCompactResult.getEstimatedTokensBefore())
                .lastCompactTokensAfter(latestCompactResult == null ? 0 : latestCompactResult.getEstimatedTokensAfter())
                .lastCompactStrategy(latestCompactResult == null ? null : latestCompactResult.getStrategy())
                .lastCompactSummary(latestCompactResult == null ? null : latestCompactResult.getSummary())
                .autoCompactFailureCount(consecutiveAutoCompactFailures)
                .autoCompactCircuitBreakerOpen(autoCompactCircuitBreakerOpen)
                .processes(processes)
                .build();
    }

    public CodingSessionState exportState() {
        MemorySnapshot snapshot = exportMemory();
        List<Object> items = snapshot == null || snapshot.getItems() == null
                ? Collections.<Object>emptyList()
                : snapshot.getItems();
        List<StoredProcessSnapshot> processSnapshots = exportProcessSnapshots();
        return CodingSessionState.builder()
                .sessionId(sessionId)
                .workspaceRoot(workspaceContext == null ? null : workspaceContext.getRootPath())
                .memorySnapshot(snapshot)
                .processCount(processSnapshots.size())
                .checkpoint(resolveCheckpoint(snapshot, processSnapshots, items.size(), false))
                .latestCompactResult(copyCompactResult(latestCompactResult))
                .autoCompactFailureCount(consecutiveAutoCompactFailures)
                .autoCompactCircuitBreakerOpen(autoCompactCircuitBreakerOpen)
                .processSnapshots(processSnapshots)
                .build();
    }

    public void restore(CodingSessionState state) {
        if (state == null) {
            return;
        }
        AgentMemory memory = resolveMemory();
        if (!(memory instanceof InMemoryAgentMemory)) {
            throw new IllegalStateException("restore is only supported for InMemoryAgentMemory");
        }
        ((InMemoryAgentMemory) memory).restore(state.getMemorySnapshot());
        if (processRegistry != null) {
            processRegistry.restoreSnapshots(state.getProcessSnapshots());
        }
        clearPendingLoopArtifacts();
        checkpoint = copyCheckpoint(state.getCheckpoint());
        latestCompactResult = copyCompactResult(state.getLatestCompactResult());
        if (checkpoint == null) {
            checkpoint = latestCompactResult == null ? null : copyCheckpoint(latestCompactResult.getCheckpoint());
        }
        if (checkpoint == null) {
            checkpoint = resolveCheckpoint(state.getMemorySnapshot(), state.getProcessSnapshots(), 0, false);
        }
        consecutiveAutoCompactFailures = Math.max(0, state.getAutoCompactFailureCount());
        autoCompactCircuitBreakerOpen = state.isAutoCompactCircuitBreakerOpen();
    }

    public CodingSessionCompactResult compact() {
        return compact(null);
    }

    public CodingSessionCompactResult compact(String summary) {
        InMemoryAgentMemory inMemoryMemory = requireInMemoryMemory();
        try {
            CodingSessionCompactResult result = COMPACTOR.compact(
                    sessionId,
                    delegate == null ? null : delegate.getContext(),
                    inMemoryMemory,
                    options,
                    summary,
                    exportProcessSnapshots(),
                    checkpoint,
                    true
            );
            checkpoint = result == null ? null : result.getCheckpoint();
            latestCompactResult = copyCompactResult(result);
            clearPendingLoopArtifacts();
            resetAutoCompactFailureTracking();
            return result;
        } catch (Exception ex) {
            throw propagateCompactException(ex);
        }
    }

    public List<BashProcessInfo> listProcesses() {
        return listProcessInfos();
    }

    public BashProcessInfo processStatus(String processId) {
        requireProcessRegistry();
        return processRegistry.status(processId);
    }

    public BashProcessLogChunk processLogs(String processId, Long offset, Integer limit) {
        requireProcessRegistry();
        return processRegistry.logs(processId, offset, limit);
    }

    public int writeProcess(String processId, String input) throws IOException {
        requireProcessRegistry();
        return processRegistry.write(processId, input);
    }

    public BashProcessInfo stopProcess(String processId) {
        requireProcessRegistry();
        return processRegistry.stop(processId);
    }

    public CodingSessionCompactResult drainLastAutoCompactResult() {
        CodingSessionCompactResult result = lastAutoCompactResult;
        lastAutoCompactResult = null;
        if (!pendingAutoCompactResults.isEmpty()) {
            pendingAutoCompactResults.remove(pendingAutoCompactResults.size() - 1);
        }
        return result;
    }

    public Exception drainLastAutoCompactError() {
        Exception error = lastAutoCompactError;
        lastAutoCompactError = null;
        if (!pendingAutoCompactErrors.isEmpty()) {
            pendingAutoCompactErrors.remove(pendingAutoCompactErrors.size() - 1);
        }
        return error;
    }

    public void clearAutoCompactState() {
        lastAutoCompactResult = null;
        lastAutoCompactError = null;
        pendingAutoCompactResults.clear();
        pendingAutoCompactErrors.clear();
    }

    public CodingSessionCompactResult getLastAutoCompactResult() {
        return copyCompactResult(lastAutoCompactResult);
    }

    public Exception getLastAutoCompactError() {
        return lastAutoCompactError;
    }

    public List<CodingSessionCompactResult> drainAutoCompactResults() {
        if (pendingAutoCompactResults.isEmpty()) {
            return Collections.emptyList();
        }
        List<CodingSessionCompactResult> drained = new ArrayList<CodingSessionCompactResult>();
        for (CodingSessionCompactResult result : pendingAutoCompactResults) {
            drained.add(copyCompactResult(result));
        }
        pendingAutoCompactResults.clear();
        lastAutoCompactResult = null;
        return drained;
    }

    public List<Exception> drainAutoCompactErrors() {
        if (pendingAutoCompactErrors.isEmpty()) {
            return Collections.emptyList();
        }
        List<Exception> drained = new ArrayList<Exception>(pendingAutoCompactErrors);
        pendingAutoCompactErrors.clear();
        lastAutoCompactError = null;
        return drained;
    }

    public void recordLoopDecision(CodingLoopDecision decision) {
        if (decision != null) {
            pendingLoopDecisions.add(decision.toBuilder().build());
        }
    }

    public List<CodingLoopDecision> drainLoopDecisions() {
        if (pendingLoopDecisions.isEmpty()) {
            return Collections.emptyList();
        }
        List<CodingLoopDecision> drained = new ArrayList<CodingLoopDecision>();
        for (CodingLoopDecision decision : pendingLoopDecisions) {
            drained.add(decision == null ? null : decision.toBuilder().build());
        }
        pendingLoopDecisions.clear();
        return drained;
    }

    public void clearPendingLoopArtifacts() {
        clearAutoCompactState();
        pendingLoopDecisions.clear();
    }

    private void maybeAutoCompactAfterTurn() {
        lastAutoCompactResult = null;
        lastAutoCompactError = null;
        if (options == null || !options.isAutoCompactEnabled()) {
            return;
        }
        InMemoryAgentMemory inMemoryMemory;
        try {
            inMemoryMemory = requireInMemoryMemory();
        } catch (Exception ex) {
            lastAutoCompactError = ex;
            pendingAutoCompactErrors.add(ex);
            return;
        }
        if (autoCompactCircuitBreakerOpen) {
            lastAutoCompactError = new IllegalStateException(
                    "Automatic compaction is paused after " + consecutiveAutoCompactFailures
                            + " consecutive failures. Run a manual compact or reduce context to reset."
            );
            pendingAutoCompactErrors.add(lastAutoCompactError);
            return;
        }
        try {
            MemorySnapshot snapshot = inMemoryMemory.snapshot();
            CodingToolResultMicroCompactResult microCompactResult = TOOL_RESULT_MICRO_COMPACTOR.compact(
                    snapshot,
                    options,
                    resolveAutoCompactTargetTokens()
            );
            if (microCompactResult != null
                    && microCompactResult.getAfterTokens() <= resolveAutoCompactTargetTokens()) {
                inMemoryMemory.restore(MemorySnapshot.from(
                        microCompactResult.getItems(),
                        snapshot == null ? null : snapshot.getSummary()
                ));
                lastAutoCompactResult = buildMicroCompactResult(snapshot, microCompactResult);
                latestCompactResult = copyCompactResult(lastAutoCompactResult);
                pendingAutoCompactResults.add(copyCompactResult(lastAutoCompactResult));
                resetAutoCompactFailureTracking();
                return;
            }
            lastAutoCompactResult = COMPACTOR.compact(
                    sessionId,
                    delegate == null ? null : delegate.getContext(),
                    inMemoryMemory,
                    options,
                    null,
                    exportProcessSnapshots(),
                    checkpoint,
                    false
            );
            checkpoint = lastAutoCompactResult == null ? checkpoint : lastAutoCompactResult.getCheckpoint();
            latestCompactResult = copyCompactResult(lastAutoCompactResult);
            if (lastAutoCompactResult != null) {
                pendingAutoCompactResults.add(copyCompactResult(lastAutoCompactResult));
            }
            resetAutoCompactFailureTracking();
        } catch (Exception ex) {
            recordAutoCompactFailure(ex);
        }
    }

    private InMemoryAgentMemory requireInMemoryMemory() {
        AgentMemory memory = resolveMemory();
        if (!(memory instanceof InMemoryAgentMemory)) {
            throw new IllegalStateException("compact is only supported for InMemoryAgentMemory");
        }
        return (InMemoryAgentMemory) memory;
    }

    private void requireProcessRegistry() {
        if (processRegistry == null) {
            throw new IllegalStateException("process registry is unavailable");
        }
    }

    private List<BashProcessInfo> listProcessInfos() {
        if (processRegistry == null) {
            return Collections.emptyList();
        }
        return new ArrayList<BashProcessInfo>(processRegistry.list());
    }

    private List<StoredProcessSnapshot> exportProcessSnapshots() {
        if (processRegistry == null) {
            return Collections.emptyList();
        }
        return processRegistry.exportSnapshots();
    }

    @Override
    public void close() {
        if (processRegistry != null) {
            processRegistry.close();
        }
    }

    private AgentMemory resolveMemory() {
        return delegate == null || delegate.getContext() == null ? null : delegate.getContext().getMemory();
    }

    private MemorySnapshot exportMemory() {
        AgentMemory memory = resolveMemory();
        if (memory == null) {
            return MemorySnapshot.from(Collections.emptyList(), null);
        }
        if (memory instanceof InMemoryAgentMemory) {
            return ((InMemoryAgentMemory) memory).snapshot();
        }
        return MemorySnapshot.from(memory.getItems(), memory.getSummary());
    }

    private CodingSessionCheckpoint resolveCheckpoint(MemorySnapshot snapshot,
                                                      List<StoredProcessSnapshot> processSnapshots,
                                                      int sourceItemCount,
                                                      boolean splitTurn) {
        if (checkpoint != null) {
            checkpoint = checkpoint.toBuilder()
                    .processSnapshots(copyProcesses(processSnapshots))
                    .sourceItemCount(sourceItemCount)
                    .splitTurn(splitTurn)
                    .build();
            return checkpoint;
        }
        String summary = snapshot == null ? null : snapshot.getSummary();
        checkpoint = CodingSessionCheckpointFormatter.create(summary, processSnapshots, sourceItemCount, splitTurn);
        return checkpoint;
    }

    private List<StoredProcessSnapshot> copyProcesses(List<StoredProcessSnapshot> processSnapshots) {
        if (processSnapshots == null || processSnapshots.isEmpty()) {
            return Collections.emptyList();
        }
        List<StoredProcessSnapshot> copies = new ArrayList<StoredProcessSnapshot>();
        for (StoredProcessSnapshot snapshot : processSnapshots) {
            if (snapshot != null) {
                copies.add(snapshot.toBuilder().build());
            }
        }
        return copies;
    }

    private RuntimeException propagateCompactException(Exception ex) {
        if (ex instanceof RuntimeException) {
            return (RuntimeException) ex;
        }
        return new IllegalStateException("Failed to compact coding session", ex);
    }

    private String resolveCompactMode(CodingSessionCompactResult result) {
        if (result == null) {
            return null;
        }
        return result.isAutomatic() ? "auto" : "manual";
    }

    private int resolveAutoCompactTargetTokens() {
        if (options == null) {
            return 0;
        }
        return Math.max(0, options.getCompactContextWindowTokens() - options.getCompactReserveTokens());
    }

    private void resetAutoCompactFailureTracking() {
        consecutiveAutoCompactFailures = 0;
        autoCompactCircuitBreakerOpen = false;
    }

    private void recordAutoCompactFailure(Exception ex) {
        consecutiveAutoCompactFailures += 1;
        int threshold = options == null ? 0 : Math.max(0, options.getAutoCompactMaxConsecutiveFailures());
        if (threshold > 0 && consecutiveAutoCompactFailures >= threshold) {
            autoCompactCircuitBreakerOpen = true;
            lastAutoCompactError = new IllegalStateException(
                    "Automatic compaction failed " + consecutiveAutoCompactFailures
                            + " consecutive times; circuit breaker opened.",
                    ex
            );
        } else {
            lastAutoCompactError = ex;
        }
        pendingAutoCompactErrors.add(lastAutoCompactError);
    }

    private CodingSessionCompactResult buildMicroCompactResult(MemorySnapshot snapshot,
                                                               CodingToolResultMicroCompactResult microCompactResult) {
        List<Object> rawItems = snapshot == null || snapshot.getItems() == null
                ? Collections.<Object>emptyList()
                : snapshot.getItems();
        return CodingSessionCompactResult.builder()
                .sessionId(sessionId)
                .beforeItemCount(rawItems.size())
                .afterItemCount(microCompactResult.getItems() == null ? 0 : microCompactResult.getItems().size())
                .summary(microCompactResult.getSummary())
                .automatic(true)
                .splitTurn(false)
                .estimatedTokensBefore(microCompactResult.getBeforeTokens())
                .estimatedTokensAfter(microCompactResult.getAfterTokens())
                .strategy("tool-result-micro")
                .compactedToolResultCount(microCompactResult.getCompactedToolResultCount())
                .checkpoint(checkpoint == null ? null : checkpoint.toBuilder().build())
                .build();
    }

    private CodingSessionCompactResult copyCompactResult(CodingSessionCompactResult result) {
        if (result == null) {
            return null;
        }
        return result.toBuilder()
                .checkpoint(copyCheckpoint(result.getCheckpoint()))
                .build();
    }

    private CodingSessionCheckpoint copyCheckpoint(CodingSessionCheckpoint source) {
        if (source == null) {
            return null;
        }
        return source.toBuilder()
                .constraints(source.getConstraints() == null ? new ArrayList<String>() : new ArrayList<String>(source.getConstraints()))
                .doneItems(source.getDoneItems() == null ? new ArrayList<String>() : new ArrayList<String>(source.getDoneItems()))
                .inProgressItems(source.getInProgressItems() == null ? new ArrayList<String>() : new ArrayList<String>(source.getInProgressItems()))
                .blockedItems(source.getBlockedItems() == null ? new ArrayList<String>() : new ArrayList<String>(source.getBlockedItems()))
                .keyDecisions(source.getKeyDecisions() == null ? new ArrayList<String>() : new ArrayList<String>(source.getKeyDecisions()))
                .nextSteps(source.getNextSteps() == null ? new ArrayList<String>() : new ArrayList<String>(source.getNextSteps()))
                .criticalContext(source.getCriticalContext() == null ? new ArrayList<String>() : new ArrayList<String>(source.getCriticalContext()))
                .processSnapshots(copyProcesses(source.getProcessSnapshots()))
                .build();
    }

    public CodingAgentResult runSingleTurn(CodingAgentRequest request, String hiddenInstructions) throws Exception {
        AgentResult result = CodingSessionScope.runWithSession(this, new CodingSessionScope.SessionCallable<AgentResult>() {
            @Override
            public AgentResult call() throws Exception {
                AgentSession executionSession = resolveExecutionSession(hiddenInstructions);
                Object input = request == null ? null : request.getInput();
                return executionSession.run(AgentRequest.builder().input(input).build());
            }
        });
        maybeAutoCompactAfterTurn();
        return CodingAgentResult.from(sessionId, result);
    }

    public CodingAgentResult runSingleTurnStream(CodingAgentRequest request,
                                                 AgentListener listener,
                                                 String hiddenInstructions) throws Exception {
        AgentResult result = CodingSessionScope.runWithSession(this, new CodingSessionScope.SessionCallable<AgentResult>() {
            @Override
            public AgentResult call() throws Exception {
                AgentSession executionSession = resolveExecutionSession(hiddenInstructions);
                Object input = request == null ? null : request.getInput();
                return executionSession.runStreamResult(AgentRequest.builder().input(input).build(), listener);
            }
        });
        maybeAutoCompactAfterTurn();
        return CodingAgentResult.from(sessionId, result);
    }

    private AgentSession resolveExecutionSession(String hiddenInstructions) {
        if (delegate == null || delegate.getContext() == null || isBlank(hiddenInstructions)) {
            return delegate;
        }
        return new AgentSession(
                delegate.getRuntime(),
                delegate.getContext().toBuilder()
                        .instructions(mergeText(delegate.getContext().getInstructions(), hiddenInstructions))
                        .build()
        );
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

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
