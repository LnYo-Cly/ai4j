package io.github.lnyocly.ai4j.agent.replay;

import java.util.UUID;

/**
 * One captured node's full input/output, assembled by {@link IoCaptureAgentListener} from the
 * agent event stream. A MODEL node pairs a {@code MODEL_REQUEST} (input = {@code AgentPrompt})
 * with its {@code MODEL_RESPONSE} (output = raw response); a TOOL node pairs a {@code TOOL_CALL}
 * with its {@code TOOL_RESULT}.
 *
 * <p>Timing: {@link #startedAtEpochMs} is recorded at request/call (node start),
 * {@link #capturedAtEpochMs} at response/result (node complete). {@link #getDurationMs()} gives
 * the per-node execution latency.
 *
 * <p>MODEL-only enrichment (TOOL nodes leave these null/zero): {@link #getReasoningText()} is
 * accumulated from {@code MODEL_REASONING} events (the model's chain-of-thought, when the provider
 * returns one); {@link #getRetryCount()} counts {@code MODEL_RETRY} events for the step;
 * {@link #getInputTokens()} / {@link #getOutputTokens()} are best-effort parsed from the raw
 * response's {@code usage} block (provider-agnostic: prompt_tokens / promptTokens / input are all
 * accepted). These make a capture self-describing for cost accounting and reasoning audit without
 * re-parsing {@link #getOutputs()}.</p>
 *
 * <p>For MODEL nodes, {@code outputText} keeps the accumulated assistant text that may arrive in
 * multiple streaming deltas, while {@code outputs} keeps the latest raw response payload. This
 * keeps human-readable replay output separate from provider-specific response objects.</p>
 *
 * <p>The {@code inputs}/{@code outputs} fields hold the original objects for in-memory live
 * replay (re-invoke the model/tool with the real captured input). For MODEL nodes, {@code outputText}
 * keeps the accumulated assistant text that may arrive in multiple streaming deltas, while
 * {@code outputs} keeps the final raw response payload. The serialized form written by
 * {@link JsonlIoCaptureSink} is the durable audit/replay artifact.</p>
 */
public final class NodeIoRecord {

    public enum NodeType { MODEL, TOOL }

    private final String recordId;
    private final String runId;
    private final String sessionId;
    private final String turnId;
    private final Integer step;
    private final NodeType nodeType;
    private final String nodeId;
    private final String modelId;
    private final Object inputs;
    private final String outputText;
    private final Object outputs;
    private final long startedAtEpochMs;
    private final long capturedAtEpochMs;
    private final String reasoningText;
    private final int retryCount;
    private final Long inputTokens;
    private final Long outputTokens;

    NodeIoRecord(Builder builder) {
        this.recordId = builder.recordId == null ? UUID.randomUUID().toString() : builder.recordId;
        this.runId = builder.runId;
        this.sessionId = builder.sessionId;
        this.turnId = builder.turnId;
        this.step = builder.step;
        this.nodeType = builder.nodeType;
        this.nodeId = builder.nodeId;
        this.modelId = builder.modelId;
        this.inputs = builder.inputs;
        this.outputText = builder.outputText;
        this.outputs = builder.outputs;
        this.startedAtEpochMs = builder.startedAtEpochMs;
        this.capturedAtEpochMs = builder.capturedAtEpochMs == null
                ? System.currentTimeMillis() : builder.capturedAtEpochMs.longValue();
        this.reasoningText = builder.reasoningText;
        this.retryCount = builder.retryCount;
        this.inputTokens = builder.inputTokens;
        this.outputTokens = builder.outputTokens;
    }

    public String getRecordId() { return recordId; }
    public String getRunId() { return runId; }
    public String getSessionId() { return sessionId; }
    public String getTurnId() { return turnId; }
    public Integer getStep() { return step; }
    public NodeType getNodeType() { return nodeType; }
    public String getNodeId() { return nodeId; }
    public String getModelId() { return modelId; }
    public Object getInputs() { return inputs; }
    public String getOutputText() { return outputText; }
    public Object getOutputs() { return outputs; }
    public long getStartedAtEpochMs() { return startedAtEpochMs; }
    public long getCapturedAtEpochMs() { return capturedAtEpochMs; }
    /** 模型思维链（仅 MODEL 节点，且 provider 返回 reasoning 时非空；流式时多段拼接）。 */
    public String getReasoningText() { return reasoningText; }
    /** 该 MODEL 节点触发的重试次数（MODEL_RETRY 事件计数）。 */
    public int getRetryCount() { return retryCount; }
    /** 输入 token（从 raw response usage 块尽力解析，可能为 null）。 */
    public Long getInputTokens() { return inputTokens; }
    /** 输出 token（从 raw response usage 块尽力解析，可能为 null）。 */
    public Long getOutputTokens() { return outputTokens; }

    /** 该节点执行耗时 = 完成时刻 - 开始时刻。 */
    public long getDurationMs() {
        return startedAtEpochMs <= 0L ? 0L : Math.max(0L, capturedAtEpochMs - startedAtEpochMs);
    }

    static Builder builder(NodeType type) {
        return new Builder(type);
    }

    static final class Builder {
        private String recordId;
        private String runId;
        private String sessionId;
        private String turnId;
        private Integer step;
        private final NodeType nodeType;
        private String nodeId;
        private String modelId;
        private Object inputs;
        private String outputText;
        private Object outputs;
        private long startedAtEpochMs;
        private Long capturedAtEpochMs;
        private String reasoningText;
        private int retryCount;
        private Long inputTokens;
        private Long outputTokens;

        private Builder(NodeType nodeType) { this.nodeType = nodeType; }

        Builder recordId(String v) { this.recordId = v; return this; }
        Builder runId(String v) { this.runId = v; return this; }
        Builder sessionId(String v) { this.sessionId = v; return this; }
        Builder turnId(String v) { this.turnId = v; return this; }
        Builder step(Integer v) { this.step = v; return this; }
        Builder nodeId(String v) { this.nodeId = v; return this; }
        Builder modelId(String v) { this.modelId = v; return this; }
        Builder inputs(Object v) { this.inputs = v; return this; }
        Builder outputText(String v) { this.outputText = v; return this; }
        Builder outputs(Object v) { this.outputs = v; return this; }
        Builder startedAtEpochMs(long v) { this.startedAtEpochMs = v; return this; }
        Builder capturedAtEpochMs(long v) { this.capturedAtEpochMs = v; return this; }
        Builder reasoningText(String v) { this.reasoningText = v; return this; }
        /** 追加一段 reasoning（流式时多次 MODEL_REASONING 拼接，换行分隔）。 */
        Builder appendReasoning(String v) {
            if (v == null || v.isEmpty()) { return this; }
            this.reasoningText = this.reasoningText == null || this.reasoningText.isEmpty()
                    ? v : this.reasoningText + "\n" + v;
            return this;
        }
        Builder incrementRetry() { this.retryCount++; return this; }
        Builder retryCount(int v) { this.retryCount = v; return this; }
        Builder inputTokens(Long v) { this.inputTokens = v; return this; }
        Builder outputTokens(Long v) { this.outputTokens = v; return this; }

        String getOutputText() { return outputText; }

        NodeIoRecord build() { return new NodeIoRecord(this); }
    }
}
