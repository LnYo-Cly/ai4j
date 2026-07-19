package io.github.lnyocly.ai4j.agent.replay;

import io.github.lnyocly.ai4j.agent.event.AgentEvent;
import io.github.lnyocly.ai4j.agent.event.AgentEventType;
import io.github.lnyocly.ai4j.agent.event.AgentListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.AgentToolResult;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * An {@link AgentListener} that turns the agent event stream into replayable {@link NodeIoRecord}s.
 *
 * <p>It reuses the events the runtime already publishes — no runtime change. MODEL nodes are
 * paired by (runId, turnId, step): {@code MODEL_REQUEST} carries the full {@link AgentPrompt} as
 * input, streamed {@code MODEL_RESPONSE} messages accumulate into {@code outputText}, and the
 * final raw response payload is kept in {@code outputs}. TOOL nodes are paired by call id: {@code TOOL_CALL} carries the
 * {@link AgentToolCall} input, {@code TOOL_RESULT} carries the {@link AgentToolResult} output.
 * Records are flushed to the sink on {@code STEP_END} (model) / {@code TOOL_RESULT} (tool).
 * For MODEL nodes, {@code MODEL_REASONING} events populate {@link NodeIoRecord#getReasoningText()},
 * {@code MODEL_RETRY} events populate {@link NodeIoRecord#getRetryCount()}, and tokens are
 * best-effort parsed from the raw response usage into
 * {@link NodeIoRecord#getInputTokens()} / {@link NodeIoRecord#getOutputTokens()}.</p>
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
                case MODEL_REASONING:
                    onModelReasoning(event);
                    break;
                case MODEL_RETRY:
                    onModelRetry(event);
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
                .inputs(event.getPayload())
                .startedAtEpochMs(System.currentTimeMillis());
        if (event.getPayload() instanceof AgentPrompt) {
            b.modelId(((AgentPrompt) event.getPayload()).getModel());
        }
        pendingModels.put(key, b);
    }

    private void onModelResponse(AgentEvent event) {
        String key = stepKey(event);
        NodeIoRecord.Builder b = pendingModels.get(key);
        if (b == null) {
            return;
        }
        String message = event.getMessage();
        if (message != null && !message.isEmpty()) {
            b.outputText(appendText(b.getOutputText(), message));
        }
        // Keep the latest raw response payload; streamed deltas may still carry one.
        b.outputs(event.getPayload());
        // best-effort token extraction from the raw response usage block (provider-agnostic)
        if (event.getPayload() != null) {
            long[] usage = extractUsage(event.getPayload());
            if (usage != null) {
                if (usage[0] >= 0) { b.inputTokens(usage[0]); }
                if (usage[1] >= 0) { b.outputTokens(usage[1]); }
            }
        }
    }

    private void onModelReasoning(AgentEvent event) {
        NodeIoRecord.Builder b = pendingModels.get(stepKey(event));
        if (b == null) {
            return;
        }
        // reasoning text arrives as message (non-stream / final) or as payload (stream delta)
        String text = event.getMessage();
        if ((text == null || text.isEmpty()) && event.getPayload() instanceof String) {
            text = (String) event.getPayload();
        }
        b.appendReasoning(text);
    }

    private void onModelRetry(AgentEvent event) {
        NodeIoRecord.Builder b = pendingModels.get(stepKey(event));
        if (b != null) {
            b.incrementRetry();
        }
    }

    /**
     * Best-effort parse of the raw response usage block into {@code [inputTokens, outputTokens]}.
     * Returns {@code null} if no usage block is found. Provider-agnostic: accepts
     * {@code prompt_tokens / promptTokens / input} for input and
     * {@code completion_tokens / completionTokens / output} for output. A {@code -1} slot means
     * "key absent" so a real 0 stays distinguishable.
     */
    private static long[] extractUsage(Object payload) {
        try {
            JSONObject obj = JSON.parseObject(JSON.toJSONString(payload));
            if (obj == null) {
                return null;
            }
            JSONObject usage = obj.getJSONObject("usage");
            if (usage == null) {
                return null;
            }
            long in = firstUsageLong(usage, "prompt_tokens", "promptTokens", "input", "input_tokens");
            long out = firstUsageLong(usage, "completion_tokens", "completionTokens", "output", "output_tokens");
            return new long[]{in, out};
        } catch (Exception ignored) {
            return null;
        }
    }

    private static long firstUsageLong(JSONObject usage, String... keys) {
        for (String k : keys) {
            Long v = usage.getLong(k);
            if (v != null) {
                return v.longValue();
            }
        }
        return -1L;
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
                .inputs(event.getPayload())
                .startedAtEpochMs(System.currentTimeMillis());
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

    private static String appendText(String existing, String delta) {
        if (delta == null || delta.isEmpty()) {
            return existing;
        }
        if (existing == null || existing.isEmpty()) {
            return delta;
        }
        return existing + delta;
    }
}
