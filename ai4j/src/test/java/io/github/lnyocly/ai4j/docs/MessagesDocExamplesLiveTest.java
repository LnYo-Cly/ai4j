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
 * Executable source of truth for the snippets in
 * {@code docs/core-sdk/model-access/messages.md}.
 *
 * <p>Validated against an Anthropic-compatible endpoint (MiniMax-M3).
 * Requires {@code MINIMAX_API_KEY}; honours {@code MINIMAX_BASE_URL} and
 * {@code MINIMAX_MODEL}. Skips when the key is absent.
 */
@Category(LiveProviderTest.class)
public class MessagesDocExamplesLiveTest {

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

    /** 遍历 content blocks 读取助手文本（thinking / tool_use 之外的 text block）。 */
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

    // ---- §4 原生调用 + 读取 content / usage ----

    @Test
    public void messagesReturnsTextAndUsage() throws Exception {
        IMessagesService service = messagesService();

        AnthropicChatCompletionResponse response = service.messages(request("Reply with exactly: PONG"));

        Assert.assertNotNull(response);
        Assert.assertEquals("message", response.getType());
        Assert.assertEquals("assistant", response.getRole());

        System.out.println("回答: " + textOf(response));
        System.out.println("input=" + response.getUsage().getInputTokens()
                + " output=" + response.getUsage().getOutputTokens());

        Assert.assertTrue("应包含 PONG：" + textOf(response),
                textOf(response).toUpperCase().contains("PONG"));
        Assert.assertTrue(response.getUsage().getInputTokens() > 0);
    }

    // ---- §4 流式（原生事件回调） ----

    @Test
    public void messagesStreamDeliversDeltasAndStopReason() throws Exception {
        IMessagesService service = messagesService();

        AnthropicChatCompletion req = request("Count from 1 to 5, digits only.");
        req.setStream(Boolean.TRUE);

        final StringBuilder streamed = new StringBuilder();
        final AtomicReference<String> stopReason = new AtomicReference<String>();
        final CountDownLatch done = new CountDownLatch(1);

        service.messagesStream(req, new AnthropicStreamHandler() {
            @Override
            public void onDeltaText(String delta) {
                if (delta != null) streamed.append(delta);
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

        Assert.assertTrue("流应在 120s 内完成", done.await(120, TimeUnit.SECONDS));
        Assert.assertTrue("应有流式文本", streamed.length() > 0);
        Assert.assertNotNull("应有 stopReason", stopReason.get());
        System.out.println("流式输出: " + streamed);
        System.out.println("stopReason: " + stopReason.get());
    }

    // ---- tool_use：Messages 协议原生的工具调用 ----

    /**
     * tool_use 是 Messages 协议最该测的能力：模型必须 emit 一个
     * type="tool_use" 的 content block，带 name / id / input（已解析对象）。
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

        AnthropicChatCompletion req = request("What is the weather in Beijing? Use the get_weather tool.");
        req.setTools(new ArrayList<AnthropicTool>(Collections.singletonList(tool)));

        AnthropicChatCompletionResponse response = service.messages(req);

        Assert.assertNotNull(response);
        List<AnthropicContentBlock> blocks = response.getContent();

        boolean sawToolUse = false;
        for (AnthropicContentBlock block : blocks) {
            if (block != null && "tool_use".equals(block.getType())) {
                sawToolUse = true;
                Assert.assertEquals("get_weather", block.getName());
                Assert.assertNotNull("tool_use 应带 id", block.getId());
                Assert.assertNotNull("tool_use 应带 input（已解析对象）", block.getInput());
                System.out.println("tool_use: " + block.getName() + " input=" + block.getInput());
            }
        }
        Assert.assertTrue("应产生 tool_use block；stopReason=" + response.getStopReason(), sawToolUse);
    }
}
