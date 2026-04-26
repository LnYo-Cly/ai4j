package io.github.lnyocly.ai4j.agent.flowgram;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class FlowGramRuntimeEvent {

    private Type type;
    private long timestamp;
    private String taskId;
    private String nodeId;
    private String status;
    private String error;

    public enum Type {
        TASK_STARTED,
        TASK_FINISHED,
        TASK_FAILED,
        TASK_CANCELED,
        NODE_STARTED,
        NODE_FINISHED,
        NODE_FAILED,
        NODE_CANCELED
    }
}
