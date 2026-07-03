package io.github.lnyocly.ai4j.agent.replay;

import io.github.lnyocly.ai4j.agent.event.AgentEvent;
import io.github.lnyocly.ai4j.agent.event.AgentEventType;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.AgentToolResult;
import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Offline tests for the capture listener + replayer + JSONL sink, driven by a synthetic event
 * stream (no model/network). Live re-invocation is verified against a fake model client.
 */
public class NodeIoCaptureReplayTest {

    @Test
    public void listenerShouldPairModelAndToolEventsIntoRecords() {
        InMemoryIoCaptureSink sink = new InMemoryIoCaptureSink();
        IoCaptureAgentListener listener = new IoCaptureAgentListener(sink);

        AgentPrompt prompt1 = AgentPrompt.builder().model("test-model").build();
        AgentToolCall call = AgentToolCall.builder().name("echo").callId("call-1").arguments("{\"text\":\"hi\"}").build();
        AgentToolResult toolOut = AgentToolResult.builder().name("echo").callId("call-1").output("echo:hi").build();
        AgentPrompt prompt2 = AgentPrompt.builder().model("test-model").build();

        // step 1: model request -> (tool call/result interleaved) -> model response -> step end
        listener.onEvent(ev(AgentEventType.MODEL_REQUEST, 1, prompt1, "raw-resp-step1-pre"));
        listener.onEvent(ev(AgentEventType.TOOL_CALL, 1, call, "echo"));
        listener.onEvent(ev(AgentEventType.TOOL_RESULT, 1, toolOut, "echo:hi"));
        listener.onEvent(ev(AgentEventType.MODEL_RESPONSE, 1, "raw-resp-step1", null));
        listener.onEvent(ev(AgentEventType.STEP_END, 1, null, null));
        // step 2: final model
        listener.onEvent(ev(AgentEventType.MODEL_REQUEST, 2, prompt2, null));
        listener.onEvent(ev(AgentEventType.MODEL_RESPONSE, 2, "raw-resp-step2", null));
        listener.onEvent(ev(AgentEventType.STEP_END, 2, null, null));

        List<NodeIoRecord> all = sink.records();
        assertEquals("2 model nodes + 1 tool node", 3, all.size());

        List<NodeIoRecord> models = sink.records(NodeIoRecord.NodeType.MODEL);
        List<NodeIoRecord> tools = sink.records(NodeIoRecord.NodeType.TOOL);
        assertEquals(2, models.size());
        assertEquals(1, tools.size());

        // MODEL step1: input is the prompt, output is the LAST model response payload (deltas overwritten)
        NodeIoRecord m1 = sink.find("model@run-1|turn-1|1");
        assertNotNull(m1);
        assertTrue("model input must be the AgentPrompt", m1.getInputs() instanceof AgentPrompt);
        assertEquals("test-model", ((AgentPrompt) m1.getInputs()).getModel());
        assertEquals("test-model", m1.getModelId());
        assertEquals("raw-resp-step1", m1.getOutputs());

        // TOOL: input is the call, output is the result
        NodeIoRecord t = tools.get(0);
        assertTrue(t.getInputs() instanceof AgentToolCall);
        assertEquals("echo", ((AgentToolCall) t.getInputs()).getName());
        assertTrue(t.getOutputs() instanceof AgentToolResult);
        assertEquals("echo:hi", ((AgentToolResult) t.getOutputs()).getOutput());
    }

    @Test
    public void replayerMockShouldReturnCapturedOutputWithoutModelCall() {
        InMemoryIoCaptureSink sink = new InMemoryIoCaptureSink();
        IoCaptureAgentListener listener = new IoCaptureAgentListener(sink);
        listener.onEvent(ev(AgentEventType.MODEL_REQUEST, 1, AgentPrompt.builder().model("m").build(), null));
        listener.onEvent(ev(AgentEventType.MODEL_RESPONSE, 1, "captured-raw", null));
        listener.onEvent(ev(AgentEventType.STEP_END, 1, null, null));

        NodeIoRecord record = sink.records(NodeIoRecord.NodeType.MODEL).get(0);
        AgentModelResult mock = new NodeReplayer().replayModelMock(record);
        assertEquals("captured-raw", mock.getRawResponse());
    }

    @Test
    public void replayerLiveShouldReinvokeModelClientWithCapturedPrompt() throws Exception {
        InMemoryIoCaptureSink sink = new InMemoryIoCaptureSink();
        IoCaptureAgentListener listener = new IoCaptureAgentListener(sink);
        AgentPrompt prompt = AgentPrompt.builder().model("m").build();
        listener.onEvent(ev(AgentEventType.MODEL_REQUEST, 1, prompt, null));
        listener.onEvent(ev(AgentEventType.MODEL_RESPONSE, 1, "old-output", null));
        listener.onEvent(ev(AgentEventType.STEP_END, 1, null, null));

        NodeIoRecord record = sink.records(NodeIoRecord.NodeType.MODEL).get(0);
        RecordingModelClient client = new RecordingModelClient(AgentModelResult.builder().outputText("fresh-live-output").build());
        AgentModelResult live = new NodeReplayer().replayModelLive(record, client);

        assertEquals("fresh-live-output", live.getOutputText());
        assertTrue("live replay must re-invoke the client with the captured prompt", client.invokedPrompt != null);
        assertEquals("m", client.invokedPrompt.getModel());
    }

    @Test
    public void replayerLiveShouldRejectNonPromptInput() {
        NodeIoRecord bad = NodeIoRecord.builder(NodeIoRecord.NodeType.MODEL)
                .nodeId("model@x").inputs("not a prompt").outputs("out").build();
        try {
            new NodeReplayer().replayModelLive(bad, new RecordingModelClient(null));
            Assert.fail("expected IllegalArgumentException for non-AgentPrompt input");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("AgentPrompt"));
        }
    }

    @Test
    public void jsonlSinkShouldPersistAndReloadRecords() throws Exception {
        Path tmp = Files.createTempFile("ai4j-capture-", ".jsonl");
        tmp.toFile().deleteOnExit();
        JsonlIoCaptureSink sink = new JsonlIoCaptureSink(tmp);
        InMemoryIoCaptureSink mem = new InMemoryIoCaptureSink();
        IoCaptureAgentListener listener = new IoCaptureAgentListener(sink);

        // route the same events to both sinks to compare durable vs in-memory
        IoCaptureAgentListener memListener = new IoCaptureAgentListener(mem);
        AgentPrompt prompt = AgentPrompt.builder().model("jsonl-model").build();
        for (AgentEvent e : new AgentEvent[]{
                ev(AgentEventType.MODEL_REQUEST, 1, prompt, null),
                ev(AgentEventType.MODEL_RESPONSE, 1, "raw-1", null),
                ev(AgentEventType.STEP_END, 1, null, null)}) {
            listener.onEvent(e);
            memListener.onEvent(e);
        }
        sink.close();

        // file has one JSON line
        List<String> lines = Files.readAllLines(tmp);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("model@run-1|turn-1|1"));

        // reloaded record preserves metadata (nodeId, type, modelId); inputs/outputs are parsed JSON
        List<NodeIoRecord> reloaded = JsonlIoCaptureSink.load(tmp);
        assertEquals(1, reloaded.size());
        assertEquals(NodeIoRecord.NodeType.MODEL, reloaded.get(0).getNodeType());
        assertEquals("model@run-1|turn-1|1", reloaded.get(0).getNodeId());
        assertEquals("jsonl-model", reloaded.get(0).getModelId());
    }

    @Test
    public void reasoningRetryAndTokensShouldBeCapturedFromEvents() {
        InMemoryIoCaptureSink sink = new InMemoryIoCaptureSink();
        IoCaptureAgentListener listener = new IoCaptureAgentListener(sink);

        AgentPrompt prompt = AgentPrompt.builder().model("glm-4").build();
        // simulate a provider raw response carrying a usage block (Map form, provider-agnostic)
        Map<String, Object> usage = new HashMap<String, Object>();
        usage.put("prompt_tokens", 50L);
        usage.put("completion_tokens", 30L);
        Map<String, Object> rawResponse = new HashMap<String, Object>();
        rawResponse.put("id", "chatcmpl-1");
        rawResponse.put("usage", usage);

        listener.onEvent(ev(AgentEventType.MODEL_REQUEST, 1, prompt, null));
        listener.onEvent(ev(AgentEventType.MODEL_REASONING, 1, null, "think-A"));
        listener.onEvent(ev(AgentEventType.MODEL_REASONING, 1, null, "think-B"));
        listener.onEvent(ev(AgentEventType.MODEL_RETRY, 1, null, "rate limit"));
        listener.onEvent(ev(AgentEventType.MODEL_RESPONSE, 1, rawResponse, null));
        listener.onEvent(ev(AgentEventType.STEP_END, 1, null, null));

        List<NodeIoRecord> models = sink.records(NodeIoRecord.NodeType.MODEL);
        assertEquals("one model node", 1, models.size());
        NodeIoRecord m = models.get(0);
        assertEquals("reasoning deltas concatenate with newline", "think-A\nthink-B", m.getReasoningText());
        assertEquals("one retry event counted", 1, m.getRetryCount());
        assertEquals("input tokens parsed from usage", Long.valueOf(50L), m.getInputTokens());
        assertEquals("output tokens parsed from usage", Long.valueOf(30L), m.getOutputTokens());
        assertTrue("startedAt recorded for latency", m.getStartedAtEpochMs() > 0L);
    }

    @Test
    public void jsonlSinkShouldRoundTripReasoningTokensAndLatency() throws Exception {
        Path tmp = Files.createTempFile("ai4j-capture-", ".jsonl");
        tmp.toFile().deleteOnExit();
        JsonlIoCaptureSink sink = new JsonlIoCaptureSink(tmp);
        IoCaptureAgentListener listener = new IoCaptureAgentListener(sink);
        AgentPrompt prompt = AgentPrompt.builder().model("m").build();
        // camelCase usage keys to exercise the alternate-name path
        Map<String, Object> usage = new HashMap<String, Object>();
        usage.put("promptTokens", 7L);
        usage.put("completionTokens", 9L);
        Map<String, Object> rawResponse = new HashMap<String, Object>();
        rawResponse.put("usage", usage);

        listener.onEvent(ev(AgentEventType.MODEL_REQUEST, 1, prompt, null));
        listener.onEvent(ev(AgentEventType.MODEL_REASONING, 1, null, "chain-of-thought"));
        listener.onEvent(ev(AgentEventType.MODEL_RESPONSE, 1, rawResponse, null));
        listener.onEvent(ev(AgentEventType.STEP_END, 1, null, null));
        sink.close();

        List<NodeIoRecord> reloaded = JsonlIoCaptureSink.load(tmp);
        assertEquals(1, reloaded.size());
        NodeIoRecord r = reloaded.get(0);
        assertEquals("reasoning survives JSON round-trip", "chain-of-thought", r.getReasoningText());
        assertEquals("input tokens survive round-trip", Long.valueOf(7L), r.getInputTokens());
        assertEquals("output tokens survive round-trip", Long.valueOf(9L), r.getOutputTokens());
        assertTrue("startedAtEpochMs must survive round-trip (regression: was dropped pre-fix)",
                r.getStartedAtEpochMs() > 0L);
    }

    @Test
    public void listenerMustNeverPropagateExceptions() {
        IoCaptureSink throwing = new IoCaptureSink() {
            @Override public void capture(NodeIoRecord record) { throw new RuntimeException("boom"); }
            @Override public List<NodeIoRecord> records() { return null; }
            @Override public List<NodeIoRecord> records(NodeIoRecord.NodeType type) { return null; }
            @Override public NodeIoRecord find(String nodeId) { return null; }
            @Override public int size() { return 0; }
            @Override public void close() { }
        };
        IoCaptureAgentListener listener = new IoCaptureAgentListener(throwing);
        // must not throw even though the sink explodes
        listener.onEvent(ev(AgentEventType.MODEL_REQUEST, 1, AgentPrompt.builder().model("m").build(), null));
        listener.onEvent(ev(AgentEventType.STEP_END, 1, null, null));
    }

    private static AgentEvent ev(AgentEventType type, int step, Object payload, String message) {
        return AgentEvent.builder()
                .eventId("evt-" + step + "-" + type)
                .runId("run-1")
                .sessionId("sess-1")
                .turnId("turn-1")
                .type(type)
                .step(step)
                .message(message)
                .payload(payload)
                .build();
    }

    private static final class RecordingModelClient implements AgentModelClient {
        final AgentModelResult toReturn;
        AgentPrompt invokedPrompt;

        RecordingModelClient(AgentModelResult toReturn) { this.toReturn = toReturn; }

        @Override
        public AgentModelResult create(AgentPrompt prompt) {
            this.invokedPrompt = prompt;
            return toReturn;
        }

        @Override
        public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
            this.invokedPrompt = prompt;
            return toReturn;
        }
    }
}
