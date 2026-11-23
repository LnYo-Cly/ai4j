package io.github.lnyocly.ai4j.service.nativeapi;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Typed Google Gemini {@code streamGenerateContent?alt=sse} native stream event.
 */
public final class NativeGeminiStreamEvent implements NativeStreamEvent {

    public static final String TYPE_CHUNK = "gemini.chunk";
    public static final String TYPE_DONE = "done";
    public static final String TYPE_ERROR = "error";

    private static final TypeReference<Map<String, Object>> MAP_TYPE =
            new TypeReference<Map<String, Object>>() {
            };

    private final String id;
    private final String type;
    private final Map<String, Object> payload;
    private final String responseId;
    private final String finishReason;
    private final String text;
    private final String thoughtText;
    private final List<String> thoughtSignatures;
    private final List<Map<String, Object>> functionCalls;
    private final Map<String, Object> usageMetadata;
    private final Set<Integer> candidateIndices;
    private final Set<Integer> finishedCandidateIndices;
    private final NativeStreamError error;
    private final boolean unknown;
    private final String rawData;

    private NativeGeminiStreamEvent(String id, String type, Map<String, Object> payload,
                                    String responseId, String finishReason,
                                    String text, String thoughtText,
                                    List<String> thoughtSignatures,
                                    List<Map<String, Object>> functionCalls,
                                    Map<String, Object> usageMetadata,
                                    Set<Integer> candidateIndices,
                                    Set<Integer> finishedCandidateIndices,
                                    NativeStreamError error, boolean unknown, String rawData) {
        this.id = id;
        this.type = type;
        this.payload = payload == null ? Collections.<String, Object>emptyMap()
                : Collections.unmodifiableMap(payload);
        this.responseId = responseId;
        this.finishReason = finishReason;
        this.text = text;
        this.thoughtText = thoughtText;
        this.thoughtSignatures = thoughtSignatures == null ? Collections.<String>emptyList()
                : Collections.unmodifiableList(thoughtSignatures);
        this.functionCalls = functionCalls == null ? Collections.<Map<String, Object>>emptyList()
                : Collections.unmodifiableList(functionCalls);
        this.usageMetadata = usageMetadata == null ? Collections.<String, Object>emptyMap()
                : Collections.unmodifiableMap(usageMetadata);
        this.candidateIndices = candidateIndices == null ? Collections.<Integer>emptySet()
                : Collections.unmodifiableSet(candidateIndices);
        this.finishedCandidateIndices = finishedCandidateIndices == null ? Collections.<Integer>emptySet()
                : Collections.unmodifiableSet(finishedCandidateIndices);
        this.error = error;
        this.unknown = unknown;
        this.rawData = rawData;
    }

    static NativeGeminiStreamEvent decode(String id, String sseType, String data) {
        String trimmed = data == null ? "" : data.trim();
        if ("[DONE]".equals(trimmed)) {
            return new NativeGeminiStreamEvent(id, TYPE_DONE, null, null, null, null, null,
                    null, null, null, null, null, null, false, data);
        }
        try {
            JsonNode root = NativeProtocolSupport.mapper().readTree(trimmed);
            if (root != null && root.isArray() && root.size() > 0) {
                root = root.get(0);
            }
            if (root != null && root.isObject()) {
                JsonNode errorNode = root.path("error");
                if (!errorNode.isMissingNode() && !errorNode.isNull()) {
                    Map<String, Object> payload = toMap(root);
                    return new NativeGeminiStreamEvent(id, TYPE_ERROR, payload, null, null, null, null,
                            null, null, null, null, null,
                            NativeStreamError.fromGeminiNode(errorNode, data), false, data);
                }
                Map<String, Object> payload = toMap(root);
                String responseId = textOrNull(root, "responseId");
                Map<String, Object> usageMetadata = root.has("usageMetadata") && root.get("usageMetadata").isObject()
                        ? toMap(root.get("usageMetadata"))
                        : Collections.<String, Object>emptyMap();

                JsonNode candidatesNode = root.path("candidates");
                if (candidatesNode.isArray() || !usageMetadata.isEmpty()) {
                    Set<Integer> indices = new LinkedHashSet<Integer>();
                    Set<Integer> finished = new LinkedHashSet<Integer>();
                    String finishReason = null;
                    StringBuilder textBuf = new StringBuilder();
                    StringBuilder thoughtBuf = new StringBuilder();
                    List<String> signatures = new ArrayList<String>();
                    List<Map<String, Object>> calls = new ArrayList<Map<String, Object>>();

                    if (candidatesNode.isArray()) {
                        for (int i = 0; i < candidatesNode.size(); i++) {
                            JsonNode cand = candidatesNode.get(i);
                            if (cand == null || !cand.isObject()) {
                                continue;
                            }
                            int index = cand.has("index") && cand.get("index").isNumber()
                                    ? cand.get("index").asInt() : i;
                            indices.add(index);
                            String fr = textOrNull(cand, "finishReason");
                            if (fr != null) {
                                finished.add(index);
                                if (finishReason == null) {
                                    finishReason = fr;
                                }
                            }
                            if (i == 0) {
                                JsonNode parts = cand.path("content").path("parts");
                                if (parts.isArray()) {
                                    for (JsonNode part : parts) {
                                        if (part == null || !part.isObject()) {
                                            continue;
                                        }
                                        String sig = textOrNull(part, "thoughtSignature");
                                        if (sig != null) {
                                            signatures.add(sig);
                                        }
                                        if (part.path("thought").asBoolean(false)) {
                                            String t = textOrNull(part, "text");
                                            if (t != null) {
                                                thoughtBuf.append(t);
                                            }
                                        } else if (part.has("functionCall") && part.get("functionCall").isObject()) {
                                            calls.add(toMap(part.get("functionCall")));
                                        } else if (part.has("text") && part.get("text").isTextual()) {
                                            textBuf.append(part.get("text").asText());
                                        }
                                    }
                                }
                            }
                        }
                    }

                    return new NativeGeminiStreamEvent(
                            id,
                            TYPE_CHUNK,
                            payload,
                            responseId,
                            finishReason,
                            textBuf.length() == 0 ? null : textBuf.toString(),
                            thoughtBuf.length() == 0 ? null : thoughtBuf.toString(),
                            signatures,
                            calls,
                            usageMetadata,
                            indices,
                            finished,
                            null,
                            false,
                            data);
                }
            }
        } catch (java.io.IOException ignored) {
            // Unparsable payload: keep as unknown event.
        }
        return new NativeGeminiStreamEvent(id, UNKNOWN_TYPE, null, null, null, null, null,
                null, null, null, null, null, null, true, data);
    }

    private static Map<String, Object> toMap(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Collections.emptyMap();
        }
        Map<String, Object> result = NativeProtocolSupport.mapper().convertValue(node, MAP_TYPE);
        return result == null ? Collections.<String, Object>emptyMap() : new LinkedHashMap<String, Object>(result);
    }

    private static String textOrNull(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText(null);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public boolean isUnknown() {
        return unknown;
    }

    @Override
    public String getRawData() {
        return rawData;
    }

    @Override
    public boolean isTerminal() {
        return TYPE_DONE.equals(type);
    }

    @Override
    public NativeStreamError getError() {
        return error;
    }

    @Override
    public boolean isCompletionSignal() {
        return finishReason != null;
    }

    @Override
    public Set<Integer> choiceIndices() {
        return candidateIndices;
    }

    @Override
    public Set<Integer> finishedChoiceIndices() {
        return finishedCandidateIndices;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public String getResponseId() {
        return responseId;
    }

    public String getFinishReason() {
        return finishReason;
    }

    public String getText() {
        return text;
    }

    public String getThoughtText() {
        return thoughtText;
    }

    public List<String> getThoughtSignatures() {
        return thoughtSignatures;
    }

    public List<Map<String, Object>> getFunctionCalls() {
        return functionCalls;
    }

    public Map<String, Object> getUsageMetadata() {
        return usageMetadata;
    }
}
