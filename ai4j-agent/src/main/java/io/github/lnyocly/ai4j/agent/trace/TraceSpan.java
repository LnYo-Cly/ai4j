package io.github.lnyocly.ai4j.agent.trace;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder(toBuilder = true)
public class TraceSpan {

    private String traceId;
    private String spanId;
    private String parentSpanId;
    private String name;
    private TraceSpanType type;
    private TraceSpanStatus status;
    private long startTime;
    private long endTime;
    private String error;
    private Map<String, Object> attributes;
    private List<TraceSpanEvent> events;
    private TraceMetrics metrics;
}
