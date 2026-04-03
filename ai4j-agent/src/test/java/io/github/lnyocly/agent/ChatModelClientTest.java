package io.github.lnyocly.agent;

import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.model.ChatModelClient;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.util.AgentInputItem;
import io.github.lnyocly.ai4j.listener.SseListener;
import io.github.lnyocly.ai4j.listener.StreamExecutionOptions;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletion;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletionResponse;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatMessage;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.Choice;
import io.github.lnyocly.ai4j.platform.openai.tool.ToolCall;
import io.github.lnyocly.ai4j.service.IChatService;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ChatModelClientTest {

    @Test
    public void test_stream_preserves_reasoning_text_and_tool_calls() throws Exception {
        FakeChatService chatService = new FakeChatService();
        ChatModelClient client = new ChatModelClient(chatService);

        List<String> reasoningDeltas = new ArrayList<>();
        List<String> textDeltas = new ArrayList<>();

        AgentModelResult result = client.createStream(AgentPrompt.builder()
                        .model("glm-4.7")
                        .build(),
                new AgentModelStreamListener() {
                    @Override
                    public void onReasoningDelta(String delta) {
                        reasoningDeltas.add(delta);
                    }

                    @Override
                    public void onDeltaText(String delta) {
                        textDeltas.add(delta);
                    }
                });

        Assert.assertEquals(Arrays.asList("Need a tool first."), reasoningDeltas);
        Assert.assertEquals(Arrays.asList("I will get the current time."), textDeltas);
        Assert.assertEquals("Need a tool first.", result.getReasoningText());
        Assert.assertEquals("I will get the current time.", result.getOutputText());
        Assert.assertEquals(1, result.getToolCalls().size());
        Assert.assertEquals("get_current_time", result.getToolCalls().get(0).getName());
        Assert.assertEquals("{}", result.getToolCalls().get(0).getArguments());
        Assert.assertTrue(chatService.lastStreamRequest != null && Boolean.TRUE.equals(chatService.lastStreamRequest.getPassThroughToolCalls()));
    }

    @Test
    public void test_stream_preserves_invalid_coding_tool_calls_for_runtime_validation() throws Exception {
        FakeInvalidBashChatService chatService = new FakeInvalidBashChatService();
        ChatModelClient client = new ChatModelClient(chatService);

        AgentModelResult result = client.createStream(AgentPrompt.builder()
                        .model("glm-4.7")
                        .build(),
                new AgentModelStreamListener() {
                });

        Assert.assertEquals("I should inspect the local time.", result.getOutputText());
        Assert.assertEquals(1, result.getToolCalls().size());
        Assert.assertEquals("bash", result.getToolCalls().get(0).getName());
    }

    @Test
    public void test_stream_propagates_stream_execution_options() throws Exception {
        FakeChatService chatService = new FakeChatService();
        ChatModelClient client = new ChatModelClient(chatService);

        client.createStream(AgentPrompt.builder()
                        .model("glm-4.7")
                        .streamExecution(StreamExecutionOptions.builder()
                                .firstTokenTimeoutMs(1234L)
                                .idleTimeoutMs(5678L)
                                .maxRetries(2)
                                .retryBackoffMs(90L)
                                .build())
                        .build(),
                new AgentModelStreamListener() {
                });

        Assert.assertNotNull(chatService.lastStreamRequest);
        Assert.assertNotNull(chatService.lastStreamRequest.getStreamExecution());
        Assert.assertEquals(1234L, chatService.lastStreamRequest.getStreamExecution().getFirstTokenTimeoutMs());
        Assert.assertEquals(5678L, chatService.lastStreamRequest.getStreamExecution().getIdleTimeoutMs());
        Assert.assertEquals(2, chatService.lastStreamRequest.getStreamExecution().getMaxRetries());
        Assert.assertEquals(90L, chatService.lastStreamRequest.getStreamExecution().getRetryBackoffMs());
    }

    @Test
    public void test_stream_surfaces_http_error_payload_when_sse_failure_has_no_throwable() throws Exception {
        FakeStreamingErrorChatService chatService = new FakeStreamingErrorChatService();
        ChatModelClient client = new ChatModelClient(chatService);
        final List<String> errors = new ArrayList<String>();

        AgentModelResult result = client.createStream(AgentPrompt.builder()
                        .model("glm-4.7")
                        .build(),
                new AgentModelStreamListener() {
                    @Override
                    public void onError(Throwable t) {
                        errors.add(t == null ? null : t.getMessage());
                    }
                });

        Assert.assertEquals("", result.getOutputText());
        Assert.assertEquals(1, errors.size());
        Assert.assertEquals("Invalid API key provided.", errors.get(0));
    }

    @Test
    public void test_create_stores_assistant_tool_calls_in_memory_items() throws Exception {
        CapturingChatService chatService = new CapturingChatService();
        chatService.responseMessage = ChatMessage.withAssistant(
                "I should inspect the workspace.",
                Collections.singletonList(new ToolCall(
                        "call_1",
                        "function",
                        new ToolCall.Function("bash", "{\"action\":\"exec\",\"command\":\"dir\"}")
                ))
        );
        ChatModelClient client = new ChatModelClient(chatService);

        AgentModelResult result = client.create(AgentPrompt.builder()
                .model("glm-4.7")
                .build());

        Assert.assertEquals(1, result.getMemoryItems().size());
        Assert.assertTrue(result.getMemoryItems().get(0) instanceof Map);
        @SuppressWarnings("unchecked")
        Map<String, Object> item = (Map<String, Object>) result.getMemoryItems().get(0);
        Assert.assertEquals("message", item.get("type"));
        Assert.assertEquals("assistant", item.get("role"));
        Assert.assertTrue(item.containsKey("tool_calls"));
    }

    @Test
    public void test_create_rehydrates_assistant_tool_calls_before_tool_output() throws Exception {
        CapturingChatService chatService = new CapturingChatService();
        ChatModelClient client = new ChatModelClient(chatService);
        List<AgentToolCall> toolCalls = Collections.singletonList(AgentToolCall.builder()
                .callId("call_1")
                .name("bash")
                .type("function")
                .arguments("{\"action\":\"exec\",\"command\":\"dir\"}")
                .build());

        client.create(AgentPrompt.builder()
                .model("glm-4.7")
                .items(Arrays.<Object>asList(
                        AgentInputItem.userMessage("inspect the workspace"),
                        AgentInputItem.assistantToolCallsMessage("I will inspect the workspace.", toolCalls),
                        AgentInputItem.functionCallOutput("call_1", "{\"exitCode\":0}")
                ))
                .build());

        Assert.assertNotNull(chatService.lastRequest);
        Assert.assertNotNull(chatService.lastRequest.getMessages());
        Assert.assertEquals(3, chatService.lastRequest.getMessages().size());

        ChatMessage assistantMessage = chatService.lastRequest.getMessages().get(1);
        Assert.assertEquals("assistant", assistantMessage.getRole());
        Assert.assertEquals("I will inspect the workspace.", assistantMessage.getContent().getText());
        Assert.assertNotNull(assistantMessage.getToolCalls());
        Assert.assertEquals(1, assistantMessage.getToolCalls().size());
        Assert.assertEquals("bash", assistantMessage.getToolCalls().get(0).getFunction().getName());

        ChatMessage toolMessage = chatService.lastRequest.getMessages().get(2);
        Assert.assertEquals("tool", toolMessage.getRole());
        Assert.assertEquals("call_1", toolMessage.getToolCallId());
    }

    private static class FakeChatService implements IChatService {
        private ChatCompletion lastStreamRequest;

        @Override
        public ChatCompletionResponse chatCompletion(String baseUrl, String apiKey, ChatCompletion chatCompletion) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public ChatCompletionResponse chatCompletion(ChatCompletion chatCompletion) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public void chatCompletionStream(String baseUrl, String apiKey, ChatCompletion chatCompletion, SseListener eventSourceListener) {
            lastStreamRequest = chatCompletion;
            eventSourceListener.onEvent(null, null, null,
                    "{\"choices\":[{\"delta\":{\"role\":\"assistant\",\"reasoning_content\":\"Need a tool first.\"},\"finish_reason\":null}]}");
            eventSourceListener.onEvent(null, null, null,
                    "{\"choices\":[{\"delta\":{\"role\":\"assistant\",\"content\":\"I will get the current time.\"},\"finish_reason\":null}]}");
            eventSourceListener.onEvent(null, null, null,
                    "{\"choices\":[{\"delta\":{\"tool_calls\":[{\"id\":\"call_1\",\"type\":\"function\",\"function\":{\"name\":\"get_current_time\",\"arguments\":\"{}\"}}]},\"finish_reason\":\"tool_calls\"}]}");
            eventSourceListener.onEvent(null, null, null, "[DONE]");
            eventSourceListener.onClosed(null);
        }

        @Override
        public void chatCompletionStream(ChatCompletion chatCompletion, SseListener eventSourceListener) {
            throw new UnsupportedOperationException("not used");
        }
    }

    private static class FakeInvalidBashChatService implements IChatService {
        @Override
        public ChatCompletionResponse chatCompletion(String baseUrl, String apiKey, ChatCompletion chatCompletion) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public ChatCompletionResponse chatCompletion(ChatCompletion chatCompletion) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public void chatCompletionStream(String baseUrl, String apiKey, ChatCompletion chatCompletion, SseListener eventSourceListener) {
            eventSourceListener.onEvent(null, null, null,
                    "{\"choices\":[{\"delta\":{\"role\":\"assistant\",\"content\":\"I should inspect the local time.\"},\"finish_reason\":null}]}");
            eventSourceListener.onEvent(null, null, null,
                    "{\"choices\":[{\"delta\":{\"tool_calls\":[{\"id\":\"call_1\",\"type\":\"function\",\"function\":{\"name\":\"bash\",\"arguments\":\"{\\\"action\\\":\\\"exec\\\"}\"}}]},\"finish_reason\":\"tool_calls\"}]}");
            eventSourceListener.onEvent(null, null, null, "[DONE]");
            eventSourceListener.onClosed(null);
        }

        @Override
        public void chatCompletionStream(ChatCompletion chatCompletion, SseListener eventSourceListener) {
            throw new UnsupportedOperationException("not used");
        }
    }

    private static class FakeStreamingErrorChatService implements IChatService {
        @Override
        public ChatCompletionResponse chatCompletion(String baseUrl, String apiKey, ChatCompletion chatCompletion) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public ChatCompletionResponse chatCompletion(ChatCompletion chatCompletion) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public void chatCompletionStream(String baseUrl, String apiKey, ChatCompletion chatCompletion, SseListener eventSourceListener) {
            Request request = new Request.Builder().url("https://example.com/chat").build();
            Response response = new Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(401)
                    .message("Unauthorized")
                    .body(ResponseBody.create(
                            MediaType.get("application/json"),
                            "{\"error\":{\"message\":\"Invalid API key provided.\"}}"
                    ))
                    .build();
            eventSourceListener.onFailure(null, null, response);
        }

        @Override
        public void chatCompletionStream(ChatCompletion chatCompletion, SseListener eventSourceListener) {
            throw new UnsupportedOperationException("not used");
        }
    }

    private static class CapturingChatService implements IChatService {
        private ChatCompletion lastRequest;
        private ChatMessage responseMessage;

        @Override
        public ChatCompletionResponse chatCompletion(String baseUrl, String apiKey, ChatCompletion chatCompletion) {
            lastRequest = chatCompletion;
            return response();
        }

        @Override
        public ChatCompletionResponse chatCompletion(ChatCompletion chatCompletion) {
            lastRequest = chatCompletion;
            return response();
        }

        @Override
        public void chatCompletionStream(String baseUrl, String apiKey, ChatCompletion chatCompletion, SseListener eventSourceListener) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public void chatCompletionStream(ChatCompletion chatCompletion, SseListener eventSourceListener) {
            throw new UnsupportedOperationException("not used");
        }

        private ChatCompletionResponse response() {
            if (responseMessage == null) {
                return new ChatCompletionResponse();
            }
            Choice choice = new Choice();
            choice.setMessage(responseMessage);
            ChatCompletionResponse response = new ChatCompletionResponse();
            response.setChoices(Collections.singletonList(choice));
            return response;
        }
    }
}
