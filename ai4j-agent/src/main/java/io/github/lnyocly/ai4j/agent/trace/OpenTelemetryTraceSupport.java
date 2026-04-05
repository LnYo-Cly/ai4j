package io.github.lnyocly.ai4j.agent.trace;

import com.alibaba.fastjson2.JSON;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;

import java.util.Map;

final class OpenTelemetryTraceSupport {

    private OpenTelemetryTraceSupport() {
    }

    static void applyCommonAttributes(Span span, TraceSpan traceSpan) {
        if (span == null || traceSpan == null) {
            return;
        }
        setAttribute(span, "ai4j.trace_id", traceSpan.getTraceId());
        setAttribute(span, "ai4j.span_id", traceSpan.getSpanId());
        setAttribute(span, "ai4j.parent_span_id", traceSpan.getParentSpanId());
        setAttribute(span, "ai4j.span_type", traceSpan.getType() == null ? null : traceSpan.getType().name());
        setAttribute(span, "ai4j.span_status", traceSpan.getStatus() == null ? null : traceSpan.getStatus().name());
        setAttribute(span, "ai4j.error", traceSpan.getError());
        if (traceSpan.getAttributes() != null) {
            for (Map.Entry<String, Object> entry : traceSpan.getAttributes().entrySet()) {
                setAttribute(span, "ai4j.attr." + entry.getKey(), entry.getValue());
            }
        }
        TraceMetrics metrics = traceSpan.getMetrics();
        if (metrics != null) {
            setAttribute(span, "ai4j.metrics.duration_ms", metrics.getDurationMillis());
            setAttribute(span, "ai4j.metrics.prompt_tokens", metrics.getPromptTokens());
            setAttribute(span, "ai4j.metrics.completion_tokens", metrics.getCompletionTokens());
            setAttribute(span, "ai4j.metrics.total_tokens", metrics.getTotalTokens());
            setAttribute(span, "ai4j.metrics.input_cost", metrics.getInputCost());
            setAttribute(span, "ai4j.metrics.output_cost", metrics.getOutputCost());
            setAttribute(span, "ai4j.metrics.total_cost", metrics.getTotalCost());
            setAttribute(span, "ai4j.metrics.currency", metrics.getCurrency());

            setAttribute(span, "gen_ai.usage.input_tokens", metrics.getPromptTokens());
            setAttribute(span, "gen_ai.usage.output_tokens", metrics.getCompletionTokens());
        }
        if (traceSpan.getEvents() != null) {
            for (TraceSpanEvent event : traceSpan.getEvents()) {
                if (event == null) {
                    continue;
                }
                span.addEvent(event.getName() == null ? "ai4j.event" : event.getName());
                if (event.getAttributes() != null) {
                    for (Map.Entry<String, Object> entry : event.getAttributes().entrySet()) {
                        setAttribute(span, "ai4j.event." + safeSegment(event.getName()) + "." + entry.getKey(), entry.getValue());
                    }
                }
            }
        }
        if (traceSpan.getStatus() == TraceSpanStatus.ERROR) {
            span.setStatus(StatusCode.ERROR, traceSpan.getError() == null ? "ai4j trace error" : traceSpan.getError());
        }
    }

    static void setAttribute(Span span, String key, Object value) {
        if (span == null || key == null || value == null) {
            return;
        }
        if (value instanceof Boolean) {
            span.setAttribute(AttributeKey.booleanKey(key), (Boolean) value);
            return;
        }
        if (value instanceof Long) {
            span.setAttribute(AttributeKey.longKey(key), (Long) value);
            return;
        }
        if (value instanceof Integer) {
            span.setAttribute(AttributeKey.longKey(key), ((Integer) value).longValue());
            return;
        }
        if (value instanceof Double) {
            span.setAttribute(AttributeKey.doubleKey(key), (Double) value);
            return;
        }
        if (value instanceof Float) {
            span.setAttribute(AttributeKey.doubleKey(key), ((Float) value).doubleValue());
            return;
        }
        span.setAttribute(AttributeKey.stringKey(key), value instanceof String ? (String) value : JSON.toJSONString(value));
    }

    static String safeSegment(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "event";
        }
        return value.trim().replace(' ', '_').toLowerCase();
    }
}
