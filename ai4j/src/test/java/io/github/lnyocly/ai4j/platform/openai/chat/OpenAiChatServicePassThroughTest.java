package io.github.lnyocly.ai4j.platform.openai.chat;

import io.github.lnyocly.ai4j.config.OpenAiConfig;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletion;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletionResponse;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatMessage;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import io.github.lnyocly.ai4j.service.Configuration;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

public class OpenAiChatServicePassThroughTest {

    @Test
    public void chatCompletionShouldReturnProviderToolCallsWhenPassThroughEnabled() throws Exception {
        final AtomicInteger requestCount = new AtomicInteger();
        final String responseJson = "{"
                + "\"id\":\"resp_1\","
                + "\"object\":\"chat.completion\","
                + "\"created\":1710000000,"
                + "\"model\":\"gpt-test\","
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
                + "\"prompt_tokens\":10,"
                + "\"completion_tokens\":5,"
                + "\"total_tokens\":15"
                + "}"
                + "}";

        OpenAiChatService service = new OpenAiChatService(configurationWithJsonResponse(responseJson, requestCount));
        ChatCompletion completion = ChatCompletion.builder()
                .model("gpt-test")
                .messages(Collections.singletonList(ChatMessage.withUser("Read README.md")))
                .tools(Collections.singletonList(tool("read_file")))
                .build();
        completion.setPassThroughToolCalls(Boolean.TRUE);

        ChatCompletionResponse response = service.chatCompletion(completion);

        Assert.assertNotNull(response);
        Assert.assertEquals(1, requestCount.get());
        Assert.assertEquals("tool_calls", response.getChoices().get(0).getFinishReason());
        Assert.assertEquals("read_file", response.getChoices().get(0).getMessage().getToolCalls().get(0).getFunction().getName());
        Assert.assertEquals("{\"path\":\"README.md\"}", response.getChoices().get(0).getMessage().getToolCalls().get(0).getFunction().getArguments());
        Assert.assertEquals(15L, response.getUsage().getTotalTokens());
    }

    @Test
    public void chatCompletionShouldKeepLegacyProviderToolLoopWhenPassThroughDisabled() throws Exception {
        final String responseJson = "{"
                + "\"id\":\"resp_2\","
                + "\"object\":\"chat.completion\","
                + "\"created\":1710000000,"
                + "\"model\":\"gpt-test\","
                + "\"choices\":[{"
                + "\"index\":0,"
                + "\"message\":{"
                + "\"role\":\"assistant\","
                + "\"content\":\"\","
                + "\"tool_calls\":[{"
                + "\"id\":\"call_2\","
                + "\"type\":\"function\","
                + "\"function\":{"
                + "\"name\":\"unit_test_missing_tool\","
                + "\"arguments\":\"{}\""
                + "}"
                + "}]"
                + "},"
                + "\"finish_reason\":\"tool_calls\""
                + "}],"
                + "\"usage\":{"
                + "\"prompt_tokens\":10,"
                + "\"completion_tokens\":5,"
                + "\"total_tokens\":15"
                + "}"
                + "}";

        OpenAiChatService service = new OpenAiChatService(configurationWithJsonResponse(responseJson, null));
        ChatCompletion completion = ChatCompletion.builder()
                .model("gpt-test")
                .messages(Collections.singletonList(ChatMessage.withUser("Call a tool")))
                .tools(Collections.singletonList(tool("unit_test_missing_tool")))
                .build();

        try {
            service.chatCompletion(completion);
            Assert.fail("Expected legacy provider tool loop to invoke ToolUtil when pass-through is disabled");
        } catch (RuntimeException ex) {
            Assert.assertTrue(String.valueOf(ex.getMessage()).contains("工具调用失败"));
        }
    }

    private Configuration configurationWithJsonResponse(String responseJson, AtomicInteger requestCount) {
        OpenAiConfig openAiConfig = new OpenAiConfig();
        openAiConfig.setApiHost("https://unit.test/");
        openAiConfig.setApiKey("config-api-key");

        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    if (requestCount != null) {
                        requestCount.incrementAndGet();
                    }
                    return new Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(ResponseBody.create(responseJson, MediaType.get("application/json")))
                            .build();
                })
                .build();

        Configuration configuration = new Configuration();
        configuration.setOpenAiConfig(openAiConfig);
        configuration.setOkHttpClient(okHttpClient);
        return configuration;
    }

    private Tool tool(String name) {
        Tool.Function function = new Tool.Function();
        function.setName(name);
        function.setDescription("test tool");
        return new Tool("function", function);
    }
}
