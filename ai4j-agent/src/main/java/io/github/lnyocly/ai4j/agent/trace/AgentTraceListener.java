package io.github.lnyocly.ai4j.agent.trace;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.agent.codeact.CodeExecutionResult;
import io.github.lnyocly.ai4j.agent.event.AgentEvent;
import io.github.lnyocly.ai4j.agent.event.AgentEventType;
import io.github.lnyocly.ai4j.agent.event.AgentListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.AgentToolResult;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AgentTraceListener implements AgentListener {

    private final TraceExporter exporter;
    private final TraceConfig config;
    private String traceId;
    private TraceSpan rootSpan;
    private final Map<Integer, TraceSpan> stepSpans = new HashMap<>();
    private final Map<Integer, TraceSpan> modelSpans = new HashMap<>();
    private final Map<String, TraceSpan> toolSpans = new HashMap<>();
    private final Map<Integer, TraceSpan> toolSpansByStep = new HashMap<>();
    private final Map<String, TraceSpan> handoffSpans = new HashMap<>();
    private final Map<String, TraceSpan> teamTaskSpans = new HashMap<>();

    public AgentTraceListener(TraceExporter exporter) {
        this(exporter, null);
    }

    public AgentTraceListener(TraceExporter exporter, TraceConfig config) {
        this.exporter = exporter;
        this.config = config == null ? TraceConfig.builder().build() : config;
    }

    @Override
    public synchronized void onEvent(AgentEvent event) {
        if (event == null) {
            return;
        }
        AgentEventType type = event.getType();
        if (type == null) {
            return;
        }
        switch (type) {
            case STEP_START:
                onStepStart(event);
                break;
            case STEP_END:
                onStepEnd(event);
                break;
            case MODEL_REQUEST:
                onModelRequest(event);
                break;
            case MODEL_RESPONSE:
                onModelResponse(event);
                break;
            case MODEL_RETRY:
                onModelRetry(event);
                break;
            case MODEL_REASONING:
                onModelReasoning(event);
                break;
            case TOOL_CALL:
                onToolCall(event);
                break;
            case TOOL_RESULT:
                onToolResult(event);
                break;
            case HANDOFF_START:
                onHandoffStart(event);
                break;
            case HANDOFF_END:
                onHandoffEnd(event);
                break;
            case TEAM_TASK_CREATED:
                onTeamTaskCreated(event);
                break;
            case TEAM_TASK_UPDATED:
                onTeamTaskUpdated(event);
                break;
            case TEAM_MESSAGE:
                onTeamMessage(event);
                break;
            case MEMORY_COMPRESS:
                onMemoryCompress(event);
                break;
            case FINAL_OUTPUT:
                onFinalOutput(event);
                break;
            case ERROR:
                onError(event);
                break;
            default:
                break;
        }
    }

    private void onStepStart(AgentEvent event) {
        if (traceId == null) {
            traceId = UUID.randomUUID().toString();
            rootSpan = startSpan("agent.run", TraceSpanType.RUN, null, null);
        }
        Integer step = event.getStep();
        if (step == null) {
            return;
        }
        TraceSpan stepSpan = startSpan("step:" + step, TraceSpanType.STEP, rootSpan == null ? null : rootSpan.getSpanId(), null);
        stepSpans.put(step, stepSpan);
    }

    private void onStepEnd(AgentEvent event) {
        Integer step = event.getStep();
        if (step == null) {
            return;
        }
        TraceSpan span = stepSpans.remove(step);
        finishSpan(span, TraceSpanStatus.OK, null);
        toolSpansByStep.remove(step);
        if (rootSpan != null && rootSpan.getEndTime() > 0 && stepSpans.isEmpty()) {
            reset();
        }
    }

    private void onModelRequest(AgentEvent event) {
        Integer step = event.getStep();
        TraceSpan parent = step == null ? rootSpan : stepSpans.get(step);
        Map<String, Object> attributes = new HashMap<>();
        Object payload = event.getPayload();
        if (payload instanceof AgentPrompt) {
            AgentPrompt prompt = (AgentPrompt) payload;
            if (prompt.getModel() != null) {
                attributes.put("model", prompt.getModel());
            }
            if (config.isRecordModelInput()) {
                attributes.put("systemPrompt", safeValue(prompt.getSystemPrompt()));
                attributes.put("instructions", safeValue(prompt.getInstructions()));
                attributes.put("items", safeValue(prompt.getItems()));
                attributes.put("tools", safeValue(prompt.getTools()));
                attributes.put("toolChoice", safeValue(prompt.getToolChoice()));
                attributes.put("parallelToolCalls", safeValue(prompt.getParallelToolCalls()));
                attributes.put("temperature", safeValue(prompt.getTemperature()));
                attributes.put("topP", safeValue(prompt.getTopP()));
                attributes.put("maxOutputTokens", safeValue(prompt.getMaxOutputTokens()));
                attributes.put("reasoning", safeValue(prompt.getReasoning()));
                attributes.put("store", safeValue(prompt.getStore()));
                attributes.put("stream", safeValue(prompt.getStream()));
                attributes.put("user", safeValue(prompt.getUser()));
                attributes.put("extraBody", safeValue(prompt.getExtraBody()));
            }
        }
        TraceSpan span = startSpan("model.request", TraceSpanType.MODEL,
                parent == null ? null : parent.getSpanId(), attributes);
        if (step != null) {
            modelSpans.put(step, span);
        }
    }

    private void onModelResponse(AgentEvent event) {
        Integer step = event.getStep();
        TraceSpan span = resolveModelSpan(step);
        if (span == null) {
            return;
        }
        if (config.isRecordModelOutput()) {
            if (event.getPayload() != null) {
                putAttribute(span, "output", safeValue(event.getPayload()));
            } else if (event.getMessage() != null && !event.getMessage().isEmpty()) {
                addSpanEvent(span, "model.response.delta", singletonAttribute("delta", safeValue(event.getMessage())));
            }
        }
        if (event.getPayload() != null) {
            if (step != null) {
                modelSpans.remove(step);
            }
            finishSpan(span, TraceSpanStatus.OK, null);
        }
    }

    private void onModelRetry(AgentEvent event) {
        TraceSpan span = resolveModelSpan(event.getStep());
        addSpanEvent(span, "model.retry", attributesFromEvent(event));
    }

    private void onModelReasoning(AgentEvent event) {
        TraceSpan span = resolveModelSpan(event.getStep());
        Map<String, Object> attributes = attributesFromEvent(event);
        if (event.getMessage() != null && !event.getMessage().isEmpty()) {
            attributes.put("text", safeValue(event.getMessage()));
        }
        addSpanEvent(span, "model.reasoning", attributes);
    }

    private void onToolCall(AgentEvent event) {
        Object payload = event.getPayload();
        String callId = null;
        String toolName = event.getMessage();
        if (payload instanceof AgentToolCall) {
            AgentToolCall call = (AgentToolCall) payload;
            callId = call.getCallId();
            toolName = call.getName();
            if (config.isRecordToolArgs()) {
                Map<String, Object> attributes = new HashMap<>();
                attributes.put("tool", toolName);
                attributes.put("callId", callId);
                attributes.put("arguments", safeValue(call.getArguments()));
                TraceSpan parent = event.getStep() == null ? rootSpan : stepSpans.get(event.getStep());
                TraceSpan span = startSpan("tool:" + (toolName == null ? "unknown" : toolName), TraceSpanType.TOOL,
                        parent == null ? null : parent.getSpanId(), attributes);
                toolSpans.put(callId == null ? keyForStep(event.getStep(), toolName) : callId, span);
                if (event.getStep() != null) {
                    toolSpansByStep.put(event.getStep(), span);
                }
                return;
            }
        }
        String key = callId == null ? keyForStep(event.getStep(), toolName) : callId;
        TraceSpan parent = event.getStep() == null ? rootSpan : stepSpans.get(event.getStep());
        Map<String, Object> attributes = new HashMap<>();
        if (toolName != null) {
            attributes.put("tool", toolName);
        }
        TraceSpan span = startSpan("tool:" + (toolName == null ? "unknown" : toolName), TraceSpanType.TOOL,
                parent == null ? null : parent.getSpanId(), attributes);
        toolSpans.put(key, span);
        if (event.getStep() != null) {
            toolSpansByStep.put(event.getStep(), span);
        }
    }

    private void onToolResult(AgentEvent event) {
        Object payload = event.getPayload();
        String callId = null;
        if (payload instanceof AgentToolResult) {
            callId = ((AgentToolResult) payload).getCallId();
        }
        String key = callId == null ? keyForStep(event.getStep(), event.getMessage()) : callId;
        TraceSpan span = toolSpans.remove(key);
        if (span == null && event.getStep() != null) {
            span = toolSpansByStep.remove(event.getStep());
        }
        TraceSpanStatus status = TraceSpanStatus.OK;
        String error = null;
        if (payload instanceof CodeExecutionResult) {
            CodeExecutionResult result = (CodeExecutionResult) payload;
            if (!result.isSuccess()) {
                status = TraceSpanStatus.ERROR;
                error = result.getError();
            }
            if (config.isRecordToolOutput()) {
                putAttribute(span, "result", safeValue(result.getResult()));
                putAttribute(span, "stdout", safeValue(result.getStdout()));
                putAttribute(span, "error", safeValue(result.getError()));
            }
        } else if (payload instanceof AgentToolResult) {
            AgentToolResult result = (AgentToolResult) payload;
            if (config.isRecordToolOutput()) {
                putAttribute(span, "output", safeValue(result.getOutput()));
            }
        } else if (config.isRecordToolOutput()) {
            putAttribute(span, "output", safeValue(event.getMessage()));
        }
        finishSpan(span, status, error);
    }

    private void onFinalOutput(AgentEvent event) {
        if (config.isRecordModelOutput()) {
            putAttribute(rootSpan, "finalOutput", safeValue(event.getMessage()));
        }
        finishSpan(rootSpan, TraceSpanStatus.OK, null);
    }

    private void onError(AgentEvent event) {
        finishSpan(rootSpan, TraceSpanStatus.ERROR, event.getMessage());
        reset();
    }

    private void onHandoffStart(AgentEvent event) {
        Map<String, Object> payload = payloadMap(event.getPayload());
        String handoffId = firstNonBlank(stringValue(payload, "handoffId"), event.getMessage(), UUID.randomUUID().toString());
        TraceSpan parent = resolveHandoffParent(event, payload);
        TraceSpan span = startSpan("handoff:" + firstNonBlank(stringValue(payload, "subagent"), stringValue(payload, "tool"), "subagent"),
                TraceSpanType.HANDOFF,
                parent == null ? null : parent.getSpanId(),
                safeAttributes(payload));
        handoffSpans.put(handoffId, span);
    }

    private void onHandoffEnd(AgentEvent event) {
        Map<String, Object> payload = payloadMap(event.getPayload());
        String handoffId = firstNonBlank(stringValue(payload, "handoffId"), event.getMessage());
        TraceSpan span = handoffId == null ? null : handoffSpans.remove(handoffId);
        if (span == null) {
            TraceSpan parent = resolveHandoffParent(event, payload);
            span = startSpan("handoff:" + firstNonBlank(stringValue(payload, "subagent"), stringValue(payload, "tool"), "subagent"),
                    TraceSpanType.HANDOFF,
                    parent == null ? null : parent.getSpanId(),
                    safeAttributes(payload));
        } else {
            mergeAttributes(span, safeAttributes(payload));
        }
        addSpanEvent(span, "handoff.end", attributesFromEvent(event));
        finishSpan(span, resolveStatus(payload, event.getMessage()), firstNonBlank(stringValue(payload, "error"), event.getMessage()));
    }

    private void onTeamTaskCreated(AgentEvent event) {
        Map<String, Object> payload = payloadMap(event.getPayload());
        String taskId = firstNonBlank(stringValue(payload, "taskId"), event.getMessage(), UUID.randomUUID().toString());
        TraceSpan span = startSpan("team.task:" + taskId,
                TraceSpanType.TEAM_TASK,
                rootSpan == null ? null : rootSpan.getSpanId(),
                safeAttributes(payload));
        teamTaskSpans.put(taskId, span);
        addSpanEvent(span, "team.task.created", attributesFromEvent(event));
    }

    private void onTeamTaskUpdated(AgentEvent event) {
        Map<String, Object> payload = payloadMap(event.getPayload());
        String taskId = firstNonBlank(stringValue(payload, "taskId"), event.getMessage());
        if (taskId == null) {
            return;
        }
        TraceSpan span = teamTaskSpans.get(taskId);
        if (span == null) {
            span = startSpan("team.task:" + taskId,
                    TraceSpanType.TEAM_TASK,
                    rootSpan == null ? null : rootSpan.getSpanId(),
                    safeAttributes(payload));
            teamTaskSpans.put(taskId, span);
        } else {
            mergeAttributes(span, safeAttributes(payload));
        }
        addSpanEvent(span, "team.task.updated", attributesFromEvent(event));
        if (isTerminalStatus(stringValue(payload, "status")) || stringValue(payload, "error") != null) {
            teamTaskSpans.remove(taskId);
            finishSpan(span, resolveStatus(payload, event.getMessage()), stringValue(payload, "error"));
        }
    }

    private void onTeamMessage(AgentEvent event) {
        Map<String, Object> payload = payloadMap(event.getPayload());
        String taskId = stringValue(payload, "taskId");
        TraceSpan span = taskId == null ? rootSpan : teamTaskSpans.get(taskId);
        addSpanEvent(span == null ? rootSpan : span, "team.message", attributesFromEvent(event));
    }

    private void onMemoryCompress(AgentEvent event) {
        TraceSpan parent = event.getStep() == null ? rootSpan : stepSpans.get(event.getStep());
        TraceSpan span = startSpan("memory.compress", TraceSpanType.MEMORY, parent == null ? null : parent.getSpanId(), attributesFromEvent(event));
        finishSpan(span, TraceSpanStatus.OK, null);
    }

    private TraceSpan startSpan(String name, TraceSpanType type, String parentId, Map<String, Object> attributes) {
        TraceSpan span = TraceSpan.builder()
                .traceId(traceId)
                .spanId(UUID.randomUUID().toString())
                .parentSpanId(parentId)
                .name(name)
                .type(type)
                .status(TraceSpanStatus.OK)
                .startTime(System.currentTimeMillis())
                .attributes(attributes == null ? new HashMap<>() : attributes)
                .events(new ArrayList<TraceSpanEvent>())
                .build();
        return span;
    }

    private TraceSpan resolveModelSpan(Integer step) {
        if (step == null) {
            return null;
        }
        return modelSpans.get(step);
    }

    private TraceSpan resolveHandoffParent(AgentEvent event, Map<String, Object> payload) {
        String callId = stringValue(payload, "callId");
        if (callId != null) {
            TraceSpan span = toolSpans.get(callId);
            if (span != null) {
                return span;
            }
        }
        if (event != null && event.getStep() != null) {
            TraceSpan span = toolSpansByStep.get(event.getStep());
            if (span != null) {
                return span;
            }
            span = stepSpans.get(event.getStep());
            if (span != null) {
                return span;
            }
        }
        return rootSpan;
    }

    private void putAttribute(TraceSpan span, String key, Object value) {
        if (span == null || key == null) {
            return;
        }
        Map<String, Object> attributes = span.getAttributes();
        if (attributes == null) {
            attributes = new HashMap<>();
            span.setAttributes(attributes);
        }
        if (value == null) {
            return;
        }
        attributes.put(key, value);
    }

    private void mergeAttributes(TraceSpan span, Map<String, Object> attributes) {
        if (span == null || attributes == null || attributes.isEmpty()) {
            return;
        }
        Map<String, Object> target = span.getAttributes();
        if (target == null) {
            target = new HashMap<String, Object>();
            span.setAttributes(target);
        }
        target.putAll(attributes);
    }

    private void addSpanEvent(TraceSpan span, String name, Map<String, Object> attributes) {
        if (span == null || name == null) {
            return;
        }
        List<TraceSpanEvent> events = span.getEvents();
        if (events == null) {
            events = new ArrayList<TraceSpanEvent>();
            span.setEvents(events);
        }
        events.add(TraceSpanEvent.builder()
                .timestamp(System.currentTimeMillis())
                .name(name)
                .attributes(attributes == null ? new HashMap<String, Object>() : attributes)
                .build());
    }

    private Object safeValue(Object value) {
        if (value == null) {
            return null;
        }
        String text;
        if (value instanceof String) {
            text = (String) value;
        } else {
            text = JSON.toJSONString(value);
        }
        if (config.getMasker() != null) {
            text = config.getMasker().mask(text);
        }
        int maxLength = config.getMaxFieldLength();
        if (maxLength > 0 && text.length() > maxLength) {
            text = text.substring(0, maxLength) + "...";
        }
        return text;
    }

    private Map<String, Object> attributesFromEvent(AgentEvent event) {
        Map<String, Object> attributes = safeAttributes(payloadMap(event == null ? null : event.getPayload()));
        if (event != null && event.getMessage() != null && !event.getMessage().isEmpty()) {
            attributes.put("message", safeValue(event.getMessage()));
        }
        if (event != null && event.getStep() != null) {
            attributes.put("step", event.getStep());
        }
        return attributes;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> payloadMap(Object payload) {
        if (payload instanceof Map) {
            return (Map<String, Object>) payload;
        }
        return new HashMap<String, Object>();
    }

    private Map<String, Object> safeAttributes(Map<String, Object> source) {
        Map<String, Object> target = new HashMap<String, Object>();
        if (source == null || source.isEmpty()) {
            return target;
        }
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            target.put(entry.getKey(), safeValue(entry.getValue()));
        }
        return target;
    }

    private Map<String, Object> singletonAttribute(String key, Object value) {
        Map<String, Object> attributes = new HashMap<String, Object>();
        if (key != null && value != null) {
            attributes.put(key, value);
        }
        return attributes;
    }

    private String stringValue(Map<String, Object> payload, String key) {
        if (payload == null || key == null) {
            return null;
        }
        Object value = payload.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private TraceSpanStatus resolveStatus(Map<String, Object> payload, String fallbackMessage) {
        String status = stringValue(payload, "status");
        if (stringValue(payload, "error") != null || (fallbackMessage != null && fallbackMessage.toLowerCase().contains("fail"))) {
            return TraceSpanStatus.ERROR;
        }
        if (status == null) {
            return TraceSpanStatus.OK;
        }
        String normalized = status.trim().toLowerCase();
        if ("canceled".equals(normalized) || "cancelled".equals(normalized)) {
            return TraceSpanStatus.CANCELED;
        }
        if ("failed".equals(normalized) || "error".equals(normalized)) {
            return TraceSpanStatus.ERROR;
        }
        return TraceSpanStatus.OK;
    }

    private boolean isTerminalStatus(String status) {
        if (status == null) {
            return false;
        }
        String normalized = status.trim().toLowerCase();
        return "completed".equals(normalized)
                || "failed".equals(normalized)
                || "blocked".equals(normalized)
                || "canceled".equals(normalized)
                || "cancelled".equals(normalized);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return null;
    }

    private void finishSpan(TraceSpan span, TraceSpanStatus status, String error) {
        if (span == null) {
            return;
        }
        span.setStatus(status == null ? TraceSpanStatus.OK : status);
        span.setEndTime(System.currentTimeMillis());
        span.setError(error);
        if (exporter != null) {
            exporter.export(span);
        }
    }

    private String keyForStep(Integer step, String toolName) {
        if (step == null) {
            return toolName == null ? "tool" : toolName;
        }
        return step + ":" + (toolName == null ? "tool" : toolName);
    }

    private void reset() {
        traceId = null;
        rootSpan = null;
        stepSpans.clear();
        modelSpans.clear();
        toolSpans.clear();
        toolSpansByStep.clear();
        handoffSpans.clear();
        teamTaskSpans.clear();
    }
}
