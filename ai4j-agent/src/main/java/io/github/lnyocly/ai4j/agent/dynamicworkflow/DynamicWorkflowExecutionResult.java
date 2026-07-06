package io.github.lnyocly.ai4j.agent.dynamicworkflow;

import com.alibaba.fastjson2.JSON;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class DynamicWorkflowExecutionResult {

    private String type;

    private String workflowSpecVersion;

    private String status;

    private String output;

    private String error;

    private String runtime;

    @Builder.Default
    private List<String> phases = new ArrayList<String>();

    @Builder.Default
    private List<DynamicWorkflowLogEntry> logs = new ArrayList<DynamicWorkflowLogEntry>();

    @Builder.Default
    private List<DynamicWorkflowAgentCallRecord> agentCalls = new ArrayList<DynamicWorkflowAgentCallRecord>();

    @Builder.Default
    private List<DynamicWorkflowTraceEvent> trace = new ArrayList<DynamicWorkflowTraceEvent>();

    private String stdout;

    private Long durationMillis;

    public String toJson() {
        return JSON.toJSONString(this);
    }
}
