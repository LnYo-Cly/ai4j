package io.github.lnyocly.ai4j.protocol;

import io.github.lnyocly.ai4j.model.CallExecutionOptions;
import io.github.lnyocly.ai4j.model.Message;
import io.github.lnyocly.ai4j.model.ModelEvent;
import io.github.lnyocly.ai4j.model.ModelEventType;
import io.github.lnyocly.ai4j.model.ModelRequest;
import io.github.lnyocly.ai4j.model.ModelResult;
import io.github.lnyocly.ai4j.model.Protocol;
import io.github.lnyocly.ai4j.model.ReasoningBlock;
import io.github.lnyocly.ai4j.model.TextBlock;
import io.github.lnyocly.ai4j.model.ToolCallItem;
import io.github.lnyocly.ai4j.model.ToolDeclaration;
import io.github.lnyocly.ai4j.model.ToolKind;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Codec-level contract tests for the three wire protocols.
 *
 * <p>These assert the design 5.1 invariants at the encode boundary: read-only
 * encode, explicit losses, metadata never on the wire, and protocol shape.</p>
 */
public class CodecContractTest {

    private static final String RESPONSES_SSE_COMPLETED = "{\"type\":\"response.completed\","
            + "\"sequence_number\":42,\"response\":{\"id\":\"resp_1\",\"status\":\"completed\","
            + "\"usage\":{\"input_tokens\":10,\"output_tokens\":5,\"total_tokens\":15}}}";

    @Test
    public void chatCodecEncodesOpenAiShapeAndKeepsMetadataOffTheWire() {
        ProtocolCodec codec = ProtocolDescriptors.of(Protocol.CHAT_COMPLETIONS).getCodec();
        EndpointPolicy policy = policy(Protocol.CHAT_COMPLETIONS);
        ModelRequest request = ModelRequest.builder()
                .model("deepseek-chat")
                .message(Message.system().text("be brief").build())
                .message(Message.user().text("hello").build())
                .tool(ToolDeclaration.function("get_weather")
                        .description("weather")
                        .parameters(Collections.<String, Object>singletonMap("type", "object"))
                        .strict(Boolean.TRUE)
                        .build())
                .maxOutputTokens(128)
                .applicationMetadata(Collections.<String, Object>singletonMap("traceId", "t1"))
                .execution(CallExecutionOptions.defaults())
                .build();

        WireRequest wire = codec.encode(request, policy);
        String json = JsonWire.write(wire.getBody());

        Assert.assertEquals("POST", wire.getMethod());
        Assert.assertEquals("/chat/completions", wire.getPath());
        Assert.assertTrue("system message must stay a message on chat",
                json.contains("\"role\":\"system\""));
        Assert.assertTrue("tools must use the nested function shape",
                json.contains("\"function\":{\"name\":\"get_weather\""));
        Assert.assertTrue(json.contains("\"max_tokens\":128"));
        Assert.assertFalse("applicationMetadata must never reach the wire",
                json.contains("traceId"));
    }

    @Test
    public void responsesCodecLiftsFirstSystemMessageIntoInstructions() {
        ProtocolCodec codec = ProtocolDescriptors.of(Protocol.RESPONSES).getCodec();
        EndpointPolicy policy = policy(Protocol.RESPONSES);
        ModelRequest request = ModelRequest.builder()
                .model("gpt-test")
                .message(Message.system().text("be brief").build())
                .message(Message.user().text("hello").build())
                .build();

        WireRequest wire = codec.encode(request, policy);
        Map<String, Object> body = wire.getBody();

        Assert.assertEquals("/responses", wire.getPath());
        Assert.assertEquals("be brief", body.get("instructions"));
        Assert.assertEquals(1, JsonWire.arrayAt(body, "input").size());
    }

    @Test
    public void responsesCodecReportsAdditionalSystemMessagesInsteadOfFlatteningThem() {
        ProtocolCodec codec = ProtocolDescriptors.of(Protocol.RESPONSES).getCodec();
        EndpointPolicy policy = policy(Protocol.RESPONSES);
        ModelRequest request = ModelRequest.builder()
                .model("gpt-test")
                .message(Message.system().text("first").build())
                .message(Message.system().text("second").build())
                .message(Message.user().text("hello").build())
                .build();

        WireRequest wire = codec.encode(request, policy);

        Assert.assertEquals("first", wire.getBody().get("instructions"));
        Assert.assertFalse("the second system message must not be silently flattened",
                wire.getWarnings().isEmpty());
    }

    @Test
    public void messagesCodecUsesAnthropicShapesAndPreservesSignature() {
        ProtocolCodec codec = ProtocolDescriptors.of(Protocol.MESSAGES).getCodec();
        EndpointPolicy policy = policy(Protocol.MESSAGES);
        ModelRequest request = ModelRequest.builder()
                .model("claude-test")
                .message(Message.assistant()
                        .block(ReasoningBlock.of("because", "sig-1"))
                        .block(TextBlock.of("answer"))
                        .build())
                .tool(ToolDeclaration.function("lookup")
                        .parameters(Collections.<String, Object>singletonMap("type", "object"))
                        .build())
                .maxOutputTokens(64)
                .build();

        WireRequest wire = codec.encode(request, policy);
        String json = JsonWire.write(wire.getBody());

        Assert.assertEquals("/v1/messages", wire.getPath());
        Assert.assertTrue(json.contains("\"signature\":\"sig-1\""));
        Assert.assertTrue(json.contains("\"input_schema\""));
        Assert.assertTrue(json.contains("\"max_tokens\":64"));
        Assert.assertFalse("Messages has no parallel_tool_calls field", json.contains("parallel_tool_calls"));
    }

    @Test
    public void messagesCodecRefusesUnsignedReasoningReplay() {
        ProtocolCodec codec = ProtocolDescriptors.of(Protocol.MESSAGES).getCodec();
        EndpointPolicy policy = policy(Protocol.MESSAGES);
        ModelRequest request = ModelRequest.builder()
                .model("claude-test")
                .message(Message.assistant()
                        .block(ReasoningBlock.of("no signature"))
                        .block(TextBlock.of("answer"))
                        .build())
                .build();

        WireRequest wire = codec.encode(request, policy);

        Assert.assertFalse("unsigned thinking must not be sent to Messages",
                wire.getWarnings().isEmpty());
        Assert.assertFalse(JsonWire.write(wire.getBody()).contains("\"type\":\"thinking\""));
    }

    @Test
    public void chatCodecDoesNotMutateTheInputRequestOnEncode() {
        ProtocolCodec codec = ProtocolDescriptors.of(Protocol.CHAT_COMPLETIONS).getCodec();
        EndpointPolicy policy = policy(Protocol.CHAT_COMPLETIONS);
        Message message = Message.user().text("hello").build();
        Map<String, Object> providerOptions = new LinkedHashMap<String, Object>();
        providerOptions.put("seed", 42);
        ModelRequest request = ModelRequest.builder()
                .model("deepseek-chat")
                .message(message)
                .providerOptions(providerOptions)
                .build();

        codec.encode(request, policy);

        Assert.assertEquals(1, request.getItems().size());
        Assert.assertEquals(0, request.getTools().size());
        Assert.assertEquals(Boolean.FALSE, Boolean.valueOf(request.getStream() != null));
        Assert.assertEquals(1, request.getProviderOptions().size());
        Assert.assertEquals(1, message.getBlocks().size());
    }

    @Test
    public void unknownProviderOptionIsReportedInsteadOfSilentlyDropped() {
        ProtocolCodec codec = ProtocolDescriptors.of(Protocol.CHAT_COMPLETIONS).getCodec();
        EndpointPolicy policy = policy(Protocol.CHAT_COMPLETIONS);
        ModelRequest request = ModelRequest.builder()
                .model("deepseek-chat")
                .message(Message.user().text("hello").build())
                .providerOption("totally_unknown_key", "v")
                .build();

        WireRequest wire = codec.encode(request, policy);

        Assert.assertFalse("unknown provider options must be reported", wire.getWarnings().isEmpty());
        Assert.assertFalse(JsonWire.write(wire.getBody()).contains("totally_unknown_key"));
    }

    @Test
    public void responsesCodecRetainsRawEventFieldsAndTerminalUsage() {
        ProtocolCodec codec = ProtocolDescriptors.of(Protocol.RESPONSES).getCodec();

        ModelEvent event = codec.decodeEvent(RawSseEvent.of(null, null, RESPONSES_SSE_COMPLETED));

        Assert.assertEquals(ModelEventType.DONE, event.getType());
        Assert.assertNotNull(event.getUsage());
        Assert.assertEquals(15L, event.getUsage().getTotalTokens());
    }

    @Test
    public void responsesCodecTreatsUnknownEventTypeAsOpaque() {
        ProtocolCodec codec = ProtocolDescriptors.of(Protocol.RESPONSES).getCodec();

        ModelEvent event = codec.decodeEvent(RawSseEvent.of(
                null, null, "{\"type\":\"response.some.future.event\",\"sequence_number\":7}"));

        Assert.assertEquals(ModelEventType.OPAQUE, event.getType());
        Assert.assertEquals("response.some.future.event", event.getItemType());
    }

    @Test
    public void chatCodecEmitsUsageEventFromTrailerChunk() {
        ProtocolCodec codec = ProtocolDescriptors.of(Protocol.CHAT_COMPLETIONS).getCodec();

        ModelEvent event = codec.decodeEvent(RawSseEvent.of(null, null,
                "{\"choices\":[],\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":2,\"total_tokens\":3}}"));

        Assert.assertEquals(ModelEventType.USAGE, event.getType());
        Assert.assertEquals(3L, event.getUsage().getTotalTokens());
    }

    @Test
    public void messagesCodecDecodesToolUseIntoAnItemWithoutExecutingAnything() {
        ProtocolCodec codec = ProtocolDescriptors.of(Protocol.MESSAGES).getCodec();
        String body = "{\"id\":\"msg_1\",\"stop_reason\":\"tool_use\","
                + "\"content\":[{\"type\":\"tool_use\",\"id\":\"toolu_1\",\"name\":\"lookup\","
                + "\"input\":{\"q\":\"x\"}}],"
                + "\"usage\":{\"input_tokens\":1,\"output_tokens\":1}}";

        ModelResult result = codec.decode(WireResponse.of(Protocol.MESSAGES, 200, body));

        Assert.assertEquals("tool_use", result.getStopReason());
        Assert.assertTrue(result.getItems().get(0) instanceof ToolCallItem);
        Assert.assertEquals("toolu_1", ((ToolCallItem) result.getItems().get(0)).getCallId());
    }

    @Test
    public void nativeToolOfAnotherProtocolIsNeverSerialized() {
        ProtocolCodec chat = ProtocolDescriptors.of(Protocol.CHAT_COMPLETIONS).getCodec();
        EndpointPolicy policy = policy(Protocol.CHAT_COMPLETIONS);
        ModelRequest request = ModelRequest.builder()
                .model("deepseek-chat")
                .message(Message.user().text("hello").build())
                .tool(ToolDeclaration.builder(ToolKind.NATIVE, "web_search")
                        .nativeSpec(Collections.<String, Object>singletonMap(
                                "type", "web_search_20250305"))
                        .build())
                .build();

        WireRequest wire = chat.encode(request, policy);

        Assert.assertFalse("hosted Messages tool has no Chat shape and must not be forwarded",
                JsonWire.write(wire.getBody()).contains("web_search_20250305"));
        Assert.assertFalse(wire.getWarnings().isEmpty());
    }

    private static EndpointPolicy policy(Protocol protocol) {
        return EndpointPolicy.resolve(
                io.github.lnyocly.ai4j.protocol.Endpoint.builder(protocol)
                        .baseUrl("https://example.test")
                        .build(),
                ProtocolDescriptors.of(protocol));
    }
}
