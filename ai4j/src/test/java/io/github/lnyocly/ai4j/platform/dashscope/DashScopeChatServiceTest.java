package io.github.lnyocly.ai4j.platform.dashscope;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.lnyocly.ai4j.codec.JsonCodec;
import io.github.lnyocly.ai4j.config.DashScopeConfig;
import io.github.lnyocly.ai4j.exception.AiAuthException;
import io.github.lnyocly.ai4j.exception.AiRateLimitException;
import io.github.lnyocly.ai4j.exception.AiServerErrorException;
import io.github.lnyocly.ai4j.listener.SseListener;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletion;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletionResponse;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatMessage;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import io.github.lnyocly.ai4j.platform.openai.tool.ToolCall;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.IChatService;
import io.github.lnyocly.ai4j.service.PlatformType;
import io.github.lnyocly.ai4j.service.factory.AiService;
import io.github.lnyocly.ai4j.tool.BuiltInToolContext;
import io.github.lnyocly.ai4j.tool.BuiltInTools;
import io.github.lnyocly.ai4j.tool.ToolUtil;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class DashScopeChatServiceTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void syncChatCompletionViaAiServiceFactory() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(jsonResponse("{"
                    + "\"id\":\"chatcmpl-ds-1\","
                    + "\"object\":\"chat.completion\","
                    + "\"created\":1710000000,"
                    + "\"model\":\"qwen-plus\","
                    + "\"choices\":[{"
                    + "\"index\":0,"
                    + "\"message\":{\"role\":\"assistant\",\"content\":\"Hello from DashScope\"},"
                    + "\"finish_reason\":\"stop\""
                    + "}],"
                    + "\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":6,\"total_tokens\":18}"
                    + "}"));
            server.start();

            IChatService service = new AiService(configuration(server, "cfg-key"))
                    .getChatService(PlatformType.DASHSCOPE);

            ChatCompletion request = ChatCompletion.builder()
                    .model("qwen-plus")
                    .message(ChatMessage.withUser("Hi"))
                    .maxCompletionTokens(256)
                    .parameter("enable_thinking", false)
                    .parameter("seed", 42)
                    .extraBody("seed", 99)
                    .build();

            ChatCompletionResponse response = service.chatCompletion(request);

            Assert.assertNotNull(response);
            Assert.assertEquals("chatcmpl-ds-1", response.getId());
            Assert.assertEquals("qwen-plus", response.getModel());
            Assert.assertEquals("Hello from DashScope", response.getChoices().get(0).getMessage().getContent().getText());
            Assert.assertEquals("stop", response.getChoices().get(0).getFinishReason());
            Assert.assertEquals(18L, response.getUsage().getTotalTokens());

            RecordedRequest recorded = server.takeRequest(1, TimeUnit.SECONDS);
            Assert.assertNotNull(recorded);
            Assert.assertEquals("POST", recorded.getMethod());
            Assert.assertEquals("/chat/completions", recorded.getPath());
            Assert.assertEquals("Bearer cfg-key", recorded.getHeader("Authorization"));

            JsonNode body = JsonCodec.readTree(recorded.getBody().readUtf8());
            Assert.assertEquals("qwen-plus", body.get("model").asText());
            Assert.assertFalse(body.get("stream").asBoolean());
            Assert.assertTrue(body.path("stream_options").isMissingNode());
            Assert.assertTrue(body.path("parameters").isMissingNode());
            Assert.assertTrue(body.path("max_completion_tokens").isMissingNode());
            Assert.assertEquals(256, body.get("max_tokens").asInt());
            Assert.assertFalse(body.get("enable_thinking").asBoolean());
            Assert.assertEquals(99, body.get("seed").asInt());
            Assert.assertTrue(body.path("parallel_tool_calls").isMissingNode());
        }
    }

    @Test
    public void sseStreamShouldAccumulateReasoningContentTextAndUsage() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            String sseBody = ""
                    + "data: {\"id\":\"chatcmpl-stream-1\",\"object\":\"chat.completion.chunk\",\"created\":1710000001,\"model\":\"qwen3-32b\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"reasoning_content\":\"Thinking...\"},\"finish_reason\":null}]}\n\n"
                    + "data: {\"id\":\"chatcmpl-stream-1\",\"object\":\"chat.completion.chunk\",\"created\":1710000001,\"model\":\"qwen3-32b\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Hello \"},\"finish_reason\":null}]}\n\n"
                    + "data: {\"id\":\"chatcmpl-stream-1\",\"object\":\"chat.completion.chunk\",\"created\":1710000001,\"model\":\"qwen3-32b\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Qwen\"},\"finish_reason\":\"stop\"}]}\n\n"
                    + "data: {\"id\":\"chatcmpl-stream-1\",\"object\":\"chat.completion.chunk\",\"created\":1710000001,\"model\":\"qwen3-32b\",\"choices\":[],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":8,\"total_tokens\":18}}\n\n"
                    + "data: [DONE]\n\n";
            server.enqueue(sseResponse(sseBody));
            server.start();

            DashScopeChatService service = new DashScopeChatService(configuration(server, "stream-key"));
            RecordingSseListener listener = new RecordingSseListener();

            ChatCompletion request = ChatCompletion.builder()
                    .model("qwen3-32b")
                    .message(ChatMessage.withUser("Say hi"))
                    .parameter("enable_thinking", true)
                    .build();

            service.chatCompletionStream(request, listener);

            Assert.assertEquals("Thinking...", listener.getReasoningOutput().toString());
            Assert.assertEquals("Hello Qwen", listener.getOutput().toString());
            Assert.assertEquals("stop", listener.getFinishReason());
            Assert.assertTrue(listener.isUsagePresent());
            Assert.assertEquals(18L, listener.getUsage().getTotalTokens());
            Assert.assertTrue(listener.sendCount >= 3);

            RecordedRequest recorded = server.takeRequest(1, TimeUnit.SECONDS);
            Assert.assertNotNull(recorded);
            Assert.assertEquals("/chat/completions", recorded.getPath());
            Assert.assertEquals("Bearer stream-key", recorded.getHeader("Authorization"));
            JsonNode body = JsonCodec.readTree(recorded.getBody().readUtf8());
            Assert.assertTrue(body.get("stream").asBoolean());
            Assert.assertTrue(body.path("stream_options").get("include_usage").asBoolean());
            Assert.assertTrue(body.get("enable_thinking").asBoolean());
        }
    }

    @Test
    public void syncToolCallLoopShouldInvokeToolUtilAndMergeUsage() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(jsonResponse("{"
                    + "\"id\":\"resp_tool_1\","
                    + "\"object\":\"chat.completion\","
                    + "\"created\":1710000000,"
                    + "\"model\":\"qwen-plus\","
                    + "\"choices\":[{"
                    + "\"index\":0,"
                    + "\"message\":{"
                    + "\"role\":\"assistant\","
                    + "\"content\":\"\","
                    + "\"tool_calls\":[{"
                    + "\"id\":\"call_weather_1\","
                    + "\"type\":\"function\","
                    + "\"function\":{\"name\":\"weather\",\"arguments\":\"{\\\"city\\\":\\\"Hangzhou\\\"}\"}"
                    + "}]"
                    + "},"
                    + "\"finish_reason\":\"tool_calls\""
                    + "}],"
                    + "\"usage\":{\"prompt_tokens\":15,\"completion_tokens\":5,\"total_tokens\":20}"
                    + "}"));
            server.enqueue(jsonResponse("{"
                    + "\"id\":\"resp_tool_2\","
                    + "\"object\":\"chat.completion\","
                    + "\"created\":1710000001,"
                    + "\"model\":\"qwen-plus\","
                    + "\"choices\":[{"
                    + "\"index\":0,"
                    + "\"message\":{\"role\":\"assistant\",\"content\":\"Hangzhou is sunny and 25C.\"},"
                    + "\"finish_reason\":\"stop\""
                    + "}],"
                    + "\"usage\":{\"prompt_tokens\":30,\"completion_tokens\":8,\"total_tokens\":38}"
                    + "}"));
            server.start();

            DashScopeChatService service = new DashScopeChatService(configuration(server, "tool-key"));
            ChatCompletion request = ChatCompletion.builder()
                    .model("qwen-plus")
                    .message(ChatMessage.withUser("What is the weather in Hangzhou?"))
                    .functions("weather")
                    .build();

            ChatCompletionResponse response = service.chatCompletion(request);

            Assert.assertNotNull(response);
            Assert.assertEquals("stop", response.getChoices().get(0).getFinishReason());
            Assert.assertEquals("Hangzhou is sunny and 25C.", response.getChoices().get(0).getMessage().getContent().getText());
            Assert.assertEquals(45L, response.getUsage().getPromptTokens());
            Assert.assertEquals(13L, response.getUsage().getCompletionTokens());
            Assert.assertEquals(58L, response.getUsage().getTotalTokens());
            Assert.assertEquals(2, server.getRequestCount());

            RecordedRequest first = server.takeRequest(1, TimeUnit.SECONDS);
            RecordedRequest second = server.takeRequest(1, TimeUnit.SECONDS);
            Assert.assertNotNull(first);
            Assert.assertNotNull(second);

            JsonNode firstBody = JsonCodec.readTree(first.getBody().readUtf8());
            Assert.assertTrue(firstBody.path("tools").isArray());
            Assert.assertEquals("weather", firstBody.path("tools").get(0).path("function").path("name").asText());

            JsonNode secondBody = JsonCodec.readTree(second.getBody().readUtf8());
            JsonNode messages = secondBody.path("messages");
            Assert.assertEquals(3, messages.size());
            Assert.assertEquals("assistant", messages.get(1).get("role").asText());
            Assert.assertEquals("call_weather_1", messages.get(1).path("tool_calls").get(0).get("id").asText());
            Assert.assertEquals("tool", messages.get(2).get("role").asText());
            Assert.assertEquals("call_weather_1", messages.get(2).get("tool_call_id").asText());
            Assert.assertTrue(messages.get(2).get("content").asText().contains("Hangzhou"));
        }
    }

    @Test
    public void streamToolCallLoopShouldPushAndPopBuiltInToolContext() throws Exception {
        File outerWorkspace = temporaryFolder.newFolder("outer-ws");
        File innerWorkspace = temporaryFolder.newFolder("inner-ws");
        File innerFile = new File(innerWorkspace, "secret.txt");
        Files.write(innerFile.toPath(), "dashscope-built-in-ok".getBytes(StandardCharsets.UTF_8));
        File outerFile = new File(outerWorkspace, "outer.txt");
        Files.write(outerFile.toPath(), "outer-context-alive".getBytes(StandardCharsets.UTF_8));

        BuiltInToolContext outerContext = BuiltInToolContext.builder()
                .workspaceRoot(outerWorkspace.getAbsolutePath())
                .build();
        BuiltInToolContext innerContext = BuiltInToolContext.builder()
                .workspaceRoot(innerWorkspace.getAbsolutePath())
                .build();

        try (MockWebServer server = new MockWebServer()) {
            String turn1Sse = ""
                    + "data: {\"id\":\"ds-stream-1\",\"object\":\"chat.completion.chunk\",\"created\":1710000000,\"model\":\"qwen-plus\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"tool_calls\":[{\"index\":0,\"id\":\"call_read_1\",\"type\":\"function\",\"function\":{\"name\":\"read_file\",\"arguments\":\"{\\\"path\\\":\\\"sec\"}}]},\"finish_reason\":null}]}\n\n"
                    + "data: {\"id\":\"ds-stream-1\",\"object\":\"chat.completion.chunk\",\"created\":1710000000,\"model\":\"qwen-plus\",\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\"ret.txt\\\"}\"}}]},\"finish_reason\":\"tool_calls\"}]}\n\n"
                    + "data: [DONE]\n\n";
            String turn2Sse = ""
                    + "data: {\"id\":\"ds-stream-2\",\"object\":\"chat.completion.chunk\",\"created\":1710000001,\"model\":\"qwen-plus\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"Read complete\"},\"finish_reason\":\"stop\"}]}\n\n"
                    + "data: [DONE]\n\n";
            server.enqueue(sseResponse(turn1Sse));
            server.enqueue(sseResponse(turn2Sse));
            server.start();

            DashScopeChatService service = new DashScopeChatService(configuration(server, "stream-tool-key"));
            RecordingSseListener listener = new RecordingSseListener();

            ChatCompletion request = ChatCompletion.builder()
                    .model("qwen-plus")
                    .message(ChatMessage.withUser("Read secret.txt"))
                    .tools(Collections.singletonList(BuiltInTools.readFileTool()))
                    .builtInToolContext(innerContext)
                    .build();

            ToolUtil.pushBuiltInToolContext(outerContext);
            try {
                service.chatCompletionStream(request, listener);

                Assert.assertEquals("Read complete", listener.getOutput().toString());
                Assert.assertEquals("stop", listener.getFinishReason());
                Assert.assertEquals(2, server.getRequestCount());

                server.takeRequest(1, TimeUnit.SECONDS);
                RecordedRequest second = server.takeRequest(1, TimeUnit.SECONDS);
                Assert.assertNotNull(second);
                JsonNode secondBody = JsonCodec.readTree(second.getBody().readUtf8());
                JsonNode toolMessage = secondBody.path("messages").get(2);
                Assert.assertEquals("tool", toolMessage.get("role").asText());
                Assert.assertEquals("call_read_1", toolMessage.get("tool_call_id").asText());
                Assert.assertTrue(toolMessage.get("content").asText().contains("dashscope-built-in-ok"));

                // Confirm innerContext was popped and outerContext remains intact
                String outerRead = ToolUtil.invoke("read_file", "{\"path\":\"outer.txt\"}");
                Assert.assertTrue(outerRead.contains("outer-context-alive"));
            } finally {
                ToolUtil.popBuiltInToolContext();
            }
        }
    }

    @Test
    public void passThroughToolCallsShouldSkipToolExecutionForSyncAndStream() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(jsonResponse("{"
                    + "\"id\":\"pt-sync-1\","
                    + "\"object\":\"chat.completion\","
                    + "\"created\":1710000000,"
                    + "\"model\":\"qwen-plus\","
                    + "\"choices\":[{"
                    + "\"index\":0,"
                    + "\"message\":{\"role\":\"assistant\",\"content\":\"\",\"tool_calls\":[{\"id\":\"call_pt_1\",\"type\":\"function\",\"function\":{\"name\":\"missing_external_tool\",\"arguments\":\"{\\\"x\\\":1}\"}}]},"
                    + "\"finish_reason\":\"tool_calls\""
                    + "}],"
                    + "\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":4,\"total_tokens\":14}"
                    + "}"));
            String streamToolSse = ""
                    + "data: {\"id\":\"pt-stream-1\",\"object\":\"chat.completion.chunk\",\"created\":1710000001,\"model\":\"qwen-plus\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"tool_calls\":[{\"index\":0,\"id\":\"call_pt_2\",\"type\":\"function\",\"function\":{\"name\":\"missing_external_tool\",\"arguments\":\"{\\\"y\\\":2}\"}}]},\"finish_reason\":\"tool_calls\"}]}\n\n"
                    + "data: [DONE]\n\n";
            server.enqueue(sseResponse(streamToolSse));
            server.start();

            DashScopeChatService service = new DashScopeChatService(configuration(server, "pt-key"));

            ChatCompletion syncReq = ChatCompletion.builder()
                    .model("qwen-plus")
                    .message(ChatMessage.withUser("Run external tool"))
                    .tools(Collections.singletonList(simpleTool("missing_external_tool")))
                    .passThroughToolCalls(Boolean.TRUE)
                    .build();

            ChatCompletionResponse syncResp = service.chatCompletion(syncReq);
            Assert.assertEquals(1, server.getRequestCount());
            Assert.assertEquals("tool_calls", syncResp.getChoices().get(0).getFinishReason());
            List<ToolCall> syncCalls = syncResp.getChoices().get(0).getMessage().getToolCalls();
            Assert.assertEquals(1, syncCalls.size());
            Assert.assertEquals("call_pt_1", syncCalls.get(0).getId());
            Assert.assertEquals("missing_external_tool", syncCalls.get(0).getFunction().getName());

            ChatCompletion streamReq = ChatCompletion.builder()
                    .model("qwen-plus")
                    .message(ChatMessage.withUser("Run external tool stream"))
                    .tools(Collections.singletonList(simpleTool("missing_external_tool")))
                    .passThroughToolCalls(Boolean.TRUE)
                    .build();
            RecordingSseListener listener = new RecordingSseListener();
            service.chatCompletionStream(streamReq, listener);

            Assert.assertEquals(2, server.getRequestCount());
            Assert.assertEquals("tool_calls", listener.getFinishReason());
            Assert.assertEquals(1, listener.getToolCalls().size());
            Assert.assertEquals("call_pt_2", listener.getToolCalls().get(0).getId());
            Assert.assertEquals("{\"y\":2}", listener.getToolCalls().get(0).getFunction().getArguments());
        }
    }

    @Test
    public void customBaseUrlAndApiKeyOverrideShouldTakePrecedenceAndConstructorConfigSupported() throws Exception {
        try (MockWebServer defaultServer = new MockWebServer();
             MockWebServer overrideServer = new MockWebServer()) {
            defaultServer.start();
            overrideServer.enqueue(jsonResponse("{"
                    + "\"id\":\"override-sync\","
                    + "\"object\":\"chat.completion\","
                    + "\"created\":1710000000,"
                    + "\"model\":\"qwen-max\","
                    + "\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"override ok\"},\"finish_reason\":\"stop\"}],"
                    + "\"usage\":{\"prompt_tokens\":5,\"completion_tokens\":2,\"total_tokens\":7}"
                    + "}"));
            overrideServer.enqueue(sseResponse(""
                    + "data: {\"id\":\"override-stream\",\"object\":\"chat.completion.chunk\",\"created\":1710000001,\"model\":\"qwen-max\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"stream ok\"},\"finish_reason\":\"stop\"}]}\n\n"
                    + "data: [DONE]\n\n"));
            overrideServer.start();

            DashScopeConfig customConfig = new DashScopeConfig();
            customConfig.setApiHost(defaultServer.url("/v1/").toString());
            customConfig.setApiKey("default-key");

            Configuration baseConfiguration = new Configuration();
            baseConfiguration.setOkHttpClient(new OkHttpClient());
            DashScopeChatService service = new DashScopeChatService(baseConfiguration, customConfig);

            ChatCompletionResponse syncResp = service.chatCompletion(
                    overrideServer.url("/custom/v1/").toString(),
                    "override-key",
                    ChatCompletion.builder()
                            .model("qwen-max")
                            .message(ChatMessage.withUser("ping"))
                            .build());
            Assert.assertEquals("override ok", syncResp.getChoices().get(0).getMessage().getContent().getText());

            RecordingSseListener listener = new RecordingSseListener();
            service.chatCompletionStream(
                    overrideServer.url("/custom/v1/").toString(),
                    "override-stream-key",
                    ChatCompletion.builder()
                            .model("qwen-max")
                            .message(ChatMessage.withUser("ping stream"))
                            .build(),
                    listener);
            Assert.assertEquals("stream ok", listener.getOutput().toString());

            Assert.assertEquals(0, defaultServer.getRequestCount());
            Assert.assertEquals(2, overrideServer.getRequestCount());

            RecordedRequest req1 = overrideServer.takeRequest(1, TimeUnit.SECONDS);
            RecordedRequest req2 = overrideServer.takeRequest(1, TimeUnit.SECONDS);
            Assert.assertNotNull(req1);
            Assert.assertNotNull(req2);
            Assert.assertEquals("/custom/v1/chat/completions", req1.getPath());
            Assert.assertEquals("Bearer override-key", req1.getHeader("Authorization"));
            Assert.assertEquals("/custom/v1/chat/completions", req2.getPath());
            Assert.assertEquals("Bearer override-stream-key", req2.getHeader("Authorization"));
        }
    }

    @Test
    public void httpErrorsShouldDecodeIntoStructuredAiHttpExceptions() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(401)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"error\":{\"message\":\"Invalid API key\",\"type\":\"invalid_request_error\"}}"));
            server.enqueue(new MockResponse().setResponseCode(429)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"error\":{\"message\":\"Rate limit exceeded\"}}"));
            server.enqueue(new MockResponse().setResponseCode(500)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"message\":\"Upstream unavailable\"}"));
            server.enqueue(new MockResponse().setResponseCode(401)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"error\":{\"message\":\"Unauthorized stream\"}}"));
            server.start();

            DashScopeChatService service = new DashScopeChatService(configuration(server, "bad-key"));

            try {
                service.chatCompletion(simpleUserRequest());
                Assert.fail("Expected AiAuthException");
            } catch (AiAuthException ex) {
                Assert.assertEquals(401, ex.getStatusCode());
                Assert.assertTrue(ex.getMessage().contains("Invalid API key"));
            }

            try {
                service.chatCompletion(simpleUserRequest());
                Assert.fail("Expected AiRateLimitException");
            } catch (AiRateLimitException ex) {
                Assert.assertEquals(429, ex.getStatusCode());
                Assert.assertTrue(ex.getMessage().contains("Rate limit exceeded"));
            }

            try {
                service.chatCompletion(simpleUserRequest());
                Assert.fail("Expected AiServerErrorException");
            } catch (AiServerErrorException ex) {
                Assert.assertEquals(500, ex.getStatusCode());
                Assert.assertTrue(ex.getMessage().contains("Upstream unavailable"));
            }

            try {
                service.chatCompletionStream(simpleUserRequest(), new RecordingSseListener());
                Assert.fail("Expected AiAuthException on stream");
            } catch (AiAuthException ex) {
                Assert.assertEquals(401, ex.getStatusCode());
                Assert.assertTrue(ex.getMessage().contains("Unauthorized stream"));
            }
        }
    }

    private Configuration configuration(MockWebServer server, String apiKey) {
        DashScopeConfig dashScopeConfig = new DashScopeConfig();
        dashScopeConfig.setApiHost(server.url("/").toString());
        dashScopeConfig.setApiKey(apiKey);
        Configuration configuration = new Configuration();
        configuration.setDashScopeConfig(dashScopeConfig);
        configuration.setOkHttpClient(new OkHttpClient());
        return configuration;
    }

    private MockResponse jsonResponse(String body) {
        return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }

    private MockResponse sseResponse(String body) {
        return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream; charset=utf-8")
                .setBody(body);
    }

    private ChatCompletion simpleUserRequest() {
        return ChatCompletion.builder()
                .model("qwen-plus")
                .message(ChatMessage.withUser("hello"))
                .build();
    }

    private Tool simpleTool(String name) {
        Tool.Function function = new Tool.Function();
        function.setName(name);
        function.setDescription("test tool");
        return new Tool("function", function);
    }

    private static final class RecordingSseListener extends SseListener {
        private final List<String> deltas = new ArrayList<String>();
        private int sendCount;

        @Override
        protected void send() {
            sendCount++;
            deltas.add(getCurrStr());
        }
    }
}
