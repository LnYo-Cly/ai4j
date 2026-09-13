package io.github.lnyocly.ai4j.harness;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.AgentToolExecution;
import io.github.lnyocly.ai4j.agent.tool.AgentToolExecutionStatus;
import io.github.lnyocly.ai4j.agent.tool.AsyncToolExecutor;
import io.github.lnyocly.ai4j.agent.tool.AgentToolResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Function-call adapter for the durable Harness command surface. It is an
 * Agent tool executor, but it never mutates state directly: every mutation is
 * delegated to {@link HarnessCommandGateway}.
 */
public final class HarnessManagementToolExecutor implements AsyncToolExecutor {

    private final HarnessExecutionContext context;

    public HarnessManagementToolExecutor(HarnessExecutionContext context) {
        if (context == null) {
            throw new IllegalArgumentException("Harness execution context is required");
        }
        this.context = context;
    }

    @Override
    public String execute(AgentToolCall call) throws Exception {
        AgentToolExecution execution = start(call);
        AgentToolResult result = execution == null ? null : execution.await();
        return result == null ? null : result.getOutput();
    }

    @Override
    public AgentToolExecution start(AgentToolCall call) throws Exception {
        if (call == null || !HarnessToolNames.isManagementTool(call.getName())) {
            throw new IllegalArgumentException("Unsupported Harness management tool: "
                    + (call == null ? null : call.getName()));
        }
        Map<String, Object> arguments = parseArguments(call.getArguments());
        if (HarnessToolNames.CONTEXT_GET.equals(call.getName())) {
            return completed(call, contextView(arguments));
        }
        if (HarnessToolNames.TASK_MANAGE.equals(call.getName())) {
            return taskManage(call, arguments);
        }
        if (HarnessToolNames.FACT_RECORD.equals(call.getName())) {
            return factManage(call, arguments);
        }
        if (HarnessToolNames.DECISION_PROPOSE.equals(call.getName())) {
            return decisionManage(call, arguments);
        }
        if (HarnessToolNames.EVIDENCE_RECORD.equals(call.getName())) {
            HarnessEvidenceSpec spec = HarnessEvidenceSpec.builder()
                    .evidenceId(string(arguments, "evidenceId"))
                    .scopeKey(scopeKey(arguments))
                    .taskId(defaultTask(string(arguments, "taskId")))
                    .executionId(string(arguments, "executionId"))
                    .kind(string(arguments, "kind"))
                    .location(string(arguments, "location"))
                    .summary(string(arguments, "summary"))
                    .contentRef(string(arguments, "contentRef"))
                    .build();
            return completed(call, context.getGateway().recordEvidence(spec, actor()));
        }
        if (HarnessToolNames.RELATION_MANAGE.equals(call.getName())) {
            return relationManage(call, arguments);
        }
        if (HarnessToolNames.CONTROL_REQUEST.equals(call.getName())) {
            return controlRequest(call, arguments);
        }
        if (HarnessToolNames.SUBMISSION_REQUEST.equals(call.getName())) {
            return submissionRequest(call, arguments);
        }
        throw new IllegalArgumentException("Unsupported Harness management tool: " + call.getName());
    }

    private AgentToolExecution taskManage(AgentToolCall call, Map<String, Object> arguments) {
        String operation = requiredString(arguments, "operation").toLowerCase();
        HarnessCommandGateway gateway = context.getGateway();
        if ("create".equals(operation)) {
            String idempotencyKey = string(arguments, "idempotencyKey");
            if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
                idempotencyKey = derivedCreateIdempotencyKey(call);
            }
            HarnessTaskSpec spec = HarnessTaskSpec.builder()
                    .taskId(string(arguments, "taskId"))
                    .scopeKey(scopeKey(arguments))
                    .title(string(arguments, "title"))
                    .goal(string(arguments, "goal"))
                    .plan(string(arguments, "plan"))
                    .parentTaskId(string(arguments, "parentTaskId"))
                    .idempotencyKey(idempotencyKey)
                    .tags(stringList(arguments.get("tags")))
                    .metadata(objectMap(arguments.get("metadata")))
                    .build();
            // A runtime-created first Task becomes the current Execution's
            // Task only when that Execution was intentionally unbound.
            if (context.getTaskId() == null && context.getExecutionId() != null) {
                TaskRecord task = gateway.createTaskAndAttachExecution(
                        spec, context.getExecutionId(), actor());
                context.setTaskId(task.getTaskId());
                return completed(call, task);
            }
            TaskRecord task = gateway.createTask(spec, actor());
            return completed(call, task);
        }
        if ("split".equals(operation)) {
            String parentId = defaultTask(string(arguments, "taskId"));
            requireTaskInScope(parentId);
            List<HarnessTaskSpec> children = taskSpecs(arguments.get("children"));
            return completed(call, gateway.splitTask(parentId, children, actor()));
        }
        if ("update".equals(operation)) {
            String taskId = requiredTask(arguments);
            return completed(call, gateway.updateTask(taskId,
                    string(arguments, "title"),
                    string(arguments, "goal"),
                    string(arguments, "plan"),
                    arguments.containsKey("metadata") ? objectMap(arguments.get("metadata")) : null,
                    actor()));
        }
        if ("transition".equals(operation)) {
            String taskId = requiredTask(arguments);
            String status = requiredString(arguments, "status");
            return completed(call, gateway.transitionTask(taskId,
                    enumValue(TaskStatus.class, status), string(arguments, "reason"), actor()));
        }
        if ("add_dependency".equals(operation) || "add-dependency".equals(operation)) {
            String taskId = requiredTask(arguments);
            String dependencyTaskId = requireTaskInScope(requiredString(arguments, "dependencyTaskId"));
            return completed(call, gateway.addDependency(taskId, dependencyTaskId, actor()));
        }
        if ("get".equals(operation)) {
            return completed(call, gateway.getTask(requiredTask(arguments)));
        }
        if ("list".equals(operation)) {
            return completed(call, gateway.listTasks(context.getScopeKey()));
        }
        if ("runnable".equals(operation) || "list_runnable".equals(operation)) {
            return completed(call, gateway.listRunnableTasks(context.getScopeKey()));
        }
        throw new HarnessValidationException("unsupported task operation: " + operation);
    }

    /**
     * A provider retry can repeat the same management call without preserving
     * the optional JSON idempotencyKey. The model's call id identifies that
     * logical call; execution id keeps the identity local to one durable slice.
     * Calls without either identity retain the explicit opt-in semantics and
     * are allowed to create distinct Tasks.
     */
    private String derivedCreateIdempotencyKey(AgentToolCall call) {
        if (call == null || context.getExecutionId() == null
                || call.getCallId() == null || call.getCallId().trim().isEmpty()) {
            return null;
        }
        return "harness-management:task.create:" + context.getExecutionId().trim()
                + ":" + call.getCallId().trim();
    }

    private AgentToolExecution factManage(AgentToolCall call, Map<String, Object> arguments) {
        String operation = requiredString(arguments, "operation").toLowerCase();
        HarnessCommandGateway gateway = context.getGateway();
        if ("record".equals(operation)) {
            HarnessFactSpec spec = HarnessFactSpec.builder()
                    .factId(string(arguments, "factId"))
                    .scopeKey(scopeKey(arguments))
                    .taskId(defaultTask(string(arguments, "taskId")))
                    .statement(string(arguments, "statement"))
                    .source(string(arguments, "source"))
                    .confidence(string(arguments, "confidence"))
                    .evidenceIds(stringList(arguments.get("evidenceIds")))
                    .metadata(objectMap(arguments.get("metadata")))
                    .build();
            return completed(call, gateway.recordFact(spec, actor()));
        }
        if ("invalidate".equals(operation)) {
            String factId = requiredString(arguments, "factId");
            requireFactInScope(factId);
            return completed(call, gateway.invalidateFact(factId,
                    string(arguments, "reason"), actor()));
        }
        throw new HarnessValidationException("unsupported fact operation: " + operation);
    }

    private AgentToolExecution decisionManage(AgentToolCall call, Map<String, Object> arguments) {
        String operation = requiredString(arguments, "operation").toLowerCase();
        HarnessCommandGateway gateway = context.getGateway();
        if ("propose".equals(operation)) {
            HarnessDecisionSpec spec = HarnessDecisionSpec.builder()
                    .decisionId(string(arguments, "decisionId"))
                    .scopeKey(scopeKey(arguments))
                    .taskId(defaultTask(string(arguments, "taskId")))
                    .question(string(arguments, "question"))
                    .chosenOption(string(arguments, "chosenOption"))
                    .rationale(string(arguments, "rationale"))
                    .factIds(stringList(arguments.get("factIds")))
                    .evidenceIds(stringList(arguments.get("evidenceIds")))
                    .build();
            return completed(call, gateway.proposeDecision(spec, actor()));
        }
        if ("resolve".equals(operation)) {
            String decisionId = requiredString(arguments, "decisionId");
            requireDecisionInScope(decisionId);
            return completed(call, gateway.resolveDecision(decisionId,
                    enumValue(DecisionStatus.class, requiredString(arguments, "status")),
                    string(arguments, "rationale"), actor()));
        }
        throw new HarnessValidationException("unsupported decision operation: " + operation);
    }

    private AgentToolExecution controlRequest(AgentToolCall call, Map<String, Object> arguments) {
        String operation = requiredString(arguments, "operation").toLowerCase();
        HarnessCommandGateway gateway = context.getGateway();
        if ("checkpoint".equals(operation)) {
            CheckpointRecord checkpoint = gateway.recordCheckpoint(context.getExecutionId(),
                    string(arguments, "summary"), objectMap(arguments.get("state")), actor());
            return completed(call, checkpoint);
        }
        if ("wait".equals(operation) || "approval".equals(operation)) {
            WaitType type = "approval".equals(operation)
                    ? WaitType.APPROVAL
                    : enumValueOrDefault(WaitType.class, string(arguments, "type"), WaitType.EXTERNAL_EVENT);
            Map<String, Object> payload = objectMap(arguments.get("payload"));
            payload.put("toolName", call.getName());
            payload.put("callId", call.getCallId());
            payload.put("arguments", call.getArguments());
            if ("approval".equals(operation)) {
                payload.put("approval", true);
                payload.put("toolName", string(arguments, "toolName"));
                payload.put("callId", string(arguments, "callId"));
                payload.put("arguments", string(arguments, "arguments"));
            }
            WaitRecord wait = gateway.ensureWait(context.getExecutionId(), context.getTaskId(),
                    string(arguments, "waitId"), type, string(arguments, "operationId"),
                    string(arguments, "externalKey"), payload, actor());
            String output = "HARNESS_WAITING: " + JSON.toJSONString(wait);
            return AgentToolExecution.of(AgentToolResult.builder()
                    .name(call.getName())
                    .callId(call.getCallId())
                    .output(output)
                    .status(AgentToolExecutionStatus.WAITING)
                    .waitId(wait.getWaitId())
                    .operationId(wait.getOperationId())
                    .build(), null);
        }
        throw new HarnessValidationException("unsupported control operation: " + operation);
    }

    private AgentToolExecution submissionRequest(AgentToolCall call, Map<String, Object> arguments) {
        String taskId = defaultTask(string(arguments, "taskId"));
        HarnessSubmissionSpec spec = HarnessSubmissionSpec.builder()
                .completionClaim(string(arguments, "completionClaim"))
                .verificationNotes(string(arguments, "verificationNotes"))
                .deliverables(stringList(arguments.get("deliverables")))
                .evidenceIds(stringList(arguments.get("evidenceIds")))
                .knownGaps(stringList(arguments.get("knownGaps")))
                .residualRisks(stringList(arguments.get("residualRisks")))
                .build();
        return completed(call, context.getGateway().submitTask(taskId,
                string(arguments, "executionId") == null ? context.getExecutionId() : string(arguments, "executionId"),
                spec, actor()));
    }

    private Map<String, Object> contextView(Map<String, Object> arguments) {
        HarnessCommandGateway gateway = context.getGateway();
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("executionId", context.getExecutionId());
        result.put("sessionId", context.getSessionId());
        result.put("runId", context.getRunId());
        String focusedTaskId = string(arguments, "taskId");
        if (focusedTaskId == null) {
            focusedTaskId = context.getTaskId();
        }
        result.put("currentTask", focusedTaskId == null ? null
                : gateway.getTaskInScope(focusedTaskId, context.getScopeKey()));
        result.put("tasks", gateway.listTasks(context.getScopeKey()));
        result.put("runnableTasks", gateway.listRunnableTasks(context.getScopeKey()));
        result.put("openWaits", gateway.listOpenWaitsInScope(context.getScopeKey()));
        result.put("facts", gateway.listFactsInScope(context.getScopeKey()));
        result.put("decisions", gateway.listDecisionsInScope(context.getScopeKey()));
        result.put("evidence", gateway.listEvidenceInScope(context.getScopeKey()));
        result.put("relations", gateway.listRelationsInScope(context.getScopeKey()));
        result.put("toolInvocations", gateway.listToolInvocationsInScope(context.getScopeKey()));
        return result;
    }

    private AgentToolExecution relationManage(AgentToolCall call, Map<String, Object> arguments) {
        String operation = requiredString(arguments, "operation").toLowerCase();
        HarnessCommandGateway gateway = context.getGateway();
        if ("create".equals(operation) || "add".equals(operation)) {
            HarnessRelationSpec spec = HarnessRelationSpec.builder()
                    .type(enumValue(RelationType.class, requiredString(arguments, "type")))
                    .scopeKey(scopeKey(arguments))
                    .fromKind(enumValue(EntityKind.class, requiredString(arguments, "fromKind")))
                    .fromId(requiredString(arguments, "fromId"))
                    .toKind(enumValue(EntityKind.class, requiredString(arguments, "toKind")))
                    .toId(requiredString(arguments, "toId"))
                    .metadata(objectMap(arguments.get("metadata")))
                    .build();
            return completed(call, gateway.addRelation(spec, actor()));
        }
        if ("get".equals(operation)) {
            return completed(call, gateway.getRelationInScope(
                    requiredString(arguments, "relationId"), context.getScopeKey()));
        }
        if ("list".equals(operation)) {
            return completed(call, gateway.listRelationsInScope(context.getScopeKey()));
        }
        throw new HarnessValidationException("unsupported relation operation: " + operation);
    }

    private AgentToolExecution completed(AgentToolCall call, Object value) {
        AgentToolResult result = AgentToolResult.builder()
                .name(call == null ? null : call.getName())
                .callId(call == null ? null : call.getCallId())
                .output(JSON.toJSONString(value))
                .status(AgentToolExecutionStatus.COMPLETED)
                .build();
        return AgentToolExecution.completed(result);
    }

    private HarnessActor actor() {
        return context.getActor() == null ? HarnessActor.agent("ai4j-agent") : context.getActor();
    }

    private String requiredTask(Map<String, Object> arguments) {
        return requireTaskInScope(requiredString(arguments, "taskId"));
    }

    private String defaultTask(String value) {
        String taskId = value == null ? context.getTaskId() : value;
        return taskId == null ? null : requireTaskInScope(taskId);
    }

    private String requireTaskInScope(String taskId) {
        String normalized = requiredString(Collections.singletonMap("taskId", taskId), "taskId");
        if (context.getGateway().getTaskInScope(normalized, context.getScopeKey()) == null) {
            throw new HarnessConflictException("task is outside the current Harness scope: " + normalized);
        }
        return normalized;
    }

    private void requireFactInScope(String factId) {
        for (FactRecord fact : context.getGateway().listFactsInScope(context.getScopeKey())) {
            if (factId.equals(fact.getFactId())) return;
        }
        throw new HarnessConflictException("fact is outside the current Harness scope: " + factId);
    }

    private void requireDecisionInScope(String decisionId) {
        for (DecisionRecord decision : context.getGateway().listDecisionsInScope(context.getScopeKey())) {
            if (decisionId.equals(decision.getDecisionId())) return;
        }
        throw new HarnessConflictException("decision is outside the current Harness scope: " + decisionId);
    }

    private String scopeKey(Map<String, Object> arguments) {
        String requested = string(arguments, "scopeKey");
        String current = context.getScopeKey();
        if (requested != null && current != null && !requested.trim().equals(current)) {
            throw new HarnessConflictException("management tool cannot write outside the current Harness scope");
        }
        return current == null ? (requested == null ? null : requested.trim()) : current;
    }

    private Map<String, Object> parseArguments(String arguments) {
        if (arguments == null || arguments.trim().isEmpty()) {
            return new LinkedHashMap<String, Object>();
        }
        try {
            JSONObject object = JSON.parseObject(arguments);
            return object == null ? new LinkedHashMap<String, Object>()
                    : new LinkedHashMap<String, Object>(object);
        } catch (RuntimeException error) {
            throw new HarnessValidationException("Harness tool arguments must be a JSON object: "
                    + error.getMessage());
        }
    }

    private String string(Map<String, Object> values, String key) {
        Object value = values == null ? null : values.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private String requiredString(Map<String, Object> values, String key) {
        String value = string(values, key);
        if (value == null || value.trim().isEmpty()) {
            throw new HarnessValidationException("Harness tool argument is required: " + key);
        }
        return value.trim();
    }

    private List<String> stringList(Object raw) {
        if (raw == null) {
            return new ArrayList<String>();
        }
        if (raw instanceof String) {
            String value = ((String) raw).trim();
            if (value.isEmpty()) {
                return new ArrayList<String>();
            }
            try {
                raw = JSON.parseArray(value);
            } catch (RuntimeException ignored) {
                return Collections.singletonList(value);
            }
        }
        List<String> result = new ArrayList<String>();
        if (raw instanceof List) {
            for (Object value : (List<?>) raw) {
                if (value != null && !String.valueOf(value).trim().isEmpty()) {
                    result.add(String.valueOf(value));
                }
            }
            return result;
        }
        result.add(String.valueOf(raw));
        return result;
    }

    private Map<String, Object> objectMap(Object raw) {
        if (raw == null) {
            return new LinkedHashMap<String, Object>();
        }
        if (raw instanceof String) {
            String value = ((String) raw).trim();
            if (value.isEmpty()) {
                return new LinkedHashMap<String, Object>();
            }
            raw = JSON.parseObject(value);
        }
        if (!(raw instanceof Map)) {
            throw new HarnessValidationException("Harness tool argument must be an object");
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) raw).entrySet()) {
            if (entry.getKey() != null) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return result;
    }

    private List<HarnessTaskSpec> taskSpecs(Object raw) {
        if (raw == null) {
            return new ArrayList<HarnessTaskSpec>();
        }
        if (raw instanceof String) {
            raw = JSON.parseArray((String) raw);
        }
        if (!(raw instanceof List)) {
            throw new HarnessValidationException("children must be an array");
        }
        List<HarnessTaskSpec> result = new ArrayList<HarnessTaskSpec>();
        for (Object value : (List<?>) raw) {
            if (value == null) {
                continue;
            }
            HarnessTaskSpec child = JSON.parseObject(JSON.toJSONString(value), HarnessTaskSpec.class);
            String requestedScope = child == null ? null : child.getScopeKey();
            String currentScope = context.getScopeKey();
            if (requestedScope != null && currentScope != null
                    && !requestedScope.trim().equals(currentScope)) {
                throw new HarnessConflictException(
                        "management tool cannot split a task outside the current Harness scope");
            }
            if (child != null && currentScope != null) {
                child = child.toBuilder().scopeKey(currentScope).build();
            }
            result.add(child);
        }
        return result;
    }

    private <E extends Enum<E>> E enumValue(Class<E> type, String value) {
        return Enum.valueOf(type, value.trim().toUpperCase());
    }

    private <E extends Enum<E>> E enumValueOrDefault(Class<E> type, String value, E fallback) {
        return value == null || value.trim().isEmpty() ? fallback : enumValue(type, value);
    }
}
