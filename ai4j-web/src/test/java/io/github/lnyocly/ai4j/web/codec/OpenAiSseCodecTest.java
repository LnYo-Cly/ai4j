package io.github.lnyocly.ai4j.web.codec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lnyocly.ai4j.listener.SseListener;
import io.github.lnyocly.ai4j.platform.openai.usage.Usage;
import io.github.lnyocly.ai4j.web.sse.AiSseEvent;
import io.github.lnyocly.ai4j.web.sse.AiSseSink;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class OpenAiSseCodecTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void shouldEncodeOpenAiChunksToolCallsUsageAndDoneFrame() throws Exception {
        OpenAiSseCodec codec = new OpenAiSseCodec(new ObjectMapper(), "chatcmpl-123", "gpt-4o", 1700000000L, "fp_abc");

        AiSseEvent role = codec.roleChunk();
        AiSseEvent reasoning = codec.reasoningDelta("Think ");
        AiSseEvent content = codec.contentDelta("Hello");
        AiSseEvent toolStart = codec.toolCallDelta(0, "call_1", "get_weather", "{\"city\":");
        AiSseEvent toolCont = codec.toolCallDelta(0, null, null, "\"Shanghai\"}");
        Usage usage = new Usage(10L, 5L, 15L);
        AiSseEvent finish = codec.finishChunk("tool_calls", usage);
        AiSseEvent usageOnly = codec.usageChunk(usage);
        AiSseEvent done = OpenAiSseCodec.doneFrame();

        JsonNode roleJson = MAPPER.readTree(role.getData());
        Assert.assertEquals("chatcmpl-123", roleJson.get("id").asText());
        Assert.assertEquals("chat.completion.chunk", roleJson.get("object").asText());
        Assert.assertEquals(1700000000L, roleJson.get("created").asLong());
        Assert.assertEquals("gpt-4o", roleJson.get("model").asText());
        Assert.assertEquals("fp_abc", roleJson.get("system_fingerprint").asText());
        Assert.assertEquals("assistant", roleJson.get("choices").get(0).get("delta").get("role").asText());
        Assert.assertTrue(roleJson.get("choices").get(0).get("finish_reason").isNull());

        JsonNode reasoningJson = MAPPER.readTree(reasoning.getData());
        Assert.assertEquals("Think ", reasoningJson.get("choices").get(0).get("delta").get("reasoning_content").asText());

        JsonNode contentJson = MAPPER.readTree(content.getData());
        Assert.assertEquals("Hello", contentJson.get("choices").get(0).get("delta").get("content").asText());

        JsonNode toolStartCall = MAPPER.readTree(toolStart.getData())
                .get("choices").get(0).get("delta").get("tool_calls").get(0);
        Assert.assertEquals(0, toolStartCall.get("index").asInt());
        Assert.assertEquals("call_1", toolStartCall.get("id").asText());
        Assert.assertEquals("function", toolStartCall.get("type").asText());
        Assert.assertEquals("get_weather", toolStartCall.get("function").get("name").asText());
        Assert.assertEquals("{\"city\":", toolStartCall.get("function").get("arguments").asText());

        JsonNode toolContCall = MAPPER.readTree(toolCont.getData())
                .get("choices").get(0).get("delta").get("tool_calls").get(0);
        Assert.assertEquals(0, toolContCall.get("index").asInt());
        Assert.assertFalse(toolContCall.has("id"));
        Assert.assertFalse(toolContCall.has("type"));
        Assert.assertFalse(toolContCall.get("function").has("name"));
        Assert.assertEquals("\"Shanghai\"}", toolContCall.get("function").get("arguments").asText());

        JsonNode finishJson = MAPPER.readTree(finish.getData());
        Assert.assertEquals("tool_calls", finishJson.get("choices").get(0).get("finish_reason").asText());
        Assert.assertEquals(15L, finishJson.get("usage").get("total_tokens").asLong());

        JsonNode usageOnlyJson = MAPPER.readTree(usageOnly.getData());
        Assert.assertEquals(0, usageOnlyJson.get("choices").size());
        Assert.assertEquals(10L, usageOnlyJson.get("usage").get("prompt_tokens").asLong());

        Assert.assertEquals("[DONE]", done.getData());
        Assert.assertEquals("data: [DONE]\n\n", done.toWireFormat());
    }

    @Test
    public void shouldRoundTripWithAiSseSinkAndAi4jSseListener() throws IOException {
        OpenAiSseCodec codec = new OpenAiSseCodec("chatcmpl-rt", "deepseek-chat");
        final List<AiSseEvent> frames = new ArrayList<AiSseEvent>();
        AiSseSink sink = new AiSseSink() {
            private boolean closed;

            @Override
            public void send(AiSseEvent event) {
                frames.add(event);
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

        sink.send(codec.roleChunk());
        sink.send(codec.reasoningDelta("Plan."));
        sink.send(codec.contentDelta("Answer."));
        sink.send(codec.toolCallDelta(0, "call_rt", "lookup", "{\"q\":"));
        sink.send(codec.toolCallDelta(0, null, null, "\"ai4j\"}"));
        sink.send(codec.finishChunk("tool_calls"));
        sink.send(codec.usageChunk(new Usage(3L, 4L, 7L)));
        sink.send(OpenAiSseCodec.doneFrame());
        sink.sendComment("keepalive");
        sink.complete();

        Assert.assertEquals(": keepalive\n\n", frames.remove(frames.size() - 1).toWireFormat());

        OpenAiSseCodec defaultCodec = new OpenAiSseCodec();
        Assert.assertTrue(defaultCodec.getId().startsWith("chatcmpl-"));
        Assert.assertNull(defaultCodec.getModel());
        Assert.assertTrue(defaultCodec.getCreated() > 0L);
        Assert.assertNull(defaultCodec.getSystemFingerprint());
        Assert.assertTrue(defaultCodec.roleChunk("assistant").getData().contains("\"assistant\""));

        try {
            defaultCodec.toolCallDelta(-1, "id", "fn", "{}");
            Assert.fail("Expected IllegalArgumentException for negative index");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("index"));
        }

        final List<String> deltas = new ArrayList<String>();
        SseListener listener = new SseListener() {
            @Override
            protected void send() {
                if (!getCurrStr().isEmpty()) {
                    deltas.add(getCurrStr());
                }
            }
        };

        for (AiSseEvent frame : frames) {
            listener.onEvent(null, frame.getId(), frame.getEvent(), frame.getData());
        }

        Assert.assertEquals("Plan.", listener.getReasoningOutput().toString());
        Assert.assertEquals("Answer.", listener.getOutput().toString());
        Assert.assertEquals("tool_calls", listener.getFinishReason());
        Assert.assertEquals(1, listener.getToolCalls().size());
        Assert.assertEquals("call_rt", listener.getToolCalls().get(0).getId());
        Assert.assertEquals("lookup", listener.getToolCalls().get(0).getFunction().getName());
        Assert.assertEquals("{\"q\":\"ai4j\"}", listener.getToolCalls().get(0).getFunction().getArguments());
        Assert.assertTrue(listener.isUsagePresent());
        Assert.assertEquals(7L, listener.getUsage().getTotalTokens());
        Assert.assertEquals(2, deltas.size());
    }
}
