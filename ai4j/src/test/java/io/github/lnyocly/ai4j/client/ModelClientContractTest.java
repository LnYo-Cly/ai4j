package io.github.lnyocly.ai4j.client;

import io.github.lnyocly.ai4j.model.CallExecutionOptions;
import io.github.lnyocly.ai4j.model.ImageBlock;
import io.github.lnyocly.ai4j.model.MediaRef;
import io.github.lnyocly.ai4j.model.Message;
import io.github.lnyocly.ai4j.model.ModelCallStatus;
import io.github.lnyocly.ai4j.model.ModelItem;
import io.github.lnyocly.ai4j.model.ModelRequest;
import io.github.lnyocly.ai4j.model.ModelResult;
import io.github.lnyocly.ai4j.model.Protocol;
import io.github.lnyocly.ai4j.model.TextBlock;
import io.github.lnyocly.ai4j.model.ToolCallItem;
import io.github.lnyocly.ai4j.model.ToolDeclaration;
import io.github.lnyocly.ai4j.protocol.Endpoint;
import io.github.lnyocly.ai4j.protocol.UnsupportedCapabilityException;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Single-turn contract tests shared by all three protocols.
 *
 * <p>Negative controls covered here: the executor counter stays at zero
 * (design C6), input requests are not mutated by encode (C11), and
 * {@code applicationMetadata} never appears on the wire (C13).</p>
 */
public class ModelClientContractTest {

    private static final String CHAT_TOOL_CALL_BODY = "{"
            + "\"id\":\"chatcmpl-1\","
            + "\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"\","
            + "\"tool_calls\":[{\"id\":\"call_1\",\"type\":\"function\","
            + "\"function\":{\"name\":\"audit_recording_function\",\"arguments\":\"{\\\"value\\\":\\\"x\\\"}\"}}]},"
            + "\"finish_reason\":\"tool_calls\"}],"
            + "\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":5,\"total_tokens\":15}}";

    private static final String RESPONSES_TOOL_CALL_BODY = "{"
            + "\"id\":\"resp_1\",\"status\":\"completed\","
            + "\"output\":[{\"type\":\"function_call\",\"call_id\":\"call_1\","
            + "\"name\":\"audit_recording_function\",\"arguments\":\"{\\\"value\\\":\\\"x\\\"}\"}],"
            + "\"usage\":{\"input_tokens\":10,\"output_tokens\":5,\"total_tokens\":15}}";

    private static final String MESSAGES_TOOL_CALL_BODY = "{"
            + "\"id\":\"msg_1\",\"stop_reason\":\"tool_use\","
            + "\"content\":[{\"type\":\"tool_use\",\"id\":\"toolu_1\","
            + "\"name\":\"audit_recording_function\",\"input\":{\"value\":\"x\"}}],"
            + "\"usage\":{\"input_tokens\":10,\"output_tokens\":5}}";

    @Test
    public void chatCompletionsSingleTurnReturnsToolCallWithoutExecutingIt() throws Exception {
        RecordingFunction.INVOCATIONS.set(0);
        TestFixtures.StaticTransport transport =
                new TestFixtures.StaticTransport(200, CHAT_TOOL_CALL_BODY);
        ModelClient client = ModelClients.of(
                TestFixtures.endpoint(Protocol.CHAT_COMPLETIONS, "https://example.test"), transport);

        ModelResult result = client.create(toolRequest(Protocol.CHAT_COMPLETIONS));

        Assert.assertNotNull(result);
        Assert.assertEquals("tool_calls", result.getStopReason());
        ModelItem toolCall = result.getItems().stream()
                .filter(item -> item instanceof ToolCallItem)
                .findFirst().orElse(null);
        Assert.assertNotNull("tool call must be returned as data", toolCall);
        Assert.assertEquals("call_1", ((ToolCallItem) toolCall).getCallId());
        Assert.assertEquals("audit_recording_function", ((ToolCallItem) toolCall).getName());
        Assert.assertEquals(15L, result.getUsage().getTotalTokens());
        Assert.assertEquals("kernel must never execute a tool",
                0, RecordingFunction.INVOCATIONS.get());
        Assert.assertEquals(1, transport.executions.get());
    }

    @Test
    public void responsesSingleTurnReturnsToolCallWithoutExecutingIt() throws Exception {
        RecordingFunction.INVOCATIONS.set(0);
        TestFixtures.StaticTransport transport =
                new TestFixtures.StaticTransport(200, RESPONSES_TOOL_CALL_BODY);
        ModelClient client = ModelClients.of(
                TestFixtures.endpoint(Protocol.RESPONSES, "https://example.test"), transport);

        ModelResult result = client.create(toolRequest(Protocol.RESPONSES));

        Assert.assertEquals("completed", result.getStopReason());
        Assert.assertEquals("call_1",
                ((ToolCallItem) result.getItems().get(0)).getCallId());
        Assert.assertEquals("kernel must never execute a tool",
                0, RecordingFunction.INVOCATIONS.get());
    }

    @Test
    public void messagesSingleTurnReturnsToolCallWithoutExecutingIt() throws Exception {
        RecordingFunction.INVOCATIONS.set(0);
        TestFixtures.StaticTransport transport =
                new TestFixtures.StaticTransport(200, MESSAGES_TOOL_CALL_BODY);
        ModelClient client = ModelClients.of(
                TestFixtures.endpoint(Protocol.MESSAGES, "https://example.test"), transport);

        ModelResult result = client.create(toolRequest(Protocol.MESSAGES));

        Assert.assertEquals("tool_use", result.getStopReason());
        ToolCallItem toolCall = (ToolCallItem) result.getItems().get(0);
        Assert.assertEquals("toolu_1", toolCall.getCallId());
        Assert.assertEquals("{\"value\":\"x\"}", toolCall.getArgumentsJson());
        Assert.assertEquals("kernel must never execute a tool",
                0, RecordingFunction.INVOCATIONS.get());
    }

    @Test
    public void applicationMetadataNeverAppearsOnTheWireForAnyProtocol() throws Exception {
        for (Protocol protocol : Protocol.values()) {
            TestFixtures.StaticTransport transport =
                    new TestFixtures.StaticTransport(200, CHAT_TOOL_CALL_BODY);
            ModelClient client = ModelClients.of(
                    TestFixtures.endpoint(protocol, "https://example.test"), transport);

            Map<String, Object> metadata = new LinkedHashMap<String, Object>();
            metadata.put("traceId", "t-20261003-1");
            metadata.put("secretBusinessKey", "must-not-leave-the-process");

            ModelRequest request = ModelRequest.builder()
                    .model("deepseek-chat")
                    .message(Message.user().text("hello").build())
                    .tool(ToolDeclaration.function("audit_recording_function")
                            .parameters(Collections.<String, Object>emptyMap())
                            .build())
                    .applicationMetadata(metadata)
                    .execution(TestFixtures.fastTimeouts())
                    .build();

            // The stream path returns a handle, so use the blocking path which reuses the
            // same encoder and let the transport capture the wire body.
            try {
                client.create(request);
            } catch (ModelCallException ignored) {
                // Response decoding is irrelevant here; we only inspect the encoded wire body.
            }
            String wireJson = io.github.lnyocly.ai4j.protocol.JsonWire.write(
                    transport.lastRequest.getWireRequest().getBody());
            Assert.assertFalse(protocol + " wire body must not contain applicationMetadata: " + wireJson,
                    wireJson.contains("must-not-leave-the-process"));
            Assert.assertFalse(protocol + " wire body must not contain the metadata key: " + wireJson,
                    wireJson.contains("traceId"));
        }
    }

    @Test
    public void encodeDoesNotMutateTheInputRequest() throws Exception {
        TestFixtures.StaticTransport transport =
                new TestFixtures.StaticTransport(200, CHAT_TOOL_CALL_BODY);
        ModelClient client = ModelClients.of(
                TestFixtures.endpoint(Protocol.CHAT_COMPLETIONS, "https://example.test"), transport);

        Map<String, Object> metadata = new LinkedHashMap<String, Object>();
        metadata.put("traceId", "t-1");
        Message message = Message.user()
                .text("hello")
                .block(ImageBlock.of(MediaRef.url("https://example.test/a.png")))
                .build();
        ModelRequest request = ModelRequest.builder()
                .model("deepseek-chat")
                .message(message)
                .tool(ToolDeclaration.function("audit_recording_function")
                        .parameters(Collections.<String, Object>emptyMap())
                        .build())
                .applicationMetadata(metadata)
                .execution(TestFixtures.fastTimeouts())
                .build();

        client.create(request);

        Assert.assertEquals(2, message.getBlocks().size());
        Assert.assertTrue(message.getBlocks().get(0) instanceof TextBlock);
        Assert.assertTrue(message.getBlocks().get(1) instanceof ImageBlock);
        Assert.assertEquals("hello", ((TextBlock) message.getBlocks().get(0)).getText());
        Assert.assertEquals("t-1", request.getApplicationMetadata().get("traceId"));
        Assert.assertEquals("deepseek-chat", request.getModel());
        Assert.assertEquals(1, request.getTools().size());
    }

    @Test
    public void unsupportedCapabilityFailsFastWithTheDeclaredMatrix() {
        ModelClient messages = ModelClients.of(
                TestFixtures.endpoint(Protocol.MESSAGES, "https://example.test"),
                new TestFixtures.StaticTransport(200, "{}"));
        try {
            messages.require(io.github.lnyocly.ai4j.protocol.Capability.PARALLEL_TOOL_CALLS);
            Assert.fail("Messages has no parallel tool call flag; require must fail fast");
        } catch (UnsupportedCapabilityException expected) {
            Assert.assertEquals(Protocol.MESSAGES, expected.getProtocol());
        }

        ModelClient chat = ModelClients.of(
                TestFixtures.endpoint(Protocol.CHAT_COMPLETIONS, "https://example.test"),
                new TestFixtures.StaticTransport(200, "{}"));
        chat.require(io.github.lnyocly.ai4j.protocol.Capability.PARALLEL_TOOL_CALLS);
    }

    @Test
    public void httpErrorMapsToModelCallExceptionWithRetryability() {
        Map<String, String> headers = new LinkedHashMap<String, String>();
        headers.put("request-id", "req_123");
        TestFixtures.StaticTransport transport = new TestFixtures.StaticTransport(429,
                "{\"error\":{\"type\":\"rate_limit_error\",\"message\":\"slow down\"}}", headers);
        ModelClient client = ModelClients.of(
                TestFixtures.endpoint(Protocol.MESSAGES, "https://example.test"), transport);

        try {
            client.create(ModelRequest.builder()
                    .model("claude-test")
                    .message(Message.user().text("hi").build())
                    .execution(TestFixtures.fastTimeouts())
                    .build());
            Assert.fail("429 must surface as ModelCallException");
        } catch (ModelCallException expected) {
            Assert.assertEquals(429, expected.getHttpStatus());
            Assert.assertTrue(expected.isRetryable());
            Assert.assertEquals(Protocol.MESSAGES, expected.getProtocol());
            Assert.assertEquals("rate_limit_error", expected.getProviderErrorType());
            Assert.assertEquals("req_123", expected.getProviderRequestId());
            Assert.assertEquals(ModelCallStatus.FAILED, expected.getStatus());
        }
    }

    private static ModelRequest toolRequest(Protocol protocol) {
        return ModelRequest.builder()
                .model("test-model")
                .message(Message.user().text("run the tool").build())
                .tool(ToolDeclaration.function("audit_recording_function")
                        .description("Records invocation for the kernel test")
                        .parameters(Collections.<String, Object>emptyMap())
                        .build())
                .execution(TestFixtures.fastTimeouts())
                .protocol(protocol)
                .build();
    }
}
