package io.github.lnyocly.ai4j.flowgram.springboot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class FlowGramTraceView {

    private String taskId;
    private String status;
    private Long startedAt;
    private Long endedAt;
    private List<EventView> events;
    private Map<String, NodeView> nodes;

    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EventView {
        private String type;
        private Long timestamp;
        private String nodeId;
        private String status;
        private String error;
    }

    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NodeView {
        private String nodeId;
        private String status;
        private boolean terminated;
        private Long startedAt;
        private Long endedAt;
        private String error;
        private Integer eventCount;
    }
}
