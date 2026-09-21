package io.github.lnyocly.ai4j.testing;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;

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
        JSONObject root;
        try {
            root = JSON.parseObject(json);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("model fixture is not valid JSON: " + e.getMessage(), e);
        }
        if (root == null) {
            throw new IllegalArgumentException("model fixture is not a JSON object");
        }
        JSONArray rawExchanges = root.getJSONArray("exchanges");
        if (rawExchanges == null || rawExchanges.isEmpty()) {
            throw new IllegalArgumentException("model fixture requires a non-empty 'exchanges' array");
        }
        List<Exchange> exchanges = new ArrayList<Exchange>(rawExchanges.size());
        for (int i = 0; i < rawExchanges.size(); i++) {
            exchanges.add(parseExchange(rawExchanges.getJSONObject(i), i));
        }
        return new ModelFixture(
                root.getString("name"),
                root.getString("recordedFrom"),
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

    private static Exchange parseExchange(JSONObject node, int index) {
        if (node == null) {
            throw new IllegalArgumentException("exchange[" + index + "] is not a JSON object");
        }
        JSONArray pins = node.getJSONArray("expectedPromptContains");
        List<String> expectedPromptContains = new ArrayList<String>();
        if (pins != null) {
            for (int i = 0; i < pins.size(); i++) {
                String pin = pins.getString(i);
                if (pin != null && !pin.isEmpty()) {
                    expectedPromptContains.add(pin);
                }
            }
        }
        return new Exchange(
                Collections.unmodifiableList(expectedPromptContains),
                parseResponse(node.getJSONObject("response"), index));
    }

    private static AgentModelResult parseResponse(JSONObject node, int index) {
        if (node == null) {
            throw new IllegalArgumentException("exchange[" + index + "] requires a 'response' object");
        }
        AgentModelResult.AgentModelResultBuilder builder = AgentModelResult.builder()
                .outputText(node.getString("outputText"))
                .reasoningText(node.getString("reasoningText"))
                .inputTokens(node.getLong("inputTokens"))
                .outputTokens(node.getLong("outputTokens"))
                .totalTokens(node.getLong("totalTokens"));

        JSONArray rawCalls = node.getJSONArray("toolCalls");
        if (rawCalls != null && !rawCalls.isEmpty()) {
            List<AgentToolCall> calls = new ArrayList<AgentToolCall>(rawCalls.size());
            for (int i = 0; i < rawCalls.size(); i++) {
                JSONObject callNode = rawCalls.getJSONObject(i);
                if (callNode == null || callNode.getString("name") == null) {
                    throw new IllegalArgumentException(
                            "exchange[" + index + "].toolCalls[" + i + "] requires a 'name'");
                }
                calls.add(AgentToolCall.builder()
                        .name(callNode.getString("name"))
                        .arguments(callNode.getString("arguments"))
                        .callId(callNode.getString("callId"))
                        .type(callNode.getString("type"))
                        .build());
            }
            builder.toolCalls(calls);
        }
        return builder.build();
    }
}
