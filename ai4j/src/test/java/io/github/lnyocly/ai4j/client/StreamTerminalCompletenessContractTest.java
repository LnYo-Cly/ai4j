package io.github.lnyocly.ai4j.client;

import io.github.lnyocly.ai4j.model.Message;
import io.github.lnyocly.ai4j.model.ModelCallStatus;
import io.github.lnyocly.ai4j.model.ModelEvent;
import io.github.lnyocly.ai4j.model.ModelEventListener;
import io.github.lnyocly.ai4j.model.ModelEventType;
import io.github.lnyocly.ai4j.model.ModelRequest;
import io.github.lnyocly.ai4j.model.ModelResult;
import io.github.lnyocly.ai4j.model.Protocol;
import io.github.lnyocly.ai4j.service.Configuration;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Whole-call terminal completeness for the shared kernel over all three public
 * protocol entry configurations.
 *
 * <p>HTTP EOF alone must never synthesize a successful {@code DONE}/
 * {@link ModelResult}. Only the protocol's own whole-call terminal completes a
 * stream: chat {@code [DONE]}, Messages {@code message_stop}, Responses
 * {@code response.completed}. A chat {@code finish_reason} is per-choice
 * evidence, not a whole-call terminal, and item/block stop is not one either.</p>
 */
public class StreamTerminalCompletenessContractTest {

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
    public void chatTruncatedStreamWithoutDoneIsNotCompleted() throws Exception {
        server.enqueue(sseResponse(
                "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Hi\"}}]}\n\n"));

        List<ModelEvent> events = new CopyOnWriteArrayList<ModelEvent>();
        ModelCall call = client(Protocol.CHAT_COMPLETIONS)
                .createStream(request(Protocol.CHAT_COMPLETIONS, "test-model"), recording(events));

        ModelCallException failure = awaitFailure(call);
        Assert.assertEquals(ModelCallStatus.FAILED, call.status());
        Assert.assertEquals(ModelCallStatus.FAILED, failure.getStatus());
        assertNoDone(events);
    }

    @Test
    public void chatFinishReasonWithoutDoneIsNotWholeCallCompletion() throws Exception {
        server.enqueue(sseResponse(
                "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Hi\"},"
                        + "\"finish_reason\":\"stop\"}]}\n\n"));

        List<ModelEvent> events = new CopyOnWriteArrayList<ModelEvent>();
        ModelCall call = client(Protocol.CHAT_COMPLETIONS)
                .createStream(request(Protocol.CHAT_COMPLETIONS, "test-model"), recording(events));

        awaitFailure(call);
        Assert.assertEquals(ModelCallStatus.FAILED, call.status());
        assertNoDone(events);
    }

    @Test
    public void messagesTruncatedStreamWithoutMessageStopIsNotCompleted() throws Exception {
        server.enqueue(sseResponse(
                "event: message_start\n"
                        + "data: {\"type\":\"message_start\",\"message\":{\"id\":\"msg_1\","
                        + "\"role\":\"assistant\"}}\n\n"
                        + "event: content_block_delta\n"
                        + "data: {\"type\":\"content_block_delta\",\"index\":0,"
                        + "\"delta\":{\"type\":\"text_delta\",\"text\":\"Hi\"}}\n\n"
                        + "event: content_block_stop\n"
                        + "data: {\"type\":\"content_block_stop\",\"index\":0}\n\n"
                        + "event: message_delta\n"
                        + "data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"}}\n\n"));

        List<ModelEvent> events = new CopyOnWriteArrayList<ModelEvent>();
        ModelCall call = client(Protocol.MESSAGES)
                .createStream(request(Protocol.MESSAGES, "claude-test"), recording(events));

        awaitFailure(call);
        Assert.assertEquals(ModelCallStatus.FAILED, call.status());
        assertNoDone(events);
    }

    @Test
    public void responsesTruncatedStreamWithoutCompletedIsNotCompleted() throws Exception {
        server.enqueue(sseResponse(
                "event: response.created\n"
                        + "data: {\"type\":\"response.created\",\"response\":{\"id\":\"resp_1\"}}\n\n"
                        + "event: response.output_text.delta\n"
                        + "data: {\"type\":\"response.output_text.delta\",\"delta\":\"Hi\"}\n\n"
                        + "event: response.output_item.done\n"
                        + "data: {\"type\":\"response.output_item.done\",\"output_index\":0,"
                        + "\"item\":{\"id\":\"msg_1\",\"type\":\"message\",\"status\":\"completed\"}}\n\n"));

        List<ModelEvent> events = new CopyOnWriteArrayList<ModelEvent>();
        ModelCall call = client(Protocol.RESPONSES)
                .createStream(request(Protocol.RESPONSES, "test-model"), recording(events));

        awaitFailure(call);
        Assert.assertEquals(ModelCallStatus.FAILED, call.status());
        assertNoDone(events);
    }

    @Test
    public void validChatDoneStillCompletes() throws Exception {
        server.enqueue(sseResponse(
                "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Hi\"}}]}\n\n"
                        + "data: [DONE]\n\n"));

        ModelCall call = client(Protocol.CHAT_COMPLETIONS)
                .createStream(request(Protocol.CHAT_COMPLETIONS, "test-model"), noop());
        ModelResult result = call.await();

        Assert.assertEquals(ModelCallStatus.COMPLETED, call.status());
        Assert.assertNotNull(result);
    }

    @Test
    public void validMessagesStopStillCompletes() throws Exception {
        server.enqueue(sseResponse(
                "event: message_start\n"
                        + "data: {\"type\":\"message_start\",\"message\":{\"id\":\"msg_1\","
                        + "\"role\":\"assistant\"}}\n\n"
                        + "event: message_stop\n"
                        + "data: {\"type\":\"message_stop\"}\n\n"));

        ModelCall call = client(Protocol.MESSAGES)
                .createStream(request(Protocol.MESSAGES, "claude-test"), noop());
        ModelResult result = call.await();

        Assert.assertEquals(ModelCallStatus.COMPLETED, call.status());
        Assert.assertNotNull(result);
    }

    @Test
    public void validResponsesCompletedStillCompletes() throws Exception {
        server.enqueue(sseResponse(
                "event: response.created\n"
                        + "data: {\"type\":\"response.created\",\"response\":{\"id\":\"resp_1\"}}\n\n"
                        + "event: response.completed\n"
                        + "data: {\"type\":\"response.completed\",\"response\":{\"id\":\"resp_1\","
                        + "\"status\":\"completed\"}}\n\n"));

        ModelCall call = client(Protocol.RESPONSES)
                .createStream(request(Protocol.RESPONSES, "test-model"), noop());
        ModelResult result = call.await();

        Assert.assertEquals(ModelCallStatus.COMPLETED, call.status());
        Assert.assertNotNull(result);
    }

    private ModelClient client(Protocol protocol) {
        return ModelClients.of(
                TestFixtures.endpoint(protocol, server.url("/").toString()), configuration);
    }

    private static ModelRequest request(Protocol protocol, String model) {
        return ModelRequest.builder()
                .protocol(protocol)
                .model(model)
                .message(Message.user().text("hello").build())
                .execution(TestFixtures.fastTimeouts())
                .build();
    }

    private static ModelCallException awaitFailure(ModelCall call) throws Exception {
        try {
            call.await();
            Assert.fail("a truncated stream must not return a successful result");
            return null;
        } catch (ModelCallException expected) {
            return expected;
        }
    }

    private static void assertNoDone(List<ModelEvent> events) {
        Assert.assertFalse("a truncated stream must not dispatch a synthetic DONE terminal",
                events.stream().anyMatch(event -> event.getType() == ModelEventType.DONE));
    }

    private static ModelEventListener recording(final List<ModelEvent> events) {
        return new ModelEventListener() {
            @Override
            public void onEvent(ModelEvent event) {
                events.add(event);
            }
        };
    }

    private static ModelEventListener noop() {
        return new ModelEventListener() {
            @Override
            public void onEvent(ModelEvent event) {
            }
        };
    }

    private static MockResponse sseResponse(String body) {
        return new MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(body);
    }
}
