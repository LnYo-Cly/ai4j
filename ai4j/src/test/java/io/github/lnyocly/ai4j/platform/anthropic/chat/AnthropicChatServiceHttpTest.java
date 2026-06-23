package io.github.lnyocly.ai4j.platform.anthropic.chat;

import io.github.lnyocly.ai4j.config.AnthropicConfig;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletion;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletionResponse;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatMessage;
import io.github.lnyocly.ai4j.service.Configuration;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Offline (CI-runnable) test of the unified {@link AnthropicChatService} non-stream path:
 * convert ChatCompletion -> Anthropic Messages, send with x-api-key + anthropic-version,
 * parse the Anthropic response back to ChatCompletionResponse. Uses an OkHttp interceptor
 * (no real network) so this runs in CI without credentials.
 */
public class AnthropicChatServiceHttpTest {

    @Test
    public void chatCompletionShouldSendAnthropicRequestAndParseResponse() throws Exception {
        final AtomicInteger requestCount = new AtomicInteger();
        final AtomicReference<Request> recordedRequest = new AtomicReference<Request>();
        final String responseJson = "{"
                + "\"id\":\"msg_1\","
                + "\"type\":\"message\","
                + "\"role\":\"assistant\","
                + "\"model\":\"claude-test\","
                + "\"content\":[{\"type\":\"text\",\"text\":\"Hello\"}],"
                + "\"stop_reason\":\"end_turn\","
                + "\"usage\":{\"input_tokens\":5,\"output_tokens\":3}"
                + "}";

        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    requestCount.incrementAndGet();
                    recordedRequest.set(chain.request());
                    return new Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(ResponseBody.create(responseJson, MediaType.get("application/json")))
                            .build();
                })
                .build();

        AnthropicConfig config = new AnthropicConfig();
        config.setApiKey("test-key");
        config.setApiHost("https://unit.test/");
        Configuration configuration = new Configuration();
        configuration.setAnthropicConfig(config);
        configuration.setOkHttpClient(okHttpClient);

        AnthropicChatService service = new AnthropicChatService(configuration);

        ChatCompletion req = ChatCompletion.builder()
                .model("claude-test")
                .message(ChatMessage.withUser("hi"))
                .build();

        ChatCompletionResponse resp = service.chatCompletion(req);

        // response mapping: text block -> outputText; stop_reason end_turn -> stop
        Assert.assertNotNull(resp);
        Assert.assertEquals(1, resp.getChoices().size());
        Assert.assertEquals("Hello", resp.getChoices().get(0).getMessage().getContent().getText());
        Assert.assertEquals("stop", resp.getChoices().get(0).getFinishReason());
        Assert.assertEquals(8L, resp.getUsage().getTotalTokens());

        // request shape: single round, anthropic auth headers, v1/messages URL
        Assert.assertEquals("single round-trip expected", 1, requestCount.get());
        Request sent = recordedRequest.get();
        Assert.assertNotNull(sent);
        Assert.assertEquals("test-key", sent.header("x-api-key"));
        Assert.assertEquals("2023-06-01", sent.header("anthropic-version"));
        String url = sent.url().toString();
        Assert.assertTrue("url should target unit.test: " + url, url.contains("unit.test"));
        Assert.assertTrue("url should end with v1/messages: " + url, url.endsWith("v1/messages"));
        Assert.assertEquals("POST", sent.method());
    }
}
