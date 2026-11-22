package io.github.lnyocly.ai4j.testing;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.codec.JsonCodec;
import io.github.lnyocly.ai4j.codec.JsonCodecException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
    private final List<Exchange> exchanges;

    private ModelFixture(String name, String recordedFrom, List<Exchange> exchanges) {
        this.name = name;
        this.recordedFrom = recordedFrom;
        this.exchanges = exchanges;
    }

    public String getName() {
        return name;
    }

    public String getRecordedFrom() {
        return recordedFrom;
    }

    /** The ordered exchanges; each entry is one model invocation's captured response. */
    public List<Exchange> getExchanges() {
        return exchanges;
    }

    /** One model invocation: optional prompt pins plus the response the client should return. */
    public static final class Exchange {
        private final List<String> expectedPromptContains;
        private final AgentModelResult response;

        private Exchange(List<String> expectedPromptContains, AgentModelResult response) {
            this.expectedPromptContains = expectedPromptContains;
            this.response = response;
        }

        /** Substrings the JSON-serialized prompt of this invocation must contain. */
        public List<String> getExpectedPromptContains() {
            return expectedPromptContains;
        }

        public AgentModelResult getResponse() {
            return response;
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
        List<Exchange> exchanges = new ArrayList<Exchange>(rawExchanges.size());
        for (int i = 0; i < rawExchanges.size(); i++) {
            exchanges.add(parseExchange(rawExchanges.get(i), i));
        }
        return new ModelFixture(
                nullableText(root.get("name")),
                nullableText(root.get("recordedFrom")),
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

    private static Exchange parseExchange(JsonNode node, int index) {
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
        return new Exchange(
                Collections.unmodifiableList(expectedPromptContains),
                parseResponse(node.get("response"), index));
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
                .totalTokens(nullableLong(node.get("totalTokens")));

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
                calls.add(AgentToolCall.builder()
                        .name(name)
                        .arguments(nullableText(callNode.get("arguments")))
                        .callId(nullableText(callNode.get("callId")))
                        .type(nullableText(callNode.get("type")))
                        .build());
            }
            builder.toolCalls(calls);
        }
        return builder.build();
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
