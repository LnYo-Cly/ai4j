package io.github.lnyocly.ai4j.flowgram.springboot.support;

import io.github.lnyocly.ai4j.agent.flowgram.FlowGramRuntimeEvent;
import io.github.lnyocly.ai4j.agent.flowgram.FlowGramRuntimeListener;
import io.github.lnyocly.ai4j.flowgram.springboot.dto.FlowGramTraceView;

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

        private synchronized FlowGramTraceView toView() {
            Map<String, FlowGramTraceView.NodeView> nodeViews = new LinkedHashMap<String, FlowGramTraceView.NodeView>();
            for (Map.Entry<String, MutableNodeTrace> entry : nodes.entrySet()) {
                MutableNodeTrace value = entry.getValue();
                nodeViews.put(entry.getKey(), FlowGramTraceView.NodeView.builder()
                        .nodeId(value.nodeId)
                        .status(value.status)
                        .terminated(value.terminated)
                        .startedAt(value.startedAt)
                        .endedAt(value.endedAt)
                        .error(value.error)
                        .eventCount(Integer.valueOf(value.eventCount))
                        .build());
            }
            return FlowGramTraceView.builder()
                    .taskId(taskId)
                    .status(status)
                    .startedAt(startedAt)
                    .endedAt(endedAt)
                    .events(Collections.unmodifiableList(new ArrayList<FlowGramTraceView.EventView>(events)))
                    .nodes(Collections.unmodifiableMap(nodeViews))
                    .build();
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

        private MutableNodeTrace(String nodeId) {
            this.nodeId = nodeId;
        }
    }
}
