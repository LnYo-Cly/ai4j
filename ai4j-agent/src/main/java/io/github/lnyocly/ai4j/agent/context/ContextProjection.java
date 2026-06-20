package io.github.lnyocly.ai4j.agent.context;

import java.util.ArrayList;
import java.util.List;

public class ContextProjection {

    private final List<Object> items;
    private final ContextReport report;

    public ContextProjection(List<Object> items, ContextReport report) {
        this.items = copyItems(items);
        this.report = report == null ? null : report.copy();
    }

    public static ContextProjection of(List<Object> items, ContextReport report) {
        return new ContextProjection(items, report);
    }

    public List<Object> getItems() {
        return copyItems(items);
    }

    public ContextReport getReport() {
        return report == null ? null : report.copy();
    }

    private static List<Object> copyItems(List<Object> source) {
        return source == null ? new ArrayList<Object>() : new ArrayList<Object>(source);
    }
}
