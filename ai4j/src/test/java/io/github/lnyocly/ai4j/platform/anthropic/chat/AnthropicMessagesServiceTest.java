package io.github.lnyocly.ai4j.platform.anthropic.chat;

import io.github.lnyocly.ai4j.config.AnthropicConfig;
import io.github.lnyocly.ai4j.platform.anthropic.stream.AnthropicStreamHandler;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.PlatformType;
import io.github.lnyocly.ai4j.service.factory.AiService;
import okhttp3.sse.EventSourceListener;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class AnthropicMessagesServiceTest {

    private static AnthropicMessagesService newService() {
        Configuration configuration = new Configuration();
        configuration.setAnthropicConfig(new AnthropicConfig());
        return new AnthropicMessagesService(configuration);
    }

    private static class Capture implements AnthropicStreamHandler {
        String startId;
        String startModel;
        final StringBuilder text = new StringBuilder();
        final StringBuilder thinking = new StringBuilder();
        final List<String> toolUseStart = new ArrayList<String>();
        final List<String> toolUseComplete = new ArrayList<String>();
        String stopReason;
        long stopOut;
        boolean completed;
        Throwable error;

        @Override
        public void onStart(String messageId, String model) {
            this.startId = messageId;
            this.startModel = model;
        }

        @Override
        public void onDeltaText(String t) {
            text.append(t);
        }

        @Override
        public void onThinkingDelta(String t) {
            thinking.append(t);
        }

        @Override
        public void onToolUseStart(int index, String toolUseId, String name) {
            toolUseStart.add(index + ":" + toolUseId + ":" + name);
        }

        @Override
        public void onToolUseComplete(int index, String toolUseId, String name, String inputJson) {
            toolUseComplete.add(index + ":" + toolUseId + ":" + name + ":" + inputJson);
        }

        @Override
        public void onStopReason(String stopReason, long inputTokens, long outputTokens) {
            this.stopReason = stopReason;
            this.stopOut = outputTokens;
        }

        @Override
        public void onComplete() {
            this.completed = true;
        }

        @Override
        public void onError(Throwable t) {
            this.error = t;
        }
    }

    private static void feed(EventSourceListener listener, String json) {
        listener.onEvent(null, null, null, json);
    }

    @Test
    public void shouldRouteTextAndThinkingAndToolEvents() {
        AnthropicMessagesService service = newService();
        Capture capture = new Capture();
        EventSourceListener listener = service.toEventListener(capture, null, null);

        feed(listener, "{\"type\":\"message_start\",\"message\":{\"id\":\"msg_1\",\"model\":\"glm-5.1\"}}");
        feed(listener, "{\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"thinking\",\"thinking\":\"\"}}");
        feed(listener, "{\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"thinking_delta\",\"thinking\":\"let me think\"}}");
        feed(listener, "{\"type\":\"content_block_stop\",\"index\":0}");
        feed(listener, "{\"type\":\"content_block_start\",\"index\":1,\"content_block\":{\"type\":\"text\",\"text\":\"\"}}");
        feed(listener, "{\"type\":\"content_block_delta\",\"index\":1,\"delta\":{\"type\":\"text_delta\",\"text\":\"Hi\"}}");
        feed(listener, "{\"type\":\"content_block_stop\",\"index\":1}");
        feed(listener, "{\"type\":\"content_block_start\",\"index\":2,\"content_block\":{\"type\":\"tool_use\",\"id\":\"toolu_1\",\"name\":\"get_weather\"}}");
        feed(listener, "{\"type\":\"content_block_delta\",\"index\":2,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"{\\\"city\\\":\\\"北京\\\"}\"}}");
        feed(listener, "{\"type\":\"content_block_stop\",\"index\":2}");
        feed(listener, "{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"tool_use\"},\"usage\":{\"output_tokens\":7}}");
        feed(listener, "{\"type\":\"message_stop\"}");

        Assert.assertEquals("msg_1", capture.startId);
        Assert.assertEquals("glm-5.1", capture.startModel);
        Assert.assertEquals("let me think", capture.thinking.toString());
        Assert.assertEquals("Hi", capture.text.toString());
        Assert.assertEquals("2:toolu_1:get_weather", capture.toolUseStart.get(0));
        Assert.assertEquals("2:toolu_1:get_weather:{\"city\":\"北京\"}", capture.toolUseComplete.get(0));
        Assert.assertEquals("tool_use", capture.stopReason);
        Assert.assertEquals(7L, capture.stopOut);
        Assert.assertTrue(capture.completed);
    }

    @Test
    public void shouldBeRegisteredUnderAnthropicAsMessagesService() {
        Configuration configuration = new Configuration();
        configuration.setAnthropicConfig(new AnthropicConfig());
        Object svc = new AiService(configuration).getMessagesService(PlatformType.ANTHROPIC);
        Assert.assertTrue(svc instanceof AnthropicMessagesService);
    }
}
