package io.github.lnyocly.ai4j.ambient.evolve;

import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.AgentToolResult;
import io.github.lnyocly.ai4j.agent.trace.TraceMetrics;
import io.github.lnyocly.ai4j.agent.trace.TraceSpan;
import io.github.lnyocly.ai4j.agent.trace.TraceSpanStatus;
import io.github.lnyocly.ai4j.agent.trace.TraceSpanType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Normalized execution trajectory record consumed by {@link TrajectoryContrastiveMiner}
 * and {@link SkillCompiler}. Can be adapted from {@link TraceSpan} logs or {@link AgentResult}.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class TrajectoryRecord {

    private String trajectoryId;
    private String taskType;
    private String skillName;
    private boolean success;
    private int steps;
    private long totalTokens;
    private long durationMillis;
    private boolean steeredByHuman;
    private String steerFeedback;
    private String errorMessage;
    @Builder.Default
    private List<ToolStep> toolSteps = new ArrayList<ToolStep>();
    @Builder.Default
    private Map<String, Object> metadata = new LinkedHashMap<String, Object>();

    public String toolSignature() {
        if (toolSteps == null || toolSteps.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < toolSteps.size(); i++) {
            if (i > 0) {
                sb.append("->");
            }
            ToolStep step = toolSteps.get(i);
            sb.append(step == null || step.getToolName() == null ? "unknown" : step.getToolName());
        }
        return sb.toString();
    }

    public static TrajectoryRecord fromTraceSpans(String trajectoryId,
                                                  String taskType,
                                                  List<TraceSpan> spans,
                                                  boolean steeredByHuman,
                                                  String steerFeedback) {
        if (spans == null || spans.isEmpty()) {
            return TrajectoryRecord.builder()
                    .trajectoryId(trajectoryId)
                    .taskType(taskType)
                    .success(false)
                    .steeredByHuman(steeredByHuman)
                    .steerFeedback(steerFeedback)
                    .errorMessage("empty trace spans")
                    .build();
        }
        boolean success = true;
        int steps = 0;
        long tokens = 0L;
        long duration = 0L;
        String error = null;
        List<ToolStep> toolSteps = new ArrayList<ToolStep>();

        for (TraceSpan span : spans) {
            if (span == null) {
                continue;
            }
            if (span.getStatus() == TraceSpanStatus.ERROR || span.getStatus() == TraceSpanStatus.CANCELED) {
                success = false;
                if (error == null && span.getError() != null) {
                    error = span.getError();
                }
            }
            if (span.getType() == TraceSpanType.STEP) {
                steps++;
            } else if (span.getType() == TraceSpanType.RUN) {
                if (span.getEndTime() > span.getStartTime()) {
                    duration = Math.max(duration, span.getEndTime() - span.getStartTime());
                }
            } else if (span.getType() == TraceSpanType.TOOL) {
                Map<String, Object> attrs = span.getAttributes();
                String toolName = attrs != null && attrs.get("toolName") != null
                        ? String.valueOf(attrs.get("toolName"))
                        : span.getName();
                String args = attrs != null && attrs.get("arguments") != null
                        ? String.valueOf(attrs.get("arguments"))
                        : "{}";
                String output = attrs != null && attrs.get("output") != null
                        ? String.valueOf(attrs.get("output"))
                        : null;
                long stepDur = span.getEndTime() > span.getStartTime()
                        ? span.getEndTime() - span.getStartTime()
                        : 0L;
                toolSteps.add(ToolStep.builder()
                        .toolName(toolName)
                        .argumentsJson(args)
                        .outputSummary(output)
                        .success(span.getStatus() != TraceSpanStatus.ERROR)
                        .durationMillis(stepDur)
                        .errorMessage(span.getError())
                        .build());
            }
            TraceMetrics metrics = span.getMetrics();
            if (metrics != null) {
                if (metrics.getTotalTokens() != null) {
                    tokens += metrics.getTotalTokens().longValue();
                }
                if (metrics.getDurationMillis() != null && span.getType() == TraceSpanType.RUN) {
                    duration = Math.max(duration, metrics.getDurationMillis().longValue());
                }
            }
        }
        if (steps == 0) {
            steps = Math.max(1, toolSteps.size());
        }
        return TrajectoryRecord.builder()
                .trajectoryId(trajectoryId)
                .taskType(taskType)
                .success(success)
                .steps(steps)
                .totalTokens(tokens)
                .durationMillis(duration)
                .steeredByHuman(steeredByHuman)
                .steerFeedback(steerFeedback)
                .errorMessage(error)
                .toolSteps(toolSteps)
                .build();
    }

    public static TrajectoryRecord fromAgentResult(String trajectoryId,
                                                   String taskType,
                                                   AgentResult result,
                                                   long durationMillis) {
        if (result == null) {
            return TrajectoryRecord.builder()
                    .trajectoryId(trajectoryId)
                    .taskType(taskType)
                    .success(false)
                    .errorMessage("null AgentResult")
                    .build();
        }
        List<ToolStep> steps = new ArrayList<ToolStep>();
        List<AgentToolCall> calls = result.getToolCalls();
        List<AgentToolResult> results = result.getToolResults();
        if (calls != null) {
            for (int i = 0; i < calls.size(); i++) {
                AgentToolCall call = calls.get(i);
                AgentToolResult res = (results != null && i < results.size()) ? results.get(i) : null;
                boolean stepOk = res == null || res.getError() == null;
                steps.add(ToolStep.builder()
                        .toolName(call == null ? null : call.getName())
                        .argumentsJson(call == null ? null : call.getArguments())
                        .outputSummary(res == null ? null : res.getOutput())
                        .success(stepOk)
                        .errorMessage(res == null ? null : res.getError())
                        .build());
            }
        }
        int stepCount = result.getSteps() == null ? Math.max(1, steps.size()) : result.getSteps().intValue();
        long tokens = result.getTotalTokens() != null ? result.getTotalTokens().longValue()
                : ((result.getInputTokens() == null ? 0L : result.getInputTokens().longValue())
                + (result.getOutputTokens() == null ? 0L : result.getOutputTokens().longValue()));
        return TrajectoryRecord.builder()
                .trajectoryId(trajectoryId)
                .taskType(taskType)
                .success(true)
                .steps(stepCount)
                .totalTokens(tokens)
                .durationMillis(durationMillis)
                .toolSteps(steps)
                .build();
    }

    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolStep {
        private String toolName;
        private String argumentsJson;
        private String outputSummary;
        @Builder.Default
        private boolean success = true;
        private long durationMillis;
        private String errorMessage;
    }
}
