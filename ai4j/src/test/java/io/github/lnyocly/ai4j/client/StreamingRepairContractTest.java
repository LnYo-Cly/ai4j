package io.github.lnyocly.ai4j.client;

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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stage-2 streaming repair contract: B1-B5 plus terminal-state consistency,
 * driven over a local {@code MockWebServer}. Every assertion here failed on the
 * pre-repair kernel (dispatch 33bbcb832dce77dc9854316a).
 */
public class StreamingRepairContractTest {

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

    // ---- B1: streaming HTTP failure fidelity + retry safety gate ----

    @Test
    public void messagesStream429KeepsProviderFailureFidelity() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(429)
                .setHeader("Content-Type", "application/json")
                .setHeader("request-id", "req_429")
                .setBody("{\"type\":\"error\",\"error\":{\"type\":\"rate_limit_error\","
                        + "\"message\":\"slow down\"}}"));

        ModelCall call = client(Protocol.MESSAGES)
                .createStream(messagesRequest(options(0)), noop());
        ModelCallException error = awaitFailure(call);

        Assert.assertEquals(429, error.getHttpStatus());
        Assert.assertTrue("429 must be classified retryable", error.isRetryable());
        Assert.assertEquals("rate_limit_error", error.getProviderErrorType());
        Assert.assertEquals("req_429", error.getProviderRequestId());
        Assert.assertEquals("slow down", error.getMessage());
        Assert.assertNotNull("raw snippet must be preserved", error.getRawSnippet());
        Assert.assertTrue(error.getRawSnippet().contains("rate_limit_error"));
        Assert.assertEquals(ModelCallStatus.FAILED, error.getStatus());
    }

    @Test
    public void chatStream400IsNotRetried() throws Exception {
        MockResponse bad = new MockResponse()
                .setResponseCode(400)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"error\":{\"type\":\"invalid_request_error\",\"message\":\"bad request\"}}");
        server.enqueue(bad);
        server.enqueue(bad);
        server.enqueue(bad);

        ModelCall call = client(Protocol.CHAT_COMPLETIONS)
                .createStream(chatRequest(options(2)), noop());
        ModelCallException error = awaitFailure(call);

        Assert.assertEquals(400, error.getHttpStatus());
        Assert.assertFalse("400 is not retryable", error.isRetryable());
        Assert.assertEquals("a non-retryable failure must not be resent",
                1, server.getRequestCount());
    }

    @Test
    public void chatStream503IsRetriedWithinBudget() throws Exception {
        MockResponse busy = new MockResponse()
                .setResponseCode(503)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"error\":{\"type\":\"server_error\",\"message\":\"busy\"}}");
        server.enqueue(busy);
        server.enqueue(busy);
        server.enqueue(busy);

        ModelCall call = client(Protocol.CHAT_COMPLETIONS)
                .createStream(chatRequest(options(2)), noop());
        ModelCallException error = awaitFailure(call);

        Assert.assertEquals(503, error.getHttpStatus());
        Assert.assertTrue(error.isRetryable());
        Assert.assertEquals("503 retries until the budget is exhausted",
                3, server.getRequestCount());
    }

    @Test
    public void nonReplayableStreamIsNotResent() throws Exception {
        MockResponse busy = new MockResponse()
                .setResponseCode(503)
                .setBody("{\"error\":{\"message\":\"busy\"}}");
        server.enqueue(busy);
        server.enqueue(busy);

        ModelRequest request = ModelRequest.builder()
                .protocol(Protocol.CHAT_COMPLETIONS)
                .model("test-model")
                .message(Message.user().text("hello").build())
                .execution(options(2))
                .replayable(false)
                .build();
        ModelCall call = client(Protocol.CHAT_COMPLETIONS).createStream(request, noop());
        ModelCallException error = awaitFailure(call);

        Assert.assertEquals(503, error.getHttpStatus());
        Assert.assertEquals("a non-replayable request must not be resent",
                1, server.getRequestCount());
    }

    // ---- B2: RESPONSES one item per output item ----

    @Test
    public void responsesStreamEmitsExactlyOneMessageItem() throws Exception {
        server.enqueue(sseResponse(RESPONSES_MESSAGE_STREAM));

        ModelCall call = client(Protocol.RESPONSES)
                .createStream(responsesRequest(options(0)), noop());
        ModelResult result = call.await();

        Assert.assertEquals(ModelCallStatus.COMPLETED, call.status());
        Assert.assertEquals("one output item must produce exactly one ModelItem",
                1, result.getItems().size());
        Assert.assertTrue(result.getItems().get(0) instanceof Message);
        Message message = (Message) result.getItems().get(0);
        Assert.assertEquals("Hello", message.textContent());
        Assert.assertEquals("msg_1", message.getItemId());
        Assert.assertEquals(5L, result.getUsage().getTotalTokens());
    }

    @Test
    public void responsesStreamEmitsExactlyOneToolCallItem() throws Exception {
        server.enqueue(sseResponse(RESPONSES_FUNCTION_CALL_STREAM));

        ModelCall call = client(Protocol.RESPONSES)
                .createStream(responsesRequest(options(0)), noop());
        ModelResult result = call.await();

        Assert.assertEquals(1, result.getItems().size());
        Assert.assertTrue(result.getItems().get(0) instanceof ToolCallItem);
        ToolCallItem toolCall = (ToolCallItem) result.getItems().get(0);
        Assert.assertEquals("call_1", toolCall.getCallId());
        Assert.assertEquals("get_weather", toolCall.getName());
    }

    // ---- B3: MESSAGES tool_use keeps the function name ----

    @Test
    public void messagesStreamToolUseKeepsFunctionName() throws Exception {
        server.enqueue(sseResponse(MESSAGES_TOOL_USE_STREAM));

        ModelCall call = client(Protocol.MESSAGES)
                .createStream(messagesRequest(options(0)), noop());
        ModelResult result = call.await();

        Assert.assertEquals(1, result.getItems().size());
        Assert.assertTrue(result.getItems().get(0) instanceof ToolCallItem);
        ToolCallItem toolCall = (ToolCallItem) result.getItems().get(0);
        Assert.assertEquals("toolu_1", toolCall.getCallId());
        Assert.assertEquals("the item name must be the function name, not the block type",
                "get_weather", toolCall.getName());
    }

    // ---- B4: CHAT finish_reason and [DONE] ----

    @Test
    public void chatStreamFinishReasonOnSeparateChunk() throws Exception {
        List<ModelEvent> events = Collections.synchronizedList(new ArrayList<ModelEvent>());
        server.enqueue(sseResponse(
                "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Hi\"}}]}\n\n"
                        + "data: {\"choices\":[{\"index\":0,\"finish_reason\":\"stop\"}]}\n\n"
                        + "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":1,"
                        + "\"completion_tokens\":1,\"total_tokens\":2}}\n\n"
                        + "data: [DONE]\n\n"));

        ModelCall call = client(Protocol.CHAT_COMPLETIONS)
                .createStream(chatRequest(options(0)), recording(events));
        ModelResult result = call.await();

        Assert.assertEquals("stop", result.getStopReason());
        Assert.assertEquals(ModelCallStatus.COMPLETED, call.status());
        assertNoDecodeError(events);
        Assert.assertEquals("Hi", ((Message) result.getItems().get(0)).textContent());
    }

    @Test
    public void chatStreamFinishReasonOnContentChunk() throws Exception {
        List<ModelEvent> events = Collections.synchronizedList(new ArrayList<ModelEvent>());
        server.enqueue(sseResponse(
                "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Hi\"},"
                        + "\"finish_reason\":\"stop\"}]}\n\n"
                        + "data: [DONE]\n\n"));

        ModelCall call = client(Protocol.CHAT_COMPLETIONS)
                .createStream(chatRequest(options(0)), recording(events));
        ModelResult result = call.await();

        Assert.assertEquals("stop", result.getStopReason());
        Assert.assertEquals(ModelCallStatus.COMPLETED, call.status());
        assertNoDecodeError(events);
        Assert.assertEquals("Hi", ((Message) result.getItems().get(0)).textContent());
    }

    // ---- terminal consistency ----

    @Test
    public void chatStreamDispatchesExactlyOneDoneBeforeAwait() throws Exception {
        List<ModelEvent> events = Collections.synchronizedList(new ArrayList<ModelEvent>());
        CountDownLatch done = new CountDownLatch(1);
        server.enqueue(sseResponse(CHAT_TEXT_STREAM));

        ModelCall call = client(Protocol.CHAT_COMPLETIONS)
                .createStream(chatRequest(options(0)), new ModelEventListener() {
                    @Override
                    public void onEvent(ModelEvent event) {
                        events.add(event);
                        if (event.getType() == ModelEventType.DONE) {
                            done.countDown();
                        }
                    }
                });

        Assert.assertTrue("DONE must be dispatched without calling await()",
                done.await(3, TimeUnit.SECONDS));
        Assert.assertEquals("exactly one DONE event", 1, countDone(events));
        call.await();
        Assert.assertEquals("await() must not synthesize a second DONE", 1, countDone(events));
    }

    @Test
    public void midStreamErrorEventSettlesFailedConsistently() throws Exception {
        CountDownLatch errorSeen = new CountDownLatch(1);
        server.enqueue(sseResponse(
                "event: message_start\n"
                        + "data: {\"type\":\"message_start\",\"message\":{\"id\":\"msg_1\","
                        + "\"role\":\"assistant\"}}\n\n"
                        + "event: error\n"
                        + "data: {\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\","
                        + "\"message\":\"boom\"}}\n\n"));

        ModelCall call = client(Protocol.MESSAGES)
                .createStream(messagesRequest(options(0)), new ModelEventListener() {
                    @Override
                    public void onEvent(ModelEvent event) {
                        if (event.getType() == ModelEventType.ERROR) {
                            errorSeen.countDown();
                        }
                    }
                });

        Assert.assertTrue(errorSeen.await(3, TimeUnit.SECONDS));
        Assert.assertEquals("an in-stream ERROR must settle FAILED", ModelCallStatus.FAILED,
                call.status());
        try {
            call.await();
            Assert.fail("a failed stream must throw");
        } catch (ModelCallException expected) {
            Assert.assertEquals(ModelCallStatus.FAILED, expected.getStatus());
        }
    }

    // ---- B5: cancel before the request is sent ----

    @Test
    public void cancelBeforeStartNeverReachesTheServer() throws Exception {
        server.enqueue(sseResponse(CHAT_TEXT_STREAM));

        ModelCall call = client(Protocol.CHAT_COMPLETIONS)
                .createStream(chatRequest(options(0)), noop());
        call.cancel();

        Assert.assertTrue(call.isCancelled());
        call.await();
        Assert.assertEquals(ModelCallStatus.CANCELLED, call.status());
        Assert.assertEquals("a call cancelled before start must not reach the server",
                0, server.getRequestCount());
    }

    // ---- fixtures ----

    private static final String CHAT_TEXT_STREAM =
            "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Hi\"}}]}\n\n"
                    + "data: [DONE]\n\n";

    private static final String RESPONSES_MESSAGE_STREAM =
            "event: response.created\n"
                    + "data: {\"type\":\"response.created\",\"response\":{\"id\":\"resp_1\"}}\n\n"
                    + "event: response.output_item.added\n"
                    + "data: {\"type\":\"response.output_item.added\",\"output_index\":0,"
                    + "\"item_id\":\"msg_1\",\"item\":{\"id\":\"msg_1\",\"type\":\"message\","
                    + "\"status\":\"in_progress\",\"role\":\"assistant\",\"content\":[]}}\n\n"
                    + "event: response.output_text.delta\n"
                    + "data: {\"type\":\"response.output_text.delta\",\"item_id\":\"msg_1\","
                    + "\"output_index\":0,\"content_index\":0,\"delta\":\"Hello\"}\n\n"
                    + "event: response.output_text.done\n"
                    + "data: {\"type\":\"response.output_text.done\",\"item_id\":\"msg_1\","
                    + "\"output_index\":0,\"content_index\":0,\"text\":\"Hello\"}\n\n"
                    + "event: response.output_item.done\n"
                    + "data: {\"type\":\"response.output_item.done\",\"output_index\":0,"
                    + "\"item\":{\"id\":\"msg_1\",\"type\":\"message\",\"status\":\"completed\","
                    + "\"role\":\"assistant\",\"content\":[{\"type\":\"output_text\","
                    + "\"text\":\"Hello\",\"annotations\":[]}]}}\n\n"
                    + "event: response.completed\n"
                    + "data: {\"type\":\"response.completed\",\"response\":{\"id\":\"resp_1\","
                    + "\"usage\":{\"input_tokens\":3,\"output_tokens\":2,\"total_tokens\":5}}}\n\n";

    private static final String RESPONSES_FUNCTION_CALL_STREAM =
            "event: response.created\n"
                    + "data: {\"type\":\"response.created\",\"response\":{\"id\":\"resp_1\"}}\n\n"
                    + "event: response.output_item.added\n"
                    + "data: {\"type\":\"response.output_item.added\",\"output_index\":0,"
                    + "\"item\":{\"id\":\"fc_1\",\"type\":\"function_call\","
                    + "\"call_id\":\"call_1\",\"name\":\"get_weather\",\"arguments\":\"\"}}\n\n"
                    + "event: response.function_call_arguments.delta\n"
                    + "data: {\"type\":\"response.function_call_arguments.delta\","
                    + "\"item_id\":\"fc_1\",\"output_index\":0,\"delta\":\"{\\\"city\\\"\"}\n\n"
                    + "event: response.function_call_arguments.delta\n"
                    + "data: {\"type\":\"response.function_call_arguments.delta\","
                    + "\"item_id\":\"fc_1\",\"output_index\":0,\"delta\":\":\\\"SF\\\"}\"}\n\n"
                    + "event: response.output_item.done\n"
                    + "data: {\"type\":\"response.output_item.done\",\"output_index\":0,"
                    + "\"item\":{\"id\":\"fc_1\",\"type\":\"function_call\","
                    + "\"call_id\":\"call_1\",\"name\":\"get_weather\","
                    + "\"arguments\":\"{\\\"city\\\":\\\"SF\\\"}\"}}\n\n"
                    + "event: response.completed\n"
                    + "data: {\"type\":\"response.completed\",\"response\":{\"id\":\"resp_1\","
                    + "\"usage\":{\"input_tokens\":3,\"output_tokens\":2,\"total_tokens\":5}}}\n\n";

    private static final String MESSAGES_TOOL_USE_STREAM =
            "event: message_start\n"
                    + "data: {\"type\":\"message_start\",\"message\":{\"id\":\"msg_1\","
                    + "\"role\":\"assistant\",\"usage\":{\"input_tokens\":4,\"output_tokens\":0}}}\n\n"
                    + "event: content_block_start\n"
                    + "data: {\"type\":\"content_block_start\",\"index\":0,"
                    + "\"content_block\":{\"type\":\"tool_use\",\"id\":\"toolu_1\","
                    + "\"name\":\"get_weather\",\"input\":{}}}\n\n"
                    + "event: content_block_delta\n"
                    + "data: {\"type\":\"content_block_delta\",\"index\":0,"
                    + "\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"{\\\"city\\\"\"}}\n\n"
                    + "event: content_block_delta\n"
                    + "data: {\"type\":\"content_block_delta\",\"index\":0,"
                    + "\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\":\\\"SF\\\"}\"}}\n\n"
                    + "event: content_block_stop\n"
                    + "data: {\"type\":\"content_block_stop\",\"index\":0}\n\n"
                    + "event: message_delta\n"
                    + "data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"tool_use\"},"
                    + "\"usage\":{\"output_tokens\":2}}\n\n"
                    + "event: message_stop\n"
                    + "data: {\"type\":\"message_stop\"}\n\n";

    private ModelClient client(Protocol protocol) {
        return ModelClients.of(
                TestFixtures.endpoint(protocol, server.url("/").toString()), configuration);
    }

    private ModelRequest chatRequest(io.github.lnyocly.ai4j.model.CallExecutionOptions execution) {
        return baseRequest(Protocol.CHAT_COMPLETIONS, "test-model", execution).build();
    }

    private ModelRequest messagesRequest(io.github.lnyocly.ai4j.model.CallExecutionOptions execution) {
        return baseRequest(Protocol.MESSAGES, "claude-test", execution).build();
    }

    private ModelRequest responsesRequest(io.github.lnyocly.ai4j.model.CallExecutionOptions execution) {
        return baseRequest(Protocol.RESPONSES, "test-model", execution).build();
    }

    private static ModelRequest.Builder baseRequest(Protocol protocol, String model,
                                                    io.github.lnyocly.ai4j.model.CallExecutionOptions execution) {
        return ModelRequest.builder()
                .protocol(protocol)
                .model(model)
                .message(Message.user().text("hello").build())
                .execution(execution);
    }

    private static io.github.lnyocly.ai4j.model.CallExecutionOptions options(int maxRetries) {
        return io.github.lnyocly.ai4j.model.CallExecutionOptions.builder()
                .firstTokenTimeoutMs(2_000)
                .idleTimeoutMs(2_000)
                .maxRetries(maxRetries)
                .build();
    }

    private static ModelEventListener noop() {
        return new ModelEventListener() {
            @Override
            public void onEvent(ModelEvent event) {
            }
        };
    }

    private static ModelEventListener recording(final List<ModelEvent> events) {
        return new ModelEventListener() {
            @Override
            public void onEvent(ModelEvent event) {
                events.add(event);
            }
        };
    }

    private static ModelCallException awaitFailure(ModelCall call) throws InterruptedException {
        try {
            call.await();
            Assert.fail("stream must fail");
            return null;
        } catch (ModelCallException expected) {
            return expected;
        }
    }

    private static void assertNoDecodeError(List<ModelEvent> events) {
        for (ModelEvent event : events) {
            Assert.assertFalse("[DONE] must not degrade into a decode error",
                    event.getType() == ModelEventType.OPAQUE
                            && "decode_error".equals(event.getItemType()));
        }
    }

    private static int countDone(List<ModelEvent> events) {
        int count = 0;
        for (ModelEvent event : events) {
            if (event.getType() == ModelEventType.DONE) {
                count++;
            }
        }
        return count;
    }

    private static MockResponse sseResponse(String body) {
        return new MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(body);
    }
}
