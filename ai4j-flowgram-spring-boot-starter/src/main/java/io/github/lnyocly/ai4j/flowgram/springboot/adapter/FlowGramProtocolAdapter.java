package io.github.lnyocly.ai4j.flowgram.springboot.adapter;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.agent.flowgram.model.FlowGramTaskReportOutput;
import io.github.lnyocly.ai4j.agent.flowgram.model.FlowGramTaskResultOutput;
import io.github.lnyocly.ai4j.agent.flowgram.model.FlowGramTaskRunInput;
import io.github.lnyocly.ai4j.agent.flowgram.model.FlowGramTaskRunOutput;
import io.github.lnyocly.ai4j.agent.flowgram.model.FlowGramTaskValidateOutput;
import io.github.lnyocly.ai4j.flowgram.springboot.dto.FlowGramTaskCancelResponse;
import io.github.lnyocly.ai4j.flowgram.springboot.dto.FlowGramTaskReportResponse;
import io.github.lnyocly.ai4j.flowgram.springboot.dto.FlowGramTaskResultResponse;
import io.github.lnyocly.ai4j.flowgram.springboot.dto.FlowGramTaskRunRequest;
import io.github.lnyocly.ai4j.flowgram.springboot.dto.FlowGramTaskRunResponse;
import io.github.lnyocly.ai4j.flowgram.springboot.dto.FlowGramTraceView;
import io.github.lnyocly.ai4j.flowgram.springboot.dto.FlowGramTaskValidateRequest;
import io.github.lnyocly.ai4j.flowgram.springboot.dto.FlowGramTaskValidateResponse;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FlowGramProtocolAdapter {

    public FlowGramTaskRunInput toTaskRunInput(FlowGramTaskRunRequest request) {
        return FlowGramTaskRunInput.builder()
                .schema(schemaToJson(request == null ? null : request.getSchema()))
                .inputs(safeMap(request == null ? null : request.getInputs()))
                .build();
    }

    public FlowGramTaskRunInput toTaskRunInput(FlowGramTaskValidateRequest request) {
        return FlowGramTaskRunInput.builder()
                .schema(schemaToJson(request == null ? null : request.getSchema()))
                .inputs(safeMap(request == null ? null : request.getInputs()))
                .build();
    }

    public FlowGramTaskRunResponse toRunResponse(FlowGramTaskRunOutput output) {
        return FlowGramTaskRunResponse.builder()
                .taskId(output == null ? null : output.getTaskID())
                .build();
    }

    public FlowGramTaskValidateResponse toValidateResponse(FlowGramTaskValidateOutput output) {
        return FlowGramTaskValidateResponse.builder()
                .valid(output != null && output.isValid())
                .errors(output == null || output.getErrors() == null
                        ? Collections.<String>emptyList()
                        : output.getErrors())
                .build();
    }

    public FlowGramTaskReportResponse toReportResponse(String taskId,
                                                       FlowGramTaskReportOutput output,
                                                       boolean includeNodeDetails,
                                                       FlowGramTraceView trace) {
        Map<String, FlowGramTaskReportResponse.NodeStatus> nodes = null;
        if (includeNodeDetails && output != null && output.getNodes() != null) {
            nodes = new LinkedHashMap<String, FlowGramTaskReportResponse.NodeStatus>();
            for (Map.Entry<String, FlowGramTaskReportOutput.NodeStatus> entry : output.getNodes().entrySet()) {
                FlowGramTaskReportOutput.NodeStatus value = entry.getValue();
                nodes.put(entry.getKey(), FlowGramTaskReportResponse.NodeStatus.builder()
                        .status(value == null ? null : value.getStatus())
                        .terminated(value != null && value.isTerminated())
                        .startTime(value == null ? null : value.getStartTime())
                        .endTime(value == null ? null : value.getEndTime())
                        .error(value == null ? null : value.getError())
                        .inputs(value == null ? Collections.<String, Object>emptyMap() : safeMap(value.getInputs()))
                        .outputs(value == null ? Collections.<String, Object>emptyMap() : safeMap(value.getOutputs()))
                        .build());
            }
        }
        FlowGramTaskReportOutput.WorkflowStatus workflow = output == null ? null : output.getWorkflow();
        return FlowGramTaskReportResponse.builder()
                .taskId(taskId)
                .inputs(output == null ? Collections.<String, Object>emptyMap() : safeMap(output.getInputs()))
                .outputs(output == null ? Collections.<String, Object>emptyMap() : safeMap(output.getOutputs()))
                .workflow(FlowGramTaskReportResponse.WorkflowStatus.builder()
                        .status(workflow == null ? null : workflow.getStatus())
                        .terminated(workflow != null && workflow.isTerminated())
                        .startTime(workflow == null ? null : workflow.getStartTime())
                        .endTime(workflow == null ? null : workflow.getEndTime())
                        .error(workflow == null ? null : workflow.getError())
                        .build())
                .nodes(nodes)
                .trace(trace)
                .build();
    }

    public FlowGramTaskResultResponse toResultResponse(String taskId,
                                                       FlowGramTaskResultOutput output,
                                                       FlowGramTraceView trace) {
        return FlowGramTaskResultResponse.builder()
                .taskId(taskId)
                .status(output == null ? null : output.getStatus())
                .terminated(output != null && output.isTerminated())
                .error(output == null ? null : output.getError())
                .result(output == null ? Collections.<String, Object>emptyMap() : safeMap(output.getResult()))
                .trace(trace)
                .build();
    }

    public FlowGramTaskCancelResponse toCancelResponse(boolean success) {
        return FlowGramTaskCancelResponse.builder()
                .success(success)
                .build();
    }

    private String schemaToJson(Object schema) {
        if (schema == null) {
            return null;
        }
        if (schema instanceof String) {
            return (String) schema;
        }
        return JSON.toJSONString(schema);
    }

    private Map<String, Object> safeMap(Map<String, Object> source) {
        Map<String, Object> target = new LinkedHashMap<String, Object>();
        if (source == null) {
            return target;
        }
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            target.put(entry.getKey(), copyValue(entry.getValue()));
        }
        return target;
    }

    @SuppressWarnings("unchecked")
    private Object copyValue(Object value) {
        if (value instanceof Map) {
            Map<String, Object> copy = new LinkedHashMap<String, Object>();
            Map<?, ?> source = (Map<?, ?>) value;
            for (Map.Entry<?, ?> entry : source.entrySet()) {
                copy.put(String.valueOf(entry.getKey()), copyValue(entry.getValue()));
            }
            return copy;
        }
        if (value instanceof List) {
            java.util.List<Object> copy = new java.util.ArrayList<Object>();
            for (Object item : (List<Object>) value) {
                copy.add(copyValue(item));
            }
            return copy;
        }
        return value;
    }
}
