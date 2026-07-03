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
 * <p>The {@code inputs}/{@code outputs} fields hold the original objects for in-memory live
 * replay (re-invoke the model/tool with the real captured input). The serialized form written by
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
    private final Object outputs;
    private final long startedAtEpochMs;
    private final long capturedAtEpochMs;

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
        this.outputs = builder.outputs;
        this.startedAtEpochMs = builder.startedAtEpochMs;
        this.capturedAtEpochMs = builder.capturedAtEpochMs == null
                ? System.currentTimeMillis() : builder.capturedAtEpochMs.longValue();
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
    public Object getOutputs() { return outputs; }
    public long getStartedAtEpochMs() { return startedAtEpochMs; }
    public long getCapturedAtEpochMs() { return capturedAtEpochMs; }

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
        private Object outputs;
        private long startedAtEpochMs;
        private Long capturedAtEpochMs;

        private Builder(NodeType nodeType) { this.nodeType = nodeType; }

        Builder recordId(String v) { this.recordId = v; return this; }
        Builder runId(String v) { this.runId = v; return this; }
        Builder sessionId(String v) { this.sessionId = v; return this; }
        Builder turnId(String v) { this.turnId = v; return this; }
        Builder step(Integer v) { this.step = v; return this; }
        Builder nodeId(String v) { this.nodeId = v; return this; }
        Builder modelId(String v) { this.modelId = v; return this; }
        Builder inputs(Object v) { this.inputs = v; return this; }
        Builder outputs(Object v) { this.outputs = v; return this; }
        Builder startedAtEpochMs(long v) { this.startedAtEpochMs = v; return this; }
        Builder capturedAtEpochMs(long v) { this.capturedAtEpochMs = v; return this; }

        NodeIoRecord build() { return new NodeIoRecord(this); }
    }
}
