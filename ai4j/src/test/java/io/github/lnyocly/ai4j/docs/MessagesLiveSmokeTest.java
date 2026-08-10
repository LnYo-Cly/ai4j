package io.github.lnyocly.ai4j.docs;

import io.github.lnyocly.ai4j.config.AnthropicConfig;
import io.github.lnyocly.ai4j.platform.anthropic.chat.entity.AnthropicChatCompletion;
import io.github.lnyocly.ai4j.platform.anthropic.chat.entity.AnthropicChatCompletionResponse;
import io.github.lnyocly.ai4j.platform.anthropic.chat.entity.AnthropicContentBlock;
import io.github.lnyocly.ai4j.platform.anthropic.chat.entity.AnthropicMessage;
import io.github.lnyocly.ai4j.platform.anthropic.chat.entity.AnthropicTool;
import io.github.lnyocly.ai4j.platform.anthropic.stream.AnthropicStreamHandler;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.IMessagesService;
import io.github.lnyocly.ai4j.service.PlatformType;
import io.github.lnyocly.ai4j.service.factory.AiService;
import io.github.lnyocly.ai4j.test.LiveProviderTest;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Live smoke test for the Messages (Anthropic-native) mainline.
 *
 * <p>Validates the documented Messages snippets against a real
 * Anthropic-compatible endpoint (verified against MiniMax-M3), covering the
 * three things a reader needs: a plain call, streaming deltas, and tool use.
 *
 * <p>Requires {@code MINIMAX_API_KEY}; honours {@code MINIMAX_BASE_URL} and
 * {@code MINIMAX_MODEL}. Skips when the key is absent.
 */
@Category(LiveProviderTest.class)
public class MessagesLiveSmokeTest {

    private IMessagesService messagesService() {
        String apiKey = System.getenv("MINIMAX_API_KEY");
        Assume.assumeTrue("MINIMAX_API_KEY not set", apiKey != null && !apiKey.trim().isEmpty());

        String baseUrl = System.getenv("MINIMAX_BASE_URL");
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            baseUrl = "https://api.minimaxi.com/anthropic/";
        }

        AnthropicConfig config = new AnthropicConfig();
        config.setApiKey(apiKey);
        config.setApiHost(baseUrl);

        Configuration configuration = new Configuration();
        configuration.setAnthropicConfig(config);
        return new AiService(configuration).getMessagesService(PlatformType.ANTHROPIC);
    }

    private String model() {
        String model = System.getenv("MINIMAX_MODEL");
        return (model == null || model.trim().isEmpty()) ? "MiniMax-M3" : model;
    }

    private AnthropicChatCompletion request(String prompt) {
        AnthropicMessage user = new AnthropicMessage();
        user.setRole("user");
        user.setContent(prompt);

        AnthropicChatCompletion request = new AnthropicChatCompletion();
        request.setModel(model());
        request.setMessages(new ArrayList<AnthropicMessage>(Collections.singletonList(user)));
        request.setMaxTokens(256);
        return request;
    }

    private static String textOf(AnthropicChatCompletionResponse response) {
        StringBuilder text = new StringBuilder();
        if (response != null && response.getContent() != null) {
            for (AnthropicContentBlock block : response.getContent()) {
                if (block != null && "text".equals(block.getType()) && block.getText() != null) {
                    text.append(block.getText());
                }
            }
        }
        return text.toString();
    }

    @Test
    public void messagesReturnsTextAndUsage() throws Exception {
        IMessagesService service = messagesService();

        AnthropicChatCompletionResponse response = service.messages(request("Reply with exactly: PONG"));

        Assert.assertNotNull("response should not be null", response);
        Assert.assertNotNull("response id should be present", response.getId());
        Assert.assertEquals("message", response.getType());
        Assert.assertEquals("assistant", response.getRole());

        String text = textOf(response);
        Assert.assertTrue("content should mention PONG, got: " + text,
                text.toUpperCase().contains("PONG"));

        Assert.assertNotNull("usage should be reported", response.getUsage());
        Assert.assertTrue("input tokens should be positive", response.getUsage().getInputTokens() > 0);
        Assert.assertTrue("output tokens should be positive", response.getUsage().getOutputTokens() > 0);
    }

    @Test
    public void messagesStreamDeliversDeltasAndStopReason() throws Exception {
        IMessagesService service = messagesService();

        AnthropicChatCompletion request = request("Count from 1 to 5, digits only.");
        request.setStream(Boolean.TRUE);

        final StringBuilder streamed = new StringBuilder();
        final AtomicReference<String> stopReason = new AtomicReference<String>();
        final CountDownLatch done = new CountDownLatch(1);

        service.messagesStream(request, new AnthropicStreamHandler() {
            @Override
            public void onDeltaText(String delta) {
                if (delta != null) {
                    streamed.append(delta);
                }
            }

            @Override
            public void onStopReason(String reason, long inputTokens, long outputTokens) {
                stopReason.set(reason);
            }

            @Override
            public void onComplete() {
                done.countDown();
            }
        });

        Assert.assertTrue("stream should complete within 120s", done.await(120, TimeUnit.SECONDS));
        Assert.assertTrue("stream should deliver text deltas, got: " + streamed, streamed.length() > 0);
        Assert.assertNotNull("stop reason should be reported", stopReason.get());
    }

    /**
     * Tool use is the Messages-mainline feature most likely to break silently:
     * the model must emit a tool_use content block that callers can act on.
     */
    @Test
    public void messagesSupportsToolUse() throws Exception {
        IMessagesService service = messagesService();

        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        Map<String, Object> cityProperty = new LinkedHashMap<String, Object>();
        cityProperty.put("type", "string");
        cityProperty.put("description", "City name");
        properties.put("city", cityProperty);

        Map<String, Object> inputSchema = new LinkedHashMap<String, Object>();
        inputSchema.put("type", "object");
        inputSchema.put("properties", properties);
        inputSchema.put("required", Collections.singletonList("city"));

        AnthropicTool tool = new AnthropicTool();
        tool.setName("get_weather");
        tool.setDescription("Get the current weather for a city.");
        tool.setInputSchema(inputSchema);

        AnthropicChatCompletion request = request("What is the weather in Beijing? Use the get_weather tool.");
        request.setTools(new ArrayList<AnthropicTool>(Collections.singletonList(tool)));

        AnthropicChatCompletionResponse response = service.messages(request);

        Assert.assertNotNull(response);
        List<AnthropicContentBlock> blocks = response.getContent();
        Assert.assertNotNull("content blocks should be present", blocks);

        boolean sawToolUse = false;
        for (AnthropicContentBlock block : blocks) {
            if (block != null && "tool_use".equals(block.getType())) {
                sawToolUse = true;
                Assert.assertEquals("get_weather", block.getName());
                Assert.assertNotNull("tool_use block should carry an id", block.getId());
                Assert.assertNotNull("tool_use block should carry input", block.getInput());
            }
        }
        Assert.assertTrue("model should emit a tool_use block; stop_reason=" + response.getStopReason(),
                sawToolUse);
    }
}
