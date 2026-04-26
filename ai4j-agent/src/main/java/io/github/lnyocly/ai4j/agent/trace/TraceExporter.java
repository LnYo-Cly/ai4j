package io.github.lnyocly.ai4j.agent.trace;

public interface TraceExporter {

    void export(TraceSpan span);
}
