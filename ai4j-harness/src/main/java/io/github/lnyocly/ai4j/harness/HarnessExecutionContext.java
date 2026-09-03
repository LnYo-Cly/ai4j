package io.github.lnyocly.ai4j.harness;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;

/** Per-slice context shared by Harness management tools and the tool boundary. */
public final class HarnessExecutionContext {

    public interface AsyncCompletionHandler {
        /** Compatibility callback retained for integrations using the original context contract. */
        default void onCompletion(String waitId, Object result, Throwable error) {
        }

        /** Callback carrying the durable operation and invocation identities. */
        default void onCompletion(String waitId,
                                  String operationId,
                                  String invocationId,
                                  Object result,
                                  Throwable error) {
            onCompletion(waitId, result, error);
        }
    }

    private final HarnessCommandGateway gateway;
    private final String executionId;
    private final String sessionId;
    private final String scopeKey;
    private final String runId;
    private final HarnessActor actor;
    private final AsyncCompletionHandler asyncCompletionHandler;
    private final List<AsyncCompletionRegistration> asyncCompletions =
            new ArrayList<AsyncCompletionRegistration>();
    private volatile String taskId;

    public HarnessExecutionContext(HarnessCommandGateway gateway,
                                   String executionId,
                                   String taskId,
                                   String sessionId,
                                   String runId,
                                   HarnessActor actor,
                                   AsyncCompletionHandler asyncCompletionHandler) {
        this(gateway, executionId, taskId, sessionId, null, runId, actor, asyncCompletionHandler);
    }

    public HarnessExecutionContext(HarnessCommandGateway gateway,
                                   String executionId,
                                   String taskId,
                                   String sessionId,
                                   String scopeKey,
                                   String runId,
                                   HarnessActor actor,
                                   AsyncCompletionHandler asyncCompletionHandler) {
        if (gateway == null) {
            throw new IllegalArgumentException("gateway is required");
        }
        this.gateway = gateway;
        this.executionId = executionId;
        this.taskId = taskId;
        this.sessionId = sessionId;
        this.scopeKey = scopeKey;
        this.runId = runId;
        this.actor = actor;
        this.asyncCompletionHandler = asyncCompletionHandler;
    }

    public HarnessCommandGateway getGateway() {
        return gateway;
    }

    public String getExecutionId() {
        return executionId;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getScopeKey() {
        return scopeKey;
    }

    public String getRunId() {
        return runId;
    }

    public HarnessActor getActor() {
        return actor;
    }

    public AsyncCompletionHandler getAsyncCompletionHandler() {
        return asyncCompletionHandler;
    }

    /**
     * Registers a CompletionStage without subscribing immediately. The
     * AgentHarness activates registrations only after the execution outcome
     * and checkpoint have been durably persisted, closing the completion race
     * between an async tool and its Harness wait record.
     */
    public synchronized void registerAsyncCompletion(String waitId,
                                                       String operationId,
                                                       CompletionStage<?> completion) {
        registerAsyncCompletion(waitId, operationId, null, completion);
    }

    public synchronized void registerAsyncCompletion(String waitId,
                                                       String operationId,
                                                       String invocationId,
                                                       CompletionStage<?> completion) {
        if (completion == null || asyncCompletionHandler == null) {
            return;
        }
        asyncCompletions.add(new AsyncCompletionRegistration(waitId, operationId,
                invocationId, completion));
    }

    public synchronized List<AsyncCompletionRegistration> drainAsyncCompletions() {
        List<AsyncCompletionRegistration> result =
                new ArrayList<AsyncCompletionRegistration>(asyncCompletions);
        asyncCompletions.clear();
        return result;
    }

    public static final class AsyncCompletionRegistration {
        private final String waitId;
        private final String operationId;
        private final String invocationId;
        private final CompletionStage<?> completion;

        private AsyncCompletionRegistration(String waitId,
                                             String operationId,
                                             String invocationId,
                                             CompletionStage<?> completion) {
            this.waitId = waitId;
            this.operationId = operationId;
            this.invocationId = invocationId;
            this.completion = completion;
        }

        public String getWaitId() {
            return waitId;
        }

        public String getOperationId() {
            return operationId;
        }

        public String getInvocationId() {
            return invocationId;
        }

        public CompletionStage<?> getCompletion() {
            return completion;
        }
    }
}
