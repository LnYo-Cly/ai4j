package io.github.lnyocly.ai4j.flowgram.springboot.support;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.agent.flowgram.FlowGramRuntimeEvent;
import io.github.lnyocly.ai4j.agent.flowgram.FlowGramRuntimeListener;
import io.github.lnyocly.ai4j.agent.flowgram.model.FlowGramTaskReportOutput;
import io.github.lnyocly.ai4j.flowgram.springboot.dto.FlowGramTraceView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class FlowGramRuntimeTraceCollector implements FlowGramRuntimeListener {

    private final ConcurrentMap<String, TraceSnapshot> snapshots = new ConcurrentHashMap<String, TraceSnapshot>();

    @Override
    public void onEvent(FlowGramRuntimeEvent event) {
        if (event == null || isBlank(event.getTaskId())) {
            return;
        }
        TraceSnapshot snapshot = snapshots.computeIfAbsent(event.getTaskId(), TraceSnapshot::new);
        snapshot.onEvent(event);
    }

    public FlowGramTraceView getTrace(String taskId) {
        TraceSnapshot snapshot = taskId == null ? null : snapshots.get(taskId);
        return snapshot == null ? null : snapshot.toView();
    }

    public FlowGramTraceView getTrace(String taskId, FlowGramTaskReportOutput report) {
        TraceSnapshot snapshot = taskId == null ? null : snapshots.get(taskId);
        if (snapshot == null) {
            return null;
        }
        snapshot.mergeReport(report);
        return snapshot.toView();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static final class TraceSnapshot {

        private final String taskId;
        private final List<FlowGramTraceView.EventView> events = new ArrayList<FlowGramTraceView.EventView>();
        private final Map<String, MutableNodeTrace> nodes = new LinkedHashMap<String, MutableNodeTrace>();
        private String status;
        private Long startedAt;
        private Long endedAt;

        private TraceSnapshot(String taskId) {
            this.taskId = taskId;
        }

        private synchronized void onEvent(FlowGramRuntimeEvent event) {
            events.add(FlowGramTraceView.EventView.builder()
                    .type(event.getType() == null ? null : event.getType().name())
                    .timestamp(Long.valueOf(event.getTimestamp()))
                    .nodeId(event.getNodeId())
                    .status(event.getStatus())
                    .error(event.getError())
                    .build());
            if (event.getType() == null) {
                return;
            }
            switch (event.getType()) {
                case TASK_STARTED:
                    startedAt = Long.valueOf(event.getTimestamp());
                    status = event.getStatus();
                    break;
                case TASK_FINISHED:
                case TASK_FAILED:
                case TASK_CANCELED:
                    if (startedAt == null) {
                        startedAt = Long.valueOf(event.getTimestamp());
                    }
                    endedAt = Long.valueOf(event.getTimestamp());
                    status = event.getStatus();
                    break;
                case NODE_STARTED:
                case NODE_FINISHED:
                case NODE_FAILED:
                case NODE_CANCELED:
                    updateNode(event);
                    break;
                default:
                    break;
            }
        }

        private void updateNode(FlowGramRuntimeEvent event) {
            if (event.getNodeId() == null) {
                return;
            }
            MutableNodeTrace node = nodes.get(event.getNodeId());
            if (node == null) {
                node = new MutableNodeTrace(event.getNodeId());
                nodes.put(event.getNodeId(), node);
            }
            node.eventCount += 1;
            node.status = event.getStatus();
            if (event.getType() == FlowGramRuntimeEvent.Type.NODE_STARTED) {
                node.startedAt = Long.valueOf(event.getTimestamp());
                return;
            }
            node.endedAt = Long.valueOf(event.getTimestamp());
            node.error = event.getError();
            node.terminated = true;
            if (node.startedAt == null) {
                node.startedAt = Long.valueOf(event.getTimestamp());
            }
        }

        private synchronized void mergeReport(FlowGramTaskReportOutput report) {
            if (report == null) {
                return;
            }
            if (report.getWorkflow() != null) {
                if (report.getWorkflow().getStartTime() != null) {
                    startedAt = report.getWorkflow().getStartTime();
                }
                if (report.getWorkflow().getEndTime() != null) {
                    endedAt = report.getWorkflow().getEndTime();
                }
                if (report.getWorkflow().getStatus() != null) {
                    status = report.getWorkflow().getStatus();
                }
            }
            if (report.getNodes() == null) {
                return;
            }
            for (Map.Entry<String, FlowGramTaskReportOutput.NodeStatus> entry : report.getNodes().entrySet()) {
                String nodeId = entry.getKey();
                if (nodeId == null) {
                    continue;
                }
                MutableNodeTrace node = nodes.get(nodeId);
                if (node == null) {
                    node = new MutableNodeTrace(nodeId);
                    nodes.put(nodeId, node);
                }
                FlowGramTaskReportOutput.NodeStatus statusView = entry.getValue();
                if (statusView == null) {
                    continue;
                }
                node.status = statusView.getStatus();
                node.terminated = statusView.isTerminated();
                node.startedAt = firstNonNull(statusView.getStartTime(), node.startedAt);
                node.endedAt = firstNonNull(statusView.getEndTime(), node.endedAt);
                node.error = firstNonNull(statusView.getError(), node.error);
                NodeMetrics metrics = extractMetrics(statusView.getInputs(), statusView.getOutputs());
                if (metrics != null) {
                    node.model = firstNonNull(metrics.model, node.model);
                    node.promptTokens = firstNonNull(metrics.promptTokens, node.promptTokens);
                    node.completionTokens = firstNonNull(metrics.completionTokens, node.completionTokens);
                    node.totalTokens = firstNonNull(metrics.totalTokens, node.totalTokens);
                    node.inputCost = firstNonNull(metrics.inputCost, node.inputCost);
                    node.outputCost = firstNonNull(metrics.outputCost, node.outputCost);
                    node.totalCost = firstNonNull(metrics.totalCost, node.totalCost);
                    node.currency = firstNonNull(metrics.currency, node.currency);
                }
            }
        }

        private synchronized FlowGramTraceView toView() {
            Map<String, FlowGramTraceView.NodeView> nodeViews = new LinkedHashMap<String, FlowGramTraceView.NodeView>();
            int terminatedNodeCount = 0;
            int successNodeCount = 0;
            int failedNodeCount = 0;
            int llmNodeCount = 0;
            Long promptTokens = null;
            Long completionTokens = null;
            Long totalTokens = null;
            Double inputCost = null;
            Double outputCost = null;
            Double totalCost = null;
            String currency = null;
            for (Map.Entry<String, MutableNodeTrace> entry : nodes.entrySet()) {
                MutableNodeTrace value = entry.getValue();
                Long durationMillis = duration(value.startedAt, value.endedAt);
                FlowGramTraceView.MetricsView metricsView = buildMetricsView(value);
                nodeViews.put(entry.getKey(), FlowGramTraceView.NodeView.builder()
                        .nodeId(value.nodeId)
                        .status(value.status)
                        .terminated(value.terminated)
                        .startedAt(value.startedAt)
                        .endedAt(value.endedAt)
                        .durationMillis(durationMillis)
                        .error(value.error)
                        .eventCount(Integer.valueOf(value.eventCount))
                        .model(value.model)
                        .metrics(metricsView)
                        .build());
                if (value.terminated) {
                    terminatedNodeCount += 1;
                }
                if ("success".equalsIgnoreCase(value.status)) {
                    successNodeCount += 1;
                }
                if ("failed".equalsIgnoreCase(value.status) || "error".equalsIgnoreCase(value.status)) {
                    failedNodeCount += 1;
                }
                if (hasUsageMetrics(value)) {
                    llmNodeCount += 1;
                    promptTokens = sum(promptTokens, value.promptTokens);
                    completionTokens = sum(completionTokens, value.completionTokens);
                    totalTokens = sum(totalTokens, value.totalTokens);
                    inputCost = sum(inputCost, value.inputCost);
                    outputCost = sum(outputCost, value.outputCost);
                    totalCost = sum(totalCost, value.totalCost);
                    if (currency == null) {
                        currency = value.currency;
                    }
                }
            }
            return FlowGramTraceView.builder()
                    .taskId(taskId)
                    .status(status)
                    .startedAt(startedAt)
                    .endedAt(endedAt)
                    .summary(FlowGramTraceView.SummaryView.builder()
                            .durationMillis(duration(startedAt, endedAt))
                            .eventCount(Integer.valueOf(events.size()))
                            .nodeCount(Integer.valueOf(nodeViews.size()))
                            .terminatedNodeCount(Integer.valueOf(terminatedNodeCount))
                            .successNodeCount(Integer.valueOf(successNodeCount))
                            .failedNodeCount(Integer.valueOf(failedNodeCount))
                            .llmNodeCount(Integer.valueOf(llmNodeCount))
                            .metrics(FlowGramTraceView.MetricsView.builder()
                                    .promptTokens(promptTokens)
                                    .completionTokens(completionTokens)
                                    .totalTokens(totalTokens)
                                    .inputCost(inputCost)
                                    .outputCost(outputCost)
                                    .totalCost(totalCost)
                                    .currency(currency)
                                    .build())
                            .build())
                    .events(Collections.unmodifiableList(new ArrayList<FlowGramTraceView.EventView>(events)))
                    .nodes(Collections.unmodifiableMap(nodeViews))
                    .build();
        }

        private FlowGramTraceView.MetricsView buildMetricsView(MutableNodeTrace value) {
            if (value == null || !hasUsageMetrics(value) && value.totalCost == null && value.inputCost == null && value.outputCost == null) {
                return null;
            }
            return FlowGramTraceView.MetricsView.builder()
                    .promptTokens(value.promptTokens)
                    .completionTokens(value.completionTokens)
                    .totalTokens(value.totalTokens)
                    .inputCost(value.inputCost)
                    .outputCost(value.outputCost)
                    .totalCost(value.totalCost)
                    .currency(value.currency)
                    .build();
        }

        private boolean hasUsageMetrics(MutableNodeTrace value) {
            return value != null && (value.promptTokens != null
                    || value.completionTokens != null
                    || value.totalTokens != null
                    || value.model != null);
        }

        private NodeMetrics extractMetrics(Map<String, Object> inputs, Map<String, Object> outputs) {
            if ((inputs == null || inputs.isEmpty()) && (outputs == null || outputs.isEmpty())) {
                return null;
            }
            Object safeInputs = inputs == null ? Collections.<String, Object>emptyMap() : inputs;
            Object safeOutputs = outputs == null ? Collections.<String, Object>emptyMap() : outputs;
            Object metrics = propertyValue(safeOutputs, "metrics");
            Object rawResponse = propertyValue(safeOutputs, "rawResponse");
            Object usage = propertyValue(rawResponse, "usage");
            return new NodeMetrics(
                    firstNonBlank(
                            stringValue(metrics, "model"),
                            stringValue(rawResponse, "model"),
                            stringValue(safeInputs, "model"),
                            stringValue(safeInputs, "modelName")),
                    longObject(firstNonNull(value(metrics, "promptTokens", "prompt_tokens"), value(usage, "promptTokens", "prompt_tokens", "input"))),
                    longObject(firstNonNull(value(metrics, "completionTokens", "completion_tokens"), value(usage, "completionTokens", "completion_tokens", "output"))),
                    longObject(firstNonNull(value(metrics, "totalTokens", "total_tokens"), value(usage, "totalTokens", "total_tokens", "total"))),
                    doubleObject(value(metrics, "inputCost", "input_cost")),
                    doubleObject(value(metrics, "outputCost", "output_cost")),
                    doubleObject(value(metrics, "totalCost", "total_cost")),
                    firstNonBlank(stringValue(metrics, "currency"))
            );
        }
    }

    private static final class MutableNodeTrace {
        private final String nodeId;
        private String status;
        private boolean terminated;
        private Long startedAt;
        private Long endedAt;
        private String error;
        private int eventCount;
        private String model;
        private Long promptTokens;
        private Long completionTokens;
        private Long totalTokens;
        private Double inputCost;
        private Double outputCost;
        private Double totalCost;
        private String currency;

        private MutableNodeTrace(String nodeId) {
            this.nodeId = nodeId;
        }
    }

    private static final class NodeMetrics {
        private final String model;
        private final Long promptTokens;
        private final Long completionTokens;
        private final Long totalTokens;
        private final Double inputCost;
        private final Double outputCost;
        private final Double totalCost;
        private final String currency;

        private NodeMetrics(String model,
                            Long promptTokens,
                            Long completionTokens,
                            Long totalTokens,
                            Double inputCost,
                            Double outputCost,
                            Double totalCost,
                            String currency) {
            this.model = model;
            this.promptTokens = promptTokens;
            this.completionTokens = completionTokens;
            this.totalTokens = totalTokens;
            this.inputCost = inputCost;
            this.outputCost = outputCost;
            this.totalCost = totalCost;
            this.currency = currency;
        }
    }

    private static Long duration(Long startedAt, Long endedAt) {
        if (startedAt == null) {
            return null;
        }
        long duration = Math.max((endedAt == null ? startedAt.longValue() : endedAt.longValue()) - startedAt.longValue(), 0L);
        return Long.valueOf(duration);
    }

    private static Object value(Object source, String... keys) {
        if (source == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            Object value = propertyValue(source, key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String stringValue(Object source, String key) {
        Object value = source == null || key == null ? null : propertyValue(source, key);
        return value == null ? null : String.valueOf(value);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }

    private static <T> T firstNonNull(T left, T right) {
        return left != null ? left : right;
    }

    private static Object firstNonNull(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static Long longObject(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return Long.valueOf(((Number) value).longValue());
        }
        try {
            return Long.valueOf(Long.parseLong(String.valueOf(value)));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Double doubleObject(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return Double.valueOf(((Number) value).doubleValue());
        }
        try {
            return Double.valueOf(Double.parseDouble(String.valueOf(value)));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Long sum(Long left, Long right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return Long.valueOf(left.longValue() + right.longValue());
    }

    private static Double sum(Double left, Double right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return Double.valueOf(left.doubleValue() + right.doubleValue());
    }

    @SuppressWarnings("unchecked")
    private static Object propertyValue(Object source, String name) {
        if (source == null || name == null || name.trim().isEmpty()) {
            return null;
        }
        Object normalized = normalizeTree(source);
        if (normalized instanceof Map) {
            return ((Map<String, Object>) normalized).get(name);
        }
        Object value = invokeAccessor(normalized, "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1));
        if (value != null) {
            return value;
        }
        value = invokeAccessor(normalized, "is" + Character.toUpperCase(name.charAt(0)) + name.substring(1));
        if (value != null) {
            return value;
        }
        return fieldValue(normalized, name);
    }

    private static Object normalizedSource(Object source) {
        if (source == null || source instanceof Map || source instanceof List
                || source instanceof String || source instanceof Number || source instanceof Boolean) {
            return source;
        }
        try {
            return JSON.parseObject(JSON.toJSONString(source));
        } catch (RuntimeException ignored) {
            return source;
        }
    }

    @SuppressWarnings("unchecked")
    private static Object normalizeTree(Object source) {
        Object normalized = normalizedSource(source);
        if (normalized == null || normalized instanceof String
                || normalized instanceof Number || normalized instanceof Boolean) {
            return normalized;
        }
        if (normalized instanceof Map) {
            Map<String, Object> copy = new LinkedHashMap<String, Object>();
            Map<?, ?> sourceMap = (Map<?, ?>) normalized;
            for (Map.Entry<?, ?> entry : sourceMap.entrySet()) {
                copy.put(String.valueOf(entry.getKey()), normalizeTree(entry.getValue()));
            }
            return copy;
        }
        if (normalized instanceof List) {
            List<Object> copy = new ArrayList<Object>();
            for (Object item : (List<Object>) normalized) {
                copy.add(normalizeTree(item));
            }
            return copy;
        }
        return normalized;
    }

    private static Object invokeAccessor(Object source, String methodName) {
        try {
            Method method = source.getClass().getMethod(methodName);
            method.setAccessible(true);
            return method.invoke(source);
        } catch (Exception ignored) {
            // fall through
        }
        Class<?> type = source.getClass();
        while (type != null && type != Object.class) {
            try {
                Method method = type.getDeclaredMethod(methodName);
                method.setAccessible(true);
                return method.invoke(source);
            } catch (Exception ignored) {
                type = type.getSuperclass();
            }
        }
        return null;
    }

    private static Object fieldValue(Object source, String name) {
        Class<?> type = source.getClass();
        while (type != null && type != Object.class) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(source);
            } catch (Exception ignored) {
                type = type.getSuperclass();
            }
        }
        return null;
    }
}
