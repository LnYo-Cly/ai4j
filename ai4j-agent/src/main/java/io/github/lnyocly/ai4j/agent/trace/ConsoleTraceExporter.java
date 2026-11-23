package io.github.lnyocly.ai4j.agent.trace;

import io.github.lnyocly.ai4j.agent.util.AgentJson;

public class ConsoleTraceExporter implements TraceExporter {

    @Override
    public void export(TraceSpan span) {
        if (span == null) {
            return;
        }
        System.out.println("TRACE " + AgentJson.toJson(span));
    }
}
