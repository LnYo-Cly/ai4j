package io.github.lnyocly.ai4j.harness.fleet;

import io.github.lnyocly.ai4j.harness.ExecutionRecord;
import io.github.lnyocly.ai4j.harness.ExecutionStatus;
import io.github.lnyocly.ai4j.harness.HarnessExecutionOutcome;
import io.github.lnyocly.ai4j.harness.HarnessSupport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Deterministic event/trigger-driven pull worker that claims READY executions
 * matching an {@link AutoClaimPolicy} and persists execution outcomes through a
 * {@link FleetTransport} without polling sleeps.
 */
public class AutoClaimWorker {

    @FunctionalInterface
    public interface ExecutionHandler {
        HarnessExecutionOutcome execute(ExecutionRecord execution);
    }

    private final String nodeId;
    private final String workerId;
    private final String credentialToken;
    private final AutoClaimPolicy policy;
    private final FleetTransport transport;

    private final AtomicInteger activeCount = new AtomicInteger(0);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Semaphore triggerSignal = new Semaphore(0);

    public AutoClaimWorker(String nodeId,
                           String workerId,
                           String credentialToken,
                           AutoClaimPolicy policy,
                           FleetTransport transport) {
        this.nodeId = HarnessSupport.requireText(nodeId, "nodeId");
        this.workerId = HarnessSupport.trimToNull(workerId) == null
                ? "fleet-worker:" + this.nodeId : workerId.trim();
        this.credentialToken = credentialToken;
        this.policy = policy == null ? AutoClaimPolicy.builder().build() : policy;
        if (transport == null) {
            throw new IllegalArgumentException("FleetTransport is required");
        }
        this.transport = transport;
    }

    public String getNodeId() {
        return nodeId;
    }

    public String getWorkerId() {
        return workerId;
    }

    public AutoClaimPolicy getPolicy() {
        return policy;
    }

    public int getActiveCount() {
        return activeCount.get();
    }

    /** Pulls and claims up to {@code maxConcurrentExecutions - activeCount} eligible executions. */
    public List<ExecutionRecord> pullAndClaimOnce() {
        int maxConcurrent = Math.max(1, policy.getMaxConcurrentExecutions());
        int available = maxConcurrent - activeCount.get();
        if (available <= 0) {
            return Collections.emptyList();
        }
        int batchLimit = policy.getBatchSize() > 0 ? Math.min(available, policy.getBatchSize()) : available;
        String singleAgent = (policy.getAllowedAgents() != null && policy.getAllowedAgents().size() == 1)
                ? policy.getAllowedAgents().get(0) : null;

        ClaimPullRequest request = ClaimPullRequest.builder()
                .nodeId(nodeId)
                .credentialToken(credentialToken)
                .workerId(workerId)
                .agentId(singleAgent)
                .allowedAgents(HarnessSupport.copyList(policy.getAllowedAgents()))
                .scopeKeys(HarnessSupport.copyList(policy.getAllowedScopeKeys()))
                .tags(HarnessSupport.copyList(policy.getTags()))
                .maxCount(batchLimit)
                .leaseDurationMillis(policy.getLeaseDurationMillis() > 0L
                        ? policy.getLeaseDurationMillis() : 60_000L)
                .claimImmediately(true)
                .build();

        ClaimPullResponse response = transport.pullClaimableExecutions(request);
        List<ExecutionRecord> claimed = response == null || response.getExecutions() == null
                ? Collections.<ExecutionRecord>emptyList()
                : response.getExecutions();
        if (!claimed.isEmpty()) {
            activeCount.addAndGet(claimed.size());
        }
        return claimed;
    }

    /** Synchronously pulls, executes via {@code handler}, and persists outcomes for one cycle. */
    public int runOnce(ExecutionHandler handler) {
        if (handler == null) {
            throw new IllegalArgumentException("ExecutionHandler is required");
        }
        List<ExecutionRecord> claimed = pullAndClaimOnce();
        if (claimed.isEmpty()) {
            return 0;
        }
        List<ExecutionRecord> processed = new ArrayList<ExecutionRecord>(claimed.size());
        for (ExecutionRecord execution : claimed) {
            try {
                HarnessExecutionOutcome outcome = executeWithFallback(handler, execution);
                transport.persistOutcome(ExecutionOutcomeRequest.builder()
                        .nodeId(nodeId)
                        .credentialToken(credentialToken)
                        .outcome(outcome)
                        .build());
                processed.add(execution);
            } finally {
                activeCount.decrementAndGet();
            }
        }
        return processed.size();
    }

    /** Signals an external dispatcher or awaiting cycle that new work is available. */
    public void trigger() {
        triggerSignal.release();
    }

    /** Blocks on the explicit trigger semaphore until {@link #trigger()} or {@link #stop()} fires. */
    public int awaitTriggerAndRunOnce(long timeout, TimeUnit unit, ExecutionHandler handler)
            throws InterruptedException {
        if (!triggerSignal.tryAcquire(timeout, unit)) {
            return 0;
        }
        if (running.get() || !triggerSignal.hasQueuedThreads()) {
            return runOnce(handler);
        }
        return runOnce(handler);
    }

    /** Runs a deterministic trigger-driven loop (typically on a worker executor) until {@link #stop()}. */
    public void runUntilStopped(ExecutionHandler handler, long awaitTimeoutMillis) {
        running.set(true);
        try {
            while (running.get()) {
                boolean signaled = triggerSignal.tryAcquire(awaitTimeoutMillis, TimeUnit.MILLISECONDS);
                if (!running.get()) {
                    break;
                }
                if (signaled) {
                    runOnce(handler);
                }
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } finally {
            running.set(false);
        }
    }

    public void stop() {
        running.set(false);
        triggerSignal.release();
    }

    private HarnessExecutionOutcome executeWithFallback(ExecutionHandler handler, ExecutionRecord execution) {
        try {
            HarnessExecutionOutcome outcome = handler.execute(execution);
            if (outcome == null) {
                outcome = HarnessExecutionOutcome.builder()
                        .status(ExecutionStatus.SUCCEEDED)
                        .build();
            }
            if (outcome.getExecutionId() == null) {
                outcome.setExecutionId(execution.getExecutionId());
            }
            if (outcome.getLeaseId() == null) {
                outcome.setLeaseId(execution.getLeaseId());
            }
            if (outcome.getFencingToken() <= 0L) {
                outcome.setFencingToken(execution.getFencingToken());
            }
            if (outcome.getStatus() == null) {
                outcome.setStatus(ExecutionStatus.SUCCEEDED);
            }
            return outcome;
        } catch (RuntimeException ex) {
            return HarnessExecutionOutcome.builder()
                    .executionId(execution.getExecutionId())
                    .leaseId(execution.getLeaseId())
                    .fencingToken(execution.getFencingToken())
                    .status(ExecutionStatus.FAILED)
                    .error(ex.getMessage())
                    .build();
        }
    }
}
