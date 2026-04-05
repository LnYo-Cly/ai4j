package io.github.lnyocly.ai4j.agent.trace;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

abstract class AbstractOpenTelemetryTraceExporter implements TraceExporter {

    private final Tracer tracer;
    private final Map<String, TraceSpan> pendingSpans = new LinkedHashMap<String, TraceSpan>();
    private final Map<String, Span> exportedSpans = new LinkedHashMap<String, Span>();
    private final Map<String, String> spanTraceIds = new LinkedHashMap<String, String>();
    private final Map<String, Boolean> completedTraces = new LinkedHashMap<String, Boolean>();

    protected AbstractOpenTelemetryTraceExporter(OpenTelemetry openTelemetry, String instrumentationName) {
        this(openTelemetry == null ? null : openTelemetry.getTracer(instrumentationName));
    }

    protected AbstractOpenTelemetryTraceExporter(Tracer tracer) {
        if (tracer == null) {
            throw new IllegalArgumentException("tracer is required");
        }
        this.tracer = tracer;
    }

    @Override
    public synchronized void export(TraceSpan traceSpan) {
        if (traceSpan == null || traceSpan.getSpanId() == null) {
            return;
        }
        pendingSpans.put(traceSpan.getSpanId(), traceSpan);
        if (traceSpan.getParentSpanId() == null) {
            completedTraces.put(traceSpan.getTraceId(), Boolean.TRUE);
        }
        flushReadySpans(traceSpan.getTraceId());
        cleanupIfTraceComplete(traceSpan.getTraceId());
    }

    protected void customizeSpan(Span span, TraceSpan traceSpan) {
    }

    private void flushReadySpans(String traceId) {
        boolean emitted;
        do {
            emitted = false;
            Iterator<Map.Entry<String, TraceSpan>> iterator = pendingSpans.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, TraceSpan> entry = iterator.next();
                TraceSpan traceSpan = entry.getValue();
                if (!sameTrace(traceId, traceSpan.getTraceId())) {
                    continue;
                }
                String parentSpanId = traceSpan.getParentSpanId();
                if (parentSpanId != null && !exportedSpans.containsKey(parentSpanId)) {
                    continue;
                }
                Span parentSpan = parentSpanId == null ? null : exportedSpans.get(parentSpanId);
                emitSpan(traceSpan, parentSpan);
                iterator.remove();
                emitted = true;
            }
        } while (emitted);
    }

    private void emitSpan(TraceSpan traceSpan, Span parentSpan) {
        SpanBuilder builder = tracer.spanBuilder(traceSpan.getName() == null ? "ai4j.trace" : traceSpan.getName())
                .setStartTimestamp(traceSpan.getStartTime(), TimeUnit.MILLISECONDS);
        if (parentSpan != null) {
            builder.setParent(Context.current().with(parentSpan));
        }
        Span span = builder.startSpan();
        try {
            OpenTelemetryTraceSupport.applyCommonAttributes(span, traceSpan);
            customizeSpan(span, traceSpan);
        } finally {
            span.end(traceSpan.getEndTime() > 0 ? traceSpan.getEndTime() : System.currentTimeMillis(), TimeUnit.MILLISECONDS);
        }
        exportedSpans.put(traceSpan.getSpanId(), span);
        spanTraceIds.put(traceSpan.getSpanId(), traceSpan.getTraceId());
    }

    private void cleanupIfTraceComplete(String traceId) {
        if (traceId == null || !Boolean.TRUE.equals(completedTraces.get(traceId)) || hasPendingTrace(traceId)) {
            return;
        }
        Iterator<Map.Entry<String, String>> iterator = spanTraceIds.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, String> entry = iterator.next();
            if (sameTrace(traceId, entry.getValue())) {
                exportedSpans.remove(entry.getKey());
                iterator.remove();
            }
        }
        completedTraces.remove(traceId);
    }

    private boolean hasPendingTrace(String traceId) {
        for (TraceSpan traceSpan : pendingSpans.values()) {
            if (sameTrace(traceId, traceSpan.getTraceId())) {
                return true;
            }
        }
        return false;
    }

    private boolean sameTrace(String left, String right) {
        if (left == null) {
            return right == null;
        }
        return left.equals(right);
    }
}
