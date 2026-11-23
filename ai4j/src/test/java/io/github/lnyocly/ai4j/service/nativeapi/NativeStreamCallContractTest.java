package io.github.lnyocly.ai4j.service.nativeapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lnyocly.ai4j.model.ModelCallStatus;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletion;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatMessage;
import io.github.lnyocly.ai4j.service.Configuration;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Cross-protocol contract tests for the native single-turn handle.
 *
 * <p>Negative controls: {@code stream(...)} does not block on the response body,
 * cancel closes the upstream call before the body arrives, and the caller's
 * request DTO is deep-snapshotted rather than mutated in place.</p>
 */
public class NativeStreamCallContractTest {

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

    @Test
    public void streamingStartsWithoutBlockingAndCancelClosesUpstream() throws Exception {
        final AtomicInteger delivered = new AtomicInteger();
        server.enqueue(sseResponse(
                "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"start\"}}]}\n\n")
                .setBodyDelay(3, TimeUnit.SECONDS));

        ChatCompletionsNativeClient client = ChatCompletionsNativeClient.of(
                server.url("/").toString(), "native-key", configuration);
        long startedAt = System.nanoTime();
        NativeStreamCall call = client.chatCompletionStream(chatRequest(), new NativeSseListener() {
            @Override
            public void onEvent(String id, String type, String data) {
                delivered.incrementAndGet();
            }

            @Override
            public void onFailure(Throwable failure) {
            }
        });
        long startElapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

        Assert.assertTrue("stream(...) must return before the body arrives; elapsed="
                + startElapsedMs + "ms", startElapsedMs < 1_500L);
        Assert.assertEquals(ModelCallStatus.RUNNING, call.status());
        Assert.assertNotNull("the request must reach the server", server.takeRequest(3, TimeUnit.SECONDS));

        call.cancel();
        Assert.assertTrue(call.isCancelled());
        call.await();

        Assert.assertEquals(ModelCallStatus.CANCELLED, call.status());
        Assert.assertNull(call.failure());
        Assert.assertEquals("cancel must close the upstream stream before the body arrives",
                0, delivered.get());
    }

    @Test
    public void timedAwaitReturnsFalseWhileStillRunning() throws Exception {
        server.enqueue(sseResponse(
                "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"start\"}}]}\n\n")
                .setBodyDelay(3, TimeUnit.SECONDS));

        ChatCompletionsNativeClient client = ChatCompletionsNativeClient.of(
                server.url("/").toString(), "native-key", configuration);
        NativeStreamCall call = client.chatCompletionStream(chatRequest(), new NativeSseListener() {
            @Override
            public void onEvent(String id, String type, String data) {
            }

            @Override
            public void onFailure(Throwable failure) {
            }
        });

        Assert.assertFalse("an in-flight stream must not report settled",
                call.await(200, TimeUnit.MILLISECONDS));
        call.cancel();
        Assert.assertTrue(call.await(3, TimeUnit.SECONDS));
    }

    @Test
    public void nonStreamCallDeepSnapshotsTheRequestInsteadOfMutatingIt() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"id\":\"chatcmpl-1\",\"choices\":[{\"index\":0,"
                        + "\"message\":{\"role\":\"assistant\",\"content\":\"ok\"},"
                        + "\"finish_reason\":\"stop\"}]}"));

        ChatCompletionsNativeClient client = ChatCompletionsNativeClient.of(
                server.url("/").toString(), "native-key", configuration);
        ChatCompletion request = ChatCompletion.builder()
                .model("gpt-test")
                .stream(true)
                .messages(Collections.singletonList(ChatMessage.withUser("hello")))
                .build();
        ObjectMapper mapper = new ObjectMapper();
        String before = mapper.writeValueAsString(request);

        client.chatCompletion(request);

        Assert.assertEquals("a non-stream call must not rewrite the caller's request",
                before, mapper.writeValueAsString(request));
        Assert.assertEquals(Boolean.TRUE, request.getStream());
        RecordedRequest recorded = server.takeRequest(3, TimeUnit.SECONDS);
        Assert.assertNotNull(recorded);
        Assert.assertTrue("the wire snapshot must force stream=false",
                recorded.getBody().readUtf8().contains("\"stream\":false"));
    }

    @Test
    public void streamingCallDeepSnapshotsTheRequestInsteadOfMutatingIt() throws Exception {
        server.enqueue(sseResponse("data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"ok\"}}]}\n\n" + "data: [DONE]\n\n"));

        ChatCompletionsNativeClient client = ChatCompletionsNativeClient.of(
                server.url("/").toString(), "native-key", configuration);
        ChatCompletion request = ChatCompletion.builder()
                .model("gpt-test")
                .messages(Collections.singletonList(ChatMessage.withUser("hello")))
                .build();
        ObjectMapper mapper = new ObjectMapper();
        String before = mapper.writeValueAsString(request);

        NativeStreamCall call = client.chatCompletionStream(request, new NativeSseListener() {
            @Override
            public void onEvent(String id, String type, String data) {
            }

            @Override
            public void onFailure(Throwable failure) {
            }
        });
        call.await();

        Assert.assertEquals(ModelCallStatus.COMPLETED, call.status());
        Assert.assertEquals("a streaming call must not rewrite the caller's request",
                before, mapper.writeValueAsString(request));
        Assert.assertEquals(Boolean.FALSE, request.getStream());
        RecordedRequest recorded = server.takeRequest(3, TimeUnit.SECONDS);
        Assert.assertNotNull(recorded);
        Assert.assertTrue(recorded.getBody().readUtf8().contains("\"stream\":true"));
    }

    private static ChatCompletion chatRequest() {
        return ChatCompletion.builder()
                .model("gpt-test")
                .messages(Collections.singletonList(ChatMessage.withUser("hello")))
                .build();
    }

    private static MockResponse sseResponse(String body) {
        return new MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(body);
    }
}

