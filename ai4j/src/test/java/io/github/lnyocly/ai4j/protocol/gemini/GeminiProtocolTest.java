package io.github.lnyocly.ai4j.protocol.gemini;

import io.github.lnyocly.ai4j.client.ModelCall;
import io.github.lnyocly.ai4j.client.ModelCallException;
import io.github.lnyocly.ai4j.client.ModelClient;
import io.github.lnyocly.ai4j.client.ModelClients;
import io.github.lnyocly.ai4j.model.Block;
import io.github.lnyocly.ai4j.model.DocumentBlock;
import io.github.lnyocly.ai4j.model.ImageBlock;
import io.github.lnyocly.ai4j.model.MediaRef;
import io.github.lnyocly.ai4j.model.Message;
import io.github.lnyocly.ai4j.model.ModelCallStatus;
import io.github.lnyocly.ai4j.model.ModelEvent;
import io.github.lnyocly.ai4j.model.ModelEventListener;
import io.github.lnyocly.ai4j.model.ModelEventType;
import io.github.lnyocly.ai4j.model.ModelItem;
import io.github.lnyocly.ai4j.model.ModelRequest;
import io.github.lnyocly.ai4j.model.ModelResult;
import io.github.lnyocly.ai4j.model.Protocol;
import io.github.lnyocly.ai4j.model.ReasoningBlock;
import io.github.lnyocly.ai4j.model.ReasoningItem;
import io.github.lnyocly.ai4j.model.TextBlock;
import io.github.lnyocly.ai4j.model.ToolCallBlock;
import io.github.lnyocly.ai4j.model.ToolCallItem;
import io.github.lnyocly.ai4j.model.ToolCallValue;
import io.github.lnyocly.ai4j.model.ToolChoice;
import io.github.lnyocly.ai4j.model.ToolDeclaration;
import io.github.lnyocly.ai4j.model.ToolResultItem;
import io.github.lnyocly.ai4j.model.ToolResultValue;
import io.github.lnyocly.ai4j.protocol.Capability;
import io.github.lnyocly.ai4j.protocol.CapabilityMatrix;
import io.github.lnyocly.ai4j.protocol.Credentials;
import io.github.lnyocly.ai4j.protocol.Endpoint;
import io.github.lnyocly.ai4j.protocol.EndpointPolicy;
import io.github.lnyocly.ai4j.protocol.JsonWire;
import io.github.lnyocly.ai4j.protocol.ProtocolCodec;
import io.github.lnyocly.ai4j.protocol.ProtocolDescriptors;
import io.github.lnyocly.ai4j.protocol.RawSseEvent;
import io.github.lnyocly.ai4j.protocol.WireRequest;
import io.github.lnyocly.ai4j.protocol.WireResponse;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Contract tests for {@link Protocol#GEMINI}, {@link GeminiCodec}, capability wiring,
 * cross-protocol signature stripping, and Google error mapping.
 */
public class GeminiProtocolTest {

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
    public void capabilityMatrixAndDescriptorAreRegistered() {
        Assert.assertTrue(CapabilityMatrix.forGemini().contains(Capability.REASONING_SIGNATURE));
        Assert.assertTrue(CapabilityMatrix.forGemini().contains(Capability.FUNCTION_TOOLS));
        Assert.assertEquals(CapabilityMatrix.forGemini(), CapabilityMatrix.forProtocol(Protocol.GEMINI));
        Assert.assertEquals(Protocol.GEMINI, ProtocolDescriptors.of(Protocol.GEMINI).getProtocol());
        Assert.assertEquals("/v1beta/models/{model}:generateContent",
                ProtocolDescriptors.of(Protocol.GEMINI).getDefaultPath());
    }

    @Test
    public void encodesSystemPartsMediaCachedContentAndToolConfig() {
        ProtocolCodec codec = ProtocolDescriptors.of(Protocol.GEMINI).getCodec();
        EndpointPolicy policy = EndpointPolicy.resolve(
                Endpoint.builder(Protocol.GEMINI).baseUrl("https://generativelanguage.googleapis.com").build(),
                ProtocolDescriptors.of(Protocol.GEMINI));

        ModelRequest request = ModelRequest.builder()
                .model("models/gemini-2.5-pro")
                .stream(Boolean.TRUE)
                .temperature(0.2d)
                .topP(0.9d)
                .maxOutputTokens(512)
                .message(Message.system().text("Be precise").build())
                .message(Message.user()
                        .block(TextBlock.of("Inspect this"))
                        .block(ImageBlock.of(MediaRef.inlineBase64("AQID", "image/png")))
                        .block(DocumentBlock.of(MediaRef.url("https://generativelanguage.googleapis.com/v1beta/files/f1", "application/pdf"),
                                "application/pdf", "spec.pdf", null, null, null))
                        .build())
                .tool(ToolDeclaration.function("lookup")
                        .description("Lookup record")
                        .parameters(Collections.<String, Object>singletonMap("type", "object"))
                        .build())
                .toolChoice(ToolChoice.function("lookup"))
                .providerOption("cachedContent", "cachedContents/c-123")
                .providerOption("topK", 40)
                .providerOption("responseMimeType", "application/json")
                .build();

        WireRequest wire = codec.encode(request, policy);
        String json = JsonWire.write(wire.getBody());

        Assert.assertEquals("/v1beta/models/gemini-2.5-pro:streamGenerateContent?alt=sse", wire.getPath());
        Assert.assertTrue(json.contains("\"systemInstruction\""));
        Assert.assertTrue(json.contains("\"cachedContent\":\"cachedContents/c-123\""));
        Assert.assertTrue(json.contains("\"inlineData\":{\"mimeType\":\"image/png\",\"data\":\"AQID\"}"));
        Assert.assertTrue(json.contains("\"fileData\":{\"mimeType\":\"application/pdf\",\"fileUri\":\"https://generativelanguage.googleapis.com/v1beta/files/f1\"}"));
        Assert.assertTrue(json.contains("\"functionDeclarations\""));
        Assert.assertTrue(json.contains("\"allowedFunctionNames\":[\"lookup\"]"));
        Assert.assertTrue(json.contains("\"maxOutputTokens\":512"));
        Assert.assertTrue(json.contains("\"topK\":40"));
        Assert.assertTrue(json.contains("\"responseMimeType\":\"application/json\""));
    }

    @Test
    public void preservesThoughtAndSignatureRoundTripAndStripsForeignSignatures() {
        ProtocolCodec gemini = ProtocolDescriptors.of(Protocol.GEMINI).getCodec();
        EndpointPolicy policy = EndpointPolicy.resolve(
                Endpoint.builder(Protocol.GEMINI).baseUrl("https://generativelanguage.googleapis.com").build(),
                ProtocolDescriptors.of(Protocol.GEMINI));

        String responseJson = "{"
                + "\"responseId\":\"resp_g1\","
                + "\"candidates\":[{"
                + "  \"content\":{\"role\":\"model\",\"parts\":["
                + "    {\"thought\":true,\"text\":\"Let me think\",\"thoughtSignature\":\"sig_thought_1\"},"
                + "    {\"functionCall\":{\"name\":\"calc\",\"args\":{\"x\":1}},\"thoughtSignature\":\"sig_call_1\"}"
                + "  ]},"
                + "  \"finishReason\":\"STOP\""
                + "}],"
                + "\"usageMetadata\":{\"promptTokenCount\":11,\"candidatesTokenCount\":7,"
                + "\"thoughtsTokenCount\":4,\"cachedContentTokenCount\":3,\"totalTokenCount\":22}"
                + "}";

        ModelResult decoded = gemini.decode(WireResponse.of(Protocol.GEMINI, 200, responseJson));
        Assert.assertEquals("resp_g1", decoded.getResponseId());
        Assert.assertEquals("STOP", decoded.getStopReason());
        Assert.assertEquals(4L, decoded.getUsage().getReasoningTokens());
        Assert.assertEquals(3L, decoded.getUsage().getCachedInputTokens());
        Assert.assertEquals(2, decoded.getItems().size());

        ReasoningItem reasoning = (ReasoningItem) decoded.getItems().get(0);
        ToolCallItem call = (ToolCallItem) decoded.getItems().get(1);
        Assert.assertEquals(Protocol.GEMINI, reasoning.getSourceProtocol());
        Assert.assertEquals("sig_thought_1", reasoning.getSignature());
        Assert.assertEquals(Protocol.GEMINI, call.getValue().getSourceProtocol());
        Assert.assertEquals("sig_call_1", call.getValue().getThoughtSignature());

        // Round-trip back to Gemini preserves thought:true + both signatures inside one merged model turn.
        ModelRequest geminiReplay = ModelRequest.builder()
                .model("gemini-2.5-pro")
                .item(Message.user().text("compute").build())
                .item(reasoning)
                .item(call)
                .item(ToolResultItem.of(ToolResultValue.text(call.getCallId(), "{\"ok\":true}")))
                .build();
        WireRequest roundTripWire = gemini.encode(geminiReplay, policy);
        String roundTripJson = JsonWire.write(roundTripWire.getBody());
        Assert.assertTrue(roundTripJson.contains("\"thought\":true"));
        Assert.assertTrue(roundTripJson.contains("\"thoughtSignature\":\"sig_thought_1\""));
        Assert.assertTrue(roundTripJson.contains("\"thoughtSignature\":\"sig_call_1\""));
        Assert.assertTrue(roundTripJson.contains("\"functionResponse\":{\"name\":\"calc\",\"response\":{\"ok\":true}}"));
        Assert.assertTrue(roundTripWire.getWarnings().isEmpty());

        // Foreign signatures from Anthropic/Messages must be stripped when handing off to Gemini.
        ModelRequest foreignToGemini = ModelRequest.builder()
                .model("gemini-2.5-pro")
                .message(Message.assistant()
                        .block(ReasoningBlock.of("claude thought", "anth_sig", Protocol.MESSAGES))
                        .block(ToolCallBlock.of(ToolCallValue.of("t1", "calc", "{\"x\":1}",
                                "anth_call_sig", Protocol.MESSAGES)))
                        .build())
                .build();
        WireRequest sanitized = gemini.encode(foreignToGemini, policy);
        String sanitizedJson = JsonWire.write(sanitized.getBody());
        Assert.assertFalse(sanitizedJson.contains("anth_sig"));
        Assert.assertFalse(sanitizedJson.contains("anth_call_sig"));
        Assert.assertTrue(sanitizedJson.contains("\"functionCall\""));
        Assert.assertEquals(2, sanitized.getWarnings().size());

        // Foreign Gemini signatures must also be stripped when handing off to CHAT_COMPLETIONS.
        ProtocolCodec chat = ProtocolDescriptors.of(Protocol.CHAT_COMPLETIONS).getCodec();
        EndpointPolicy chatPolicy = EndpointPolicy.resolve(
                Endpoint.builder(Protocol.CHAT_COMPLETIONS).baseUrl("https://api.openai.com").build(),
                ProtocolDescriptors.of(Protocol.CHAT_COMPLETIONS));
        WireRequest chatWire = chat.encode(geminiReplay, chatPolicy);
        String chatJson = JsonWire.write(chatWire.getBody());
        Assert.assertFalse(chatJson.contains("sig_thought_1"));
        Assert.assertFalse(chatJson.contains("sig_call_1"));
        Assert.assertFalse(chatWire.getWarnings().isEmpty());
    }

    @Test
    public void streamsGoogleChunksWithoutDoneMarkerAndMapsErrors() throws Exception {
        // Test x-goog-api-key + multi-chunk parallel tool calls + finishReason termination without [DONE]
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody("data: {\"responseId\":\"g_s1\",\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"thought\":true,\"text\":\"Thinking\",\"thoughtSignature\":\"ts1\"}]}}]}\n\n"
                        + "data: {\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"functionCall\":{\"name\":\"tool_a\",\"args\":{\"k\":1}},\"thoughtSignature\":\"cs1\"}]}}]}\n\n"
                        + "data: {\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"functionCall\":{\"name\":\"tool_b\",\"args\":{\"k\":2}},\"thoughtSignature\":\"cs2\"}]},\"finishReason\":\"STOP\"}],\"usageMetadata\":{\"promptTokenCount\":5,\"candidatesTokenCount\":3,\"totalTokenCount\":8}}\n\n"));

        ModelClient client = ModelClients.of(
                Endpoint.builder(Protocol.GEMINI)
                        .baseUrl(server.url("/").toString())
                        .credentials(Credentials.header("x-goog-api-key", "goog-secret-1"))
                        .build(),
                configuration);

        final List<ModelEvent> events = Collections.synchronizedList(new ArrayList<ModelEvent>());
        ModelCall call = client.createStream(
                ModelRequest.builder().model("gemini-2.5-flash").message(Message.user().text("hi").build()).build(),
                new ModelEventListener() {
                    @Override
                    public void onEvent(ModelEvent event) {
                        events.add(event);
                    }
                });
        ModelResult result = call.await();
        RecordedRequest recorded = server.takeRequest();

        Assert.assertEquals("goog-secret-1", recorded.getHeader("x-goog-api-key"));
        Assert.assertEquals("/v1beta/models/gemini-2.5-flash:streamGenerateContent?alt=sse", recorded.getPath());
        Assert.assertEquals(ModelCallStatus.COMPLETED, call.status());
        Assert.assertEquals("STOP", result.getStopReason());
        Assert.assertEquals(8L, result.getUsage().getTotalTokens());
        // Both sequential functionCall chunks must be preserved, not overwritten
        int toolCallCount = 0;
        for (ModelItem item : result.getItems()) {
            if (item instanceof ToolCallItem) {
                toolCallCount++;
            }
        }
        Assert.assertEquals(2, toolCallCount);

        // Test Bearer auth + Google JSON array error with Retry-After and x-goog-request-id
        server.enqueue(new MockResponse()
                .setResponseCode(429)
                .setHeader("Content-Type", "application/json")
                .setHeader("x-goog-request-id", "goog-req-99")
                .setHeader("Retry-After", "7")
                .setBody("[{\"error\":{\"code\":429,\"message\":\"Quota exceeded\",\"status\":\"RESOURCE_EXHAUSTED\"}}]"));

        ModelClient bearerClient = ModelClients.of(
                Endpoint.builder(Protocol.GEMINI)
                        .baseUrl(server.url("/").toString())
                        .credentials(Credentials.bearer("oauth-token-1"))
                        .build(),
                configuration);
        try {
            bearerClient.create(ModelRequest.builder()
                    .model("gemini-2.5-pro")
                    .message(Message.user().text("hi").build())
                    .build());
            Assert.fail("Expected 429 ModelCallException");
        } catch (ModelCallException expected) {
            Assert.assertEquals(429, expected.getHttpStatus());
            Assert.assertTrue(expected.isRetryable());
            Assert.assertEquals("RESOURCE_EXHAUSTED", expected.getProviderErrorType());
            Assert.assertEquals("goog-req-99", expected.getProviderRequestId());
            Assert.assertEquals(Long.valueOf(7000L), expected.getRetryAfterMillis());
            Assert.assertTrue(expected.getMessage().contains("Quota exceeded"));
        }
        RecordedRequest errReq = server.takeRequest();
        Assert.assertEquals("Bearer oauth-token-1", errReq.getHeader("Authorization"));
    }

    @Test
    public void decodeEventHandlesArrayWrappedErrorAndMultiPartChunks() {
        ProtocolCodec codec = ProtocolDescriptors.of(Protocol.GEMINI).getCodec();
        ModelEvent errEvent = codec.decodeEvent(RawSseEvent.of(
                "[{\"error\":{\"code\":503,\"status\":\"UNAVAILABLE\",\"message\":\"backend busy\"}}]"));
        Assert.assertEquals(ModelEventType.ERROR, errEvent.getType());
        Assert.assertEquals(503, errEvent.getFailure().getHttpStatus());
        Assert.assertTrue(errEvent.getFailure().isRetryable());

        ModelEvent multi = codec.decodeEvent(RawSseEvent.of(
                "{\"responseId\":\"r2\",\"candidates\":[{\"content\":{\"parts\":["
                        + "{\"thought\":true,\"text\":\"plan\",\"thoughtSignature\":\"s1\"},"
                        + "{\"text\":\"answer\"}"
                        + "]},\"finishReason\":\"STOP\"}]}"));
        Assert.assertEquals(ModelEventType.ITEM_DONE, multi.getType());
        Assert.assertEquals(2, multi.getItems().size());
        Assert.assertEquals("STOP", multi.getStopReason());
    }
}
