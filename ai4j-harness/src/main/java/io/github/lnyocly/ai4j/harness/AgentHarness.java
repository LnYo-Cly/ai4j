package io.github.lnyocly.ai4j.harness;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentExecutionStatus;
import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.control.AgentHostInputException;
import io.github.lnyocly.ai4j.agent.permission.AgentApprovalRequiredException;
import io.github.lnyocly.ai4j.agent.memory.MemorySnapshot;
import io.github.lnyocly.ai4j.agent.session.AgentSessionSnapshot;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.AgentToolExecutionStatus;
import io.github.lnyocly.ai4j.agent.tool.AgentToolResult;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Durable outer loop for an existing ai4j {@link Agent}.
 *
 * <p>Each public run executes one bounded slice. The Agent remains responsible
 * for model calls, context projection, compaction, MCP, Function Call,
 * permissions, sandboxing, hooks and all other existing capabilities. This
 * class owns the long-running concerns around it: a durable Execution, lease,
 * checkpoint, wait/wakeup, runtime-created Tasks and recovery.</p>
 */
public final class AgentHarness implements AutoCloseable {

    private static final long DEFAULT_LEASE_MILLIS = 60_000L;
    private static final String DEFAULT_WORKER_PREFIX = "ai4j-harness-worker-";
    private static final int EXECUTION_LOCK_STRIPES = 64;

    private final Agent agent;
    private final HarnessExecutionAdapter executionAdapter;
    private final HarnessStore store;
    private final HarnessPersistence persistence;
    private final HarnessCommandGateway gateway;
    private final HarnessContract contract;
    private final HarnessActor actor;
    private final String workerId;
    private final boolean autoResume;
    private final HarnessRunListener listener;
    private final ScheduledExecutorService heartbeatExecutor;
    private final ExecutorService continuationExecutor;
    private final ReentrantLock[] executionLocks;

    private AgentHarness(Builder builder) {
        if (builder.agent == null && builder.executionAdapter == null) {
            throw new IllegalArgumentException("agent or executionAdapter is required");
        }
        if (builder.agent != null && builder.executionAdapter != null) {
            throw new IllegalArgumentException("choose agent or executionAdapter, not both");
        }
        if (builder.persistence != null && builder.store != null) {
            throw new IllegalArgumentException("choose persistence or store, not both");
        }
        this.agent = builder.agent;
        this.executionAdapter = builder.executionAdapter == null
                ? new AgentHarnessExecutionAdapter(builder.agent) : builder.executionAdapter;
        this.persistence = builder.persistence;
        this.store = builder.store == null
                ? (builder.persistence == null ? null : builder.persistence.getStore())
                : builder.store;
        if (this.store == null) {
            throw new IllegalArgumentException("durable Harness persistence is required");
        }
        this.contract = builder.contract == null ? HarnessContract.builder().build() : builder.contract;
        this.actor = builder.actor == null ? HarnessActor.agent("ai4j-agent") : builder.actor;
        this.workerId = builder.workerId == null || builder.workerId.trim().isEmpty()
                ? DEFAULT_WORKER_PREFIX + UUID.randomUUID().toString().replace("-", "")
                : builder.workerId.trim();
        this.autoResume = builder.autoResume;
        this.listener = builder.listener;
        this.gateway = new HarnessCommandGateway(store, contract, actor);
        this.heartbeatExecutor = Executors.newScheduledThreadPool(1);
        this.continuationExecutor = Executors.newCachedThreadPool();
        this.executionLocks = new ReentrantLock[EXECUTION_LOCK_STRIPES];
        for (int i = 0; i < executionLocks.length; i++) {
            executionLocks[i] = new ReentrantLock();
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static AgentHarness file(Path directory, Agent agent) {
        return builder().agent(agent).persistence(HarnessPersistence.file(directory)).build();
    }

    public static AgentHarness file(Path directory,
                                    Agent agent,
                                    HarnessContract contract) {
        return builder().agent(agent).contract(contract)
                .persistence(HarnessPersistence.file(directory)).build();
    }

    public static AgentHarness jdbc(DataSource dataSource,
                                    String harnessId,
                                    Agent agent) {
        return builder().agent(agent)
                .persistence(HarnessPersistence.jdbc(dataSource, harnessId)).build();
    }

    public Agent getAgent() {
        return agent;
    }

    public HarnessExecutionAdapter getExecutionAdapter() {
        return executionAdapter;
    }

    public HarnessCommandGateway getGateway() {
        return gateway;
    }

    public HarnessContract getContract() {
        return contract;
    }

    public HarnessRunResult run(Object input) {
        return run(HarnessRunRequest.input(input));
    }

    /** Executes one durable, bounded slice. */
    public HarnessRunResult run(HarnessRunRequest request) {
        HarnessRunRequest effectiveRequest = request == null
                ? HarnessRunRequest.input(null) : request;
        AgentRequest agentRequest = effectiveRequest.resolveAgentRequest();
        ExecutionRecord execution = resolveExecution(effectiveRequest, agentRequest);
        return executeExecution(execution, agentRequest, effectiveRequest.getBudget());
    }

    /** Convenience entry point for an application that already has a Task id. */
    public HarnessRunResult runTask(String taskId, Object input) {
        return run(HarnessRunRequest.builder().taskId(taskId).input(input).build());
    }

    /** Resumes a READY execution; WAITING executions must first receive a wakeup. */
    public HarnessRunResult resume(String executionId) {
        return run(HarnessRunRequest.builder().executionId(executionId).build());
    }

    /** Finds the latest non-terminal execution for a Task and resumes it. */
    public HarnessRunResult resumeTask(String taskId) {
        String taskKey = required(taskId, "task id");
        ExecutionRecord candidate = null;
        for (ExecutionRecord execution : gateway.listExecutions()) {
            if (execution == null || !taskKey.equals(execution.getTaskId())) {
                continue;
            }
            if (execution.getStatus() == ExecutionStatus.READY
                    || execution.getStatus() == ExecutionStatus.RUNNING
                    || execution.getStatus() == ExecutionStatus.WAITING) {
                if (candidate == null || execution.getUpdatedAtEpochMs() > candidate.getUpdatedAtEpochMs()) {
                    candidate = execution;
                }
            }
        }
        if (candidate == null) {
            return runTask(taskKey, null);
        }
        return resume(candidate.getExecutionId());
    }

    /**
     * Delivers a host/user/external answer. The answer and resumed Session are
     * persisted atomically before the Execution becomes READY, then a new
     * bounded slice is run synchronously.
     */
    public HarnessRunResult deliver(String waitId, Object input) {
        return deliverInternal(waitId, input, true);
    }

    /** Runs up to {@code budget.maxExecutions} currently runnable Tasks. */
    public List<HarnessRunResult> runReady(HarnessRunBudget budget) {
        HarnessRunBudget effective = budget == null ? HarnessRunBudget.builder().build() : budget;
        int limit = effective.getMaxExecutions() <= 0 ? 1 : effective.getMaxExecutions();
        List<HarnessRunResult> results = new ArrayList<HarnessRunResult>();
        for (int i = 0; i < limit; i++) {
            List<TaskRecord> runnable = gateway.listRunnableTasks();
            if (runnable.isEmpty()) {
                break;
            }
            TaskRecord task = runnable.get(0);
            HarnessRunRequest request = HarnessRunRequest.builder()
                    .taskId(task.getTaskId())
                    .budget(effective)
                    .build();
            results.add(run(request));
        }
        return results;
    }

    private ExecutionRecord resolveExecution(HarnessRunRequest request,
                                              AgentRequest agentRequest) {
        String requestedExecutionId = trimToNull(request.getExecutionId());
        if (requestedExecutionId != null) {
            ExecutionRecord execution = gateway.getExecution(requestedExecutionId);
            if (execution == null) {
                throw new HarnessValidationException("execution not found: " + requestedExecutionId);
            }
            String requestedScope = trimToNull(request.getScopeKey());
            if (requestedScope != null && !requestedScope.equals(trimToNull(execution.getScopeKey()))) {
                throw new HarnessConflictException("execution does not belong to requested scope: "
                        + requestedExecutionId);
            }
            return execution;
        }
        String sessionId = firstNonBlank(request.getSessionId(),
                agentRequest == null ? null : agentRequest.getMetadataString(AgentRequest.METADATA_KEY_SESSION_ID));
        String runId = agentRequest == null ? null
                : agentRequest.getMetadataString(AgentRequest.METADATA_KEY_RUN_ID);
        if (sessionId == null) {
            sessionId = "session_" + UUID.randomUUID().toString().replace("-", "");
        }
        return gateway.createExecution(HarnessExecutionSpec.builder()
                .taskId(trimToNull(request.getTaskId()))
                .scopeKey(trimToNull(request.getScopeKey()))
                .sessionId(sessionId)
                .runId(runId)
                .inputSummary(inputSummary(agentRequest == null ? null : agentRequest.getInput()))
                .idempotencyKey(trimToNull(request.getIdempotencyKey()))
                .build(), actor);
    }

    private HarnessRunResult executeExecution(ExecutionRecord source,
                                               AgentRequest request,
                                               HarnessRunBudget budget) {
        if (source == null) {
            throw new HarnessValidationException("execution is required");
        }
        ReentrantLock executionLock = executionLock(source.getExecutionId());
        executionLock.lock();
        try {
            // The caller may have read a READY copy before another local
            // request completed the same Execution. Refresh under the mutex
            // so the second request observes the committed terminal/waiting
            // state instead of claiming and running it again.
            ExecutionRecord current = gateway.getExecution(source.getExecutionId());
            if (current == null) {
                throw new HarnessValidationException("execution not found: " + source.getExecutionId());
            }
            return executeExecutionLocked(current, request, budget);
        } finally {
            executionLock.unlock();
        }
    }

    private HarnessRunResult executeExecutionLocked(ExecutionRecord source,
                                                     AgentRequest request,
                                                     HarnessRunBudget budget) {
        if (source.getStatus() == ExecutionStatus.WAITING) {
            return result(HarnessRunStatus.WAITING, source, null, source.getOutputText(),
                    source.getWaitId(), source.getOperationId(), source.getError());
        }
        if (source.getStatus() == ExecutionStatus.UNKNOWN) {
            return result(HarnessRunStatus.UNKNOWN, source, task(source), source.getOutputText(),
                    source.getWaitId(), source.getOperationId(), source.getError());
        }
        if (source.getStatus() == ExecutionStatus.SUCCEEDED) {
            return result(HarnessRunStatus.COMPLETED, source, task(source), source.getOutputText(),
                    null, null, source.getError());
        }
        if (source.getStatus() == ExecutionStatus.FAILED) {
            return result(HarnessRunStatus.FAILED, source, task(source), source.getOutputText(),
                    null, null, source.getError());
        }
        if (source.getStatus() == ExecutionStatus.CANCELLED) {
            return result(HarnessRunStatus.CANCELLED, source, task(source), null,
                    null, null, source.getError());
        }

        HarnessRunBudget effectiveBudget = budget == null ? HarnessRunBudget.builder().build() : budget;
        long leaseDuration = effectiveBudget.getLeaseDurationMillis() <= 0L
                ? DEFAULT_LEASE_MILLIS : effectiveBudget.getLeaseDurationMillis();
        ExecutionRecord claimed = gateway.claimExecution(source.getExecutionId(),
                effectiveBudget.getWorkerId() == null ? workerId : effectiveBudget.getWorkerId(),
                leaseDuration);
        String effectiveWorker = claimed.getWorkerId();
        ScheduledFuture<?> heartbeat = scheduleHeartbeat(claimed, effectiveWorker, leaseDuration);
        HarnessExecutionContext executionContext = new HarnessExecutionContext(
                gateway,
                claimed.getExecutionId(),
                claimed.getTaskId(),
                claimed.getSessionId(),
                claimed.getScopeKey(),
                claimed.getRunId(),
                actor,
                new HarnessExecutionContext.AsyncCompletionHandler() {
                    @Override
                    public void onCompletion(String waitId,
                                             String operationId,
                                             String invocationId,
                                             Object value,
                                             Throwable error) {
                        handleAsyncCompletion(claimed.getExecutionId(), waitId, operationId,
                                invocationId, value, error);
                    }
                });
        return executeAdaptedExecution(claimed, executionContext, request, effectiveBudget, heartbeat);
    }

    private ReentrantLock executionLock(String executionId) {
        String key = required(executionId, "execution id");
        int hash = key.hashCode() & Integer.MAX_VALUE;
        return executionLocks[hash % executionLocks.length];
    }

    private HarnessRunResult executeAdaptedExecution(ExecutionRecord claimed,
                                                      HarnessExecutionContext executionContext,
                                                      AgentRequest request,
                                                      HarnessRunBudget budget,
                                                      ScheduledFuture<?> heartbeat) {
        HarnessAdapterExecution adapterExecution = null;
        HarnessExecutionAdapterSession adapterSession = null;
        HarnessAdapterState adapterState = null;
        ExecutionStatus executionStatus;
        String outputText = null;
        String errorText = null;
        String waitId = null;
        String operationId = null;
        try {
            HarnessAdapterState previousState = previousAdapterState(claimed);
            adapterSession = executionAdapter.open(
                    executionContext,
                    budget,
                    previousState);
            adapterExecution = adapterSession.run(prepareRequest(request, claimed));
            if (adapterExecution == null) {
                throw new IllegalStateException("execution adapter returned no result");
            }
            AgentExecutionStatus status = adapterExecution.getStatus() == null
                    ? AgentExecutionStatus.COMPLETED : adapterExecution.getStatus();
            executionStatus = mapStatus(status);
            outputText = adapterExecution.getOutputText();
            errorText = adapterExecution.getError();
            waitId = adapterExecution.getWaitId();
            operationId = adapterExecution.getOperationId();
            if (executionStatus == ExecutionStatus.WAITING && waitId == null) {
                WaitRecord wait = gateway.ensureWait(claimed.getExecutionId(), claimed.getTaskId(),
                        null, WaitType.EXTERNAL_EVENT, operationId, null,
                        Collections.<String, Object>singletonMap("source", "execution-adapter"), actor);
                waitId = wait.getWaitId();
            }
        } catch (AgentHostInputException inputException) {
            WaitRecord wait = gateway.ensureWait(claimed.getExecutionId(), claimed.getTaskId(),
                    null, WaitType.USER_INPUT, null, null,
                    Collections.<String, Object>singletonMap("request", inputException.getRequest()), actor);
            executionStatus = ExecutionStatus.WAITING;
            waitId = wait.getWaitId();
            outputText = "HARNESS_USER_INPUT_REQUIRED: " + JSON.toJSONString(inputException.getRequest());
        } catch (AgentApprovalRequiredException approvalException) {
            String toolName = approvalException.getRequest() == null
                    ? null : approvalException.getRequest().getToolName();
            String callId = approvalException.getRequest() == null
                    ? null : approvalException.getRequest().getCallId();
            WaitRecord wait = gateway.requestApproval(claimed.getExecutionId(), claimed.getTaskId(),
                    toolName, callId, approvalException.getRequest() == null
                            ? null : approvalException.getRequest().getArguments(), actor);
            executionStatus = ExecutionStatus.WAITING;
            waitId = wait.getWaitId();
            outputText = "HARNESS_APPROVAL_REQUIRED: waitId=" + waitId;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            executionStatus = ExecutionStatus.UNKNOWN;
            errorText = "execution adapter interrupted; side effects require reconciliation";
        } catch (Exception failure) {
            executionStatus = ExecutionStatus.FAILED;
            errorText = failure.getMessage() == null ? failure.toString() : failure.getMessage();
        } finally {
            if (adapterSession != null) {
                try {
                    adapterState = adapterSession.snapshot();
                } catch (RuntimeException ignored) {
                    // The execution result remains authoritative when an
                    // adapter cannot export a late recovery snapshot.
                }
                try {
                    adapterSession.close();
                } catch (RuntimeException ignored) {
                    // Resource cleanup must not hide the durable outcome.
                }
            }
            if (heartbeat != null) {
                heartbeat.cancel(false);
            }
        }

        Map<String, Object> checkpointState = adapterExecution == null
                ? new LinkedHashMap<String, Object>()
                : new LinkedHashMap<String, Object>(adapterExecution.getCheckpointState() == null
                        ? Collections.<String, Object>emptyMap() : adapterExecution.getCheckpointState());
        if (adapterState == null && adapterExecution != null) {
            adapterState = adapterExecution.getState();
        }
        if (adapterState != null) {
            HarnessAdapterState state = adapterState;
            checkpointState.put("harnessAdapterType", state.getAdapterType());
            checkpointState.put("harnessAdapterState", JSON.parseObject(JSON.toJSONString(state)));
        }
        AgentSessionSnapshot legacySessionSnapshot = executionAdapter instanceof AgentHarnessExecutionAdapter
                ? ((AgentHarnessExecutionAdapter) executionAdapter).sessionSnapshot(adapterState) : null;
        String checkpointSummary = adapterExecution == null ? null : adapterExecution.getCheckpointSummary();
        HarnessExecutionOutcome outcome = HarnessExecutionOutcome.builder()
                .executionId(claimed.getExecutionId())
                .leaseId(claimed.getLeaseId())
                .fencingToken(claimed.getFencingToken())
                .status(executionStatus)
                .outputText(outputText)
                .error(errorText)
                .waitId(waitId)
                .operationId(operationId)
                .checkpointSummary(checkpointSummary == null
                        ? checkpointSummary(executionStatus, outputText, errorText) : checkpointSummary)
                .checkpointState(checkpointState)
                .sessionSnapshot(legacySessionSnapshot)
                .build();
        ExecutionRecord persisted;
        try {
            persisted = gateway.persistExecutionOutcome(outcome);
        } catch (RuntimeException persistenceFailure) {
            ExecutionRecord durable = null;
            try {
                durable = gateway.markExecutionUnknownIfLeaseLost(
                        claimed.getExecutionId(), claimed.getLeaseId(), claimed.getFencingToken(),
                        claimed.getWorkerId(), "execution outcome could not be persisted: "
                                + persistenceFailure.getMessage());
            } catch (RuntimeException ignored) {
                // A newer worker may have reclaimed the execution, or the
                // store may be temporarily unavailable. The durable state is
                // authoritative; report its current view below.
            }
            if (durable == null) {
                durable = gateway.getExecution(claimed.getExecutionId());
            }
            if (durable == null) {
                return result(HarnessRunStatus.UNKNOWN, null, null, outputText,
                        waitId, operationId, persistenceFailure.getMessage());
            }
            HarnessRunResult durableResult = resultForExecution(durable);
            durableResult.setError(persistenceFailure.getMessage());
            return durableResult;
        }
        activateAsyncCompletions(executionContext);
        String exposedOutput = persisted.getStatus() == ExecutionStatus.CANCELLED ? null : outputText;
        HarnessRunResult completed = result(mapRunStatus(persisted.getStatus()), persisted, task(persisted),
                exposedOutput, persisted.getWaitId(), persisted.getOperationId(), errorText);
        Object adapterResult = adapterExecution == null ? null : adapterExecution.getResult();
        completed.setAdapterResult(adapterResult);
        if (adapterResult instanceof AgentResult) {
            completed.setAgentResult((AgentResult) adapterResult);
        }
        return completed;
    }

    private HarnessAdapterState previousAdapterState(ExecutionRecord execution) {
        HarnessAdapterState current = adapterStateFromExecution(execution);
        if (current != null || execution == null || execution.getSessionId() == null) {
            return current;
        }

        // A new host message normally creates a new Execution. Its checkpoint
        // is empty, but the adapter-owned session state belongs to the stable
        // session identity rather than to that new Execution. Reuse the latest
        // durable state for that session without binding the Task to it.
        HarnessAdapterState latest = null;
        long latestUpdatedAt = Long.MIN_VALUE;
        for (ExecutionRecord candidate : gateway.listExecutions()) {
            if (candidate == null || candidate.getExecutionId() == null
                    || candidate.getExecutionId().equals(execution.getExecutionId())
                    || !execution.getSessionId().equals(candidate.getSessionId())) {
                continue;
            }
            HarnessAdapterState candidateState = adapterStateFromExecution(candidate);
            if (candidateState == null) {
                continue;
            }
            if (latest == null || candidate.getUpdatedAtEpochMs() >= latestUpdatedAt) {
                latest = candidateState;
                latestUpdatedAt = candidate.getUpdatedAtEpochMs();
            }
        }
        return latest;
    }

    private HarnessAdapterState adapterStateFromExecution(ExecutionRecord execution) {
        if (execution == null || execution.getCheckpointId() == null) {
            return null;
        }
        CheckpointRecord checkpoint = gateway.getCheckpoint(execution.getCheckpointId());
        if (checkpoint == null || checkpoint.getState() == null) {
            return null;
        }
        Object raw = checkpoint.getState().get("harnessAdapterState");
        if (raw == null) {
            return null;
        }
        HarnessAdapterState state = raw instanceof String
                ? JSON.parseObject((String) raw, HarnessAdapterState.class)
                : JSON.parseObject(JSON.toJSONString(raw), HarnessAdapterState.class);
        if (state != null && state.getAdapterType() != null
                && !state.getAdapterType().equals(executionAdapter.getAdapterType())) {
            throw new HarnessConflictException("execution adapter type mismatch: " + state.getAdapterType());
        }
        return state;
    }

    private AgentRequest prepareRequest(AgentRequest request, ExecutionRecord execution) {
        AgentRequest.AgentRequestBuilder builder = request == null
                ? AgentRequest.builder() : request.toBuilder();
        Map<String, Object> metadata = request == null || request.getMetadata() == null
                ? new LinkedHashMap<String, Object>()
                : new LinkedHashMap<String, Object>(request.getMetadata());
        metadata.put(AgentRequest.METADATA_KEY_SESSION_ID, execution.getSessionId());
        metadata.put(AgentRequest.METADATA_KEY_RUN_ID, execution.getRunId());
        metadata.put(AgentRequest.METADATA_KEY_HARNESS_EXECUTION_ID, execution.getExecutionId());
        metadata.put(AgentRequest.METADATA_KEY_HARNESS_SCOPE, execution.getScopeKey());
        if (execution.getTaskId() != null) {
            metadata.put(AgentRequest.METADATA_KEY_HARNESS_TASK_ID, execution.getTaskId());
        }
        return builder.metadata(metadata).build();
    }

    private ScheduledFuture<?> scheduleHeartbeat(final ExecutionRecord execution,
                                                 final String worker,
                                                 long duration) {
        long interval = Math.max(100L, duration / 3L);
        return heartbeatExecutor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    gateway.heartbeat(execution.getExecutionId(), execution.getLeaseId(),
                            execution.getFencingToken(), worker, duration);
                } catch (RuntimeException ignored) {
                    // The next durable operation will surface the fencing or
                    // lease error and classify the execution as UNKNOWN.
                }
            }
        }, interval, interval, TimeUnit.MILLISECONDS);
    }

    private void activateAsyncCompletions(HarnessExecutionContext context) {
        for (HarnessExecutionContext.AsyncCompletionRegistration registration
                : context.drainAsyncCompletions()) {
            if (registration == null || registration.getCompletion() == null) {
                continue;
            }
            registration.getCompletion().whenComplete(new java.util.function.BiConsumer<Object, Throwable>() {
                @Override
                public void accept(Object value, Throwable error) {
                    if (context.getAsyncCompletionHandler() != null) {
                        context.getAsyncCompletionHandler().onCompletion(
                                registration.getWaitId(), registration.getOperationId(),
                                registration.getInvocationId(), value, unwrap(error));
                    }
                }
            });
        }
    }

    private void handleAsyncCompletion(String executionId,
                                       String waitId,
                                       String operationId,
                                       String invocationId,
                                       Object value,
                                       Throwable error) {
        Object delivery = value;
        String effectiveOperationId = operationId;
        String effectiveWaitId = waitId;
        String completionError = error == null ? null
                : (error.getMessage() == null ? error.toString() : error.getMessage());
        if (value instanceof AgentToolResult) {
            AgentToolResult result = (AgentToolResult) value;
            delivery = result.getOutput();
            if (effectiveOperationId == null) effectiveOperationId = result.getOperationId();
            if (effectiveWaitId == null) effectiveWaitId = result.getWaitId();
            if (completionError == null) completionError = result.getError();
        }
        if (error != null) {
            Map<String, Object> failure = new LinkedHashMap<String, Object>();
            failure.put("status", "FAILED");
            failure.put("error", completionError);
            delivery = failure;
        }
        if (invocationId != null) {
            try {
                contextCompleteToolInvocation(invocationId, effectiveOperationId, effectiveWaitId,
                        value, delivery, completionError);
            } catch (RuntimeException failure) {
                recordAsyncFailure(executionId, effectiveWaitId, effectiveOperationId,
                        invocationId, failure);
                return;
            }
        }
        if (effectiveWaitId == null) {
            return;
        }
        final String continuationOperationId = effectiveOperationId;
        final String continuationInvocationId = invocationId;
        try {
            WaitRecord wait = gateway.getWait(effectiveWaitId);
            if (wait == null || !executionId.equals(wait.getExecutionId())) {
                recordAsyncFailure(executionId, effectiveWaitId, continuationOperationId,
                        invocationId, new HarnessValidationException("async completion wait is unavailable"));
                return;
            }
            if (!WaitStatus.OPEN.equals(wait.getStatus())) {
                if (invocationId == null) {
                    recordQuarantinedAsyncCompletion(executionId, effectiveWaitId,
                            continuationOperationId, null, wait, delivery, completionError);
                }
                return;
            }
            final Object deliveredValue = delivery;
            final String deliveredWaitId = effectiveWaitId;
            continuationExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        HarnessRunResult result = deliverInternal(deliveredWaitId, deliveredValue, autoResume);
                        notifyAsyncResult(result, executionId, deliveredWaitId);
                    } catch (RuntimeException failure) {
                        recordAsyncFailure(executionId, deliveredWaitId, continuationOperationId,
                                continuationInvocationId, failure);
                    }
                }
            });
        } catch (RuntimeException ignored) {
            recordAsyncFailure(executionId, effectiveWaitId, continuationOperationId,
                    invocationId, ignored);
        }
    }

    /**
     * Persists the terminal result of an asynchronous business tool before its
     * wait is delivered.  A failed CompletionStage is classified UNKNOWN:
     * the future failed, but that alone cannot prove that the remote side did
     * not apply the side effect.
     */
    private void contextCompleteToolInvocation(String invocationId,
                                                String operationId,
                                                String waitId,
                                                Object rawResult,
                                                Object delivery,
                                                String error) {
        ToolInvocationStatus status;
        String output;
        String effectiveError = error;
        if (rawResult instanceof AgentToolResult) {
            AgentToolResult result = (AgentToolResult) rawResult;
            status = completionLedgerStatus(result, error);
            output = result.getOutput();
            if (effectiveError == null) {
                effectiveError = result.getError();
            }
            if (operationId == null) {
                operationId = result.getOperationId();
            }
            if (waitId == null) {
                waitId = result.getWaitId();
            }
        } else {
            status = error == null ? ToolInvocationStatus.SUCCEEDED : ToolInvocationStatus.UNKNOWN;
            output = error == null ? stringify(rawResult) : stringify(delivery);
        }
        gateway.completeToolInvocation(invocationId, status, operationId, waitId,
                output, effectiveError, actor);
    }

    private ToolInvocationStatus completionLedgerStatus(AgentToolResult result,
                                                         String completionError) {
        if (completionError != null) {
            return ToolInvocationStatus.UNKNOWN;
        }
        if (result == null || AgentToolExecutionStatus.COMPLETED.equals(result.getStatus())) {
            return result != null && result.isFailed()
                    ? ToolInvocationStatus.FAILED : ToolInvocationStatus.SUCCEEDED;
        }
        if (AgentToolExecutionStatus.UNKNOWN.equals(result.getStatus())) {
            return ToolInvocationStatus.UNKNOWN;
        }
        if (AgentToolExecutionStatus.FAILED.equals(result.getStatus()) || result.isFailed()) {
            return ToolInvocationStatus.FAILED;
        }
        // A CompletionStage that completes with another WAITING result is
        // malformed. Do not turn it into a retryable success.
        return ToolInvocationStatus.UNKNOWN;
    }

    /** Completes a pending invocation when a host directly delivers its Wait. */
    private void completePendingToolInvocation(WaitRecord wait, Object input) {
        if (wait == null || wait.getPayload() == null) {
            return;
        }
        Object rawInvocationId = wait.getPayload()
                .get(AgentToolCall.METADATA_KEY_HARNESS_INVOCATION_ID);
        String invocationId = trimToNull(rawInvocationId == null ? null : String.valueOf(rawInvocationId));
        if (invocationId == null) {
            return;
        }
        ToolInvocationRecord existing = gateway.getToolInvocationInScope(invocationId,
                executionScope(wait));
        if (existing == null || isTerminalInvocation(existing.getStatus())) {
            return;
        }
        if (WaitType.APPROVAL.equals(wait.getType())) {
            if (!gateway.deliveryApproved(input)) {
                gateway.completeToolInvocation(invocationId, ToolInvocationStatus.FAILED,
                        wait.getOperationId(), wait.getWaitId(),
                        "HARNESS_APPROVAL_DENIED", "approval was denied", actor);
            }
            // An approved invocation remains WAITING until the resumed Agent
            // re-reserves it. That transition is the single retry authority.
            return;
        }
        AgentToolResult result = input instanceof AgentToolResult
                ? (AgentToolResult) input : null;
        ToolInvocationStatus status = result == null
                ? ToolInvocationStatus.SUCCEEDED : completionLedgerStatus(result, null);
        String output = result == null ? stringify(input) : result.getOutput();
        String error = result == null ? null : result.getError();
        String operationId = result == null ? wait.getOperationId() : result.getOperationId();
        String waitId = result == null ? wait.getWaitId() : firstNonBlank(result.getWaitId(), wait.getWaitId());
        gateway.completeToolInvocation(invocationId, status, operationId, waitId,
                output, error, actor);
    }

    private String executionScope(WaitRecord wait) {
        if (wait == null || wait.getExecutionId() == null) {
            return null;
        }
        ExecutionRecord execution = gateway.getExecution(wait.getExecutionId());
        return execution == null ? null : execution.getScopeKey();
    }

    private boolean isTerminalInvocation(ToolInvocationStatus status) {
        return ToolInvocationStatus.SUCCEEDED.equals(status)
                || ToolInvocationStatus.FAILED.equals(status)
                || ToolInvocationStatus.UNKNOWN.equals(status)
                || ToolInvocationStatus.CANCELLED.equals(status);
    }

    private void recordQuarantinedAsyncCompletion(String executionId,
                                                  String waitId,
                                                  String operationId,
                                                  String invocationId,
                                                  WaitRecord wait,
                                                  Object delivery,
                                                  String error) {
        try {
            gateway.recordEvent("async.completion_quarantined", executionId,
                    mapOf("waitId", waitId,
                            "operationId", operationId,
                            "invocationId", invocationId,
                            "waitStatus", wait == null ? null : wait.getStatus(),
                            "delivery", delivery,
                            "error", error,
                            "lateCompletion", true,
                            "sideEffectStatus", "unknown"), actor);
        } catch (RuntimeException ignored) {
            // A closed or unavailable store must not execute a late callback.
        }
    }

    private void notifyAsyncResult(HarnessRunResult result,
                                   String executionId,
                                   String waitId) {
        if (listener == null || result == null) {
            return;
        }
        try {
            listener.onResult(result);
        } catch (RuntimeException listenerFailure) {
            recordAsyncFailure(executionId, waitId, null, null, listenerFailure);
        }
    }

    private void recordAsyncFailure(String executionId,
                                     String waitId,
                                     String operationId,
                                     String invocationId,
                                     RuntimeException failure) {
        try {
            gateway.recordEvent("async.completion_delivery_failed", executionId,
                    mapOf("waitId", waitId, "operationId", operationId,
                            "invocationId", invocationId,
                            "error", failure == null ? null : failure.getMessage()), actor);
        } catch (RuntimeException ignored) {
            // The Harness may already be closing; the durable execution state
            // remains authoritative when event recording is unavailable.
        }
    }

    private HarnessRunResult deliverInternal(String waitId, Object input, boolean resume) {
        String id = required(waitId, "wait id");
        WaitRecord initialWait = gateway.getWait(id);
        if (initialWait == null) {
            throw new HarnessValidationException("wait not found: " + id);
        }
        ExecutionRecord initialExecution = gateway.getExecution(initialWait.getExecutionId());
        if (initialExecution == null) {
            throw new HarnessValidationException("execution not found for wait: " + id);
        }
        ReentrantLock lock = executionLock(initialExecution.getExecutionId());
        lock.lock();
        try {
            // Re-read under the same local mutex used by run/resume. This
            // prevents two callbacks for the same wait from both opening and
            // running the same Agent slice.
            WaitRecord wait = gateway.getWait(id);
            ExecutionRecord execution = wait == null ? null : gateway.getExecution(wait.getExecutionId());
            if (wait == null) {
                throw new HarnessValidationException("wait not found: " + id);
            }
            if (execution == null) {
                throw new HarnessValidationException("execution not found for wait: " + id);
            }
            if (WaitStatus.DELIVERED.equals(wait.getStatus())) {
                // Gateway delivery is idempotent. A repeated delivery must
                // remain a read of the committed state and must never turn a
                // READY continuation into a second Agent run.
                return resultForExecution(execution);
            }
            if (!WaitStatus.OPEN.equals(wait.getStatus())) {
                throw new HarnessConflictException("wait is not open: " + id);
            }

            completePendingToolInvocation(wait, input);

            boolean replaceToolResult;
            if (executionAdapter != null) {
                HarnessAdapterState previousState = previousAdapterState(execution);
                HarnessAdapterDelivery delivery = executionAdapter.applyDelivery(previousState, wait, input);
                replaceToolResult = delivery != null && delivery.isReplacedPendingResult();
                HarnessAdapterState deliveredState = delivery == null ? previousState : delivery.getState();
                AgentSessionSnapshot legacySessionSnapshot = executionAdapter instanceof AgentHarnessExecutionAdapter
                        ? ((AgentHarnessExecutionAdapter) executionAdapter).sessionSnapshot(deliveredState) : null;
                gateway.deliverAdapterWait(id, input, deliveredState, legacySessionSnapshot, actor);
            } else {
                AgentSessionSnapshot snapshot = execution.getSessionId() == null
                        ? null : gateway.getSessionSnapshot(execution.getSessionId());
                replaceToolResult = applyDelivery(snapshot, wait, input);
                gateway.deliverWait(id, input, snapshot, actor);
            }
            return continueAfterDelivery(execution, wait, input, replaceToolResult, resume);
        } finally {
            lock.unlock();
        }
    }

    private HarnessRunResult continueAfterDelivery(ExecutionRecord previousExecution,
                                                    WaitRecord wait,
                                                    Object input,
                                                    boolean replaceToolResult,
                                                    boolean resume) {
        ExecutionRecord updated = gateway.getExecution(previousExecution.getExecutionId());
        if (updated == null) {
            throw new HarnessValidationException("execution not found after wait delivery: "
                    + previousExecution.getExecutionId());
        }
        if (ExecutionStatus.WAITING.equals(updated.getStatus())
                || ExecutionStatus.CANCELLED.equals(updated.getStatus())
                || ExecutionStatus.SUCCEEDED.equals(updated.getStatus())
                || ExecutionStatus.FAILED.equals(updated.getStatus())
                || ExecutionStatus.UNKNOWN.equals(updated.getStatus())) {
            return resultForExecution(updated);
        }
        if (!resume) {
            return result(HarnessRunStatus.CONTINUATION_REQUIRED, updated, task(updated), null,
                    updated.getWaitId(), updated.getOperationId(),
                    replaceToolResult ? null : "wake delivered without a replaceable tool result");
        }
        HarnessRunRequest resumeRequest = HarnessRunRequest.builder()
                .executionId(updated.getExecutionId())
                .build();
        if (!replaceToolResult && (WaitType.USER_INPUT.equals(wait.getType())
                || WaitType.EXTERNAL_EVENT.equals(wait.getType()))) {
            resumeRequest = HarnessRunRequest.builder()
                    .executionId(updated.getExecutionId())
                    .input(input)
                    .build();
        }
        return run(resumeRequest);
    }

    private HarnessRunResult resultForExecution(ExecutionRecord execution) {
        if (execution == null) {
            return result(HarnessRunStatus.FAILED, null, null, null, null, null,
                    "execution is unavailable");
        }
        HarnessRunStatus status = ExecutionStatus.RUNNING.equals(execution.getStatus())
                ? HarnessRunStatus.CONTINUATION_REQUIRED : mapRunStatus(execution.getStatus());
        String output = ExecutionStatus.CANCELLED.equals(execution.getStatus())
                ? null : execution.getOutputText();
        return result(status, execution, task(execution), output,
                execution.getWaitId(), execution.getOperationId(), execution.getError());
    }

    private boolean applyDelivery(AgentSessionSnapshot snapshot,
                                  WaitRecord wait,
                                  Object input) {
        if (snapshot == null || wait == null || wait.getPayload() == null) {
            return false;
        }
        Object rawCallId = wait.getPayload().get(AgentToolCall.METADATA_KEY_PARENT_CALL_ID);
        if (rawCallId == null || String.valueOf(rawCallId).trim().isEmpty()) {
            rawCallId = wait.getPayload().get("callId");
        }
        if (rawCallId == null || String.valueOf(rawCallId).trim().isEmpty()
                || snapshot.getMemory() == null) {
            return false;
        }
        String output;
        if (WaitType.APPROVAL.equals(wait.getType())) {
            output = gateway.deliveryApproved(input)
                    ? "HARNESS_APPROVAL_GRANTED: retry the approved tool call. delivery="
                    + stringify(input)
                    : "HARNESS_APPROVAL_DENIED: do not retry the denied tool call. delivery="
                    + stringify(input);
        } else {
            output = stringify(input);
        }
        MemorySnapshot memory = snapshot.getMemory();
        List<Object> items = memory == null ? null : memory.getItems();
        if (items == null) {
            return false;
        }
        for (Object item : items) {
            if (replaceFunctionCallOutput(item, String.valueOf(rawCallId), output)
                    || replaceCodeActMarker(item, String.valueOf(rawCallId), output)) {
                memory.setItems(items);
                snapshot.setMemory(memory);
                return true;
            }
        }
        return false;
    }

    private boolean replaceFunctionCallOutput(Object item, String callId, String output) {
        if (!(item instanceof Map)) {
            return false;
        }
        Map<?, ?> candidate = (Map<?, ?>) item;
        if (!"function_call_output".equals(String.valueOf(candidate.get("type")))
                || !callId.equals(String.valueOf(candidate.get("call_id")))) {
            return false;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> mutable = (Map<String, Object>) item;
        mutable.put("output", output);
        return true;
    }

    private boolean replaceCodeActMarker(Object item, String callId, String output) {
        if (!(item instanceof Map)) {
            return false;
        }
        Map<?, ?> message = (Map<?, ?>) item;
        if (!"message".equals(String.valueOf(message.get("type")))
                || !"system".equals(String.valueOf(message.get("role")))) {
            return false;
        }
        Object rawContent = message.get("content");
        if (!(rawContent instanceof List)) {
            return false;
        }
        for (Object block : (List<?>) rawContent) {
            if (!(block instanceof Map)) {
                continue;
            }
            Map<?, ?> content = (Map<?, ?>) block;
            Object rawText = content.get("text");
            if (rawText == null || !String.valueOf(rawText)
                    .startsWith(AgentToolCall.CODEACT_PENDING_RESULT_PREFIX)) {
                continue;
            }
            String markerJson = String.valueOf(rawText)
                    .substring(AgentToolCall.CODEACT_PENDING_RESULT_PREFIX.length()).trim();
            try {
                JSONObject marker = JSON.parseObject(markerJson);
                if (marker == null || !callId.equals(String.valueOf(marker.get("callId")))) {
                    continue;
                }
            } catch (RuntimeException ignored) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> mutable = (Map<String, Object>) block;
            mutable.put("text", "CODE_RESULT: " + stringify(output));
            return true;
        }
        return false;
    }

    private TaskRecord task(ExecutionRecord execution) {
        return execution == null || execution.getTaskId() == null
                ? null : gateway.getTask(execution.getTaskId());
    }

    private HarnessRunResult result(HarnessRunStatus status,
                                    ExecutionRecord execution,
                                    TaskRecord task,
                                    String output,
                                    String waitId,
                                    String operationId,
                                    String error) {
        return HarnessRunResult.builder()
                .status(status)
                .execution(execution == null ? null : execution.copy())
                .task(task == null ? null : task.copy())
                .agentResult(null)
                .outputText(output)
                .waitId(waitId)
                .operationId(operationId)
                .error(error)
                .build();
    }

    private ExecutionStatus mapStatus(AgentExecutionStatus status) {
        if (status == AgentExecutionStatus.WAITING) return ExecutionStatus.WAITING;
        if (status == AgentExecutionStatus.CONTINUATION_REQUIRED) return ExecutionStatus.READY;
        if (status == AgentExecutionStatus.FAILED) return ExecutionStatus.FAILED;
        return ExecutionStatus.SUCCEEDED;
    }

    private HarnessRunStatus mapRunStatus(ExecutionStatus status) {
        if (status == ExecutionStatus.WAITING) return HarnessRunStatus.WAITING;
        if (status == ExecutionStatus.READY) return HarnessRunStatus.CONTINUATION_REQUIRED;
        if (status == ExecutionStatus.SUCCEEDED) return HarnessRunStatus.COMPLETED;
        if (status == ExecutionStatus.UNKNOWN) return HarnessRunStatus.UNKNOWN;
        if (status == ExecutionStatus.CANCELLED) return HarnessRunStatus.CANCELLED;
        return HarnessRunStatus.FAILED;
    }

    private Map<String, Object> checkpointState(AgentResult result,
                                                ExecutionStatus status,
                                                String waitId,
                                                String operationId) {
        Map<String, Object> state = new LinkedHashMap<String, Object>();
        state.put("executionStatus", status == null ? null : status.name());
        state.put("waitId", waitId);
        state.put("operationId", operationId);
        if (result != null) {
            state.put("agentStatus", result.getExecutionStatus() == null
                    ? null : result.getExecutionStatus().name());
            state.put("steps", result.getSteps());
            state.put("runId", result.getRunId());
            state.put("sessionId", result.getSessionId());
        }
        return state;
    }

    private String checkpointSummary(ExecutionStatus status, String output, String error) {
        if (error != null) return "Agent slice failed: " + error;
        if (status == ExecutionStatus.WAITING) return "Agent slice is waiting for a durable wakeup";
        if (status == ExecutionStatus.READY) return "Agent slice reached its boundary and can continue";
        return output == null ? "Agent slice completed" : "Agent slice completed: " + output;
    }

    private String inputSummary(Object input) {
        if (input == null) return null;
        String value = String.valueOf(input);
        return value.length() > 500 ? value.substring(0, 500) : value;
    }

    private String stringify(Object value) {
        return value instanceof String ? (String) value : JSON.toJSONString(value);
    }

    private Map<String, Object> mapOf(Object... values) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        if (values == null) {
            return result;
        }
        for (int i = 0; i + 1 < values.length; i += 2) {
            result.put(String.valueOf(values[i]), values[i + 1]);
        }
        return result;
    }

    private Throwable unwrap(Throwable error) {
        if (error instanceof java.util.concurrent.CompletionException
                && error.getCause() != null) return error.getCause();
        return error;
    }

    private String firstNonBlank(String first, String second) {
        return trimToNull(first) == null ? trimToNull(second) : trimToNull(first);
    }

    private String required(String value, String label) {
        String normalized = trimToNull(value);
        if (normalized == null) throw new HarnessValidationException(label + " is required");
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    @Override
    public void close() {
        heartbeatExecutor.shutdownNow();
        continuationExecutor.shutdownNow();
        gateway.close();
        if (persistence != null && persistence.getStore() == store) {
            persistence.close();
        }
        if (executionAdapter != null) {
            executionAdapter.close();
        }
    }

    public static final class Builder {
        private Agent agent;
        private HarnessExecutionAdapter executionAdapter;
        private HarnessStore store;
        private HarnessPersistence persistence;
        private HarnessContract contract;
        private HarnessActor actor;
        private String workerId;
        private boolean autoResume = true;
        private HarnessRunListener listener;

        public Builder agent(Agent value) { this.agent = value; return this; }
        public Builder executionAdapter(HarnessExecutionAdapter value) { this.executionAdapter = value; return this; }
        public Builder store(HarnessStore value) { this.store = value; return this; }
        public Builder persistence(HarnessPersistence value) { this.persistence = value; return this; }
        public Builder contract(HarnessContract value) { this.contract = value; return this; }
        public Builder actor(HarnessActor value) { this.actor = value; return this; }
        public Builder workerId(String value) { this.workerId = value; return this; }
        public Builder autoResume(boolean value) { this.autoResume = value; return this; }
        public Builder listener(HarnessRunListener value) { this.listener = value; return this; }

        public AgentHarness build() { return new AgentHarness(this); }
    }
}
