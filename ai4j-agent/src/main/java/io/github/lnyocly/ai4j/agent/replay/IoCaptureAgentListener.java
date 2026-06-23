package io.github.lnyocly.ai4j.agent.replay;

import io.github.lnyocly.ai4j.agent.event.AgentEvent;
import io.github.lnyocly.ai4j.agent.event.AgentEventType;
import io.github.lnyocly.ai4j.agent.event.AgentListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.AgentToolResult;

import java.util.HashMap;
import java.util.Map;

/**
 * An {@link AgentListener} that turns the agent event stream into replayable {@link NodeIoRecord}s.
 *
 * <p>It reuses the events the runtime already publishes — no runtime change. MODEL nodes are
 * paired by (runId, turnId, step): {@code MODEL_REQUEST} carries the full {@link AgentPrompt} as
 * input, the final {@code MODEL_RESPONSE} carries the raw response as output (streaming deltas
 * overwrite until the step ends). TOOL nodes are paired by call id: {@code TOOL_CALL} carries the
 * {@link AgentToolCall} input, {@code TOOL_RESULT} carries the {@link AgentToolResult} output.
 * Records are flushed to the sink on {@code STEP_END} (model) / {@code TOOL_RESULT} (tool).</p>
 */
public class IoCaptureAgentListener implements AgentListener {

    private final IoCaptureSink sink;
    private final Map<String, NodeIoRecord.Builder> pendingModels = new HashMap<String, NodeIoRecord.Builder>();
    private final Map<String, NodeIoRecord.Builder> pendingTools = new HashMap<String, NodeIoRecord.Builder>();

    public IoCaptureAgentListener(IoCaptureSink sink) {
        if (sink == null) {
            throw new IllegalArgumentException("sink must not be null");
        }
        this.sink = sink;
    }

    public IoCaptureSink getSink() {
        return sink;
    }

    @Override
    public synchronized void onEvent(AgentEvent event) {
        if (event == null || event.getType() == null) {
            return;
        }
        try {
            switch (event.getType()) {
                case MODEL_REQUEST:
                    onModelRequest(event);
                    break;
                case MODEL_RESPONSE:
                    onModelResponse(event);
                    break;
                case TOOL_CALL:
                    onToolCall(event);
                    break;
                case TOOL_RESULT:
                    onToolResult(event);
                    break;
                case STEP_END:
                    flushModel(event);
                    break;
                default:
                    // ignore other event types
                    break;
            }
        } catch (Exception ignored) {
            // a capture listener must never break the agent run
        }
    }

    private void onModelRequest(AgentEvent event) {
        String key = stepKey(event);
        NodeIoRecord.Builder b = NodeIoRecord.builder(NodeIoRecord.NodeType.MODEL)
                .runId(event.getRunId())
                .sessionId(event.getSessionId())
                .turnId(event.getTurnId())
                .step(event.getStep())
                .nodeId("model@" + key)
                .inputs(event.getPayload());
        if (event.getPayload() instanceof AgentPrompt) {
            b.modelId(((AgentPrompt) event.getPayload()).getModel());
        }
        pendingModels.put(key, b);
    }

    private void onModelResponse(AgentEvent event) {
        String key = stepKey(event);
        NodeIoRecord.Builder b = pendingModels.get(key);
        if (b != null) {
            // last response wins (handles streaming deltas); keep the richest payload available
            b.outputs(event.getPayload());
        }
    }

    private void flushModel(AgentEvent event) {
        String key = stepKey(event);
        NodeIoRecord.Builder b = pendingModels.remove(key);
        if (b != null) {
            sink.capture(b.build());
        }
    }

    private void onToolCall(AgentEvent event) {
        String callId = null;
        String toolName = null;
        if (event.getPayload() instanceof AgentToolCall) {
            AgentToolCall call = (AgentToolCall) event.getPayload();
            callId = call.getCallId();
            toolName = call.getName();
        }
        String key = toolKey(event, callId, toolName);
        NodeIoRecord.Builder b = NodeIoRecord.builder(NodeIoRecord.NodeType.TOOL)
                .runId(event.getRunId())
                .sessionId(event.getSessionId())
                .turnId(event.getTurnId())
                .step(event.getStep())
                .nodeId("tool@" + key)
                .inputs(event.getPayload());
        pendingTools.put(key, b);
    }

    private void onToolResult(AgentEvent event) {
        String callId = null;
        String toolName = null;
        if (event.getPayload() instanceof AgentToolResult) {
            AgentToolResult result = (AgentToolResult) event.getPayload();
            callId = result.getCallId();
            toolName = result.getName();
        }
        String key = toolKey(event, callId, toolName);
        NodeIoRecord.Builder b = pendingTools.remove(key);
        if (b != null) {
            b.outputs(event.getPayload());
            sink.capture(b.build());
        } else {
            // result without a paired call: capture best-effort
            sink.capture(NodeIoRecord.builder(NodeIoRecord.NodeType.TOOL)
                    .runId(event.getRunId())
                    .sessionId(event.getSessionId())
                    .turnId(event.getTurnId())
                    .step(event.getStep())
                    .nodeId("tool@" + key)
                    .outputs(event.getPayload())
                    .build());
        }
    }

    private static String stepKey(AgentEvent event) {
        return safe(event.getRunId()) + "|" + safe(event.getTurnId()) + "|" + event.getStep();
    }

    private static String toolKey(AgentEvent event, String callId, String toolName) {
        String discrim = callId != null ? callId : (toolName != null ? toolName : "anon");
        return stepKey(event) + "|" + discrim;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
