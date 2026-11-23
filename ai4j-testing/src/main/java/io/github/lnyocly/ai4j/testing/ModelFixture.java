package io.github.lnyocly.ai4j.testing;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.codec.JsonCodec;
import io.github.lnyocly.ai4j.codec.JsonCodecException;
import io.github.lnyocly.ai4j.model.Block;
import io.github.lnyocly.ai4j.model.Message;
import io.github.lnyocly.ai4j.model.ModelItem;
import io.github.lnyocly.ai4j.model.ModelResult;
import io.github.lnyocly.ai4j.model.ModelUsage;
import io.github.lnyocly.ai4j.model.Protocol;
import io.github.lnyocly.ai4j.model.ReasoningItem;
import io.github.lnyocly.ai4j.model.ToolCallBlock;
import io.github.lnyocly.ai4j.model.ToolCallItem;
import io.github.lnyocly.ai4j.model.ToolCallValue;
import io.github.lnyocly.ai4j.protocol.ProtocolDescriptors;
import io.github.lnyocly.ai4j.protocol.WireResponse;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A compact, committable JSON fixture: an ordered list of model exchanges captured once from a
 * real provider conversation (or hand-written) and replayed deterministically by
 * {@link ReplayModelClient}.
 *
 * <p>Format:
 * <pre>{@code
 * {
 *   "name": "deepseek-weather-toolcall",
 *   "recordedFrom": "deepseek",
 *   "exchanges": [
 *     {
 *       "expectedPromptContains": ["shanghai", "get_weather"],
 *       "response": {
 *         "toolCalls": [{"name": "get_weather", "arguments": "{\"city\":\"shanghai\"}", "callId": "call_1"}]
 *       }
 *     },
 *     {
 *       "expectedPromptContains": ["sunny"],
 *       "response": {"outputText": "It is sunny.", "inputTokens": 40, "outputTokens": 8}
 *     }
 *   ]
 * }
 * }</pre>
 *
 * <p>{@code expectedPromptContains} pins what the runtime must have sent: each entry is a
 * substring matched against the JSON-serialized {@link AgentPrompt} of that invocation. Omit it
 * for exchanges whose prompt you do not want to pin.</p>
 *
 * <p>Deliberately not the {@code agent.replay} io-capture format: {@link NodeIoRecord} carries
 * runIds, timestamps, and token-accounting for audit/live-replay of real sessions, while a golden
 * fixture must be small, stable, and free of anything that drifts between runs. Keep fixtures
 * free of real API keys and real user data.</p>
 */
public final class ModelFixture {

    private final String name;
    private final String recordedFrom;
    private final Protocol protocol;
    private final List<Exchange> exchanges;

    private ModelFixture(String name, String recordedFrom, List<Exchange> exchanges) {
        this(name, recordedFrom, null, exchanges);
    }

    private ModelFixture(String name, String recordedFrom, Protocol protocol, List<Exchange> exchanges) {
        this.name = name;
        this.recordedFrom = recordedFrom;
        this.protocol = protocol;
        this.exchanges = exchanges;
    }

    public String getName() {
        return name;
    }

    public String getRecordedFrom() {
        return recordedFrom;
    }

    /** Optional protocol declared by the fixture (or inferred from {@code recordedFrom}), nullable if unspecified. */
    public Protocol getProtocol() {
        return protocol;
    }

    /** Effective protocol for kernel {@link ModelResult} / wire replay (defaults to {@link Protocol#CHAT_COMPLETIONS}). */
    public Protocol resolveProtocol() {
        return protocol != null ? protocol : Protocol.CHAT_COMPLETIONS;
    }

    /** The ordered exchanges; each entry is one model invocation's captured response. */
    public List<Exchange> getExchanges() {
        return exchanges;
    }

    /** One model invocation: optional prompt pins plus the response the client should return. */
    public static final class Exchange {
        private final List<String> expectedPromptContains;
        private final AgentModelResult response;
        private final Protocol protocol;
        private final String reasoningSignature;
        private final String wireResponseJson;
        private final List<String> wireStreamEvents;
        private final ModelResult modelResult;

        private Exchange(List<String> expectedPromptContains, AgentModelResult response,
                         Protocol protocol, String reasoningSignature, String wireResponseJson,
                         List<String> wireStreamEvents, ModelResult modelResult) {
            this.expectedPromptContains = expectedPromptContains;
            this.response = response;
            this.protocol = protocol;
            this.reasoningSignature = reasoningSignature;
            this.wireResponseJson = wireResponseJson;
            this.wireStreamEvents = wireStreamEvents == null
                    ? Collections.<String>emptyList()
                    : wireStreamEvents;
            this.modelResult = modelResult;
        }

        /** Substrings the JSON-serialized prompt of this invocation must contain. */
        public List<String> getExpectedPromptContains() {
            return expectedPromptContains;
        }

        public AgentModelResult getResponse() {
            return response;
        }

        /** Protocol for this exchange (or fixture default if omitted), nullable if neither was specified. */
        public Protocol getProtocol() {
            return protocol;
        }

        /** Reasoning / thought signature recorded at the exchange or response level, or {@code null}. */
        public String getReasoningSignature() {
            return reasoningSignature;
        }

        /** Raw wire JSON payload when {@code wireResponse} is supplied in the fixture, else {@code null}. */
        public String getWireResponseJson() {
            return wireResponseJson;
        }

        /** Raw SSE JSON payloads when {@code streamEvents} is supplied in the fixture, else empty. */
        public List<String> getWireStreamEvents() {
            return wireStreamEvents;
        }

        /** Decoded or synthesized kernel {@link ModelResult} preserving signatures and protocol provenance. */
        public ModelResult getModelResult() {
            return modelResult;
        }
    }

    /** Parses a fixture from its JSON text. */
    public static ModelFixture parse(String json) {
        if (json == null || json.trim().isEmpty()) {
            throw new IllegalArgumentException("model fixture json is empty");
        }
        JsonNode root;
        try {
            root = JsonCodec.readTree(json);
        } catch (JsonCodecException e) {
            throw new IllegalArgumentException("model fixture is not valid JSON: " + e.getMessage(), e);
        }
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("model fixture is not a JSON object");
        }
        JsonNode rawExchanges = root.get("exchanges");
        if (rawExchanges == null || !rawExchanges.isArray() || rawExchanges.size() == 0) {
            throw new IllegalArgumentException("model fixture requires a non-empty 'exchanges' array");
        }
        String recordedFrom = nullableText(root.get("recordedFrom"));
        Protocol protocol = parseProtocol(nullableText(root.get("protocol")), recordedFrom);
        List<Exchange> exchanges = new ArrayList<Exchange>(rawExchanges.size());
        for (int i = 0; i < rawExchanges.size(); i++) {
            exchanges.add(parseExchange(rawExchanges.get(i), i, protocol));
        }
        return new ModelFixture(
                nullableText(root.get("name")),
                recordedFrom,
                protocol,
                Collections.unmodifiableList(exchanges));
    }

    /** Parses a fixture file. */
    public static ModelFixture load(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        return parse(new String(bytes, StandardCharsets.UTF_8));
    }

    /** Parses a fixture from a stream (e.g. {@code getResourceAsStream}); the stream is consumed but not closed. */
    public static ModelFixture load(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = in.read(buffer)) >= 0) {
            out.write(buffer, 0, read);
        }
        return parse(new String(out.toByteArray(), StandardCharsets.UTF_8));
    }

    private static Exchange parseExchange(JsonNode node, int index, Protocol fixtureProtocol) {
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException("exchange[" + index + "] is not a JSON object");
        }
        JsonNode pins = node.get("expectedPromptContains");
        List<String> expectedPromptContains = new ArrayList<String>();
        if (pins != null && pins.isArray()) {
            for (int i = 0; i < pins.size(); i++) {
                String pin = nullableText(pins.get(i));
                if (pin != null && !pin.isEmpty()) {
                    expectedPromptContains.add(pin);
                }
            }
        }
        String rawExchangeProto = nullableText(node.get("protocol"));
        Protocol exchangeProtocol = rawExchangeProto != null
                ? parseExplicitProtocol(rawExchangeProto, "exchange[" + index + "].protocol")
                : fixtureProtocol;
        Protocol effectiveProtocol = exchangeProtocol != null ? exchangeProtocol : Protocol.CHAT_COMPLETIONS;

        JsonNode wireNode = node.get("wireResponse");
        String wireResponseJson = null;
        if (wireNode != null && !wireNode.isNull()) {
            if (!wireNode.isObject()) {
                throw new IllegalArgumentException("exchange[" + index + "].wireResponse must be a JSON object");
            }
            wireResponseJson = JsonCodec.toJson(wireNode);
        }

        List<String> wireStreamEvents = parseWireStreamEvents(node.get("streamEvents"), index);

        JsonNode responseNode = node.get("response");
        String reasoningSignature = firstNonBlankText(node, "reasoningSignature", "thoughtSignature");
        if (responseNode != null && !responseNode.isNull()) {
            String respSig = firstNonBlankText(responseNode, "reasoningSignature", "thoughtSignature");
            if (respSig != null) {
                reasoningSignature = respSig;
            }
        }

        ModelResult modelResult;
        AgentModelResult response;
        if (wireResponseJson != null) {
            modelResult = decodeWireResponse(effectiveProtocol, wireResponseJson);
            if (reasoningSignature == null) {
                reasoningSignature = extractReasoningSignature(modelResult);
            }
            response = (responseNode != null && !responseNode.isNull())
                    ? parseResponse(responseNode, index)
                    : toAgentModelResult(modelResult);
        } else if (responseNode != null && !responseNode.isNull()) {
            response = parseResponse(responseNode, index);
            modelResult = toModelResult(response, reasoningSignature, effectiveProtocol);
        } else {
            throw new IllegalArgumentException(
                    "exchange[" + index + "] requires a 'response' or 'wireResponse' object");
        }

        return new Exchange(
                Collections.unmodifiableList(expectedPromptContains),
                response,
                exchangeProtocol,
                reasoningSignature,
                wireResponseJson,
                wireStreamEvents,
                modelResult);
    }

    private static List<String> parseWireStreamEvents(JsonNode streamNode, int index) {
        if (streamNode == null || streamNode.isNull()) {
            return Collections.emptyList();
        }
        if (!streamNode.isArray()) {
            throw new IllegalArgumentException("exchange[" + index + "].streamEvents must be a JSON array");
        }
        List<String> events = new ArrayList<String>(streamNode.size());
        for (int i = 0; i < streamNode.size(); i++) {
            JsonNode item = streamNode.get(i);
            if (item == null || item.isNull()) {
                continue;
            }
            events.add(item.isTextual() ? item.asText() : JsonCodec.toJson(item));
        }
        return Collections.unmodifiableList(events);
    }

    private static AgentModelResult parseResponse(JsonNode node, int index) {
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException("exchange[" + index + "] requires a 'response' object");
        }
        AgentModelResult.AgentModelResultBuilder builder = AgentModelResult.builder()
                .outputText(nullableText(node.get("outputText")))
                .reasoningText(nullableText(node.get("reasoningText")))
                .inputTokens(nullableLong(node.get("inputTokens")))
                .outputTokens(nullableLong(node.get("outputTokens")))
                .totalTokens(nullableLong(node.get("totalTokens")))
                .cacheReadInputTokens(firstNonNullLong(node, "cachedInputTokens", "cacheReadInputTokens"))
                .reasoningTokens(firstNonNullLong(node, "reasoningTokens", "thoughtsTokenCount"));

        JsonNode rawCalls = node.get("toolCalls");
        if (rawCalls != null && rawCalls.isArray() && rawCalls.size() > 0) {
            List<AgentToolCall> calls = new ArrayList<AgentToolCall>(rawCalls.size());
            for (int i = 0; i < rawCalls.size(); i++) {
                JsonNode callNode = rawCalls.get(i);
                String name = callNode == null || !callNode.isObject() ? null : nullableText(callNode.get("name"));
                if (name == null) {
                    throw new IllegalArgumentException(
                            "exchange[" + index + "].toolCalls[" + i + "] requires a 'name'");
                }
                Map<String, Object> metadata = parseMetadataMap(callNode.get("metadata"));
                String thoughtSig = firstNonBlankText(callNode, "thoughtSignature", "signature");
                if (thoughtSig != null) {
                    if (metadata == null) {
                        metadata = new LinkedHashMap<String, Object>();
                    }
                    metadata.put("thoughtSignature", thoughtSig);
                }
                calls.add(AgentToolCall.builder()
                        .name(name)
                        .arguments(nullableText(callNode.get("arguments")))
                        .callId(nullableText(callNode.get("callId")))
                        .type(nullableText(callNode.get("type")))
                        .metadata(metadata == null ? null : Collections.unmodifiableMap(metadata))
                        .build());
            }
            builder.toolCalls(calls);
        }
        return builder.build();
    }

    private static Protocol parseProtocol(String rawProtocol, String recordedFrom) {
        if (rawProtocol != null && !rawProtocol.trim().isEmpty()) {
            return parseExplicitProtocol(rawProtocol, "protocol");
        }
        if (recordedFrom == null || recordedFrom.trim().isEmpty()) {
            return null;
        }
        String norm = recordedFrom.trim().toLowerCase(Locale.ROOT);
        if (norm.contains("gemini") || norm.contains("google")) {
            return Protocol.GEMINI;
        }
        if (norm.contains("anthropic") || norm.contains("claude") || norm.contains("messages")) {
            return Protocol.MESSAGES;
        }
        if (norm.contains("responses")) {
            return Protocol.RESPONSES;
        }
        if (norm.contains("deepseek") || norm.contains("openai") || norm.contains("chat")) {
            return Protocol.CHAT_COMPLETIONS;
        }
        return null;
    }

    private static Protocol parseExplicitProtocol(String raw, String field) {
        String norm = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if ("CHAT_COMPLETIONS".equals(norm) || "OPENAI_CHAT".equals(norm) || "CHAT".equals(norm)) {
            return Protocol.CHAT_COMPLETIONS;
        }
        if ("RESPONSES".equals(norm) || "OPENAI_RESPONSES".equals(norm)) {
            return Protocol.RESPONSES;
        }
        if ("ANTHROPIC_MESSAGES".equals(norm) || "MESSAGES".equals(norm) || "ANTHROPIC".equals(norm)) {
            return Protocol.MESSAGES;
        }
        if ("GEMINI".equals(norm) || "GEMINI_GENERATE_CONTENT".equals(norm) || "GOOGLE_GEMINI".equals(norm)) {
            return Protocol.GEMINI;
        }
        throw new IllegalArgumentException("Unsupported " + field + ": " + raw);
    }

    private static ModelResult decodeWireResponse(Protocol protocol, String wireResponseJson) {
        return ProtocolDescriptors.of(protocol).getCodec()
                .decode(WireResponse.of(protocol, 200, wireResponseJson));
    }

    private static ModelResult toModelResult(AgentModelResult agentResult,
                                             String reasoningSignature,
                                             Protocol protocol) {
        ModelResult.Builder builder = ModelResult.builder();
        if (agentResult.getReasoningText() != null || reasoningSignature != null) {
            builder.item(ReasoningItem.of(
                    agentResult.getReasoningText(),
                    null,
                    reasoningSignature,
                    protocol,
                    null,
                    null,
                    null));
        }
        if (agentResult.getOutputText() != null && !agentResult.getOutputText().isEmpty()) {
            builder.item(Message.assistant().text(agentResult.getOutputText()).build());
        }
        boolean hasCalls = false;
        if (agentResult.getToolCalls() != null) {
            for (AgentToolCall call : agentResult.getToolCalls()) {
                if (call == null) {
                    continue;
                }
                hasCalls = true;
                String sig = null;
                if (call.getMetadata() != null) {
                    Object s = call.getMetadata().get("thoughtSignature");
                    if (s == null) {
                        s = call.getMetadata().get("signature");
                    }
                    if (s != null) {
                        sig = String.valueOf(s);
                    }
                }
                ToolCallValue value = ToolCallValue.of(
                        call.getCallId(),
                        call.getName(),
                        call.getArguments(),
                        sig,
                        protocol,
                        null);
                builder.item(ToolCallItem.of(value, call.getCallId(), call.getMetadata(), null));
            }
        }
        if (agentResult.getInputTokens() != null || agentResult.getOutputTokens() != null
                || agentResult.getTotalTokens() != null || agentResult.getCacheReadInputTokens() != null
                || agentResult.getReasoningTokens() != null) {
            ModelUsage.Builder usage = ModelUsage.builder();
            if (agentResult.getInputTokens() != null) {
                usage.inputTokens(agentResult.getInputTokens().longValue());
            }
            if (agentResult.getOutputTokens() != null) {
                usage.outputTokens(agentResult.getOutputTokens().longValue());
            }
            if (agentResult.getTotalTokens() != null) {
                usage.totalTokens(agentResult.getTotalTokens().longValue());
            }
            if (agentResult.getCacheReadInputTokens() != null) {
                usage.cachedInputTokens(agentResult.getCacheReadInputTokens().longValue());
            }
            if (agentResult.getReasoningTokens() != null) {
                usage.reasoningTokens(agentResult.getReasoningTokens().longValue());
            }
            builder.usage(usage.build());
        }
        builder.stopReason(hasCalls ? "tool_calls" : "stop");
        return builder.build();
    }

    private static AgentModelResult toAgentModelResult(ModelResult modelResult) {
        StringBuilder reasoning = new StringBuilder();
        StringBuilder output = new StringBuilder();
        List<AgentToolCall> calls = new ArrayList<AgentToolCall>();
        for (ModelItem item : modelResult.getItems()) {
            if (item instanceof ReasoningItem) {
                String text = ((ReasoningItem) item).getText();
                if (text != null) {
                    reasoning.append(text);
                }
            } else if (item instanceof ToolCallItem) {
                ToolCallItem tc = (ToolCallItem) item;
                calls.add(toAgentToolCall(tc.getValue()));
            } else if (item instanceof Message) {
                Message msg = (Message) item;
                String text = msg.textContent();
                if (text != null) {
                    output.append(text);
                }
                for (Block block : msg.getBlocks()) {
                    if (block instanceof ToolCallBlock) {
                        calls.add(toAgentToolCall(((ToolCallBlock) block).getValue()));
                    }
                }
            }
        }
        AgentModelResult.AgentModelResultBuilder builder = AgentModelResult.builder()
                .reasoningText(reasoning.length() == 0 ? null : reasoning.toString())
                .outputText(output.length() == 0 ? null : output.toString())
                .toolCalls(calls.isEmpty() ? null : calls);
        ModelUsage usage = modelResult.getUsage();
        if (usage != null) {
            if (usage.getInputTokens() != ModelUsage.UNKNOWN) {
                builder.inputTokens(Long.valueOf(usage.getInputTokens()));
            }
            if (usage.getOutputTokens() != ModelUsage.UNKNOWN) {
                builder.outputTokens(Long.valueOf(usage.getOutputTokens()));
            }
            if (usage.getTotalTokens() != ModelUsage.UNKNOWN) {
                builder.totalTokens(Long.valueOf(usage.getTotalTokens()));
            }
            if (usage.getCachedInputTokens() != ModelUsage.UNKNOWN) {
                builder.cacheReadInputTokens(Long.valueOf(usage.getCachedInputTokens()));
            }
            if (usage.getReasoningTokens() != ModelUsage.UNKNOWN) {
                builder.reasoningTokens(Long.valueOf(usage.getReasoningTokens()));
            }
        }
        return builder.build();
    }

    private static AgentToolCall toAgentToolCall(ToolCallValue value) {
        Map<String, Object> meta = null;
        if (value.getThoughtSignature() != null) {
            meta = Collections.<String, Object>singletonMap("thoughtSignature", value.getThoughtSignature());
        }
        return AgentToolCall.builder()
                .name(value.getName())
                .arguments(value.getArgumentsJson())
                .callId(value.getCallId())
                .type("function")
                .metadata(meta)
                .build();
    }

    private static String extractReasoningSignature(ModelResult result) {
        if (result == null) {
            return null;
        }
        for (ModelItem item : result.getItems()) {
            if (item instanceof ReasoningItem && ((ReasoningItem) item).getSignature() != null) {
                return ((ReasoningItem) item).getSignature();
            }
        }
        return null;
    }

    private static Map<String, Object> parseMetadataMap(JsonNode node) {
        if (node == null || !node.isObject() || node.size() == 0) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            JsonNode v = entry.getValue();
            if (v == null || v.isNull()) {
                map.put(entry.getKey(), null);
            } else if (v.isTextual()) {
                map.put(entry.getKey(), v.asText());
            } else if (v.isBoolean()) {
                map.put(entry.getKey(), Boolean.valueOf(v.booleanValue()));
            } else if (v.isIntegralNumber()) {
                map.put(entry.getKey(), Long.valueOf(v.longValue()));
            } else if (v.isFloatingPointNumber()) {
                map.put(entry.getKey(), Double.valueOf(v.doubleValue()));
            } else {
                map.put(entry.getKey(), JsonCodec.toJson(v));
            }
        }
        return map;
    }

    private static String firstNonBlankText(JsonNode node, String... keys) {
        if (node == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            String value = nullableText(node.get(key));
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return null;
    }

    private static Long firstNonNullLong(JsonNode node, String... keys) {
        if (node == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            Long value = nullableLong(node.get(key));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String nullableText(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        return node.isValueNode() ? node.asText() : JsonCodec.toJson(node);
    }

    private static Long nullableLong(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isNumber()) {
            return Long.valueOf(node.longValue());
        }
        if (node.isTextual()) {
            String text = node.asText().trim();
            if (!text.isEmpty()) {
                try {
                    return Long.valueOf(Long.parseLong(text));
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }
}
