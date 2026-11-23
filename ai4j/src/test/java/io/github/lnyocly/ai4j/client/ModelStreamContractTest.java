package io.github.lnyocly.ai4j.client;

import io.github.lnyocly.ai4j.model.CallExecutionOptions;
import io.github.lnyocly.ai4j.model.Message;
import io.github.lnyocly.ai4j.model.ModelCallStatus;
import io.github.lnyocly.ai4j.model.ModelEvent;
import io.github.lnyocly.ai4j.model.ModelEventListener;
import io.github.lnyocly.ai4j.model.ModelEventType;
import io.github.lnyocly.ai4j.model.ModelRequest;
import io.github.lnyocly.ai4j.model.ModelResult;
import io.github.lnyocly.ai4j.model.Protocol;
import io.github.lnyocly.ai4j.model.ToolCallItem;
import io.github.lnyocly.ai4j.service.Configuration;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Streaming contract tests over a local {@code MockWebServer} SSE fixture.
 *
 * <p>Negative controls covered here: cancel closes the upstream stream
 * (design 8.4) and the executor counter stays at zero while streaming
 * (design C6).</p>
 */
public class ModelStreamContractTest {

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
    public void chatStreamAggregatesTextAndReturnsToolCallsAsData() throws Exception {
        RecordingFunction.INVOCATIONS.set(0);
        server.enqueue(sseResponse(
                "data: {\"id\":\"chatcmpl-1\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"Hel\"}}]}\n\n"
                        + "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"lo\"}}]}\n\n"
                        + "data: {\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_1\","
                        + "\"function\":{\"name\":\"audit_recording_function\",\"arguments\":\"{\\\"value\\\"\"}}]}}]}\n\n"
                        + "data: {\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":[{\"index\":0,"
                        + "\"function\":{\"arguments\":\":\\\"x\\\"}\"}}]},\"finish_reason\":\"tool_calls\"}]}\n\n"
                        + "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":3,\"completion_tokens\":2,\"total_tokens\":5}}\n\n"
                        + "data: [DONE]\n\n"));

        ModelClient client = ModelClients.of(
                TestFixtures.endpoint(Protocol.CHAT_COMPLETIONS, server.url("/").toString()),
                configuration);
        List<ModelEvent> events = Collections.synchronizedList(new ArrayList<ModelEvent>());
        CountDownLatch done = new CountDownLatch(1);
        ModelEventListener listener = new ModelEventListener() {
            @Override
            public void onEvent(ModelEvent event) {
                events.add(event);
                if (event.getType() == ModelEventType.DONE) {
                    done.countDown();
                }
            }
        };

        ModelCall call = client.createStream(chatRequest(), listener);
        ModelResult result = call.await();

        Assert.assertTrue("stream must settle", done.await(3, TimeUnit.SECONDS));
        Assert.assertTrue("must receive text deltas", events.stream()
                .anyMatch(e -> e.getType() == ModelEventType.ITEM_DELTA && "Hel".equals(e.getDeltaText())));
        Assert.assertTrue("must receive tool-args deltas", events.stream()
                .anyMatch(e -> e.getType() == ModelEventType.ITEM_DELTA
                        && e.getDeltaKind() == io.github.lnyocly.ai4j.model.EventDeltaKind.TOOL_ARGS));
        Assert.assertEquals(ModelCallStatus.COMPLETED, call.status());
        Assert.assertEquals("kernel must never execute a tool",
                0, RecordingFunction.INVOCATIONS.get());
        Assert.assertNotNull(result);
        Assert.assertEquals(5L, result.getUsage().getTotalTokens());
        Assert.assertTrue("streamed tool call must be returned as an item",
                result.getItems().stream().anyMatch(item -> item instanceof ToolCallItem));
    }

    @Test
    public void messagesStreamConsumesSignatureDeltaWithoutError() throws Exception {
        server.enqueue(sseResponse(
                "event: message_start\n"
                        + "data: {\"type\":\"message_start\",\"message\":{\"id\":\"msg_1\",\"role\":\"assistant\","
                        + "\"usage\":{\"input_tokens\":4,\"output_tokens\":0}}}\n\n"
                        + "event: content_block_start\n"
                        + "data: {\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"thinking\",\"thinking\":\"\"}}\n\n"
                        + "event: content_block_delta\n"
                        + "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"thinking_delta\",\"thinking\":\"why\"}}\n\n"
                        + "event: content_block_delta\n"
                        + "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"signature_delta\",\"signature\":\"sig-abc\"}}\n\n"
                        + "event: content_block_stop\n"
                        + "data: {\"type\":\"content_block_stop\",\"index\":0}\n\n"
                        + "event: message_delta\n"
                        + "data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"},\"usage\":{\"output_tokens\":2}}\n\n"
                        + "event: message_stop\n"
                        + "data: {\"type\":\"message_stop\"}\n\n"));

        ModelClient client = ModelClients.of(
                TestFixtures.endpoint(Protocol.MESSAGES, server.url("/").toString()), configuration);
        List<ModelEvent> events = Collections.synchronizedList(new ArrayList<ModelEvent>());
        ModelCall call = client.createStream(messagesRequest(), new ModelEventListener() {
            @Override
            public void onEvent(ModelEvent event) {
                events.add(event);
            }
        });
        ModelResult result = call.await();

        Assert.assertTrue("signature_delta must be consumed, not dropped",
                events.stream().anyMatch(e -> "signature_delta".equals(e.getItemType())));
        Assert.assertTrue("thinking delta must be surfaced",
                events.stream().anyMatch(e -> e.getDeltaKind()
                        == io.github.lnyocly.ai4j.model.EventDeltaKind.REASONING));
        Assert.assertEquals("end_turn", result.getStopReason());
        Assert.assertEquals(ModelCallStatus.COMPLETED, call.status());
    }

    @Test
    public void cancelClosesTheUpstreamStream() throws Exception {
        AtomicInteger delivered = new AtomicInteger();
        // Headers arrive immediately, the body is held back so the call is still in
        // flight when cancel() runs.
        server.enqueue(sseResponse(
                "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"start\"}}]}\n\n")
                .setBodyDelay(3, TimeUnit.SECONDS));

        ModelClient client = ModelClients.of(
                TestFixtures.endpoint(Protocol.CHAT_COMPLETIONS, server.url("/").toString()),
                configuration);
        ModelCall call = client.createStream(chatRequest(), new ModelEventListener() {
            @Override
            public void onEvent(ModelEvent event) {
                if (event.getType() == ModelEventType.ITEM_DELTA) {
                    delivered.incrementAndGet();
                }
            }
        });

        // Wait until the request is actually in flight: MockWebServer records it as soon as
        // headers arrive, while the body is still held back.
        Assert.assertNotNull("the stream request must reach the server",
                server.takeRequest(3, TimeUnit.SECONDS));
        long startedAt = System.nanoTime();
        call.cancel();
        Assert.assertTrue(call.isCancelled());
        call.await();
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

        Assert.assertEquals("a cancelled call must settle as CANCELLED",
                ModelCallStatus.CANCELLED, call.status());
        Assert.assertEquals("cancel must close the upstream stream before the body arrives",
                0, delivered.get());
        Assert.assertTrue("cancel must not block until the upstream body would have arrived;"
                        + " elapsed=" + elapsedMillis + "ms",
                elapsedMillis < 2_500L);
    }

    private ModelRequest chatRequest() {
        return ModelRequest.builder()
                .model("test-model")
                .message(Message.user().text("hello").build())
                .execution(TestFixtures.fastTimeouts())
                .build();
    }

    private ModelRequest messagesRequest() {
        return ModelRequest.builder()
                .model("claude-test")
                .message(Message.user().text("hello").build())
                .execution(TestFixtures.fastTimeouts())
                .build();
    }

    private static MockResponse sseResponse(String body) {
        return new MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(body);
    }
}
