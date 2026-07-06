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
import java.util.List;

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

        // step 1: model request -> streamed deltas -> tool call/result interleaved -> final raw response -> step end
        listener.onEvent(ev(AgentEventType.MODEL_REQUEST, 1, prompt1, null));
        listener.onEvent(ev(AgentEventType.MODEL_RESPONSE, 1, null, "hello "));
        listener.onEvent(ev(AgentEventType.MODEL_RESPONSE, 1, null, "world"));
        listener.onEvent(ev(AgentEventType.TOOL_CALL, 1, call, "echo"));
        listener.onEvent(ev(AgentEventType.TOOL_RESULT, 1, toolOut, "echo:hi"));
        listener.onEvent(ev(AgentEventType.MODEL_RESPONSE, 1, "raw-resp-step1", null));
        listener.onEvent(ev(AgentEventType.STEP_END, 1, null, null));
        // step 2: non-streaming model text followed by final raw response
        listener.onEvent(ev(AgentEventType.MODEL_REQUEST, 2, prompt2, null));
        listener.onEvent(ev(AgentEventType.MODEL_RESPONSE, 2, "raw-resp-step2", "step2 text"));
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
        assertEquals("hello world", m1.getOutputText());
        assertEquals("raw-resp-step1", m1.getOutputs());

        NodeIoRecord m2 = sink.find("model@run-1|turn-1|2");
        assertNotNull(m2);
        assertEquals("step2 text", m2.getOutputText());
        assertEquals("raw-resp-step2", m2.getOutputs());

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
        listener.onEvent(ev(AgentEventType.MODEL_RESPONSE, 1, null, "captured "));
        listener.onEvent(ev(AgentEventType.MODEL_RESPONSE, 1, "captured-raw", null));
        listener.onEvent(ev(AgentEventType.STEP_END, 1, null, null));

        NodeIoRecord record = sink.records(NodeIoRecord.NodeType.MODEL).get(0);
        AgentModelResult mock = new NodeReplayer().replayModelMock(record);
        assertEquals("captured-raw", mock.getRawResponse());
        assertEquals("captured ", mock.getOutputText());
    }

    @Test
    public void replayerMockShouldFallBackToOutputTextWhenRawResponseMissing() {
        NodeIoRecord record = NodeIoRecord.builder(NodeIoRecord.NodeType.MODEL)
                .nodeId("model@fallback")
                .outputText("streamed-only")
                .build();

        AgentModelResult mock = new NodeReplayer().replayModelMock(record);
        assertEquals("streamed-only", mock.getRawResponse());
        assertEquals("streamed-only", mock.getOutputText());
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
                ev(AgentEventType.MODEL_RESPONSE, 1, null, "jsonl stream"),
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
        assertEquals("jsonl stream", reloaded.get(0).getOutputText());
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
