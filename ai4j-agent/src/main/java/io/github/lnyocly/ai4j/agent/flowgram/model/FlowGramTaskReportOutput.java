package io.github.lnyocly.ai4j.agent.flowgram.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class FlowGramTaskReportOutput {

    private Map<String, Object> inputs;
    private Map<String, Object> outputs;
    private WorkflowStatus workflow;
    private Map<String, NodeStatus> nodes;

    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkflowStatus {
        private String status;
        private boolean terminated;
        private Long startTime;
        private Long endTime;
        private String error;
    }

    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NodeStatus {
        private String status;
        private boolean terminated;
        private Long startTime;
        private Long endTime;
        private String error;
        private Map<String, Object> inputs;
        private Map<String, Object> outputs;
    }
}
