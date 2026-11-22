package io.github.lnyocly.ai4j.docs;

import io.github.lnyocly.ai4j.client.RecordingFunction;
import io.github.lnyocly.ai4j.exception.AiRateLimitException;
import io.github.lnyocly.ai4j.exception.AiServerErrorException;
import io.github.lnyocly.ai4j.listener.StreamExecutionOptions;
import io.github.lnyocly.ai4j.model.ModelCallStatus;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletion;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletionResponse;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatMessage;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import io.github.lnyocly.ai4j.service.nativeapi.ChatCompletionsNativeClient;
import io.github.lnyocly.ai4j.service.nativeapi.NativeSseListener;
import io.github.lnyocly.ai4j.service.nativeapi.NativeStreamCall;
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
import java.util.concurrent.atomic.AtomicReference;

/**
 * Executable example for the protocol-native Chat Completions entry.
 *
 * <p>A caller needs only a base URL, an API key and a native
 * {@link ChatCompletion}; the protocol is fixed by the client type. The local
 * {@code MockWebServer} pins endpoint/auth, native field passthrough, the
 * non-stream response, raw SSE order, HTTP error mapping and zero client-side
 * tool execution.</p>
 */
public class NativeChatCompletionsExampleTest {

    private MockWebServer server;

    @Before
    public void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @After
    public void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    public void minimalNonStreamCallSendsEndpointAndNativeFields() throws Exception {
        RecordingFunction.INVOCATIONS.set(0);
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(CHAT_TOOL_CALL_BODY));

        ChatCompletionsNativeClient client =
                ChatCompletionsNativeClient.of(server.url("/").toString(), "native-key");
        ChatCompletion request = ChatCompletion.builder()
                .model("gpt-test")
                .messages(Collections.singletonList(ChatMessage.withUser("hello")))
                .tools(Collections.singletonList(functionTool("audit_recording_function")))
                .extraBody("some_native_field", "kept")
                .streamExecution(StreamExecutionOptions.builder().firstTokenTimeoutMs(1234L).build())
                .build();

        ChatCompletionResponse response = client.chatCompletion(request);

        Assert.assertNotNull(response);
        Assert.assertEquals("tool_calls", response.getChoices().get(0).getFinishReason());
        Assert.assertEquals("audit_recording_function",
                response.getChoices().get(0).getMessage().getToolCalls().get(0).getFunction().getName());
        Assert.assertEquals("the native entry must never execute a tool",
                0, RecordingFunction.INVOCATIONS.get());

        RecordedRequest recorded = server.takeRequest(3, TimeUnit.SECONDS);
        Assert.assertNotNull(recorded);
        Assert.assertEquals("/v1/chat/completions", recorded.getPath());
        Assert.assertEquals("Bearer native-key", recorded.getHeader("Authorization"));
        String body = recorded.getBody().readUtf8();
        Assert.assertTrue("native model field must be on the wire", body.contains("\"model\":\"gpt-test\""));
        Assert.assertTrue("native tools must be on the wire", body.contains("\"audit_recording_function\""));
        Assert.assertTrue("extraBody protocol extension must be on the wire",
                body.contains("\"some_native_field\":\"kept\""));
        Assert.assertFalse("local execution options must not leak to the wire",
                body.contains("firstTokenTimeoutMs"));
    }

    @Test
    public void streamingDeliversRawSseEventsInOrderWithoutMutatingRequest() throws Exception {
        server.enqueue(sseResponse(
                "data: {\"id\":\"chatcmpl-native-1\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Hel\"}}]}\n\n"
                        + "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"lo\"}}]}\n\n"
                        + "data: [DONE]\n\n"));

        ChatCompletionsNativeClient client =
                ChatCompletionsNativeClient.of(server.url("/").toString(), "native-key");
        ChatCompletion request = ChatCompletion.builder()
                .model("gpt-test")
                .messages(Collections.singletonList(ChatMessage.withUser("hello")))
                .build();

        final List<String> events = Collections.synchronizedList(new ArrayList<String>());
        NativeStreamCall call = client.chatCompletionStream(request, new NativeSseListener() {
            @Override
            public void onEvent(String id, String type, String data) {
                events.add(data);
            }

            @Override
            public void onFailure(Throwable failure) {
                events.add("failure:" + failure.getClass().getSimpleName());
            }
        });
        call.await();

        Assert.assertEquals(ModelCallStatus.COMPLETED, call.status());
        Assert.assertEquals(3, events.size());
        Assert.assertTrue(events.get(0).contains("chatcmpl-native-1"));
        Assert.assertTrue(events.get(1).contains("\"content\":\"lo\""));
        Assert.assertEquals("[DONE]", events.get(2));
        Assert.assertFalse("the native entry must not mutate the caller's stream flag",
                Boolean.TRUE.equals(request.getStream()));

        RecordedRequest recorded = server.takeRequest(3, TimeUnit.SECONDS);
        Assert.assertNotNull(recorded);
        Assert.assertTrue("the wire snapshot must carry stream=true",
                recorded.getBody().readUtf8().contains("\"stream\":true"));
    }

    @Test
    public void httpErrorThrowsTypedNativeException() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(429)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"error\":{\"message\":\"rate limited\"}}"));

        ChatCompletionsNativeClient client =
                ChatCompletionsNativeClient.of(server.url("/").toString(), "native-key");
        try {
            client.chatCompletion(ChatCompletion.builder()
                    .model("gpt-test")
                    .messages(Collections.singletonList(ChatMessage.withUser("hello")))
                    .build());
            Assert.fail("expected a typed HTTP error");
        } catch (AiRateLimitException expected) {
            Assert.assertEquals(429, expected.getStatusCode());
        }
    }

    @Test
    public void streamingHttpErrorSurfacesOnHandleAndListener() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(500)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"error\":{\"message\":\"boom\"}}"));

        ChatCompletionsNativeClient client =
                ChatCompletionsNativeClient.of(server.url("/").toString(), "native-key");
        final AtomicReference<Throwable> listenerFailure = new AtomicReference<Throwable>();
        NativeStreamCall call = client.chatCompletionStream(
                ChatCompletion.builder()
                        .model("gpt-test")
                        .messages(Collections.singletonList(ChatMessage.withUser("hello")))
                        .build(),
                new NativeSseListener() {
                    @Override
                    public void onEvent(String id, String type, String data) {
                    }

                    @Override
                    public void onFailure(Throwable failure) {
                        listenerFailure.set(failure);
                    }
                });
        call.await();

        Assert.assertEquals(ModelCallStatus.FAILED, call.status());
        Assert.assertTrue(call.failure() instanceof AiServerErrorException);
        Assert.assertTrue(listenerFailure.get() instanceof AiServerErrorException);
        Assert.assertEquals(500, ((AiServerErrorException) call.failure()).getStatusCode());
    }

    private static Tool functionTool(String name) {
        Tool.Function function = new Tool.Function();
        function.setName(name);
        function.setDescription("test tool");
        return new Tool("function", function);
    }

    private static MockResponse sseResponse(String body) {
        return new MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(body);
    }

    private static final String CHAT_TOOL_CALL_BODY = "{"
            + "\"id\":\"chatcmpl-native-1\",\"object\":\"chat.completion\",\"model\":\"gpt-test\","
            + "\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"\","
            + "\"tool_calls\":[{\"id\":\"call_1\",\"type\":\"function\","
            + "\"function\":{\"name\":\"audit_recording_function\",\"arguments\":\"{\\\"value\\\":\\\"x\\\"}\"}}]},"
            + "\"finish_reason\":\"tool_calls\"}],"
            + "\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":5,\"total_tokens\":15}}";
}
