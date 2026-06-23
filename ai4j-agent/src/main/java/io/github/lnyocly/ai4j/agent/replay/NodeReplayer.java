package io.github.lnyocly.ai4j.agent.replay;

import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.AgentToolResult;

import java.util.function.Function;

/**
 * Replays a single captured {@link NodeIoRecord}.
 *
 * <p>Two modes for MODEL nodes:</p>
 * <ul>
 *   <li><b>live</b> ({@link #replayModelLive}): re-invokes the real {@link AgentModelClient} with
 *       the captured {@link AgentPrompt}. This is a REAL model call (non-deterministic) — use it
 *       to re-run a node, A/B different models, or reproduce a flow with fresh outputs.</li>
 *   <li><b>mock</b> ({@link #replayModelMock}): returns a result reconstructed from the captured
 *       output. Deterministic, no model call — use it to reproduce a past run exactly.</li>
 * </ul>
 *
 * <p>For TOOL nodes, {@link #replayToolLive} re-invokes a tool via a supplied function (the SDK
 * does not assume how tools are re-bound; the caller provides the re-invocation).</p>
 */
public class NodeReplayer {

    /**
     * Live-replays a MODEL node: re-invokes the model with the captured prompt.
     * @throws IllegalArgumentException if the record is not a MODEL node with an AgentPrompt input.
     */
    public AgentModelResult replayModelLive(NodeIoRecord record, AgentModelClient modelClient) throws Exception {
        requireModel(record);
        if (modelClient == null) {
            throw new IllegalArgumentException("modelClient must not be null");
        }
        Object input = record.getInputs();
        if (!(input instanceof AgentPrompt)) {
            throw new IllegalArgumentException(
                    "MODEL node input is not an AgentPrompt (got " + (input == null ? "null" : input.getClass()) + ")");
        }
        return modelClient.create((AgentPrompt) input);
    }

    /**
     * Deterministic/mock replay of a MODEL node: builds a result from the captured output, with
     * no model call. Useful for exact reproduction of a past turn.
     */
    public AgentModelResult replayModelMock(NodeIoRecord record) {
        requireModel(record);
        return AgentModelResult.builder()
                .rawResponse(record.getOutputs())
                .build();
    }

    /**
     * Live-replays a TOOL node by re-invoking the tool via the supplied function.
     */
    public AgentToolResult replayToolLive(NodeIoRecord record, Function<AgentToolCall, AgentToolResult> reinvoker) {
        if (record == null || record.getNodeType() != NodeIoRecord.NodeType.TOOL) {
            throw new IllegalArgumentException("record is not a TOOL node");
        }
        if (reinvoker == null) {
            throw new IllegalArgumentException("reinvoker must not be null");
        }
        Object input = record.getInputs();
        if (!(input instanceof AgentToolCall)) {
            throw new IllegalArgumentException(
                    "TOOL node input is not an AgentToolCall (got " + (input == null ? "null" : input.getClass()) + ")");
        }
        return reinvoker.apply((AgentToolCall) input);
    }

    private static void requireModel(NodeIoRecord record) {
        if (record == null || record.getNodeType() != NodeIoRecord.NodeType.MODEL) {
            throw new IllegalArgumentException("record is not a MODEL node");
        }
    }
}
