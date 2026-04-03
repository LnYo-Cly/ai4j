package io.github.lnyocly.ai4j.agent.trace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class InMemoryTraceExporter implements TraceExporter {

    private final List<TraceSpan> spans = new ArrayList<>();

    @Override
    public synchronized void export(TraceSpan span) {
        if (span != null) {
            spans.add(span);
        }
    }

    public synchronized List<TraceSpan> getSpans() {
        return Collections.unmodifiableList(new ArrayList<>(spans));
    }
}
