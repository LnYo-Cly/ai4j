package io.github.lnyocly.ai4j.harness;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.agent.control.AgentHostInputException;
import io.github.lnyocly.ai4j.agent.permission.AgentApprovalRequiredException;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.AgentToolExecution;
import io.github.lnyocly.ai4j.agent.tool.AgentToolExecutionStatus;
import io.github.lnyocly.ai4j.agent.tool.AgentToolResult;
import io.github.lnyocly.ai4j.agent.tool.AsyncToolExecutor;
import io.github.lnyocly.ai4j.agent.tool.AsyncToolExecutors;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Mandatory execution boundary for a Harness-enabled Agent. It routes
 * management calls to the command adapter and all other calls to the existing
 * application executor, while making task requirements, approvals, waits and
 * asynchronous completion durable.
 */
public final class HarnessToolExecutor implements AsyncToolExecutor {

    private final HarnessExecutionContext context;
    private final ToolExecutor businessExecutor;
    private final HarnessManagementToolExecutor managementExecutor;

    public HarnessToolExecutor(HarnessExecutionContext context,
                               ToolExecutor businessExecutor) {
        if (context == null) {
            throw new IllegalArgumentException("Harness execution context is required");
        }
        this.context = context;
        this.businessExecutor = businessExecutor;
        this.managementExecutor = new HarnessManagementToolExecutor(context);
    }

    public ToolExecutor getBusinessExecutor() {
        return businessExecutor;
    }

    @Override
    public String execute(AgentToolCall call) throws Exception {
        AgentToolExecution execution = start(call);
        AgentToolResult result = execution == null ? null : execution.await();
        return result == null ? null : result.getOutput();
    }

    @Override
    public AgentToolExecution start(AgentToolCall call) throws Exception {
        if (call == null || call.getName() == null || call.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("tool call name is required");
        }
        if (HarnessToolNames.isManagementTool(call.getName())) {
            return managementExecutor.start(call);
        }
        String invocationId = invocationId(call);
        if (businessExecutor == null) {
            throw new IllegalStateException("business tool executor is required for: " + call.getName());
        }

        if (requiresTask(call) && context.getTaskId() == null) {
            return failed(call, "HARNESS_TASK_REQUIRED: create or select a Task before calling " + call.getName());
        }

        boolean approvalGranted = context.getGateway().isApprovalGranted(
                context.getExecutionId(), call.getName(), call.getCallId(), call.getArguments());
        ToolInvocationRecord existing = context.getGateway().getToolInvocationInScope(
                invocationId, context.getScopeKey());
        if (existing == null && approvalGranted) {
            ToolInvocationRecord approvedRetry = context.getGateway().findApprovedWaitingToolInvocation(
                    context.getExecutionId(), context.getScopeKey(), call.getName(),
                    call.getCallId(), call.getArguments());
            if (approvedRetry != null) {
                invocationId = approvedRetry.getInvocationId();
                existing = approvedRetry;
            }
        }
        if (existing != null && !(ToolInvocationStatus.WAITING.equals(existing.getStatus())
                && approvalGranted)) {
            return existingResult(call, existing);
        }

        AgentToolCall effectiveCall = call;
        if (isApprovalRequired(call) && !approvalGranted) {
            return approvalWait(call);
        }
        if (approvalGranted) {
            effectiveCall = copyWithApproval(effectiveCall);
        }
        effectiveCall = copyWithMetadata(effectiveCall,
                AgentToolCall.METADATA_KEY_HARNESS_INVOCATION_ID, invocationId);

        ToolInvocationReservation reservation = context.getGateway().reserveToolInvocation(
                HarnessToolInvocationSpec.builder()
                .invocationId(invocationId)
                .executionId(context.getExecutionId())
                .taskId(context.getTaskId())
                .sessionId(context.getSessionId())
                .scopeKey(context.getScopeKey())
                .toolName(call.getName())
                .callId(call.getCallId())
                .arguments(call.getArguments())
                .build(), context.getActor());
        ToolInvocationRecord started = reservation.getInvocation();
        if (!reservation.isCreated()) {
            return existingResult(call, started);
        }
        if (!ToolInvocationStatus.STARTED.equals(started.getStatus())) {
            return existingResult(call, started);
        }

        try {
            AgentToolExecution execution = AsyncToolExecutors.start(businessExecutor, effectiveCall);
            if (execution == null) {
                context.getGateway().completeToolInvocation(invocationId,
                        ToolInvocationStatus.SUCCEEDED, null, null, null, null, context.getActor());
                return completed(call, null);
            }
            AgentToolResult initial = normalize(call, execution.isPending()
                    ? execution.getInitialResult() : execution.await());
            if (!initial.isWaiting()) {
                context.getGateway().completeToolInvocation(invocationId,
                        ledgerStatus(initial), initial.getOperationId(), initial.getWaitId(),
                        initial.getOutput(), initial.getError(), context.getActor());
                return AgentToolExecution.completed(initial);
            }

            WaitRecord wait = ensureToolWait(effectiveCall, initial, WaitType.ASYNC_OPERATION);
            initial.setWaitId(wait.getWaitId());
            if (initial.getOperationId() == null) {
                initial.setOperationId(wait.getOperationId());
            }
            context.getGateway().markToolInvocationWaiting(invocationId,
                    initial.getOperationId(), wait.getWaitId(), context.getActor());
            CompletionStage<AgentToolResult> completion = execution.getCompletion();
            context.registerAsyncCompletion(wait.getWaitId(), wait.getOperationId(), invocationId, completion);
            return AgentToolExecution.of(initial, completion);
        } catch (AgentApprovalRequiredException approval) {
            return approvalWait(call, approval, invocationId);
        } catch (AgentHostInputException input) {
            return inputWait(effectiveCall, input, invocationId);
        } catch (Exception failure) {
            try {
                context.getGateway().completeToolInvocation(invocationId,
                        ToolInvocationStatus.UNKNOWN, null, null, null,
                        failure.getMessage() == null ? failure.toString() : failure.getMessage(),
                        context.getActor());
            } catch (RuntimeException ignored) {
                // The original tool failure remains visible to the Agent; a
                // later reconciliation can inspect the durable STARTED entry.
            }
            throw failure;
        }
    }

    private boolean requiresTask(AgentToolCall call) {
        return context.getGateway().getContract().requiresTaskForTool(call.getName());
    }

    private boolean isApprovalRequired(AgentToolCall call) {
        return context.getGateway().getContract().requiresApprovalForTool(call.getName());
    }

    private AgentToolExecution approvalWait(AgentToolCall call) {
        return approvalWait(call, null);
    }

    private AgentToolExecution approvalWait(AgentToolCall call,
                                            AgentApprovalRequiredException cause) {
        return approvalWait(call, cause, null);
    }

    private AgentToolExecution approvalWait(AgentToolCall call,
                                            AgentApprovalRequiredException cause,
                                            String invocationId) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("toolName", call.getName());
        payload.put("callId", call.getCallId());
        payload.put("arguments", call.getArguments());
        payload.put("approval", true);
        if (cause != null && cause.getDecision() != null) {
            payload.put("reason", cause.getDecision().getReason());
        }
        WaitRecord wait = context.getGateway().requestApproval(context.getExecutionId(),
                context.getTaskId(), call.getName(), call.getCallId(), call.getArguments(),
                invocationId, context.getActor());
        String output = "HARNESS_APPROVAL_REQUIRED: " + JSON.toJSONString(payload)
                + "; waitId=" + wait.getWaitId();
        AgentToolResult result = AgentToolResult.builder()
                .name(call.getName())
                .callId(call.getCallId())
                .output(output)
                .status(AgentToolExecutionStatus.WAITING)
                .waitId(wait.getWaitId())
                .build();
        return AgentToolExecution.of(result, null);
    }

    private AgentToolExecution inputWait(AgentToolCall call,
                                         AgentHostInputException cause,
                                         String invocationId) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("toolName", call.getName());
        payload.put("callId", call.getCallId());
        payload.put("arguments", call.getArguments());
        payload.put("request", cause.getRequest());
        WaitRecord wait = ensureToolWait(call, null, WaitType.USER_INPUT);
        context.getGateway().markToolInvocationWaiting(invocationId, null,
                wait.getWaitId(), context.getActor());
        String output = "HARNESS_USER_INPUT_REQUIRED: " + JSON.toJSONString(cause.getRequest())
                + "; waitId=" + wait.getWaitId();
        AgentToolResult result = AgentToolResult.builder()
                .name(call.getName())
                .callId(call.getCallId())
                .output(output)
                .status(AgentToolExecutionStatus.WAITING)
                .waitId(wait.getWaitId())
                .build();
        return AgentToolExecution.of(result, null);
    }

    private WaitRecord ensureToolWait(AgentToolCall call,
                                      AgentToolResult initial,
                                      WaitType type) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("toolName", call.getName());
        payload.put("callId", call.getCallId());
        payload.put("arguments", call.getArguments());
        Object invocationId = call.getMetadata() == null ? null
                : call.getMetadata().get(AgentToolCall.METADATA_KEY_HARNESS_INVOCATION_ID);
        if (invocationId != null && !String.valueOf(invocationId).trim().isEmpty()) {
            payload.put(AgentToolCall.METADATA_KEY_HARNESS_INVOCATION_ID,
                    String.valueOf(invocationId));
        }
        if (initial != null) {
            payload.put("initialOutput", initial.getOutput());
            payload.put("operationId", initial.getOperationId());
        }
        if (call.getMetadata() != null) {
            Object parentCallId = call.getMetadata().get(AgentToolCall.METADATA_KEY_PARENT_CALL_ID);
            if (parentCallId != null && !String.valueOf(parentCallId).trim().isEmpty()) {
                payload.put(AgentToolCall.METADATA_KEY_PARENT_CALL_ID, String.valueOf(parentCallId));
            }
        }
        return context.getGateway().ensureWait(context.getExecutionId(), context.getTaskId(),
                initial == null ? null : initial.getWaitId(), type,
                initial == null ? null : initial.getOperationId(), null, payload, context.getActor());
    }

    private AgentToolCall copyWithApproval(AgentToolCall source) {
        Map<String, Object> metadata = source.getMetadata() == null
                ? new LinkedHashMap<String, Object>()
                : new LinkedHashMap<String, Object>(source.getMetadata());
        metadata.put(AgentToolCall.METADATA_KEY_HARNESS_APPROVAL_GRANTED, Boolean.TRUE);
        return AgentToolCall.builder()
                .name(source.getName())
                .arguments(source.getArguments())
                .callId(source.getCallId())
                .type(source.getType())
                .metadata(metadata)
                .build();
    }

    private AgentToolCall copyWithMetadata(AgentToolCall source, String key, Object value) {
        Map<String, Object> metadata = source.getMetadata() == null
                ? new LinkedHashMap<String, Object>()
                : new LinkedHashMap<String, Object>(source.getMetadata());
        metadata.put(key, value);
        return AgentToolCall.builder()
                .name(source.getName())
                .arguments(source.getArguments())
                .callId(source.getCallId())
                .type(source.getType())
                .metadata(metadata)
                .build();
    }

    private String invocationId(AgentToolCall call) {
        Object explicit = call.getMetadata() == null ? null
                : call.getMetadata().get(AgentToolCall.METADATA_KEY_HARNESS_INVOCATION_ID);
        if (explicit != null && !String.valueOf(explicit).trim().isEmpty()) {
            return String.valueOf(explicit).trim();
        }
        String seed = String.valueOf(context.getExecutionId()) + "|"
                + String.valueOf(call.getName()) + "|"
                + String.valueOf(call.getCallId()) + "|"
                + String.valueOf(call.getArguments());
        return "toolinv_" + UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8))
                .toString().replace("-", "");
    }

    private AgentToolExecution existingResult(AgentToolCall call,
                                              ToolInvocationRecord invocation) {
        AgentToolExecutionStatus status;
        String output;
        if (ToolInvocationStatus.SUCCEEDED.equals(invocation.getStatus())) {
            status = AgentToolExecutionStatus.COMPLETED;
            output = invocation.getOutput();
        } else if (ToolInvocationStatus.WAITING.equals(invocation.getStatus())) {
            status = AgentToolExecutionStatus.WAITING;
            output = "HARNESS_TOOL_WAITING: invocationId=" + invocation.getInvocationId()
                    + "; waitId=" + invocation.getWaitId();
        } else if (ToolInvocationStatus.CANCELLED.equals(invocation.getStatus())) {
            status = AgentToolExecutionStatus.FAILED;
            output = "HARNESS_TOOL_CANCELLED: invocationId=" + invocation.getInvocationId();
        } else if (ToolInvocationStatus.FAILED.equals(invocation.getStatus())) {
            status = AgentToolExecutionStatus.FAILED;
            output = invocation.getOutput() == null ? invocation.getError() : invocation.getOutput();
        } else {
            status = AgentToolExecutionStatus.UNKNOWN;
            output = "HARNESS_TOOL_RECONCILIATION_REQUIRED: invocationId="
                    + invocation.getInvocationId() + "; status=" + invocation.getStatus();
        }
        return AgentToolExecution.of(AgentToolResult.builder()
                .name(call.getName())
                .callId(call.getCallId())
                .output(output)
                .error(invocation.getError())
                .status(status)
                .waitId(invocation.getWaitId())
                .operationId(invocation.getOperationId())
                .build(), null);
    }

    private ToolInvocationStatus ledgerStatus(AgentToolResult result) {
        if (result == null) {
            return ToolInvocationStatus.SUCCEEDED;
        }
        if (AgentToolExecutionStatus.UNKNOWN.equals(result.getStatus())) {
            return ToolInvocationStatus.UNKNOWN;
        }
        return result.isFailed() ? ToolInvocationStatus.FAILED : ToolInvocationStatus.SUCCEEDED;
    }

    private AgentToolExecution completed(AgentToolCall call, String output) {
        return AgentToolExecution.completed(AgentToolResult.builder()
                .name(call.getName())
                .callId(call.getCallId())
                .output(output)
                .status(AgentToolExecutionStatus.COMPLETED)
                .build());
    }

    private AgentToolExecution failed(AgentToolCall call, String output) {
        return AgentToolExecution.completed(AgentToolResult.builder()
                .name(call.getName())
                .callId(call.getCallId())
                .output(output)
                .status(AgentToolExecutionStatus.FAILED)
                .ok(Boolean.FALSE)
                .error(output)
                .build());
    }

    private AgentToolResult normalize(AgentToolCall call, AgentToolResult source) {
        AgentToolResult result = source == null ? new AgentToolResult() : source;
        if (result.getName() == null) {
            result.setName(call.getName());
        }
        if (result.getCallId() == null) {
            result.setCallId(call.getCallId());
        }
        if (result.getStatus() == null) {
            result.setStatus(AgentToolExecutionStatus.COMPLETED);
        }
        return result;
    }
}
