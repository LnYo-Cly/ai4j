package io.github.lnyocly.ai4j.docs;

import io.github.lnyocly.ai4j.client.RecordingFunction;
import io.github.lnyocly.ai4j.model.ModelCallStatus;
import io.github.lnyocly.ai4j.platform.anthropic.chat.entity.AnthropicChatCompletion;
import io.github.lnyocly.ai4j.platform.anthropic.chat.entity.AnthropicChatCompletionResponse;
import io.github.lnyocly.ai4j.platform.anthropic.chat.entity.AnthropicMessage;
import io.github.lnyocly.ai4j.platform.anthropic.errors.AnthropicApiException;
import io.github.lnyocly.ai4j.platform.anthropic.errors.AnthropicApiException.AnthropicRateLimitException;
import io.github.lnyocly.ai4j.service.nativeapi.MessagesNativeClient;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Executable example for the protocol-native Anthropic Messages entry.
 *
 * <p>A caller needs only a base URL, an API key and a native
 * {@link AnthropicChatCompletion}. Authentication uses the native
 * {@code x-api-key} header; the SSE {@code id}/{@code event} envelope and order
 * are preserved; HTTP errors stay typed as {@link AnthropicApiException}.</p>
 */
public class NativeMessagesExampleTest {

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
    public void minimalNonStreamCallUsesNativeAuthAndKeepsExtensionFields() throws Exception {
        RecordingFunction.INVOCATIONS.set(0);
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(MESSAGES_TOOL_CALL_BODY));

        MessagesNativeClient client =
                MessagesNativeClient.of(server.url("/").toString(), "native-key");
        AnthropicChatCompletion request = new AnthropicChatCompletion();
        request.setModel("claude-test");
        request.setMaxTokens(64);
        request.setMessages(Collections.singletonList(new AnthropicMessage("user", "hello")));
        Map<String, Object> extraBody = new HashMap<String, Object>();
        extraBody.put("thinking", Collections.singletonMap("type", "enabled"));
        request.setExtraBody(extraBody);

        AnthropicChatCompletionResponse response = client.messages(request);

        Assert.assertNotNull(response);
        Assert.assertEquals("msg_native_1", response.getId());
        Assert.assertEquals("tool_use", response.getStopReason());
        Assert.assertEquals("the native entry must never execute a tool",
                0, RecordingFunction.INVOCATIONS.get());

        RecordedRequest recorded = server.takeRequest(3, TimeUnit.SECONDS);
        Assert.assertNotNull(recorded);
        Assert.assertEquals("/v1/messages", recorded.getPath());
        Assert.assertEquals("native-key", recorded.getHeader("x-api-key"));
        Assert.assertEquals("2023-06-01", recorded.getHeader("anthropic-version"));
        String body = recorded.getBody().readUtf8();
        Assert.assertTrue(body.contains("\"model\":\"claude-test\""));
        Assert.assertTrue("extraBody protocol extension must be on the wire",
                body.contains("\"thinking\":{\"type\":\"enabled\"}"));
    }

    @Test
    public void streamingPreservesSseEnvelopeIdTypeAndOrder() throws Exception {
        server.enqueue(sseResponse(
                "id: evt-1\n"
                        + "event: message_start\n"
                        + "data: {\"type\":\"message_start\",\"message\":{\"id\":\"msg_native_1\",\"model\":\"claude-test\"}}\n\n"
                        + "event: content_block_delta\n"
                        + "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"Hi\"}}\n\n"
                        + "event: message_delta\n"
                        + "data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"},\"usage\":{\"output_tokens\":2}}\n\n"
                        + "event: message_stop\n"
                        + "data: {\"type\":\"message_stop\"}\n\n"));

        MessagesNativeClient client =
                MessagesNativeClient.of(server.url("/").toString(), "native-key");
        AnthropicChatCompletion request = new AnthropicChatCompletion();
        request.setModel("claude-test");
        request.setMaxTokens(64);
        request.setMessages(Collections.singletonList(new AnthropicMessage("user", "hello")));

        final List<String> events = Collections.synchronizedList(new ArrayList<String>());
        NativeStreamCall call = client.messagesStream(request, new NativeSseListener() {
            @Override
            public void onEvent(String id, String type, String data) {
                events.add(id + "|" + type + "|" + data);
            }

            @Override
            public void onFailure(Throwable failure) {
                events.add("failure:" + failure.getClass().getSimpleName());
            }
        });
        call.await();

        Assert.assertEquals(ModelCallStatus.COMPLETED, call.status());
        Assert.assertEquals(4, events.size());
        Assert.assertTrue("the SSE id envelope must survive: " + events.get(0),
                events.get(0).startsWith("evt-1|message_start|"));
        Assert.assertTrue("the SSE id persists across events: " + events.get(1),
                events.get(1).startsWith("evt-1|content_block_delta|"));
        Assert.assertTrue(events.get(1).contains("\"text\":\"Hi\""));
        Assert.assertTrue(events.get(3).startsWith("evt-1|message_stop|"));
        Assert.assertFalse("the native entry must not mutate the caller's stream flag",
                Boolean.TRUE.equals(request.getStream()));

        RecordedRequest recorded = server.takeRequest(3, TimeUnit.SECONDS);
        Assert.assertNotNull(recorded);
        Assert.assertTrue(recorded.getBody().readUtf8().contains("\"stream\":true"));
    }

    @Test
    public void httpErrorThrowsTypedAnthropicException() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(429)
                .setHeader("Content-Type", "application/json")
                .setHeader("request-id", "req_native_1")
                .setBody("{\"type\":\"error\",\"error\":{\"type\":\"rate_limit_error\",\"message\":\"slow down\"}}"));

        MessagesNativeClient client =
                MessagesNativeClient.of(server.url("/").toString(), "native-key");
        AnthropicChatCompletion request = new AnthropicChatCompletion();
        request.setModel("claude-test");
        request.setMaxTokens(64);
        request.setMessages(Collections.singletonList(new AnthropicMessage("user", "hello")));
        try {
            client.messages(request);
            Assert.fail("expected a typed Anthropic error");
        } catch (AnthropicRateLimitException expected) {
            Assert.assertEquals(429, expected.getStatus());
            Assert.assertEquals("rate_limit_error", expected.getType());
        }
    }

    @Test
    public void streamingHttpErrorSurfacesOnHandleAndListener() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(529)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\",\"message\":\"busy\"}}"));

        MessagesNativeClient client =
                MessagesNativeClient.of(server.url("/").toString(), "native-key");
        AnthropicChatCompletion request = new AnthropicChatCompletion();
        request.setModel("claude-test");
        request.setMaxTokens(64);
        request.setMessages(Collections.singletonList(new AnthropicMessage("user", "hello")));
        final AtomicReference<Throwable> listenerFailure = new AtomicReference<Throwable>();
        NativeStreamCall call = client.messagesStream(request, new NativeSseListener() {
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
        Assert.assertTrue(call.failure() instanceof AnthropicApiException);
        Assert.assertEquals(529, ((AnthropicApiException) call.failure()).getStatus());
        Assert.assertSame(call.failure(), listenerFailure.get());
    }

    private static MockResponse sseResponse(String body) {
        return new MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(body);
    }

    private static final String MESSAGES_TOOL_CALL_BODY = "{"
            + "\"id\":\"msg_native_1\",\"type\":\"message\",\"role\":\"assistant\",\"model\":\"claude-test\","
            + "\"content\":[{\"type\":\"tool_use\",\"id\":\"toolu_1\","
            + "\"name\":\"audit_recording_function\",\"input\":{\"value\":\"x\"}}],"
            + "\"stop_reason\":\"tool_use\","
            + "\"usage\":{\"input_tokens\":10,\"output_tokens\":5}}";
}
