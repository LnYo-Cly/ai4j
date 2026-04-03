package io.github.lnyocly.ai4j.agent.trace;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CompositeTraceExporter implements TraceExporter {

    private final List<TraceExporter> exporters;

    public CompositeTraceExporter(TraceExporter... exporters) {
        this(exporters == null ? null : Arrays.asList(exporters));
    }

    public CompositeTraceExporter(List<TraceExporter> exporters) {
        this.exporters = new ArrayList<TraceExporter>();
        if (exporters == null) {
            return;
        }
        for (TraceExporter exporter : exporters) {
            if (exporter != null) {
                this.exporters.add(exporter);
            }
        }
    }

    @Override
    public void export(TraceSpan span) {
        for (TraceExporter exporter : exporters) {
            exporter.export(span);
        }
    }
}
