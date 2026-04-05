package io.github.lnyocly.ai4j.platform.minimax.chat;

import io.github.lnyocly.ai4j.config.MinimaxConfig;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletion;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletionResponse;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatMessage;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import io.github.lnyocly.ai4j.service.Configuration;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class MinimaxChatServiceTest {

    @Test
    public void chatCompletionShouldReturnProviderToolCallsWhenPassThroughEnabled() throws Exception {
        final AtomicInteger requestCount = new AtomicInteger();
        final AtomicReference<Request> recordedRequest = new AtomicReference<Request>();
        final String responseJson = "{"
                + "\"id\":\"resp_1\","
                + "\"object\":\"chat.completion\","
                + "\"created\":1710000000,"
                + "\"model\":\"MiniMax-M2.1\","
                + "\"choices\":[{"
                + "\"index\":0,"
                + "\"message\":{"
                + "\"role\":\"assistant\","
                + "\"content\":\"\","
                + "\"tool_calls\":[{"
                + "\"id\":\"call_1\","
                + "\"type\":\"function\","
                + "\"function\":{"
                + "\"name\":\"read_file\","
                + "\"arguments\":\"{\\\"path\\\":\\\"README.md\\\"}\""
                + "}"
                + "}]"
                + "},"
                + "\"finish_reason\":\"tool_calls\""
                + "}],"
                + "\"usage\":{"
                + "\"prompt_tokens\":11,"
                + "\"completion_tokens\":7,"
                + "\"total_tokens\":18"
                + "}"
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

        MinimaxConfig minimaxConfig = new MinimaxConfig();
        minimaxConfig.setApiHost("https://unit.test/");
        minimaxConfig.setApiKey("config-api-key");

        Configuration configuration = new Configuration();
        configuration.setMinimaxConfig(minimaxConfig);
        configuration.setOkHttpClient(okHttpClient);

        MinimaxChatService service = new MinimaxChatService(configuration);

        ChatCompletion completion = ChatCompletion.builder()
                .model("MiniMax-M2.1")
                .messages(Collections.singletonList(ChatMessage.withUser("Read README.md")))
                .tools(Collections.singletonList(tool("read_file")))
                .build();
        completion.setPassThroughToolCalls(Boolean.TRUE);

        ChatCompletionResponse response = service.chatCompletion(completion);

        Assert.assertNotNull(response);
        Assert.assertNotNull(response.getChoices());
        Assert.assertEquals(1, response.getChoices().size());
        Assert.assertEquals(1, requestCount.get());
        Assert.assertEquals("tool_calls", response.getChoices().get(0).getFinishReason());
        Assert.assertNotNull(response.getChoices().get(0).getMessage());
        Assert.assertNotNull(response.getChoices().get(0).getMessage().getToolCalls());
        Assert.assertEquals(1, response.getChoices().get(0).getMessage().getToolCalls().size());
        Assert.assertEquals("read_file", response.getChoices().get(0).getMessage().getToolCalls().get(0).getFunction().getName());
        Assert.assertEquals("{\"path\":\"README.md\"}", response.getChoices().get(0).getMessage().getToolCalls().get(0).getFunction().getArguments());
        Assert.assertEquals("Bearer config-api-key", recordedRequest.get().header("Authorization"));
        Assert.assertEquals("https://unit.test/v1/text/chatcompletion_v2", recordedRequest.get().url().toString());
        Assert.assertEquals(18L, response.getUsage().getTotalTokens());
    }

    private Tool tool(String name) {
        Tool.Function function = new Tool.Function();
        function.setName(name);
        function.setDescription("test tool");
        return new Tool("function", function);
    }
}
