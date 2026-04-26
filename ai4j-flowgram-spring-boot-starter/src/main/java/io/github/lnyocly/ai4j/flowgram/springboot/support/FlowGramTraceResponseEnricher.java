package io.github.lnyocly.ai4j.flowgram.springboot.support;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.flowgram.springboot.dto.FlowGramTaskReportResponse;
import io.github.lnyocly.ai4j.flowgram.springboot.dto.FlowGramTraceView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class FlowGramTraceResponseEnricher {

    FlowGramTaskReportResponse enrichReportResponse(FlowGramTaskReportResponse response) {
        if (response == null) {
            return null;
        }
        Map<String, FlowGramTaskReportResponse.NodeStatus> nodes = enrichNodes(response.getNodes());
        return response.toBuilder()
                .nodes(nodes)
                .trace(enrichTrace(response.getTrace(), nodes))
                .build();
    }

    FlowGramTraceView enrichTrace(FlowGramTraceView trace,
                                  Map<String, FlowGramTaskReportResponse.NodeStatus> nodes) {
        if (trace == null || nodes == null || nodes.isEmpty()) {
            return trace;
        }
        Map<String, FlowGramTraceView.NodeView> sourceNodes = trace.getNodes() == null
                ? new LinkedHashMap<String, FlowGramTraceView.NodeView>()
                : trace.getNodes();
        Map<String, FlowGramTraceView.NodeView> mergedNodes = new LinkedHashMap<String, FlowGramTraceView.NodeView>();
        Long promptTokens = null;
        Long completionTokens = null;
        Long totalTokens = null;
        Double inputCost = null;
        Double outputCost = null;
        Double totalCost = null;
        String currency = trace.getSummary() == null || trace.getSummary().getMetrics() == null
                ? null
                : trace.getSummary().getMetrics().getCurrency();
        int llmNodeCount = 0;

        for (Map.Entry<String, FlowGramTraceView.NodeView> entry : sourceNodes.entrySet()) {
            String nodeId = entry.getKey();
            FlowGramTraceView.NodeView source = entry.getValue();
            NodeMetrics nodeMetrics = extractNodeMetrics(nodes.get(nodeId));
            FlowGramTraceView.MetricsView mergedMetrics = mergeMetrics(source == null ? null : source.getMetrics(), nodeMetrics);
            String model = firstNonBlank(source == null ? null : source.getModel(), nodeMetrics == null ? null : nodeMetrics.model);
            FlowGramTraceView.NodeView merged = source == null
                    ? FlowGramTraceView.NodeView.builder().nodeId(nodeId).model(model).metrics(mergedMetrics).build()
                    : source.toBuilder().model(model).metrics(mergedMetrics).build();
            mergedNodes.put(nodeId, merged);
            if (model != null || hasMetrics(mergedMetrics)) {
                llmNodeCount += 1;
                promptTokens = sum(promptTokens, mergedMetrics == null ? null : mergedMetrics.getPromptTokens());
                completionTokens = sum(completionTokens, mergedMetrics == null ? null : mergedMetrics.getCompletionTokens());
                totalTokens = sum(totalTokens, mergedMetrics == null ? null : mergedMetrics.getTotalTokens());
                inputCost = sum(inputCost, mergedMetrics == null ? null : mergedMetrics.getInputCost());
                outputCost = sum(outputCost, mergedMetrics == null ? null : mergedMetrics.getOutputCost());
                totalCost = sum(totalCost, mergedMetrics == null ? null : mergedMetrics.getTotalCost());
                if (currency == null && mergedMetrics != null) {
                    currency = mergedMetrics.getCurrency();
                }
            }
        }

        FlowGramTraceView.SummaryView summary = trace.getSummary() == null
                ? FlowGramTraceView.SummaryView.builder().build()
                : trace.getSummary().toBuilder().build();
        FlowGramTraceView.MetricsView existingSummaryMetrics = summary.getMetrics();
        FlowGramTraceView.MetricsView summaryMetrics = (existingSummaryMetrics == null
                ? FlowGramTraceView.MetricsView.builder()
                : existingSummaryMetrics.toBuilder())
                .promptTokens(firstNonNull(existingSummaryMetrics == null ? null : existingSummaryMetrics.getPromptTokens(), promptTokens))
                .completionTokens(firstNonNull(existingSummaryMetrics == null ? null : existingSummaryMetrics.getCompletionTokens(), completionTokens))
                .totalTokens(firstNonNull(existingSummaryMetrics == null ? null : existingSummaryMetrics.getTotalTokens(), totalTokens))
                .inputCost(firstNonNull(existingSummaryMetrics == null ? null : existingSummaryMetrics.getInputCost(), inputCost))
                .outputCost(firstNonNull(existingSummaryMetrics == null ? null : existingSummaryMetrics.getOutputCost(), outputCost))
                .totalCost(firstNonNull(existingSummaryMetrics == null ? null : existingSummaryMetrics.getTotalCost(), totalCost))
                .currency(firstNonBlank(existingSummaryMetrics == null ? null : existingSummaryMetrics.getCurrency(), currency))
                .build();

        return trace.toBuilder()
                .nodes(mergedNodes)
                .summary(summary.toBuilder()
                        .llmNodeCount(summary.getLlmNodeCount() == null || summary.getLlmNodeCount().intValue() <= 0
                                ? Integer.valueOf(llmNodeCount)
                                : summary.getLlmNodeCount())
                        .metrics(summaryMetrics)
                        .build())
                .build();
    }

    private Map<String, FlowGramTaskReportResponse.NodeStatus> enrichNodes(
            Map<String, FlowGramTaskReportResponse.NodeStatus> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return nodes;
        }
        Map<String, FlowGramTaskReportResponse.NodeStatus> enriched = new LinkedHashMap<String, FlowGramTaskReportResponse.NodeStatus>();
        for (Map.Entry<String, FlowGramTaskReportResponse.NodeStatus> entry : nodes.entrySet()) {
            FlowGramTaskReportResponse.NodeStatus node = entry.getValue();
            if (node == null) {
                enriched.put(entry.getKey(), null);
                continue;
            }
            NodeMetrics metrics = extractNodeMetrics(node);
            enriched.put(entry.getKey(), node.toBuilder()
                    .inputs(normalizeMap(node.getInputs()))
                    .outputs(mergeOutputs(node.getOutputs(), metrics))
                    .build());
        }
        return enriched;
    }

    private Map<String, Object> mergeOutputs(Map<String, Object> outputs, NodeMetrics metrics) {
        Map<String, Object> mergedOutputs = normalizeMap(outputs);
        if (metrics == null || !metrics.hasUsageLikeContent()) {
            return mergedOutputs;
        }
        Map<String, Object> metricMap = normalizeMap(mergedOutputs.get("metrics"));
        if (metricMap.isEmpty() && metrics.model == null && metrics.promptTokens == null
                && metrics.completionTokens == null && metrics.totalTokens == null
                && metrics.inputCost == null && metrics.outputCost == null
                && metrics.totalCost == null && metrics.currency == null) {
            return mergedOutputs;
        }
        putIfAbsent(metricMap, "model", metrics.model);
        putIfAbsent(metricMap, "promptTokens", metrics.promptTokens);
        putIfAbsent(metricMap, "completionTokens", metrics.completionTokens);
        putIfAbsent(metricMap, "totalTokens", metrics.totalTokens);
        putIfAbsent(metricMap, "inputCost", metrics.inputCost);
        putIfAbsent(metricMap, "outputCost", metrics.outputCost);
        putIfAbsent(metricMap, "totalCost", metrics.totalCost);
        putIfAbsent(metricMap, "currency", metrics.currency);
        mergedOutputs.put("metrics", metricMap);
        return mergedOutputs;
    }

    private FlowGramTraceView.MetricsView mergeMetrics(FlowGramTraceView.MetricsView existing, NodeMetrics metrics) {
        if ((existing == null || !hasMetrics(existing)) && (metrics == null || !metrics.hasUsageLikeContent())) {
            return existing;
        }
        FlowGramTraceView.MetricsView.MetricsViewBuilder builder = existing == null
                ? FlowGramTraceView.MetricsView.builder()
                : existing.toBuilder();
        return builder
                .promptTokens(firstNonNull(existing == null ? null : existing.getPromptTokens(), metrics == null ? null : metrics.promptTokens))
                .completionTokens(firstNonNull(existing == null ? null : existing.getCompletionTokens(), metrics == null ? null : metrics.completionTokens))
                .totalTokens(firstNonNull(existing == null ? null : existing.getTotalTokens(), metrics == null ? null : metrics.totalTokens))
                .inputCost(firstNonNull(existing == null ? null : existing.getInputCost(), metrics == null ? null : metrics.inputCost))
                .outputCost(firstNonNull(existing == null ? null : existing.getOutputCost(), metrics == null ? null : metrics.outputCost))
                .totalCost(firstNonNull(existing == null ? null : existing.getTotalCost(), metrics == null ? null : metrics.totalCost))
                .currency(firstNonBlank(existing == null ? null : existing.getCurrency(), metrics == null ? null : metrics.currency))
                .build();
    }

    private NodeMetrics extractNodeMetrics(FlowGramTaskReportResponse.NodeStatus node) {
        if (node == null) {
            return null;
        }
        Object inputs = normalizeTree(node.getInputs());
        Object outputs = normalizeTree(node.getOutputs());
        Object metrics = value(outputs, "metrics");
        Object rawResponse = value(outputs, "rawResponse");
        Object usage = value(rawResponse, "usage");
        return new NodeMetrics(
                firstNonBlank(
                        stringValue(metrics, "model"),
                        stringValue(rawResponse, "model"),
                        stringValue(inputs, "model"),
                        stringValue(inputs, "modelName")),
                longValue(firstNonNull(value(metrics, "promptTokens", "prompt_tokens"), value(usage, "promptTokens", "prompt_tokens", "input"))),
                longValue(firstNonNull(value(metrics, "completionTokens", "completion_tokens"), value(usage, "completionTokens", "completion_tokens", "output"))),
                longValue(firstNonNull(value(metrics, "totalTokens", "total_tokens"), value(usage, "totalTokens", "total_tokens", "total"))),
                doubleValue(value(metrics, "inputCost", "input_cost")),
                doubleValue(value(metrics, "outputCost", "output_cost")),
                doubleValue(value(metrics, "totalCost", "total_cost")),
                firstNonBlank(stringValue(metrics, "currency"))
        );
    }

    private static boolean hasMetrics(FlowGramTraceView.MetricsView metrics) {
        return metrics != null && (metrics.getPromptTokens() != null
                || metrics.getCompletionTokens() != null
                || metrics.getTotalTokens() != null
                || metrics.getInputCost() != null
                || metrics.getOutputCost() != null
                || metrics.getTotalCost() != null
                || firstNonBlank(metrics.getCurrency()) != null);
    }

    private static void putIfAbsent(Map<String, Object> target, String key, Object value) {
        if (target != null && key != null && value != null && !target.containsKey(key)) {
            target.put(key, value);
        }
    }

    @SuppressWarnings("unchecked")
    private static Object value(Object source, String... keys) {
        if (source == null || keys == null) {
            return null;
        }
        Object normalized = normalizeTree(source);
        for (String key : keys) {
            if (key == null || key.trim().isEmpty()) {
                continue;
            }
            if (normalized instanceof Map && ((Map<String, Object>) normalized).containsKey(key)) {
                Object value = ((Map<String, Object>) normalized).get(key);
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> normalizeMap(Object source) {
        Object normalized = normalizeTree(source);
        if (!(normalized instanceof Map)) {
            return new LinkedHashMap<String, Object>();
        }
        Map<String, Object> copy = new LinkedHashMap<String, Object>();
        Map<?, ?> sourceMap = (Map<?, ?>) normalized;
        for (Map.Entry<?, ?> entry : sourceMap.entrySet()) {
            copy.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return copy;
    }

    @SuppressWarnings("unchecked")
    private static Object normalizeTree(Object source) {
        if (source == null || source instanceof String || source instanceof Number || source instanceof Boolean) {
            return source;
        }
        Object normalized = source;
        if (!(source instanceof Map) && !(source instanceof List)) {
            try {
                normalized = JSON.parse(JSON.toJSONString(source));
            } catch (RuntimeException ignored) {
                return source;
            }
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

    private static String stringValue(Object source, String key) {
        Object value = value(source, key);
        return value == null ? null : String.valueOf(value);
    }

    private static Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return Long.valueOf(((Number) value).longValue());
        }
        try {
            return Long.valueOf(Long.parseLong(String.valueOf(value)));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Double doubleValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return Double.valueOf(((Number) value).doubleValue());
        }
        try {
            return Double.valueOf(Double.parseDouble(String.valueOf(value)));
        } catch (NumberFormatException ignored) {
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

        private boolean hasUsageLikeContent() {
            return firstNonBlank(model, currency) != null
                    || promptTokens != null
                    || completionTokens != null
                    || totalTokens != null
                    || inputCost != null
                    || outputCost != null
                    || totalCost != null;
        }
    }
}
