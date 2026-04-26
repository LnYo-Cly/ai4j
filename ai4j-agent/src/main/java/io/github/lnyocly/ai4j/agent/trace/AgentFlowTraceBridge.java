package io.github.lnyocly.ai4j.agent.trace;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.agentflow.AgentFlowUsage;
import io.github.lnyocly.ai4j.agentflow.chat.AgentFlowChatEvent;
import io.github.lnyocly.ai4j.agentflow.chat.AgentFlowChatRequest;
import io.github.lnyocly.ai4j.agentflow.chat.AgentFlowChatResponse;
import io.github.lnyocly.ai4j.agentflow.trace.AgentFlowTraceContext;
import io.github.lnyocly.ai4j.agentflow.trace.AgentFlowTraceListener;
import io.github.lnyocly.ai4j.agentflow.workflow.AgentFlowWorkflowEvent;
import io.github.lnyocly.ai4j.agentflow.workflow.AgentFlowWorkflowRequest;
import io.github.lnyocly.ai4j.agentflow.workflow.AgentFlowWorkflowResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AgentFlowTraceBridge implements AgentFlowTraceListener {

    private final TraceExporter exporter;
    private final TraceConfig config;
    private final Map<String, TraceSpan> activeSpans = new LinkedHashMap<String, TraceSpan>();

    public AgentFlowTraceBridge(TraceExporter exporter) {
        this(exporter, null);
    }

    public AgentFlowTraceBridge(TraceExporter exporter, TraceConfig config) {
        if (exporter == null) {
            throw new IllegalArgumentException("exporter is required");
        }
        this.exporter = exporter;
        this.config = config == null ? TraceConfig.builder().build() : config;
    }

    @Override
    public synchronized void onStart(AgentFlowTraceContext context) {
        if (context == null || isBlank(context.getExecutionId())) {
            return;
        }
        TraceSpan span = TraceSpan.builder()
                .traceId(UUID.randomUUID().toString())
                .spanId(UUID.randomUUID().toString())
                .name(spanName(context))
                .type(TraceSpanType.AGENT_FLOW)
                .startTime(context.getStartedAt() > 0L ? context.getStartedAt() : System.currentTimeMillis())
                .attributes(startAttributes(context))
                .events(new ArrayList<TraceSpanEvent>())
                .build();
        activeSpans.put(context.getExecutionId(), span);
    }

    @Override
    public synchronized void onEvent(AgentFlowTraceContext context, Object event) {
        TraceSpan span = span(context);
        if (span == null || event == null) {
            return;
        }
        if (event instanceof AgentFlowChatEvent) {
            applyChatEvent(span, (AgentFlowChatEvent) event);
            return;
        }
        if (event instanceof AgentFlowWorkflowEvent) {
            applyWorkflowEvent(span, (AgentFlowWorkflowEvent) event);
            return;
        }
        addSpanEvent(span, "agentflow.event", singletonAttribute("value", safeValue(event)));
    }

    @Override
    public synchronized void onComplete(AgentFlowTraceContext context, Object response) {
        TraceSpan span = removeSpan(context);
        if (span == null) {
            return;
        }
        if (response instanceof AgentFlowChatResponse) {
            applyChatResponse(span, (AgentFlowChatResponse) response);
        } else if (response instanceof AgentFlowWorkflowResponse) {
            applyWorkflowResponse(span, (AgentFlowWorkflowResponse) response);
        } else if (response != null && config.isRecordModelOutput()) {
            putAttribute(span, "output", safeValue(response));
        }
        finishSpan(span, TraceSpanStatus.OK, null);
    }

    @Override
    public synchronized void onError(AgentFlowTraceContext context, Throwable throwable) {
        TraceSpan span = removeSpan(context);
        if (span == null) {
            return;
        }
        finishSpan(span, TraceSpanStatus.ERROR, throwable == null ? null : safeText(throwable.getMessage()));
    }

    private TraceSpan span(AgentFlowTraceContext context) {
        if (context == null || isBlank(context.getExecutionId())) {
            return null;
        }
        return activeSpans.get(context.getExecutionId());
    }

    private TraceSpan removeSpan(AgentFlowTraceContext context) {
        if (context == null || isBlank(context.getExecutionId())) {
            return null;
        }
        return activeSpans.remove(context.getExecutionId());
    }

    private String spanName(AgentFlowTraceContext context) {
        String operation = context == null ? null : context.getOperation();
        return isBlank(operation) ? "agentflow.run" : "agentflow." + operation;
    }

    private Map<String, Object> startAttributes(AgentFlowTraceContext context) {
        Map<String, Object> attributes = new LinkedHashMap<String, Object>();
        if (context == null) {
            return attributes;
        }
        putIfPresent(attributes, "providerType", context.getType() == null ? null : context.getType().name());
        putIfPresent(attributes, "operation", context.getOperation());
        attributes.put("streaming", Boolean.valueOf(context.isStreaming()));
        putIfPresent(attributes, "baseUrl", safeText(context.getBaseUrl()));
        putIfPresent(attributes, "webhookUrl", safeText(context.getWebhookUrl()));
        putIfPresent(attributes, "botId", safeText(context.getBotId()));
        putIfPresent(attributes, "configuredWorkflowId", safeText(context.getWorkflowId()));
        putIfPresent(attributes, "appId", safeText(context.getAppId()));
        putIfPresent(attributes, "configuredUserId", safeText(context.getConfiguredUserId()));
        putIfPresent(attributes, "configuredConversationId", safeText(context.getConfiguredConversationId()));
        applyRequest(attributes, context.getRequest());
        return attributes;
    }

    private void applyRequest(Map<String, Object> attributes, Object request) {
        if (attributes == null || request == null || !config.isRecordModelInput()) {
            return;
        }
        if (request instanceof AgentFlowChatRequest) {
            AgentFlowChatRequest chatRequest = (AgentFlowChatRequest) request;
            putIfPresent(attributes, "message", safeText(chatRequest.getPrompt()));
            putIfPresent(attributes, "arguments", safeValue(chatRequest.getInputs()));
            putIfPresent(attributes, "requestUserId", safeText(chatRequest.getUserId()));
            putIfPresent(attributes, "requestConversationId", safeText(chatRequest.getConversationId()));
            putIfPresent(attributes, "requestMetadata", safeValue(chatRequest.getMetadata()));
            putIfPresent(attributes, "requestExtraBody", safeValue(chatRequest.getExtraBody()));
            return;
        }
        if (request instanceof AgentFlowWorkflowRequest) {
            AgentFlowWorkflowRequest workflowRequest = (AgentFlowWorkflowRequest) request;
            putIfPresent(attributes, "arguments", safeValue(workflowRequest.getInputs()));
            putIfPresent(attributes, "requestUserId", safeText(workflowRequest.getUserId()));
            putIfPresent(attributes, "requestWorkflowId", safeText(workflowRequest.getWorkflowId()));
            putIfPresent(attributes, "requestMetadata", safeValue(workflowRequest.getMetadata()));
            putIfPresent(attributes, "requestExtraBody", safeValue(workflowRequest.getExtraBody()));
            return;
        }
        putIfPresent(attributes, "arguments", safeValue(request));
    }

    private void applyChatEvent(TraceSpan span, AgentFlowChatEvent event) {
        if (span == null || event == null) {
            return;
        }
        putIfPresent(span.getAttributes(), "conversationId", safeText(event.getConversationId()));
        putIfPresent(span.getAttributes(), "messageId", safeText(event.getMessageId()));
        putIfPresent(span.getAttributes(), "taskId", safeText(event.getTaskId()));
        applyUsage(span, event.getUsage());

        Map<String, Object> attributes = new HashMap<String, Object>();
        putIfPresent(attributes, "type", safeText(event.getType()));
        if (config.isRecordModelOutput()) {
            putIfPresent(attributes, "delta", safeText(event.getContentDelta()));
        }
        if (event.isDone()) {
            attributes.put("done", Boolean.TRUE);
        }
        putIfPresent(attributes, "conversationId", safeText(event.getConversationId()));
        putIfPresent(attributes, "messageId", safeText(event.getMessageId()));
        putIfPresent(attributes, "taskId", safeText(event.getTaskId()));
        addSpanEvent(span, "agentflow.chat.event", attributes);
    }

    private void applyWorkflowEvent(TraceSpan span, AgentFlowWorkflowEvent event) {
        if (span == null || event == null) {
            return;
        }
        putIfPresent(span.getAttributes(), "status", safeText(event.getStatus()));
        putIfPresent(span.getAttributes(), "taskId", safeText(event.getTaskId()));
        putIfPresent(span.getAttributes(), "workflowRunId", safeText(event.getWorkflowRunId()));
        applyUsage(span, event.getUsage());

        Map<String, Object> attributes = new HashMap<String, Object>();
        putIfPresent(attributes, "type", safeText(event.getType()));
        putIfPresent(attributes, "status", safeText(event.getStatus()));
        if (config.isRecordModelOutput()) {
            putIfPresent(attributes, "outputText", safeText(event.getOutputText()));
        }
        if (event.isDone()) {
            attributes.put("done", Boolean.TRUE);
        }
        putIfPresent(attributes, "taskId", safeText(event.getTaskId()));
        putIfPresent(attributes, "workflowRunId", safeText(event.getWorkflowRunId()));
        addSpanEvent(span, "agentflow.workflow.event", attributes);
    }

    private void applyChatResponse(TraceSpan span, AgentFlowChatResponse response) {
        if (span == null || response == null) {
            return;
        }
        putIfPresent(span.getAttributes(), "conversationId", safeText(response.getConversationId()));
        putIfPresent(span.getAttributes(), "messageId", safeText(response.getMessageId()));
        putIfPresent(span.getAttributes(), "taskId", safeText(response.getTaskId()));
        if (config.isRecordModelOutput()) {
            putIfPresent(span.getAttributes(), "output", safeText(response.getContent()));
            putIfPresent(span.getAttributes(), "rawResponse", safeValue(response.getRaw()));
        }
        applyUsage(span, response.getUsage());
    }

    private void applyWorkflowResponse(TraceSpan span, AgentFlowWorkflowResponse response) {
        if (span == null || response == null) {
            return;
        }
        putIfPresent(span.getAttributes(), "status", safeText(response.getStatus()));
        putIfPresent(span.getAttributes(), "taskId", safeText(response.getTaskId()));
        putIfPresent(span.getAttributes(), "workflowRunId", safeText(response.getWorkflowRunId()));
        if (config.isRecordModelOutput()) {
            putIfPresent(span.getAttributes(), "output", safeText(response.getOutputText()));
            putIfPresent(span.getAttributes(), "outputs", safeValue(response.getOutputs()));
            putIfPresent(span.getAttributes(), "rawResponse", safeValue(response.getRaw()));
        }
        applyUsage(span, response.getUsage());
    }

    private void applyUsage(TraceSpan span, AgentFlowUsage usage) {
        if (span == null || usage == null || !config.isRecordMetrics()) {
            return;
        }
        TraceMetrics metrics = span.getMetrics();
        if (metrics == null) {
            metrics = new TraceMetrics();
            span.setMetrics(metrics);
        }
        if (usage.getInputTokens() != null) {
            metrics.setPromptTokens(Long.valueOf(usage.getInputTokens().longValue()));
        }
        if (usage.getOutputTokens() != null) {
            metrics.setCompletionTokens(Long.valueOf(usage.getOutputTokens().longValue()));
        }
        if (usage.getTotalTokens() != null) {
            metrics.setTotalTokens(Long.valueOf(usage.getTotalTokens().longValue()));
        }
    }

    private void finishSpan(TraceSpan span, TraceSpanStatus status, String error) {
        span.setStatus(status == null ? TraceSpanStatus.OK : status);
        span.setEndTime(System.currentTimeMillis());
        span.setError(error);
        if (config.isRecordMetrics()) {
            TraceMetrics metrics = span.getMetrics();
            if (metrics == null) {
                metrics = new TraceMetrics();
                span.setMetrics(metrics);
            }
            long durationMillis = Math.max(span.getEndTime() - span.getStartTime(), 0L);
            metrics.setDurationMillis(Long.valueOf(durationMillis));
        }
        exporter.export(span);
    }

    private void addSpanEvent(TraceSpan span, String name, Map<String, Object> attributes) {
        if (span == null || isBlank(name)) {
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

    private void putAttribute(TraceSpan span, String key, Object value) {
        if (span == null || isBlank(key) || value == null) {
            return;
        }
        Map<String, Object> attributes = span.getAttributes();
        if (attributes == null) {
            attributes = new LinkedHashMap<String, Object>();
            span.setAttributes(attributes);
        }
        attributes.put(key, value);
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (target != null && !isBlank(key) && value != null) {
            target.put(key, value);
        }
    }

    private Map<String, Object> singletonAttribute(String key, Object value) {
        Map<String, Object> attributes = new HashMap<String, Object>();
        if (!isBlank(key) && value != null) {
            attributes.put(key, value);
        }
        return attributes;
    }

    private Object safeValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = value instanceof String ? (String) value : JSON.toJSONString(value);
        if (config.getMasker() != null) {
            text = config.getMasker().mask(text);
        }
        int maxLength = config.getMaxFieldLength();
        if (maxLength > 0 && text.length() > maxLength) {
            text = text.substring(0, maxLength) + "...";
        }
        return text;
    }

    private String safeText(String value) {
        Object safe = safeValue(value);
        return safe == null ? null : String.valueOf(safe);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
