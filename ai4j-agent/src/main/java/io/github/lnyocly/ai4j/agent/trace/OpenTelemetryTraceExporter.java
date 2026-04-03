package io.github.lnyocly.ai4j.agent.trace;

import com.alibaba.fastjson2.JSON;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class OpenTelemetryTraceExporter implements TraceExporter {

    private final Tracer tracer;

    public OpenTelemetryTraceExporter(OpenTelemetry openTelemetry) {
        this(openTelemetry == null ? null : openTelemetry.getTracer("io.github.lnyocly.ai4j.agent.trace"));
    }

    public OpenTelemetryTraceExporter(Tracer tracer) {
        if (tracer == null) {
            throw new IllegalArgumentException("tracer is required");
        }
        this.tracer = tracer;
    }

    @Override
    public void export(TraceSpan traceSpan) {
        if (traceSpan == null) {
            return;
        }
        Span span = tracer.spanBuilder(traceSpan.getName() == null ? "ai4j.trace" : traceSpan.getName())
                .setStartTimestamp(traceSpan.getStartTime(), TimeUnit.MILLISECONDS)
                .startSpan();
        try {
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
            if (traceSpan.getEvents() != null) {
                for (TraceSpanEvent event : traceSpan.getEvents()) {
                    if (event == null) {
                        continue;
                    }
                    span.addEvent(event.getName() == null ? "ai4j.event" : event.getName(),
                            event.getTimestamp() > 0 ? event.getTimestamp() : System.currentTimeMillis(),
                            TimeUnit.MILLISECONDS);
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
        } finally {
            span.end(traceSpan.getEndTime() > 0 ? traceSpan.getEndTime() : System.currentTimeMillis(), TimeUnit.MILLISECONDS);
        }
    }

    private void setAttribute(Span span, String key, Object value) {
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

    private String safeSegment(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "event";
        }
        return value.trim().replace(' ', '_').toLowerCase();
    }
}
