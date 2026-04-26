package io.github.lnyocly.ai4j.agent.trace;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;

public class OpenTelemetryTraceExporter extends AbstractOpenTelemetryTraceExporter {

    public OpenTelemetryTraceExporter(OpenTelemetry openTelemetry) {
        super(openTelemetry, "io.github.lnyocly.ai4j.agent.trace");
    }

    public OpenTelemetryTraceExporter(Tracer tracer) {
        super(tracer);
    }
}
