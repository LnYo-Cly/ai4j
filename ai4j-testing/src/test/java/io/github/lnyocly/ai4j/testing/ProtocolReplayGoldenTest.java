package io.github.lnyocly.ai4j.testing;

import io.github.lnyocly.ai4j.client.ModelClient;
import io.github.lnyocly.ai4j.dx.AiClient;
import io.github.lnyocly.ai4j.dx.AiTool;
import io.github.lnyocly.ai4j.dx.AiToolParam;
import io.github.lnyocly.ai4j.model.Message;
import io.github.lnyocly.ai4j.model.ModelRequest;
import io.github.lnyocly.ai4j.model.ModelResult;
import io.github.lnyocly.ai4j.model.Protocol;
import io.github.lnyocly.ai4j.model.ReasoningItem;
import io.github.lnyocly.ai4j.model.ToolCallItem;
import io.github.lnyocly.ai4j.model.ToolCallValue;
import io.github.lnyocly.ai4j.model.ToolDeclaration;
import io.github.lnyocly.ai4j.model.ToolResultItem;
import io.github.lnyocly.ai4j.model.ToolResultValue;
import io.github.lnyocly.ai4j.model.TextBlock;
import io.github.lnyocly.ai4j.protocol.Endpoint;
import io.github.lnyocly.ai4j.protocol.EndpointPolicy;
import io.github.lnyocly.ai4j.protocol.JsonWire;
import io.github.lnyocly.ai4j.protocol.ProtocolDescriptor;
import io.github.lnyocly.ai4j.protocol.ProtocolDescriptors;
import io.github.lnyocly.ai4j.protocol.WireRequest;
import io.github.lnyocly.ai4j.stream.AiReply;
import io.github.lnyocly.ai4j.stream.AiStreamEvent;
import io.github.lnyocly.ai4j.stream.AiStreamEventType;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Golden replay verification covering all four wire protocols ({@code CHAT_COMPLETIONS},
 * {@code RESPONSES}, {@code MESSAGES}, {@code GEMINI}) and Gemini {@code thoughtSignature}
 * multi-turn preservation plus cross-protocol handoff stripping.
 */
public class ProtocolReplayGoldenTest {

    public static final class WeatherTools {
        @AiTool(name = "get_weather", description = "Get current weather by city")
        public String getWeather(@AiToolParam(name = "city", description = "City name") String city) {
            if ("tokyo".equalsIgnoreCase(city)) {
                return "19C, clear";
            }
            if ("beijing".equalsIgnoreCase(city)) {
                return "21C, breezy";
            }
            if ("hangzhou".equalsIgnoreCase(city)) {
                return "23C, sunny";
            }
            return "sunny";
        }
    }

    @Test
    public void chatCompletionsGoldenReplayThroughModelClient() throws IOException {
        ReplayModelClient replay = loadFixture("/fixtures/deepseek-weather-toolcall.json");
        assertEquals(Protocol.CHAT_COMPLETIONS, replay.endpoint().getProtocol());

        AiClient ai = AiClient.builder().defaultClient(replay).build();
        AiReply<String> reply = ai.prompt()
                .model("openai:deepseek-chat")
                .user("weather in shanghai?")
                .tools(new WeatherTools())
                .call();

        assertEquals("It is sunny in Shanghai, around 26C.", reply.getText());
        assertEquals(2, replay.getKernelRequests().size());
    }

    @Test
    public void openAiResponsesGoldenReplayPreservesEncryptedReasoning() throws IOException {
        ReplayModelClient replay = loadFixture("/fixtures/openai-responses-weather.json");
        assertEquals(Protocol.RESPONSES, replay.endpoint().getProtocol());

        AiClient ai = AiClient.builder().defaultClient(replay).build();
        AiReply<String> reply = ai.prompt()
                .model("openai:gpt-4.1")
                .system("Be helpful")
                .user("weather in tokyo?")
                .tools(new WeatherTools())
                .call();

        assertEquals("Tokyo is 19C and clear.", reply.getText());
        assertEquals(2, replay.getKernelRequests().size());
        assertEquals(85L, reply.getUsage().getInputTokens());
        assertEquals(27L, reply.getUsage().getOutputTokens());
    }

    @Test
    public void anthropicMessagesGoldenReplayPreservesThinkingSignature() throws IOException {
        ReplayModelClient replay = loadFixture("/fixtures/anthropic-messages-thinking.json");
        assertEquals(Protocol.MESSAGES, replay.endpoint().getProtocol());

        AiClient ai = AiClient.builder().defaultClient(replay).build();
        AiReply<String> reply = ai.prompt()
                .model("anthropic:claude-sonnet-4-5")
                .system("Be concise")
                .user("weather in beijing?")
                .tools(new WeatherTools())
                .call();

        assertEquals("Beijing is 21C.", reply.getText());
        assertEquals(2, replay.getKernelRequests().size());
        assertEquals(10L, reply.getUsage().getCachedInputTokens());
    }

    @Test
    public void geminiThoughtSignatureGoldenReplayMultiTurnAndStreaming() throws IOException {
        ReplayModelClient syncReplay = loadFixture("/fixtures/gemini-thought-signature-toolcall.json");
        assertEquals(Protocol.GEMINI, syncReplay.endpoint().getProtocol());

        AiClient ai = AiClient.builder().defaultClient(syncReplay).build();
        AiReply<String> syncReply = ai.prompt()
                .model("gemini:gemini-2.5-flash")
                .system("Be helpful")
                .user("weather in hangzhou?")
                .tools(new WeatherTools())
                .call();

        assertEquals("Hangzhou is 23C and sunny.", syncReply.getText());
        assertEquals(2, syncReplay.getKernelRequests().size());
        assertEquals(9L, syncReply.getUsage().getReasoningTokens());
        assertEquals(12L, syncReply.getUsage().getCachedInputTokens());

        ReplayModelClient streamReplay = loadFixture("/fixtures/gemini-thought-signature-toolcall.json");
        AiClient streamAi = AiClient.builder().defaultClient(streamReplay).build();
        List<AiStreamEventType> seenTypes = Collections.synchronizedList(new ArrayList<AiStreamEventType>());
        AiReply<String> streamReply = streamAi.prompt()
                .model("gemini:gemini-2.5-flash")
                .system("Be helpful")
                .user("weather in hangzhou?")
                .tools(new WeatherTools())
                .stream()
                .onEvent(new java.util.function.Consumer<AiStreamEvent>() {
                    @Override
                    public void accept(AiStreamEvent event) {
                        seenTypes.add(event.getType());
                    }
                })
                .await();

        assertEquals("Hangzhou is 23C and sunny.", streamReply.getText());
        assertTrue(seenTypes.contains(AiStreamEventType.THINKING_DELTA));
        assertTrue(seenTypes.contains(AiStreamEventType.TOOLCALL_END));
        assertTrue(seenTypes.contains(AiStreamEventType.TOOL_EXEC_START));
        assertTrue(seenTypes.contains(AiStreamEventType.TOOL_EXEC_END));
        assertTrue(seenTypes.contains(AiStreamEventType.TEXT_DELTA));
        assertTrue(seenTypes.contains(AiStreamEventType.DONE));
    }

    @Test
    public void geminiReplayFailsFastWhenThoughtSignatureDropped() throws IOException {
        ReplayModelClient replay = loadFixture("/fixtures/gemini-thought-signature-toolcall.json");
        ModelClient client = replay.asModelClient();

        ModelResult turn1 = client.create(ModelRequest.builder()
                .model("gemini-2.5-flash")
                .item(Message.system().text("Be helpful").build())
                .item(Message.user().text("weather in hangzhou?").build())
                .tool(ToolDeclaration.function("get_weather")
                        .description("Get current weather by city")
                        .parameters(Collections.<String, Object>singletonMap("type", "object"))
                        .build())
                .build());
        assertNotNull(turn1);

        try {
            client.create(ModelRequest.builder()
                    .model("gemini-2.5-flash")
                    .item(Message.user().text("weather in hangzhou?").build())
                    .tool(ToolDeclaration.function("get_weather")
                            .description("Get current weather by city")
                            .parameters(Collections.<String, Object>singletonMap("type", "object"))
                            .build())
                    .item(ToolCallItem.of(
                            ToolCallValue.of("gem_c1", "get_weather", "{\"city\":\"hangzhou\"}"),
                            "gem_c1", null, null))
                    .item(ToolResultItem.of(
                            ToolResultValue.of("gem_c1", Collections.singletonList(TextBlock.of("23C")), false,
                                    null, Collections.<String, Object>singletonMap("name", "get_weather")),
                            null, null, null))
                    .build());
            fail("Expected missing Gemini thoughtSignature pin to fail");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("gem_thought_sig_abc"));
        }
    }

    @Test
    public void crossProtocolHandoffStripsForeignSignaturesCleanly() throws IOException {
        ReplayModelClient geminiReplay = loadFixture("/fixtures/gemini-thought-signature-toolcall.json");
        ModelResult geminiTurn1 = geminiReplay.create(ModelRequest.builder()
                .model("gemini-2.5-flash")
                .item(Message.system().text("Be helpful").build())
                .item(Message.user().text("weather in hangzhou?").build())
                .tool(ToolDeclaration.function("get_weather")
                        .description("Get current weather by city")
                        .parameters(Collections.<String, Object>singletonMap("type", "object"))
                        .build())
                .build());

        ReasoningItem geminiReasoning = null;
        ToolCallItem geminiCall = null;
        for (io.github.lnyocly.ai4j.model.ModelItem item : geminiTurn1.getItems()) {
            if (item instanceof ReasoningItem) {
                geminiReasoning = (ReasoningItem) item;
            } else if (item instanceof ToolCallItem) {
                geminiCall = (ToolCallItem) item;
            }
        }
        assertNotNull(geminiReasoning);
        assertEquals("gem_thought_sig_abc", geminiReasoning.getSignature());
        assertEquals(Protocol.GEMINI, geminiReasoning.getSourceProtocol());
        assertNotNull(geminiCall);
        assertEquals("gem_call_sig_xyz", geminiCall.getValue().getThoughtSignature());

        ModelRequest handoffReq = ModelRequest.builder()
                .model("claude-sonnet-4-5")
                .item(Message.user().text("weather in hangzhou?").build())
                .item(geminiReasoning)
                .item(geminiCall)
                .item(ToolResultItem.of(
                        ToolResultValue.text(geminiCall.getCallId(), "23C"),
                        null, null, null))
                .build();

        String anthropicWire = encodeWireJson(Protocol.MESSAGES, handoffReq);
        assertFalse(anthropicWire.contains("gem_thought_sig_abc"));
        assertFalse(anthropicWire.contains("gem_call_sig_xyz"));

        String responsesWire = encodeWireJson(Protocol.RESPONSES, handoffReq);
        assertFalse(responsesWire.contains("gem_thought_sig_abc"));
        assertFalse(responsesWire.contains("gem_call_sig_xyz"));
    }

    @Test
    public void geminiRawWireStreamEventsReplayThroughAiStream() {
        String fixtureJson = "{"
                + "\"name\":\"gemini-raw-sse\","
                + "\"protocol\":\"GEMINI\","
                + "\"exchanges\":[{"
                + "  \"expectedPromptContains\":[\"hello\"],"
                + "  \"wireResponse\":{\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"text\":\"Hello world\"}]},\"finishReason\":\"STOP\"}]},"
                + "  \"streamEvents\":["
                + "    {\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"thought\":true,\"text\":\"think\",\"thoughtSignature\":\"sig_sse_1\"}]}}]},"
                + "    {\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"text\":\"Hello world\"}]},\"finishReason\":\"STOP\"}],\"usageMetadata\":{\"promptTokenCount\":5,\"candidatesTokenCount\":2,\"totalTokenCount\":7}}"
                + "  ]"
                + "}]"
                + "}";
        ReplayModelClient replay = ReplayModelClient.parse(fixtureJson);
        AiClient ai = AiClient.of(replay);
        final List<AiStreamEvent> events = Collections.synchronizedList(new ArrayList<AiStreamEvent>());
        AiReply<String> reply = ai.prompt("gemini:gemini-2.5-flash")
                .user("hello")
                .stream()
                .onEvent(new java.util.function.Consumer<AiStreamEvent>() {
                    @Override
                    public void accept(AiStreamEvent event) {
                        events.add(event);
                    }
                })
                .await();

        assertEquals("Hello world", reply.getText());
        boolean sawThinkingDelta = false;
        boolean sawTextDelta = false;
        boolean sawDone = false;
        for (AiStreamEvent e : events) {
            if (e.getType() == AiStreamEventType.THINKING_DELTA) {
                sawThinkingDelta = true;
            } else if (e.getType() == AiStreamEventType.TEXT_DELTA) {
                sawTextDelta = true;
            } else if (e.getType() == AiStreamEventType.DONE) {
                sawDone = true;
            }
        }
        assertTrue(sawThinkingDelta);
        assertTrue(sawTextDelta);
        assertTrue(sawDone);
    }

    private static String encodeWireJson(Protocol protocol, ModelRequest request) {
        ProtocolDescriptor descriptor = ProtocolDescriptors.of(protocol);
        Endpoint endpoint = Endpoint.builder(protocol).baseUrl("https://replay.local").build();
        WireRequest wire = descriptor.getCodec().encode(request, EndpointPolicy.resolve(endpoint, descriptor));
        return JsonWire.write(wire.getBody());
    }

    private static ReplayModelClient loadFixture(String resourcePath) throws IOException {
        try (InputStream in = ProtocolReplayGoldenTest.class.getResourceAsStream(resourcePath)) {
            assertNotNull("Missing fixture " + resourcePath, in);
            return ReplayModelClient.load(in);
        }
    }
}
