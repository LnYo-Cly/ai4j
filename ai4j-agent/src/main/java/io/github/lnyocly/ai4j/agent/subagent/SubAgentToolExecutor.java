package io.github.lnyocly.ai4j.agent.subagent;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.agent.event.AgentEventType;
import io.github.lnyocly.ai4j.agent.runtime.AgentToolExecutionScope;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public class SubAgentToolExecutor implements ToolExecutor {

    private static final AtomicInteger HANDOFF_THREAD_INDEX = new AtomicInteger(1);
    private static final ExecutorService HANDOFF_EXECUTOR = Executors.newCachedThreadPool(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "ai4j-handoff-" + HANDOFF_THREAD_INDEX.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    });

    private final SubAgentRegistry subAgentRegistry;
    private final ToolExecutor delegate;
    private final HandoffPolicy policy;
    private final Set<String> allowedTools;
    private final Set<String> deniedTools;

    public SubAgentToolExecutor(SubAgentRegistry subAgentRegistry, ToolExecutor delegate) {
        this(subAgentRegistry, delegate, HandoffPolicy.builder().build());
    }

    public SubAgentToolExecutor(SubAgentRegistry subAgentRegistry, ToolExecutor delegate, HandoffPolicy policy) {
        this.subAgentRegistry = subAgentRegistry;
        this.delegate = delegate;
        this.policy = policy == null ? HandoffPolicy.builder().build() : policy;
        this.allowedTools = toSafeSet(this.policy.getAllowedTools());
        this.deniedTools = toSafeSet(this.policy.getDeniedTools());
    }

    @Override
    public String execute(AgentToolCall call) throws Exception {
        if (call == null) {
            return null;
        }
        String toolName = call.getName();
        if (subAgentRegistry != null && subAgentRegistry.supports(toolName)) {
            return executeSubAgent(call, toolName);
        }
        if (delegate == null) {
            throw new IllegalStateException("toolExecutor is required for non-subagent tool: " + toolName);
        }
        return delegate.execute(call);
    }

    private String executeSubAgent(AgentToolCall call, String toolName) throws Exception {
        if (!policy.isEnabled()) {
            return executeWithoutPolicy(call, toolName);
        }

        SubAgentDefinition definition = subAgentRegistry == null ? null : subAgentRegistry.getDefinition(toolName);
        int nextDepth = HandoffContext.currentDepth() + 1;
        String handoffId = resolveHandoffId(call);
        long startedAt = System.currentTimeMillis();
        emitHandoffEvent(AgentEventType.HANDOFF_START, buildHandoffPayload(
                handoffId,
                call,
                definition,
                toolName,
                nextDepth,
                "starting",
                "Delegating to subagent " + resolveSubAgentName(definition, toolName) + ".",
                null,
                null,
                null,
                0L
        ));
        String deniedReason = denyReason(toolName, nextDepth);
        if (deniedReason != null) {
            return onDenied(call, definition, toolName, handoffId, nextDepth, startedAt, deniedReason);
        }

        AgentToolCall filteredCall = applyInputFilter(call);

        Exception lastError = null;
        int attempts = Math.max(1, policy.getMaxRetries() + 1);
        for (int attempt = 0; attempt < attempts; attempt++) {
            try {
                String output = executeOnce(filteredCall, nextDepth, toolName);
                emitHandoffEvent(AgentEventType.HANDOFF_END, buildHandoffPayload(
                        handoffId,
                        call,
                        definition,
                        toolName,
                        nextDepth,
                        "completed",
                        "Subagent completed.",
                        extractResultOutput(output),
                        null,
                        Integer.valueOf(attempt + 1),
                        System.currentTimeMillis() - startedAt
                ));
                return output;
            } catch (Exception ex) {
                lastError = ex;
            }
        }

        return onError(call, definition, toolName, handoffId, nextDepth, startedAt, lastError, attempts);
    }

    private String executeWithoutPolicy(AgentToolCall call, String toolName) throws Exception {
        SubAgentDefinition definition = subAgentRegistry == null ? null : subAgentRegistry.getDefinition(toolName);
        int depth = HandoffContext.currentDepth() + 1;
        String handoffId = resolveHandoffId(call);
        long startedAt = System.currentTimeMillis();
        emitHandoffEvent(AgentEventType.HANDOFF_START, buildHandoffPayload(
                handoffId,
                call,
                definition,
                toolName,
                depth,
                "starting",
                "Delegating to subagent " + resolveSubAgentName(definition, toolName) + ".",
                null,
                null,
                null,
                0L
        ));
        try {
            String output = executeOnce(call, depth, toolName);
            emitHandoffEvent(AgentEventType.HANDOFF_END, buildHandoffPayload(
                    handoffId,
                    call,
                    definition,
                    toolName,
                    depth,
                    "completed",
                    "Subagent completed.",
                    extractResultOutput(output),
                    null,
                    Integer.valueOf(1),
                    System.currentTimeMillis() - startedAt
            ));
            return output;
        } catch (Exception ex) {
            emitHandoffEvent(AgentEventType.HANDOFF_END, buildHandoffPayload(
                    handoffId,
                    call,
                    definition,
                    toolName,
                    depth,
                    "failed",
                    safeMessage(ex),
                    null,
                    safeMessage(ex),
                    Integer.valueOf(1),
                    System.currentTimeMillis() - startedAt
            ));
            throw ex;
        }
    }

    private AgentToolCall applyInputFilter(AgentToolCall call) throws Exception {
        HandoffInputFilter inputFilter = policy.getInputFilter();
        if (inputFilter == null) {
            return call;
        }
        AgentToolCall filtered = inputFilter.filter(call);
        return filtered == null ? call : filtered;
    }

    private String executeOnce(AgentToolCall call, int depth, String toolName) throws Exception {
        long timeoutMillis = policy.getTimeoutMillis();
        if (timeoutMillis <= 0L) {
            return HandoffContext.runWithDepth(depth, () -> subAgentRegistry.execute(call));
        }
        Future<String> future = HANDOFF_EXECUTOR.submit(() -> HandoffContext.runWithDepth(depth, () -> subAgentRegistry.execute(call)));
        try {
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException timeoutException) {
            future.cancel(true);
            throw new RuntimeException("Handoff timeout for subagent tool " + toolName + " after " + timeoutMillis + " ms", timeoutException);
        } catch (ExecutionException executionException) {
            Throwable cause = executionException.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw new RuntimeException(cause);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw interruptedException;
        }
    }

    private String onDenied(AgentToolCall call,
                            SubAgentDefinition definition,
                            String toolName,
                            String handoffId,
                            int depth,
                            long startedAt,
                            String deniedReason) throws Exception {
        if (policy.getOnDenied() == HandoffFailureAction.FALLBACK_TO_PRIMARY && delegate != null) {
            try {
                String output = delegate.execute(call);
                emitHandoffEvent(AgentEventType.HANDOFF_END, buildHandoffPayload(
                        handoffId,
                        call,
                        definition,
                        toolName,
                        depth,
                        "fallback",
                        "Handoff denied. Fell back to primary tool executor.",
                        extractResultOutput(output),
                        deniedReason,
                        Integer.valueOf(0),
                        System.currentTimeMillis() - startedAt
                ));
                return output;
            } catch (Exception ex) {
                emitHandoffEvent(AgentEventType.HANDOFF_END, buildHandoffPayload(
                        handoffId,
                        call,
                        definition,
                        toolName,
                        depth,
                        "failed",
                        safeMessage(ex),
                        null,
                        safeMessage(ex),
                        Integer.valueOf(0),
                        System.currentTimeMillis() - startedAt
                ));
                throw ex;
            }
        }
        emitHandoffEvent(AgentEventType.HANDOFF_END, buildHandoffPayload(
                handoffId,
                call,
                definition,
                toolName,
                depth,
                "failed",
                deniedReason,
                null,
                deniedReason,
                Integer.valueOf(0),
                System.currentTimeMillis() - startedAt
        ));
        throw new IllegalStateException("Handoff denied for subagent tool " + toolName + ": " + deniedReason);
    }

    private String onError(AgentToolCall call,
                           SubAgentDefinition definition,
                           String toolName,
                           String handoffId,
                           int depth,
                           long startedAt,
                           Exception error,
                           int attempts) throws Exception {
        if (policy.getOnError() == HandoffFailureAction.FALLBACK_TO_PRIMARY && delegate != null) {
            try {
                String output = delegate.execute(call);
                emitHandoffEvent(AgentEventType.HANDOFF_END, buildHandoffPayload(
                        handoffId,
                        call,
                        definition,
                        toolName,
                        depth,
                        "fallback",
                        "Subagent failed. Fell back to primary tool executor.",
                        extractResultOutput(output),
                        safeMessage(error),
                        Integer.valueOf(attempts),
                        System.currentTimeMillis() - startedAt
                ));
                return output;
            } catch (Exception ex) {
                emitHandoffEvent(AgentEventType.HANDOFF_END, buildHandoffPayload(
                        handoffId,
                        call,
                        definition,
                        toolName,
                        depth,
                        "failed",
                        safeMessage(ex),
                        null,
                        safeMessage(ex),
                        Integer.valueOf(attempts),
                        System.currentTimeMillis() - startedAt
                ));
                throw ex;
            }
        }
        emitHandoffEvent(AgentEventType.HANDOFF_END, buildHandoffPayload(
                handoffId,
                call,
                definition,
                toolName,
                depth,
                "failed",
                safeMessage(error),
                null,
                safeMessage(error),
                Integer.valueOf(attempts),
                System.currentTimeMillis() - startedAt
        ));
        if (error == null) {
            throw new RuntimeException("Handoff failed for subagent tool " + toolName);
        }
        throw error;
    }

    private String denyReason(String toolName, int nextDepth) {
        if (!allowedTools.isEmpty() && !allowedTools.contains(toolName)) {
            return "tool is not in allowedTools";
        }
        if (!deniedTools.isEmpty() && deniedTools.contains(toolName)) {
            return "tool is in deniedTools";
        }
        if (policy.getMaxDepth() > 0 && nextDepth > policy.getMaxDepth()) {
            return "handoff depth " + nextDepth + " exceeds maxDepth " + policy.getMaxDepth();
        }
        return null;
    }

    private Set<String> toSafeSet(Set<String> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(new HashSet<>(source));
    }

    private void emitHandoffEvent(AgentEventType type, Map<String, Object> payload) {
        AgentToolExecutionScope.emit(
                type,
                payload == null ? null : String.valueOf(payload.get("title")),
                payload
        );
    }

    private Map<String, Object> buildHandoffPayload(String handoffId,
                                                    AgentToolCall call,
                                                    SubAgentDefinition definition,
                                                    String toolName,
                                                    int depth,
                                                    String status,
                                                    String detail,
                                                    String output,
                                                    String error,
                                                    Integer attempts,
                                                    long durationMillis) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        String subAgentName = resolveSubAgentName(definition, toolName);
        payload.put("handoffId", handoffId);
        payload.put("callId", call == null ? null : call.getCallId());
        payload.put("tool", toolName);
        payload.put("subagent", subAgentName);
        payload.put("title", "Subagent " + subAgentName);
        payload.put("detail", detail);
        payload.put("status", status == null ? null : status.toLowerCase(Locale.ROOT));
        payload.put("depth", Integer.valueOf(depth));
        payload.put("sessionMode", definition == null || definition.getSessionMode() == null
                ? null
                : definition.getSessionMode().name().toLowerCase(Locale.ROOT));
        payload.put("attempts", attempts);
        payload.put("durationMillis", Long.valueOf(durationMillis));
        payload.put("output", output);
        payload.put("error", error);
        return payload;
    }

    private String resolveSubAgentName(SubAgentDefinition definition, String toolName) {
        String name = definition == null ? null : trimToNull(definition.getName());
        return name == null ? firstNonBlank(trimToNull(toolName), "subagent") : name;
    }

    private String resolveHandoffId(AgentToolCall call) {
        String callId = call == null ? null : trimToNull(call.getCallId());
        return "handoff:" + (callId == null ? UUID.randomUUID().toString() : callId);
    }

    private String extractResultOutput(String raw) {
        String value = trimToNull(raw);
        if (value == null) {
            return null;
        }
        try {
            JSONObject object = JSON.parseObject(value);
            String output = trimToNull(object == null ? null : object.getString("output"));
            return output == null ? value : output;
        } catch (Exception ignored) {
            return value;
        }
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
        return message == null ? throwable.getClass().getSimpleName() : message;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = trimToNull(value);
            if (normalized != null) {
                return normalized;
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
}
