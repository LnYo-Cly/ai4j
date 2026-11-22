package io.github.lnyocly.ai4j.web.codec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lnyocly.ai4j.web.sse.AiSseEvent;
import io.github.lnyocly.ai4j.web.sse.AiSseSink;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AguiSseCodecTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void shouldSerializeFullAguiLifecycleEventsWithJackson() throws Exception {
        AguiSseCodec codec = new AguiSseCodec();

        Map<String, Object> input = Collections.<String, Object>singletonMap("prompt", "weather?");
        Map<String, Object> snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("step", "done");
        snapshot.put("count", 2);
        Map<String, Object> result = Collections.<String, Object>singletonMap("answer", "22C");

        List<AiSseEvent> frames = new ArrayList<AiSseEvent>();
        frames.add(codec.runStarted("thread-1", "run-1", input));
        frames.add(codec.stepStarted("agent_loop"));
        frames.add(codec.reasoningMessageStart("reason-1"));
        frames.add(codec.reasoningMessageContent("reason-1", "Checking tool..."));
        frames.add(codec.reasoningMessageEnd("reason-1"));
        frames.add(codec.toolCallStart("call-1", "get_weather", "msg-1"));
        frames.add(codec.toolCallArgs("call-1", "{\"city\":\"Beijing\"}"));
        frames.add(codec.toolCallEnd("call-1"));
        frames.add(codec.toolCallResult("msg-tool-1", "call-1", "{\"temp\":22}"));
        frames.add(codec.textMessageStart("msg-1"));
        frames.add(codec.textMessageContent("msg-1", "It is 22C."));
        frames.add(codec.textMessageEnd("msg-1"));
        frames.add(codec.stateSnapshot(snapshot));
        frames.add(codec.stepFinished("agent_loop"));
        frames.add(codec.runFinished("thread-1", "run-1", result));

        Assert.assertEquals(15, frames.size());

        JsonNode runStarted = MAPPER.readTree(frames.get(0).getData());
        Assert.assertEquals("RUN_STARTED", runStarted.get("type").asText());
        Assert.assertEquals("thread-1", runStarted.get("threadId").asText());
        Assert.assertEquals("run-1", runStarted.get("runId").asText());
        Assert.assertEquals("weather?", runStarted.get("input").get("prompt").asText());

        JsonNode stepStarted = MAPPER.readTree(frames.get(1).getData());
        Assert.assertEquals("STEP_STARTED", stepStarted.get("type").asText());
        Assert.assertEquals("agent_loop", stepStarted.get("stepName").asText());

        JsonNode reasoningContent = MAPPER.readTree(frames.get(3).getData());
        Assert.assertEquals("REASONING_MESSAGE_CONTENT", reasoningContent.get("type").asText());
        Assert.assertEquals("reason-1", reasoningContent.get("messageId").asText());
        Assert.assertEquals("Checking tool...", reasoningContent.get("delta").asText());

        JsonNode toolStart = MAPPER.readTree(frames.get(5).getData());
        Assert.assertEquals("TOOL_CALL_START", toolStart.get("type").asText());
        Assert.assertEquals("call-1", toolStart.get("toolCallId").asText());
        Assert.assertEquals("get_weather", toolStart.get("toolCallName").asText());
        Assert.assertEquals("msg-1", toolStart.get("parentMessageId").asText());

        JsonNode toolArgs = MAPPER.readTree(frames.get(6).getData());
        Assert.assertEquals("TOOL_CALL_ARGS", toolArgs.get("type").asText());
        Assert.assertEquals("{\"city\":\"Beijing\"}", toolArgs.get("delta").asText());

        JsonNode toolEnd = MAPPER.readTree(frames.get(7).getData());
        Assert.assertEquals("TOOL_CALL_END", toolEnd.get("type").asText());
        Assert.assertEquals("call-1", toolEnd.get("toolCallId").asText());

        JsonNode toolResult = MAPPER.readTree(frames.get(8).getData());
        Assert.assertEquals("TOOL_CALL_RESULT", toolResult.get("type").asText());
        Assert.assertEquals("msg-tool-1", toolResult.get("messageId").asText());
        Assert.assertEquals("call-1", toolResult.get("toolCallId").asText());
        Assert.assertEquals("tool", toolResult.get("role").asText());
        Assert.assertEquals("{\"temp\":22}", toolResult.get("content").asText());

        JsonNode textStart = MAPPER.readTree(frames.get(9).getData());
        Assert.assertEquals("TEXT_MESSAGE_START", textStart.get("type").asText());
        Assert.assertEquals("assistant", textStart.get("role").asText());

        JsonNode textContent = MAPPER.readTree(frames.get(10).getData());
        Assert.assertEquals("TEXT_MESSAGE_CONTENT", textContent.get("type").asText());
        Assert.assertEquals("It is 22C.", textContent.get("delta").asText());

        JsonNode textEnd = MAPPER.readTree(frames.get(11).getData());
        Assert.assertEquals("TEXT_MESSAGE_END", textEnd.get("type").asText());

        JsonNode stateSnapshot = MAPPER.readTree(frames.get(12).getData());
        Assert.assertEquals("STATE_SNAPSHOT", stateSnapshot.get("type").asText());
        Assert.assertEquals("done", stateSnapshot.get("snapshot").get("step").asText());
        Assert.assertEquals(2, stateSnapshot.get("snapshot").get("count").asInt());

        JsonNode runFinished = MAPPER.readTree(frames.get(14).getData());
        Assert.assertEquals("RUN_FINISHED", runFinished.get("type").asText());
        Assert.assertEquals("22C", runFinished.get("result").get("answer").asText());

        for (AiSseEvent frame : frames) {
            Assert.assertNull(frame.getEvent());
            Assert.assertTrue(frame.toWireFormat().startsWith("data: {"));
            Assert.assertTrue(frame.toWireFormat().endsWith("\n\n"));
        }
    }

    @Test
    public void shouldSupportNamedSseEventsErrorFramesAndSinkEmission() throws Exception {
        AguiSseCodec codec = new AguiSseCodec(new ObjectMapper(), true);
        final List<AiSseEvent> emitted = new ArrayList<AiSseEvent>();
        AiSseSink recordingSink = new AiSseSink() {
            private boolean closed;

            @Override
            public void send(AiSseEvent event) throws IOException {
                emitted.add(event);
            }

            @Override
            public void complete() {
                closed = true;
            }

            @Override
            public void completeWithError(Throwable error) {
                closed = true;
            }

            @Override
            public void onCancel(Runnable cancelAction) {
            }

            @Override
            public boolean isClosed() {
                return closed;
            }
        };

        recordingSink.send(codec.runStarted("t-1", "r-1"));
        recordingSink.send(codec.toolCallStart("c-1", "search"));
        recordingSink.send(codec.toolCallResult("c-1", "ok"));
        recordingSink.send(codec.runError("Rate limited", "RATE_LIMIT"));
        recordingSink.send(codec.runError(null));
        recordingSink.send(codec.runFinished("t-1", "r-1"));
        recordingSink.complete();

        Assert.assertEquals(6, emitted.size());
        Assert.assertEquals("RUN_STARTED", emitted.get(0).getEvent());
        Assert.assertEquals("TOOL_CALL_START", emitted.get(1).getEvent());
        Assert.assertEquals("RUN_ERROR", emitted.get(3).getEvent());

        JsonNode errorNode = MAPPER.readTree(emitted.get(3).getData());
        Assert.assertEquals("Rate limited", errorNode.get("message").asText());
        Assert.assertEquals("RATE_LIMIT", errorNode.get("code").asText());

        JsonNode fallbackError = MAPPER.readTree(emitted.get(4).getData());
        Assert.assertEquals("Unknown error", fallbackError.get("message").asText());
        Assert.assertFalse(fallbackError.has("code"));
        Assert.assertTrue(recordingSink.isClosed());
    }
}
