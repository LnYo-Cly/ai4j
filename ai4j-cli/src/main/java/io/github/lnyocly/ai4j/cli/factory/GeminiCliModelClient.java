package io.github.lnyocly.ai4j.cli.factory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.util.AgentInputItem;
import io.github.lnyocly.ai4j.listener.StreamExecutionOptions;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import io.github.lnyocly.ai4j.service.Configuration;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Gemini native REST/SSE {@link AgentModelClient} used by {@code ai4j-cli} for {@code gemini} protocol routing.
 */
public class GeminiCliModelClient implements AgentModelClient {

    public static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com";
    public static final String METADATA_THOUGHT_SIGNATURE = "thoughtSignature";

    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");
    private static final long DEFAULT_STREAM_TIMEOUT_MS = 120_000L;
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<Map<String, Object>>() {
    };

    private final OkHttpClient httpClient;
    private final String baseUrl;
    private final String apiKey;

    public GeminiCliModelClient(Configuration configuration, String baseUrl, String apiKey) {
        OkHttpClient configuredClient = configuration == null ? null : configuration.getOkHttpClient();
        this.httpClient = configuredClient == null ? new OkHttpClient() : configuredClient;
        this.baseUrl = trimToNull(baseUrl);
        this.apiKey = trimToNull(apiKey);
    }

    @Override
    public AgentModelResult create(AgentPrompt prompt) throws Exception {
        String url = resolveEndpointUrl(prompt == null ? null : prompt.getModel(), false);
        String payload = MAPPER.writeValueAsString(buildRequestBody(prompt));
        Request request = buildRequest(url, payload, false);
        try (Response response = httpClient.newCall(request).execute()) {
            ResponseBody body = response.body();
            String bodyText = body == null ? "" : new String(body.bytes(), StandardCharsets.UTF_8);
            if (!response.isSuccessful()) {
                throw new IllegalStateException("Gemini API request failed with HTTP " + response.code()
                        + (isBlank(bodyText) ? "" : ": " + bodyText));
            }
            JsonNode root = MAPPER.readTree(bodyText);
            ResultAccumulator accumulator = new ResultAccumulator();
            accumulator.consumeChunk(root, null);
            return accumulator.buildResult();
        }
    }

    @Override
    public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) throws Exception {
        String url = resolveEndpointUrl(prompt == null ? null : prompt.getModel(), true);
        String payload = MAPPER.writeValueAsString(buildRequestBody(prompt));
        Request request = buildRequest(url, payload, true);

        final ResultAccumulator accumulator = new ResultAccumulator();
        final CountDownLatch doneLatch = new CountDownLatch(1);
        final AtomicReference<Throwable> failureRef = new AtomicReference<Throwable>();
        final AtomicBoolean completed = new AtomicBoolean(false);

        EventSource.Factory factory = EventSources.createFactory(httpClient);
        EventSource eventSource = factory.newEventSource(request, new EventSourceListener() {
            @Override
            public void onEvent(@NotNull EventSource source,
                                @Nullable String id,
                                @Nullable String type,
                                @NotNull String data) {
                if (isBlank(data) || "[DONE]".equals(data.trim())) {
                    return;
                }
                try {
                    JsonNode chunk = MAPPER.readTree(data);
                    accumulator.consumeChunk(chunk, listener);
                } catch (Exception ex) {
                    failureRef.compareAndSet(null, ex);
                    source.cancel();
                    doneLatch.countDown();
                }
            }

            @Override
            public void onClosed(@NotNull EventSource source) {
                completed.set(true);
                doneLatch.countDown();
            }

            @Override
            public void onFailure(@NotNull EventSource source,
                                  @Nullable Throwable t,
                                  @Nullable Response response) {
                Throwable resolved = buildStreamFailure(t, response);
                failureRef.compareAndSet(null, resolved);
                doneLatch.countDown();
            }
        });

        long timeoutMs = resolveStreamTimeoutMillis(prompt == null ? null : prompt.getStreamExecution());
        boolean finished;
        try {
            finished = doneLatch.await(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            eventSource.cancel();
            Thread.currentThread().interrupt();
            throw ex;
        }
        if (!finished && !completed.get()) {
            eventSource.cancel();
            throw new IllegalStateException("Gemini SSE stream timed out after " + timeoutMs + " ms");
        }
        Throwable failure = failureRef.get();
        if (failure != null) {
            if (listener != null) {
                listener.onError(failure);
            }
            if (failure instanceof Exception) {
                throw (Exception) failure;
            }
            throw new RuntimeException(failure);
        }
        AgentModelResult result = accumulator.buildResult();
        if (listener != null) {
            listener.onComplete(result);
        }
        return result;
    }

    String resolveEndpointUrl(String model, boolean stream) {
        String normalizedModel = trimToNull(model);
        if (normalizedModel == null) {
            throw new IllegalArgumentException("model is required for gemini protocol");
        }
        if (normalizedModel.startsWith("models/")) {
            normalizedModel = normalizedModel.substring("models/".length());
        }
        String suffix = stream ? ":streamGenerateContent?alt=sse" : ":generateContent";
        String host = trimTrailingSlashes(baseUrl == null ? DEFAULT_BASE_URL : baseUrl);
        if (host.endsWith(":generateContent") || host.endsWith(":streamGenerateContent")
                || host.endsWith(":streamGenerateContent?alt=sse")) {
            return host;
        }
        if (host.endsWith("/models")) {
            return host + "/" + normalizedModel + suffix;
        }
        if (host.endsWith("/v1beta") || host.endsWith("/v1")) {
            return host + "/models/" + normalizedModel + suffix;
        }
        if (host.contains("/v1beta/models/") || host.contains("/v1/models/")) {
            return host + suffix;
        }
        return host + "/v1beta/models/" + normalizedModel + suffix;
    }

    Map<String, Object> buildRequestBody(AgentPrompt prompt) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        StringBuilder systemBuilder = new StringBuilder();
        if (prompt != null) {
            appendParagraph(systemBuilder, prompt.getSystemPrompt());
            appendParagraph(systemBuilder, prompt.getInstructions());
        }

        List<Map<String, Object>> contents = new ArrayList<Map<String, Object>>();
        Map<String, String> callNamesById = new LinkedHashMap<String, String>();
        if (prompt != null && prompt.getItems() != null) {
            for (Object item : prompt.getItems()) {
                appendMemoryItem(item, systemBuilder, contents, callNamesById);
            }
        }
        if (systemBuilder.length() > 0) {
            Map<String, Object> systemInstruction = new LinkedHashMap<String, Object>();
            systemInstruction.put("parts", Collections.singletonList(textPart(systemBuilder.toString())));
            body.put("systemInstruction", systemInstruction);
        }
        body.put("contents", contents);

        List<Map<String, Object>> functionDeclarations = convertTools(prompt == null ? null : prompt.getTools());
        if (!functionDeclarations.isEmpty()) {
            Map<String, Object> toolContainer = new LinkedHashMap<String, Object>();
            toolContainer.put("functionDeclarations", functionDeclarations);
            body.put("tools", Collections.singletonList(toolContainer));
            Map<String, Object> toolConfig = convertToolConfig(prompt == null ? null : prompt.getToolChoice());
            if (toolConfig != null) {
                body.put("toolConfig", toolConfig);
            }
        }

        Map<String, Object> generationConfig = buildGenerationConfig(prompt);
        if (!generationConfig.isEmpty()) {
            body.put("generationConfig", generationConfig);
        }
        if (prompt != null && prompt.getExtraBody() != null && !prompt.getExtraBody().isEmpty()) {
            body.putAll(prompt.getExtraBody());
        }
        return body;
    }

    private Request buildRequest(String url, String payload, boolean stream) {
        Request.Builder builder = new Request.Builder()
                .url(url)
                .post(RequestBody.create(payload, JSON_MEDIA_TYPE))
                .header("Accept", stream ? "text/event-stream" : "application/json");
        if (apiKey != null) {
            builder.header("x-goog-api-key", apiKey);
        }
        return builder.build();
    }

    private Throwable buildStreamFailure(Throwable t, Response response) {
        if (response != null) {
            String errorText = "";
            try {
                ResponseBody body = response.body();
                if (body != null) {
                    errorText = new String(body.bytes(), StandardCharsets.UTF_8);
                }
            } catch (IOException ignored) {
            }
            return new IllegalStateException("Gemini SSE stream failed with HTTP " + response.code()
                    + (isBlank(errorText) ? "" : ": " + errorText), t);
        }
        return t == null ? new IllegalStateException("Gemini SSE stream failed") : t;
    }

    private void appendMemoryItem(Object rawItem,
                                  StringBuilder systemBuilder,
                                  List<Map<String, Object>> contents,
                                  Map<String, String> callNamesById) {
        if (rawItem == null) {
            return;
        }
        if (rawItem instanceof String) {
            addTurnParts(contents, "user", Collections.singletonList(textPart((String) rawItem)));
            return;
        }
        Map<String, Object> map = asMap(rawItem);
        if (map == null || map.isEmpty()) {
            return;
        }
        String type = stringValue(map.get("type"));
        if ("function_call".equals(type)) {
            String callId = firstNonBlank(stringValue(map.get("call_id")), stringValue(map.get("id")));
            String name = stringValue(map.get("name"));
            if (callId != null && name != null) {
                callNamesById.put(callId, name);
            }
            Map<String, Object> part = functionCallPart(name, map.get("arguments"), extractThoughtSignature(map));
            if (part != null) {
                addTurnParts(contents, "model", Collections.singletonList(part));
            }
            return;
        }
        if ("function_call_output".equals(type)) {
            String callId = firstNonBlank(stringValue(map.get("call_id")), stringValue(map.get("tool_call_id")));
            String name = firstNonBlank(stringValue(map.get("name")),
                    callId == null ? null : callNamesById.get(callId),
                    "tool");
            addTurnParts(contents, "user",
                    Collections.singletonList(functionResponsePart(name, map.get("output"))));
            return;
        }

        String role = stringValue(map.get("role"));
        if ("system".equalsIgnoreCase(role) || "developer".equalsIgnoreCase(role)) {
            appendParagraph(systemBuilder, extractTextFromContent(map.get("content")));
            return;
        }
        if ("tool".equalsIgnoreCase(role)) {
            String callId = firstNonBlank(stringValue(map.get("tool_call_id")), stringValue(map.get("call_id")));
            String name = firstNonBlank(stringValue(map.get("name")),
                    callId == null ? null : callNamesById.get(callId),
                    "tool");
            addTurnParts(contents, "user",
                    Collections.singletonList(functionResponsePart(name, map.get("content"))));
            return;
        }

        String geminiRole = "assistant".equalsIgnoreCase(role) || "model".equalsIgnoreCase(role) ? "model" : "user";
        List<Map<String, Object>> parts = new ArrayList<Map<String, Object>>();
        collectContentParts(map.get("content"), parts);
        Object rawToolCalls = map.get("tool_calls");
        if (rawToolCalls instanceof List) {
            for (Object entry : (List<?>) rawToolCalls) {
                Map<String, Object> callMap = asMap(entry);
                if (callMap == null) {
                    continue;
                }
                Map<String, Object> functionMap = asMap(callMap.get("function"));
                String callId = firstNonBlank(stringValue(callMap.get("id")), stringValue(callMap.get("call_id")));
                String name = functionMap != null
                        ? stringValue(functionMap.get("name"))
                        : stringValue(callMap.get("name"));
                Object args = functionMap != null ? functionMap.get("arguments") : callMap.get("arguments");
                if (callId != null && name != null) {
                    callNamesById.put(callId, name);
                }
                Map<String, Object> fcPart = functionCallPart(name, args, extractThoughtSignature(callMap));
                if (fcPart != null) {
                    parts.add(fcPart);
                }
            }
        }
        if (!parts.isEmpty()) {
            addTurnParts(contents, geminiRole, parts);
        }
    }

    private void addTurnParts(List<Map<String, Object>> contents,
                              String role,
                              List<Map<String, Object>> parts) {
        if (parts == null || parts.isEmpty()) {
            return;
        }
        if (!contents.isEmpty()) {
            Map<String, Object> previous = contents.get(contents.size() - 1);
            if (role.equals(previous.get("role"))) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> existingParts = (List<Map<String, Object>>) previous.get("parts");
                existingParts.addAll(parts);
                return;
            }
        }
        Map<String, Object> content = new LinkedHashMap<String, Object>();
        content.put("role", role);
        content.put("parts", new ArrayList<Map<String, Object>>(parts));
        contents.add(content);
    }

    private void collectContentParts(Object content, List<Map<String, Object>> parts) {
        if (content == null) {
            return;
        }
        if (content instanceof String) {
            String text = (String) content;
            if (!text.isEmpty()) {
                parts.add(textPart(text));
            }
            return;
        }
        if (!(content instanceof List)) {
            String fallback = extractTextFromContent(content);
            if (!isBlank(fallback)) {
                parts.add(textPart(fallback));
            }
            return;
        }
        for (Object element : (List<?>) content) {
            if (element instanceof String) {
                if (!((String) element).isEmpty()) {
                    parts.add(textPart((String) element));
                }
                continue;
            }
            Map<String, Object> partMap = asMap(element);
            if (partMap == null) {
                continue;
            }
            String text = stringValue(partMap.get("text"));
            if (!isBlank(text)) {
                parts.add(textPart(text));
                continue;
            }
            String imageUrl = extractImageUrl(partMap);
            if (!isBlank(imageUrl) && imageUrl.startsWith("data:")) {
                Map<String, Object> inlinePart = parseDataUrlPart(imageUrl);
                if (inlinePart != null) {
                    parts.add(inlinePart);
                }
            }
        }
    }

    private String extractImageUrl(Map<String, Object> partMap) {
        Object rawImageUrl = partMap.get("image_url");
        if (rawImageUrl instanceof String) {
            return (String) rawImageUrl;
        }
        Map<String, Object> nested = asMap(rawImageUrl);
        return nested == null ? null : stringValue(nested.get("url"));
    }

    private Map<String, Object> parseDataUrlPart(String dataUrl) {
        int comma = dataUrl.indexOf(',');
        if (comma <= 5) {
            return null;
        }
        String meta = dataUrl.substring(5, comma);
        String payload = dataUrl.substring(comma + 1);
        int semi = meta.indexOf(';');
        String mimeType = semi > 0 ? meta.substring(0, semi) : meta;
        if (isBlank(mimeType)) {
            mimeType = "image/png";
        }
        Map<String, Object> inlineData = new LinkedHashMap<String, Object>();
        inlineData.put("mimeType", mimeType);
        inlineData.put("data", payload);
        Map<String, Object> part = new LinkedHashMap<String, Object>();
        part.put("inlineData", inlineData);
        return part;
    }

    private String extractTextFromContent(Object content) {
        if (content == null) {
            return null;
        }
        if (content instanceof String) {
            return (String) content;
        }
        if (content instanceof List) {
            StringBuilder sb = new StringBuilder();
            for (Object item : (List<?>) content) {
                String text = extractTextFromContent(item);
                if (!isBlank(text)) {
                    if (sb.length() > 0) {
                        sb.append('\n');
                    }
                    sb.append(text);
                }
            }
            return sb.length() == 0 ? null : sb.toString();
        }
        Map<String, Object> map = asMap(content);
        return map == null ? String.valueOf(content) : stringValue(map.get("text"));
    }

    private List<Map<String, Object>> convertTools(List<Object> rawTools) {
        if (rawTools == null || rawTools.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> declarations = new ArrayList<Map<String, Object>>();
        for (Object rawTool : rawTools) {
            if (rawTool instanceof Tool) {
                Tool tool = (Tool) rawTool;
                Tool.Function fn = tool.getFunction();
                if (fn != null && !isBlank(fn.getName())) {
                    declarations.add(toFunctionDeclaration(fn.getName(), fn.getDescription(), fn.getParameters()));
                }
                continue;
            }
            Map<String, Object> map = asMap(rawTool);
            if (map == null) {
                continue;
            }
            Map<String, Object> fnMap = asMap(map.get("function"));
            if (fnMap != null) {
                String name = stringValue(fnMap.get("name"));
                if (!isBlank(name)) {
                    declarations.add(toFunctionDeclaration(
                            name,
                            stringValue(fnMap.get("description")),
                            fnMap.get("parameters")
                    ));
                }
            } else {
                String name = stringValue(map.get("name"));
                if (!isBlank(name)) {
                    declarations.add(toFunctionDeclaration(
                            name,
                            stringValue(map.get("description")),
                            map.get("parameters")
                    ));
                }
            }
        }
        return declarations;
    }

    private Map<String, Object> toFunctionDeclaration(String name, String description, Object rawParameters) {
        Map<String, Object> declaration = new LinkedHashMap<String, Object>();
        declaration.put("name", name);
        if (!isBlank(description)) {
            declaration.put("description", description);
        }
        Map<String, Object> schema = sanitizeSchema(asMap(rawParameters));
        if (schema != null && !schema.isEmpty()) {
            declaration.put("parameters", schema);
        }
        return declaration;
    }

    private Map<String, Object> sanitizeSchema(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        Map<String, Object> clean = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value == null || "additionalProperties".equals(key)) {
                continue;
            }
            if ("type".equals(key) && value instanceof List) {
                String primaryType = null;
                for (Object item : (List<?>) value) {
                    if (item != null && !"null".equals(String.valueOf(item))) {
                        primaryType = String.valueOf(item);
                        break;
                    }
                }
                if (primaryType != null) {
                    clean.put("type", primaryType);
                }
                continue;
            }
            if ("properties".equals(key) && value instanceof Map) {
                Map<?, ?> props = (Map<?, ?>) value;
                Map<String, Object> cleanProps = new LinkedHashMap<String, Object>();
                for (Map.Entry<?, ?> propEntry : props.entrySet()) {
                    Map<String, Object> nested = sanitizeSchema(asMap(propEntry.getValue()));
                    if (nested != null) {
                        cleanProps.put(String.valueOf(propEntry.getKey()), nested);
                    }
                }
                clean.put("properties", cleanProps);
                continue;
            }
            if ("items".equals(key)) {
                Map<String, Object> nestedItems = sanitizeSchema(asMap(value));
                if (nestedItems != null) {
                    clean.put("items", nestedItems);
                }
                continue;
            }
            clean.put(key, value);
        }
        return clean;
    }

    private Map<String, Object> convertToolConfig(Object toolChoice) {
        if (toolChoice == null) {
            return null;
        }
        String mode = null;
        if (toolChoice instanceof String) {
            String raw = ((String) toolChoice).trim().toLowerCase(Locale.ROOT);
            if ("none".equals(raw)) {
                mode = "NONE";
            } else if ("any".equals(raw) || "required".equals(raw)) {
                mode = "ANY";
            } else if ("auto".equals(raw)) {
                mode = "AUTO";
            }
        }
        if (mode == null) {
            return null;
        }
        Map<String, Object> fcConfig = new LinkedHashMap<String, Object>();
        fcConfig.put("mode", mode);
        Map<String, Object> toolConfig = new LinkedHashMap<String, Object>();
        toolConfig.put("functionCallingConfig", fcConfig);
        return toolConfig;
    }

    private Map<String, Object> buildGenerationConfig(AgentPrompt prompt) {
        Map<String, Object> config = new LinkedHashMap<String, Object>();
        if (prompt == null) {
            return config;
        }
        if (prompt.getTemperature() != null) {
            config.put("temperature", prompt.getTemperature());
        }
        if (prompt.getTopP() != null) {
            config.put("topP", prompt.getTopP());
        }
        if (prompt.getMaxOutputTokens() != null) {
            config.put("maxOutputTokens", prompt.getMaxOutputTokens());
        }
        Map<String, Object> reasoningMap = asMap(prompt.getReasoning());
        if (reasoningMap != null && !reasoningMap.isEmpty()) {
            Map<String, Object> thinkingConfig = new LinkedHashMap<String, Object>();
            if (reasoningMap.containsKey("includeThoughts")) {
                thinkingConfig.put("includeThoughts", reasoningMap.get("includeThoughts"));
            }
            if (reasoningMap.containsKey("thinkingBudget")) {
                thinkingConfig.put("thinkingBudget", reasoningMap.get("thinkingBudget"));
            }
            if (!thinkingConfig.isEmpty()) {
                config.put("thinkingConfig", thinkingConfig);
            }
        }
        return config;
    }

    private Map<String, Object> textPart(String text) {
        Map<String, Object> part = new LinkedHashMap<String, Object>();
        part.put("text", text);
        return part;
    }

    private Map<String, Object> functionCallPart(String name, Object rawArgs, String thoughtSignature) {
        if (isBlank(name)) {
            return null;
        }
        Map<String, Object> fc = new LinkedHashMap<String, Object>();
        fc.put("name", name);
        fc.put("args", parseJsonObject(rawArgs));
        Map<String, Object> part = new LinkedHashMap<String, Object>();
        part.put("functionCall", fc);
        if (!isBlank(thoughtSignature)) {
            part.put(METADATA_THOUGHT_SIGNATURE, thoughtSignature);
        }
        return part;
    }

    private Map<String, Object> functionResponsePart(String name, Object rawOutput) {
        Map<String, Object> responseMap = parseFunctionResponsePayload(rawOutput);
        Map<String, Object> fr = new LinkedHashMap<String, Object>();
        fr.put("name", name);
        fr.put("response", responseMap);
        Map<String, Object> part = new LinkedHashMap<String, Object>();
        part.put("functionResponse", fr);
        return part;
    }

    private Map<String, Object> parseJsonObject(Object rawArgs) {
        if (rawArgs instanceof Map) {
            return asMap(rawArgs);
        }
        if (rawArgs instanceof String) {
            String trimmed = ((String) rawArgs).trim();
            if (!trimmed.isEmpty() && trimmed.startsWith("{")) {
                try {
                    Map<String, Object> parsed = MAPPER.readValue(trimmed, MAP_TYPE);
                    if (parsed != null) {
                        return parsed;
                    }
                } catch (Exception ignored) {
                }
            }
            if (!trimmed.isEmpty()) {
                Map<String, Object> wrapped = new LinkedHashMap<String, Object>();
                wrapped.put("raw", trimmed);
                return wrapped;
            }
        }
        return new LinkedHashMap<String, Object>();
    }

    private Map<String, Object> parseFunctionResponsePayload(Object rawOutput) {
        if (rawOutput instanceof Map) {
            return asMap(rawOutput);
        }
        String text = rawOutput == null ? "" : String.valueOf(rawOutput);
        String trimmed = text.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                Map<String, Object> parsed = MAPPER.readValue(trimmed, MAP_TYPE);
                if (parsed != null) {
                    return parsed;
                }
            } catch (Exception ignored) {
            }
        }
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("result", text);
        return response;
    }

    private Map<String, Object> asMap(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map) {
            Map<?, ?> source = (Map<?, ?>) value;
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            for (Map.Entry<?, ?> entry : source.entrySet()) {
                if (entry.getKey() != null) {
                    result.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return result;
        }
        try {
            return MAPPER.convertValue(value, MAP_TYPE);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String extractThoughtSignature(Map<String, Object> map) {
        String sig = firstNonBlank(
                stringValue(map.get(METADATA_THOUGHT_SIGNATURE)),
                stringValue(map.get("thought_signature"))
        );
        if (sig != null) {
            return sig;
        }
        Map<String, Object> metadata = asMap(map.get("metadata"));
        return metadata == null ? null : stringValue(metadata.get(METADATA_THOUGHT_SIGNATURE));
    }

    private long resolveStreamTimeoutMillis(StreamExecutionOptions options) {
        if (options != null && options.getIdleTimeoutMs() > 0L) {
            return Math.max(DEFAULT_STREAM_TIMEOUT_MS, options.getIdleTimeoutMs());
        }
        return DEFAULT_STREAM_TIMEOUT_MS;
    }

    private static void appendParagraph(StringBuilder builder, String text) {
        if (isBlank(text)) {
            return;
        }
        if (builder.length() > 0) {
            builder.append("\n\n");
        }
        builder.append(text.trim());
    }

    private static String trimTrailingSlashes(String value) {
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static final class ResultAccumulator {

        private final StringBuilder outputText = new StringBuilder();
        private final StringBuilder reasoningText = new StringBuilder();
        private final List<AgentToolCall> toolCalls = new ArrayList<AgentToolCall>();
        private final List<JsonNode> rawChunks = new ArrayList<JsonNode>();
        private final AtomicInteger callSequence = new AtomicInteger(0);

        private Long inputTokens;
        private Long outputTokens;
        private Long totalTokens;
        private Long cacheReadInputTokens;
        private Long reasoningTokens;

        void consumeChunk(JsonNode chunk, AgentModelStreamListener listener) {
            if (chunk == null || chunk.isNull()) {
                return;
            }
            rawChunks.add(chunk);
            if (listener != null) {
                listener.onEvent(chunk);
            }

            JsonNode candidates = chunk.path("candidates");
            if (candidates.isArray() && candidates.size() > 0) {
                JsonNode parts = candidates.get(0).path("content").path("parts");
                if (parts.isArray()) {
                    for (JsonNode part : parts) {
                        consumePart(part, listener);
                    }
                }
            }

            JsonNode usageNode = chunk.path("usageMetadata");
            if (usageNode.isObject()) {
                inputTokens = readLong(usageNode, "promptTokenCount", inputTokens);
                outputTokens = readLong(usageNode, "candidatesTokenCount", outputTokens);
                totalTokens = readLong(usageNode, "totalTokenCount", totalTokens);
                cacheReadInputTokens = readLong(usageNode, "cachedContentTokenCount", cacheReadInputTokens);
                reasoningTokens = readLong(usageNode, "thoughtsTokenCount", reasoningTokens);
            }
        }

        private void consumePart(JsonNode part, AgentModelStreamListener listener) {
            if (part == null || part.isNull()) {
                return;
            }
            if (part.hasNonNull("text")) {
                String text = part.get("text").asText("");
                boolean isThought = part.path("thought").asBoolean(false);
                if (isThought) {
                    reasoningText.append(text);
                    if (listener != null && !text.isEmpty()) {
                        listener.onReasoningDelta(text);
                    }
                } else {
                    outputText.append(text);
                    if (listener != null && !text.isEmpty()) {
                        listener.onDeltaText(text);
                    }
                }
            }

            JsonNode functionCall = part.get("functionCall");
            if (functionCall != null && functionCall.isObject()) {
                String name = functionCall.path("name").asText("");
                if (!isBlank(name)) {
                    String callId = "call_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16)
                            + "_" + callSequence.incrementAndGet();
                    JsonNode argsNode = functionCall.get("args");
                    String arguments;
                    try {
                        arguments = argsNode == null || argsNode.isNull()
                                ? "{}"
                                : MAPPER.writeValueAsString(argsNode);
                    } catch (Exception ex) {
                        arguments = "{}";
                    }
                    String thoughtSignature = part.hasNonNull(METADATA_THOUGHT_SIGNATURE)
                            ? part.get(METADATA_THOUGHT_SIGNATURE).asText()
                            : (part.hasNonNull("thought_signature") ? part.get("thought_signature").asText() : null);
                    AgentToolCall call = AgentToolCall.builder()
                            .callId(callId)
                            .name(name)
                            .arguments(arguments)
                            .type("function")
                            .build();
                    if (!isBlank(thoughtSignature)) {
                        call.withMetadata(METADATA_THOUGHT_SIGNATURE, thoughtSignature);
                    }
                    toolCalls.add(call);
                    if (listener != null) {
                        listener.onToolCall(call);
                    }
                }
            }
        }

        AgentModelResult buildResult() {
            String output = outputText.toString();
            String reasoning = reasoningText.length() == 0 ? null : reasoningText.toString();
            List<Object> memoryItems = buildMemoryItems(output);
            Long uncached = null;
            if (inputTokens != null) {
                long cached = cacheReadInputTokens == null ? 0L : cacheReadInputTokens.longValue();
                uncached = Long.valueOf(Math.max(0L, inputTokens.longValue() - cached));
            }
            Object rawResponse = rawChunks.isEmpty() ? null
                    : (rawChunks.size() == 1 ? rawChunks.get(0) : rawChunks);
            return AgentModelResult.builder()
                    .outputText(output)
                    .reasoningText(reasoning)
                    .toolCalls(new ArrayList<AgentToolCall>(toolCalls))
                    .memoryItems(memoryItems)
                    .rawResponse(rawResponse)
                    .inputTokens(inputTokens)
                    .outputTokens(outputTokens)
                    .totalTokens(totalTokens)
                    .uncachedInputTokens(uncached)
                    .cacheReadInputTokens(cacheReadInputTokens)
                    .reasoningTokens(reasoningTokens)
                    .build();
        }

        private List<Object> buildMemoryItems(String output) {
            if (toolCalls.isEmpty() && isBlank(output)) {
                return Collections.emptyList();
            }
            if (toolCalls.isEmpty()) {
                return Collections.<Object>singletonList(AgentInputItem.message("assistant", output));
            }
            Map<String, Object> item = AgentInputItem.assistantToolCallsMessage(
                    isBlank(output) ? null : output,
                    toolCalls
            );
            // Attach optional Gemini thoughtSignature onto serialized tool_calls so multi-turn calls preserve it.
            Object rawCalls = item.get("tool_calls");
            if (rawCalls instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> serializedCalls = (List<Map<String, Object>>) rawCalls;
                for (int i = 0; i < serializedCalls.size() && i < toolCalls.size(); i++) {
                    AgentToolCall call = toolCalls.get(i);
                    if (call.getMetadata() != null && call.getMetadata().get(METADATA_THOUGHT_SIGNATURE) != null) {
                        serializedCalls.get(i).put(
                                METADATA_THOUGHT_SIGNATURE,
                                call.getMetadata().get(METADATA_THOUGHT_SIGNATURE)
                        );
                    }
                }
            }
            return Collections.<Object>singletonList(item);
        }

        private Long readLong(JsonNode node, String field, Long current) {
            if (node.hasNonNull(field)) {
                return Long.valueOf(node.get(field).asLong());
            }
            return current;
        }
    }
}
