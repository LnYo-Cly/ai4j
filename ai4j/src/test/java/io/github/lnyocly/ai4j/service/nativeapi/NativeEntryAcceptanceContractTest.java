package io.github.lnyocly.ai4j.service.nativeapi;

import io.github.lnyocly.ai4j.exception.AiClientException;
import io.github.lnyocly.ai4j.exception.AiServerErrorException;
import io.github.lnyocly.ai4j.listener.StreamExecutionOptions;
import io.github.lnyocly.ai4j.model.ModelCallStatus;
import io.github.lnyocly.ai4j.platform.anthropic.chat.entity.AnthropicChatCompletion;
import io.github.lnyocly.ai4j.platform.anthropic.chat.entity.AnthropicMessage;
import io.github.lnyocly.ai4j.platform.anthropic.errors.AnthropicApiException;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletion;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletionResponse;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatMessage;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.Content;
import io.github.lnyocly.ai4j.platform.openai.response.entity.Response;
import io.github.lnyocly.ai4j.platform.openai.response.entity.ResponseRequest;
import io.github.lnyocly.ai4j.protocol.RawSseEvent;
import io.github.lnyocly.ai4j.service.Configuration;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Acceptance regressions for the protocol-native entry (F1-F6, R7, R10).
 *
 * <p>Every test asserts the corrected contract, so the file compiles and fails
 * on the {@code 01a82a04} baseline and passes after the repair. The typed
 * per-protocol event surface is covered separately by
 * {@code NativeTypedStreamContractTest}.</p>
 */
public class NativeEntryAcceptanceContractTest {

    private MockWebServer server;
    private Configuration configuration;

    @Before
    public void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        configuration = new Configuration();
    }

    @After
    public void tearDown() throws Exception {
        server.shutdown();
    }

    // ---- F1: local streamExecution is applied, helpers fail before networking ----

    @Test
    public void firstTokenTimeoutFromRequestIsAppliedLocally() throws Exception {
        server.enqueue(sseResponse("data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"late\"}}]}\n\n"
                + "data: [DONE]\n\n").setBodyDelay(3, TimeUnit.SECONDS));

        ChatCompletionsNativeClient client = chatClient();
        ChatCompletion request = ChatCompletion.builder()
                .model("gpt-test")
                .messages(Collections.singletonList(ChatMessage.withUser("hello")))
                .streamExecution(StreamExecutionOptions.builder().firstTokenTimeoutMs(200L).build())
                .build();

        long startedAt = System.nanoTime();
        NativeStreamCall call = client.chatCompletionStream(request, noopListener());
        Assert.assertTrue("the configured budget must settle the call", call.await(6, TimeUnit.SECONDS));
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

        Assert.assertEquals(ModelCallStatus.FAILED, call.status());
        Assert.assertTrue("a first-token timeout must surface, got " + call.failure(),
                call.failure() instanceof TimeoutException);
        Assert.assertTrue("the configured 200ms budget must fire well before the 3s body delay; elapsed="
                + elapsedMs + "ms", elapsedMs < 2_000L);
    }

    @Test
    public void unsupportedLegacyHelperFailsBeforeNetworking() throws Exception {
        ChatCompletionsNativeClient client = chatClient();
        ChatCompletion request = ChatCompletion.builder()
                .model("gpt-test")
                .messages(Collections.singletonList(ChatMessage.withUser("hello")))
                .functions("legacy_function")
                .build();

        try {
            client.chatCompletionStream(request, noopListener());
            Assert.fail("an unresolved helper option must fail before networking");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("functions"));
        }
        Assert.assertEquals("no request may reach the server", 0, server.getRequestCount());
    }

    // ---- F2: in-band protocol error is a real failure ----

    @Test
    public void anthropicInBandErrorEventFailsTheCall() throws Exception {
        server.enqueue(sseResponse(
                "event: message_start\n"
                        + "data: {\"type\":\"message_start\",\"message\":{\"id\":\"m1\",\"model\":\"claude\"}}\n\n"
                        + "event: error\n"
                        + "data: {\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\",\"message\":\"Overloaded\"}}\n\n"));

        NativeStreamCall call = messagesClient().messagesStream(messagesRequest(), noopListener());
        Assert.assertTrue(call.await(5, TimeUnit.SECONDS));

        Assert.assertEquals(ModelCallStatus.FAILED, call.status());
        Assert.assertTrue("in-band error must be a typed failure, got " + call.failure(),
                call.failure() instanceof AnthropicApiException);
        AnthropicApiException failure = (AnthropicApiException) call.failure();
        Assert.assertEquals("overloaded_error", failure.getType());
        Assert.assertEquals("Overloaded", failure.getMessage());
    }

    // ---- F3: terminal is required; clean EOF without it is incomplete ----

    @Test
    public void anthropicCleanEofWithoutMessageStopIsIncomplete() throws Exception {
        server.enqueue(sseResponse(
                "event: content_block_delta\n"
                        + "data: {\"type\":\"content_block_delta\",\"index\":0,"
                        + "\"delta\":{\"type\":\"text_delta\",\"text\":\"Hi\"}}\n\n"));

        NativeStreamCall call = messagesClient().messagesStream(messagesRequest(), noopListener());
        Assert.assertTrue(call.await(5, TimeUnit.SECONDS));
        Assert.assertEquals("truncated Anthropic stream must not report COMPLETED",
                ModelCallStatus.FAILED, call.status());
    }

    @Test
    public void responsesCleanEofWithoutCompletedIsIncomplete() throws Exception {
        server.enqueue(sseResponse(
                "event: response.output_text.delta\n"
                        + "data: {\"type\":\"response.output_text.delta\",\"delta\":\"Hi\"}\n\n"));

        NativeStreamCall call = responsesClient().createStream(responsesRequest(), noopListener());
        Assert.assertTrue(call.await(5, TimeUnit.SECONDS));
        Assert.assertEquals("truncated Responses stream must not report COMPLETED",
                ModelCallStatus.FAILED, call.status());
    }

    @Test
    public void chatCleanEofWithoutTerminalIsIncomplete() throws Exception {
        server.enqueue(sseResponse(
                "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Hi\"}}]}\n\n"));

        NativeStreamCall call = chatClient().chatCompletionStream(chatRequest(), noopListener());
        Assert.assertTrue(call.await(5, TimeUnit.SECONDS));
        Assert.assertEquals("truncated chat stream must not report COMPLETED",
                ModelCallStatus.FAILED, call.status());
    }

    @Test
    public void validTerminalsStillComplete() throws Exception {
        server.enqueue(sseResponse("data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Hi\"}}]}\n\n"
                + "data: [DONE]\n\n"));
        NativeStreamCall doneCall = chatClient().chatCompletionStream(chatRequest(), noopListener());
        Assert.assertTrue(doneCall.await(5, TimeUnit.SECONDS));
        Assert.assertEquals(ModelCallStatus.COMPLETED, doneCall.status());

        server.enqueue(sseResponse("data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Hi\"},"
                + "\"finish_reason\":\"stop\"}]}\n\n"));
        NativeStreamCall finishReasonCall = chatClient().chatCompletionStream(chatRequest(), noopListener());
        Assert.assertTrue(finishReasonCall.await(5, TimeUnit.SECONDS));
        Assert.assertEquals("a native finish_reason is a valid terminal",
                ModelCallStatus.COMPLETED, finishReasonCall.status());

        server.enqueue(sseResponse("event: message_stop\ndata: {\"type\":\"message_stop\"}\n\n"));
        NativeStreamCall messagesCall = messagesClient().messagesStream(messagesRequest(), noopListener());
        Assert.assertTrue(messagesCall.await(5, TimeUnit.SECONDS));
        Assert.assertEquals(ModelCallStatus.COMPLETED, messagesCall.status());

        server.enqueue(sseResponse("event: response.completed\n"
                + "data: {\"type\":\"response.completed\",\"response\":{\"id\":\"r1\",\"status\":\"completed\"}}\n\n"));
        NativeStreamCall responsesCall = responsesClient().createStream(responsesRequest(), noopListener());
        Assert.assertTrue(responsesCall.await(5, TimeUnit.SECONDS));
        Assert.assertEquals(ModelCallStatus.COMPLETED, responsesCall.status());
    }

    // ---- F4: request-id lookup is case-insensitive and diagnostics survive ----

    @Test
    public void anthropicRequestIdLookupIsCaseInsensitive() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(429)
                .setHeader("Content-Type", "application/json")
                .setHeader("Request-Id", "req-mixed-case")
                .setBody("{\"type\":\"error\",\"error\":{\"type\":\"rate_limit_error\",\"message\":\"slow\"}}"));

        try {
            messagesClient().messages(messagesRequest());
            Assert.fail("expected a typed Anthropic error");
        } catch (AnthropicApiException expected) {
            Assert.assertEquals("req-mixed-case", expected.getRequestId());
        }
    }

    @Test
    public void openAiHttpErrorPreservesRequestIdHeader() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(500)
                .setHeader("Content-Type", "application/json")
                .setHeader("x-request-id", "req-openai-1")
                .setBody("{\"error\":{\"message\":\"boom\"}}"));

        try {
            chatClient().chatCompletion(chatRequest());
            Assert.fail("expected a typed HTTP error");
        } catch (AiServerErrorException expected) {
            Assert.assertTrue("request-id must survive into the diagnostic: " + expected.getMessage(),
                    expected.getMessage().contains("req-openai-1"));
        }
    }

    // ---- F5: a throwing listener keeps its own cause ----

    @Test
    public void throwingListenerKeepsItsOwnCauseInsteadOfSyntheticHttp200() throws Exception {
        server.enqueue(sseResponse("data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"a\"}}]}\n\n"
                + "data: [DONE]\n\n"));

        final RuntimeException listenerBoom = new RuntimeException("listener boom");
        NativeStreamCall call = chatClient().chatCompletionStream(chatRequest(), new NativeSseListener() {
            @Override
            public void onEvent(String id, String type, String data) {
                throw listenerBoom;
            }

            @Override
            public void onFailure(Throwable failure) {
            }
        });
        Assert.assertTrue(call.await(5, TimeUnit.SECONDS));

        Assert.assertEquals(ModelCallStatus.FAILED, call.status());
        Assert.assertFalse("the listener cause must not be rewritten as a synthetic HTTP 200 failure",
                call.failure() instanceof AiClientException && "HTTP 200".equals(call.failure().getMessage()));
        Assert.assertTrue("the original listener cause must be retained: " + call.failure(),
                isCause(call.failure(), listenerBoom));
    }

    // ---- F6: terminal state is atomic; late callbacks do not resurrect ----

    @Test
    public void lateCallbacksAfterCancelDoNotResurrectTheCall() throws Exception {
        server.enqueue(sseResponse("data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"x\"}}]}\n\n")
                .setBodyDelay(5, TimeUnit.SECONDS));

        final List<String> delivered = Collections.synchronizedList(new ArrayList<String>());
        NativeStreamCall call = chatClient().chatCompletionStream(chatRequest(), new NativeSseListener() {
            @Override
            public void onEvent(String id, String type, String data) {
                delivered.add(data);
            }

            @Override
            public void onFailure(Throwable failure) {
                delivered.add("failure");
            }

            @Override
            public void onClosed() {
                delivered.add("closed");
            }
        });
        NativeStreamCallHandle handle = (NativeStreamCallHandle) call;
        handle.onEvent(RawSseEvent.of(null, null,
                "{\"choices\":[{\"index\":0,\"delta\":{\"content\":\"first\"}}]}"));

        call.cancel();
        Assert.assertTrue(call.await(3, TimeUnit.SECONDS));

        handle.onEvent(RawSseEvent.of(null, null,
                "{\"choices\":[{\"index\":0,\"delta\":{\"content\":\"late\"}}]}"));
        handle.onClosed();
        handle.onFailure(new RuntimeException("late failure"), null);

        Assert.assertEquals(ModelCallStatus.CANCELLED, call.status());
        Assert.assertNull("a cancelled call must not gain a failure", call.failure());
        Assert.assertEquals("only the pre-cancel event may be delivered", 1, delivered.size());
    }

    @Test
    public void pendingHeaderCancelIsPermanentAndClientStaysReusable() throws Exception {
        server.enqueue(sseResponse("data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"x\"}}]}\n\n")
                .setBodyDelay(3, TimeUnit.SECONDS));

        ChatCompletionsNativeClient client = chatClient();
        NativeStreamCall call = client.chatCompletionStream(chatRequest(), noopListener());
        Assert.assertNotNull("the stream request must reach the server", server.takeRequest(3, TimeUnit.SECONDS));
        call.cancel();
        Assert.assertTrue(call.await(3, TimeUnit.SECONDS));
        Assert.assertEquals(ModelCallStatus.CANCELLED, call.status());
        Assert.assertNull(call.failure());

        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"id\":\"chatcmpl-2\",\"choices\":[{\"index\":0,"
                        + "\"message\":{\"role\":\"assistant\",\"content\":\"ok\"},\"finish_reason\":\"stop\"}]}"));
        ChatCompletionResponse reused = client.chatCompletion(chatRequest());
        Assert.assertNotNull("the shared client must remain usable after a cancel", reused);
        Assert.assertEquals("chatcmpl-2", reused.getId());
    }

    // ---- R7 / R10: snapshot ownership and local-vs-API metadata ----

    @Test
    public void mutatingNestedInputAfterDispatchDoesNotChangeTheSentSnapshot() throws Exception {
        server.enqueue(sseResponse("data: [DONE]\n\n"));

        ChatCompletion request = chatRequest();
        NativeStreamCall call = chatClient().chatCompletionStream(request, noopListener());
        request.getMessages().get(0).setContent(Content.ofText("mutated-after-dispatch"));
        request.setModel("mutated-model");

        RecordedRequest recorded = server.takeRequest(3, TimeUnit.SECONDS);
        Assert.assertNotNull(recorded);
        String body = recorded.getBody().readUtf8();
        Assert.assertTrue("the original message must be on the wire", body.contains("hello"));
        Assert.assertFalse("a post-dispatch mutation must not reach the wire",
                body.contains("mutated-after-dispatch"));
        Assert.assertFalse(body.contains("mutated-model"));

        call.cancel();
    }

    @Test
    public void apiMetadataStaysOnTheWireAndLocalOptionsDoNot() throws Exception {
        server.enqueue(sseResponse("event: response.completed\n"
                + "data: {\"type\":\"response.completed\",\"response\":{\"id\":\"r1\",\"status\":\"completed\"}}\n\n"));

        ResponseRequest request = ResponseRequest.builder()
                .model("gpt-test")
                .input("hello")
                .metadata(Collections.singletonMap("trace_id", "t-1"))
                .streamExecution(StreamExecutionOptions.builder().firstTokenTimeoutMs(1234L).build())
                .build();
        NativeStreamCall call = responsesClient().createStream(request, noopListener());
        call.await(3, TimeUnit.SECONDS);

        RecordedRequest recorded = server.takeRequest(3, TimeUnit.SECONDS);
        Assert.assertNotNull(recorded);
        String body = recorded.getBody().readUtf8();
        Assert.assertTrue("protocol API metadata must be transmitted",
                body.contains("\"trace_id\":\"t-1\""));
        Assert.assertFalse("local execution options must never reach the wire",
                body.contains("firstTokenTimeoutMs"));
    }

    // ---- helpers ----

    private ChatCompletionsNativeClient chatClient() {
        return ChatCompletionsNativeClient.of(server.url("/").toString(), "native-key", configuration);
    }

    private ResponsesNativeClient responsesClient() {
        return ResponsesNativeClient.of(server.url("/").toString(), "native-key", configuration);
    }

    private MessagesNativeClient messagesClient() {
        return MessagesNativeClient.of(server.url("/").toString(), "native-key", configuration);
    }

    private static ChatCompletion chatRequest() {
        List<ChatMessage> messages = new ArrayList<ChatMessage>();
        messages.add(ChatMessage.withUser("hello"));
        return ChatCompletion.builder()
                .model("gpt-test")
                .messages(messages)
                .build();
    }

    private static ResponseRequest responsesRequest() {
        return ResponseRequest.builder().model("gpt-test").input("hello").build();
    }

    private static AnthropicChatCompletion messagesRequest() {
        AnthropicChatCompletion request = new AnthropicChatCompletion();
        request.setModel("claude-test");
        request.setMaxTokens(64);
        request.setMessages(Collections.singletonList(new AnthropicMessage("user", "hello")));
        return request;
    }

    private static NativeSseListener noopListener() {
        return new NativeSseListener() {
            @Override
            public void onEvent(String id, String type, String data) {
            }

            @Override
            public void onFailure(Throwable failure) {
            }
        };
    }

    private static boolean isCause(Throwable actual, Throwable expected) {
        Throwable current = actual;
        while (current != null) {
            if (current == expected) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static MockResponse sseResponse(String body) {
        return new MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(body);
    }
}


