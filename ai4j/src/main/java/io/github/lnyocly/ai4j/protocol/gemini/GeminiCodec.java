package io.github.lnyocly.ai4j.protocol.gemini;

import io.github.lnyocly.ai4j.model.AudioBlock;
import io.github.lnyocly.ai4j.model.Block;
import io.github.lnyocly.ai4j.model.DocumentBlock;
import io.github.lnyocly.ai4j.model.DroppedField;
import io.github.lnyocly.ai4j.model.EventDeltaKind;
import io.github.lnyocly.ai4j.model.ImageBlock;
import io.github.lnyocly.ai4j.model.MediaKind;
import io.github.lnyocly.ai4j.model.MediaRef;
import io.github.lnyocly.ai4j.model.Message;
import io.github.lnyocly.ai4j.model.MessageRole;
import io.github.lnyocly.ai4j.model.ModelCallExceptionMarker;
import io.github.lnyocly.ai4j.model.ModelCallStatus;
import io.github.lnyocly.ai4j.model.ModelEvent;
import io.github.lnyocly.ai4j.model.ModelEventType;
import io.github.lnyocly.ai4j.model.ModelItem;
import io.github.lnyocly.ai4j.model.ModelRequest;
import io.github.lnyocly.ai4j.model.ModelResult;
import io.github.lnyocly.ai4j.model.ModelUsage;
import io.github.lnyocly.ai4j.model.NativeItem;
import io.github.lnyocly.ai4j.model.OpaqueBlock;
import io.github.lnyocly.ai4j.model.Protocol;
import io.github.lnyocly.ai4j.model.ReasoningBlock;
import io.github.lnyocly.ai4j.model.ReasoningItem;
import io.github.lnyocly.ai4j.model.TextBlock;
import io.github.lnyocly.ai4j.model.ToolCallBlock;
import io.github.lnyocly.ai4j.model.ToolCallItem;
import io.github.lnyocly.ai4j.model.ToolCallValue;
import io.github.lnyocly.ai4j.model.ToolChoice;
import io.github.lnyocly.ai4j.model.ToolDeclaration;
import io.github.lnyocly.ai4j.model.ToolKind;
import io.github.lnyocly.ai4j.model.ToolResultBlock;
import io.github.lnyocly.ai4j.model.ToolResultItem;
import io.github.lnyocly.ai4j.model.ToolResultValue;
import io.github.lnyocly.ai4j.model.VideoBlock;
import io.github.lnyocly.ai4j.protocol.EndpointPolicy;
import io.github.lnyocly.ai4j.protocol.JsonWire;
import io.github.lnyocly.ai4j.protocol.ProtocolCodec;
import io.github.lnyocly.ai4j.protocol.RawSseEvent;
import io.github.lnyocly.ai4j.protocol.WireRequest;
import io.github.lnyocly.ai4j.protocol.WireResponse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GEMINI codec: Google Gemini {@code /v1beta/models/{model}:generateContent} and
 * {@code :streamGenerateContent?alt=sse} wire shape.
 *
 * <p>Preserves {@code thought: true} and {@code thoughtSignature} across
 * {@link ReasoningBlock}, {@link ReasoningItem}, {@link ToolCallBlock}, and
 * {@link ToolCallItem} round-trips, while stripping incompatible foreign
 * signatures on cross-protocol handoff.</p>
 */
public final class GeminiCodec implements ProtocolCodec {

    private static final String DEFAULT_SYNC_PATH = "/v1beta/models/{model}:generateContent";

    private static final Set<String> GENERATION_CONFIG_KEYS = Collections.unmodifiableSet(
            new HashSet<String>(Arrays.asList(
                    "temperature", "topP", "topK", "maxOutputTokens", "stopSequences",
                    "responseMimeType", "responseSchema", "thinkingConfig", "candidateCount",
                    "presencePenalty", "frequencyPenalty", "seed", "responseLogprobs",
                    "logprobs", "responseModalities")));

    private static final Set<String> ALLOWED_PROVIDER_OPTIONS = Collections.unmodifiableSet(
            new HashSet<String>(Arrays.asList(
                    "cachedContent", "cached_content",
                    "safetySettings", "safety_settings",
                    "generationConfig", "generation_config",
                    "toolConfig", "tool_config",
                    "labels",
                    "topK", "top_k",
                    "stopSequences", "stop_sequences", "stop",
                    "responseMimeType", "response_mime_type",
                    "responseSchema", "response_schema",
                    "thinkingConfig", "thinking_config",
                    "candidateCount", "presencePenalty", "frequencyPenalty",
                    "seed", "responseLogprobs", "logprobs", "responseModalities")));

    @Override
    public Protocol getProtocol() {
        return Protocol.GEMINI;
    }

    @Override
    public WireRequest encode(ModelRequest request, EndpointPolicy policy) {
        List<DroppedField> warnings = new ArrayList<DroppedField>();
        Map<String, Object> body = new LinkedHashMap<String, Object>();

        Map<String, String> callIdToName = new LinkedHashMap<String, String>();
        Map<String, String> callIdToNativeId = new LinkedHashMap<String, String>();
        indexToolCalls(request.getItems(), callIdToName, callIdToNativeId);

        List<Object> systemParts = new ArrayList<Object>();
        List<Map<String, Object>> contents = new ArrayList<Map<String, Object>>();

        for (ModelItem item : request.getItems()) {
            if (item instanceof Message && ((Message) item).getRole() == MessageRole.SYSTEM) {
                systemParts.addAll(encodeMessageParts((Message) item, callIdToName, callIdToNativeId, warnings));
                continue;
            }
            if (item instanceof Message) {
                Message message = (Message) item;
                String role = wireRole(message.getRole());
                List<Object> parts = encodeMessageParts(message, callIdToName, callIdToNativeId, warnings);
                if (!parts.isEmpty()) {
                    appendContent(contents, role, parts);
                }
            } else if (item instanceof ReasoningItem) {
                Object part = encodeReasoningItem((ReasoningItem) item, warnings);
                if (part != null) {
                    appendContent(contents, "model", Collections.singletonList(part));
                }
            } else if (item instanceof ToolCallItem) {
                Map<String, Object> part = encodeToolCallValue(((ToolCallItem) item).getValue(), warnings);
                if (part != null) {
                    appendContent(contents, "model", Collections.singletonList(part));
                }
            } else if (item instanceof ToolResultItem) {
                ToolResultItem resultItem = (ToolResultItem) item;
                Map<String, Object> part = encodeToolResult(
                        resultItem.getValue(), null, resultItem.getProtocolData(),
                        callIdToName, callIdToNativeId);
                appendContent(contents, "user", Collections.singletonList(part));
            } else if (item instanceof NativeItem) {
                NativeItem nativeItem = (NativeItem) item;
                if (nativeItem.getProtocol() == Protocol.GEMINI && !nativeItem.getPayload().isEmpty()) {
                    contents.add(new LinkedHashMap<String, Object>(nativeItem.getPayload()));
                } else if (nativeItem.getProtocol() == Protocol.GEMINI) {
                    warnings.add(DroppedField.of("items[native]",
                            "native item payload is empty and would emit an empty object"));
                } else {
                    warnings.add(DroppedField.of("items[native]",
                            "native item belongs to " + nativeItem.getProtocol()
                                    + " and is not replay-valid for GEMINI"));
                }
            } else {
                warnings.add(DroppedField.of("items[" + item.getType() + "]",
                        "GEMINI cannot express this top-level item kind"));
            }
        }

        if (!systemParts.isEmpty()) {
            Map<String, Object> systemInstruction = new LinkedHashMap<String, Object>();
            systemInstruction.put("parts", systemParts);
            body.put("systemInstruction", systemInstruction);
        }
        body.put("contents", contents);

        if (!request.getTools().isEmpty()) {
            List<Object> tools = encodeTools(request.getTools(), warnings);
            if (!tools.isEmpty()) {
                body.put("tools", tools);
            }
        }

        if (request.getToolChoice() != null) {
            Object toolConfig = toolChoiceToWire(request.getToolChoice(), warnings);
            if (toolConfig != null) {
                body.put("toolConfig", toolConfig);
            }
        }

        if (request.getParallelToolCalls() != null) {
            warnings.add(DroppedField.of("parallelToolCalls",
                    "GEMINI does not define a parallelToolCalls request flag"));
        }

        Map<String, Object> generationConfig = new LinkedHashMap<String, Object>();
        applyProviderOptions(request, policy, body, generationConfig, warnings);

        if (request.getTemperature() != null) {
            generationConfig.put("temperature", request.getTemperature());
        }
        if (request.getTopP() != null) {
            generationConfig.put("topP", request.getTopP());
        }
        if (request.getMaxOutputTokens() != null) {
            generationConfig.put("maxOutputTokens", request.getMaxOutputTokens());
        }
        if (!generationConfig.isEmpty()) {
            body.put("generationConfig", generationConfig);
        }

        boolean stream = Boolean.TRUE.equals(request.getStream());
        String path = resolvePath(policy == null ? null : policy.getPath(), request.getModel(), stream);
        return WireRequest.of(getProtocol(), "POST", path, body, null, stream, warnings);
    }

    public static String resolvePath(String templatePath, String model, boolean stream) {
        String base = (templatePath == null || templatePath.trim().isEmpty())
                ? DEFAULT_SYNC_PATH
                : templatePath.trim();
        String modelName = model == null ? "" : model.trim();
        if (modelName.startsWith("models/") && base.contains("models/{model}")) {
            modelName = modelName.substring("models/".length());
        }
        String resolved = base.replace("{model}", modelName);
        if (stream) {
            if (resolved.contains(":generateContent")) {
                resolved = resolved.replace(":generateContent", ":streamGenerateContent");
            }
            if (resolved.contains(":streamGenerateContent") && !resolved.contains("alt=")) {
                resolved = resolved + (resolved.indexOf('?') >= 0 ? "&alt=sse" : "?alt=sse");
            }
        }
        return resolved;
    }

    private static void indexToolCalls(List<ModelItem> items,
                                       Map<String, String> callIdToName,
                                       Map<String, String> callIdToNativeId) {
        for (ModelItem item : items) {
            if (item instanceof ToolCallItem) {
                registerToolCall(((ToolCallItem) item).getValue(), callIdToName, callIdToNativeId);
            } else if (item instanceof Message) {
                for (Block block : ((Message) item).getBlocks()) {
                    if (block instanceof ToolCallBlock) {
                        registerToolCall(((ToolCallBlock) block).getValue(), callIdToName, callIdToNativeId);
                    }
                }
            }
        }
    }

    private static void registerToolCall(ToolCallValue value,
                                         Map<String, String> callIdToName,
                                         Map<String, String> callIdToNativeId) {
        if (value == null || value.getCallId() == null) {
            return;
        }
        if (value.getName() != null && !value.getName().isEmpty()) {
            callIdToName.put(value.getCallId(), value.getName());
        }
        Object nativeId = value.getProtocolData().get("gemini_call_id");
        if (nativeId != null) {
            callIdToNativeId.put(value.getCallId(), String.valueOf(nativeId));
        }
    }

    private static void appendContent(List<Map<String, Object>> contents, String role, List<Object> parts) {
        if (parts.isEmpty()) {
            return;
        }
        if (!contents.isEmpty()) {
            Map<String, Object> last = contents.get(contents.size() - 1);
            if (role.equals(last.get("role"))) {
                @SuppressWarnings("unchecked")
                List<Object> existingParts = (List<Object>) last.get("parts");
                existingParts.addAll(parts);
                return;
            }
        }
        Map<String, Object> content = new LinkedHashMap<String, Object>();
        content.put("role", role);
        content.put("parts", new ArrayList<Object>(parts));
        contents.add(content);
    }

    private List<Object> encodeMessageParts(Message message,
                                            Map<String, String> callIdToName,
                                            Map<String, String> callIdToNativeId,
                                            List<DroppedField> warnings) {
        List<Object> parts = new ArrayList<Object>();
        for (Block block : message.getBlocks()) {
            if (block instanceof TextBlock) {
                Map<String, Object> part = new LinkedHashMap<String, Object>();
                part.put("text", ((TextBlock) block).getText());
                String sig = extractCompatibleThoughtSignature(block.getProtocolData(), warnings,
                        "message.block.text");
                if (sig != null) {
                    part.put("thoughtSignature", sig);
                }
                parts.add(part);
            } else if (block instanceof ReasoningBlock) {
                Object part = encodeReasoningBlock((ReasoningBlock) block, warnings);
                if (part != null) {
                    parts.add(part);
                }
            } else if (block instanceof ToolCallBlock) {
                Map<String, Object> part = encodeToolCallValue(((ToolCallBlock) block).getValue(), warnings);
                if (part != null) {
                    parts.add(part);
                }
            } else if (block instanceof ToolResultBlock) {
                ToolResultBlock tr = (ToolResultBlock) block;
                parts.add(encodeToolResult(tr.getValue(), message.getName(), tr.getProtocolData(),
                        callIdToName, callIdToNativeId));
            } else if (block instanceof ImageBlock) {
                Map<String, Object> part = encodeMediaPart(((ImageBlock) block).getMedia(), null,
                        "message.content.image", warnings);
                if (part != null) {
                    parts.add(part);
                }
            } else if (block instanceof AudioBlock) {
                Map<String, Object> part = encodeMediaPart(((AudioBlock) block).getMedia(), null,
                        "message.content.audio", warnings);
                if (part != null) {
                    parts.add(part);
                }
            } else if (block instanceof VideoBlock) {
                Map<String, Object> part = encodeMediaPart(((VideoBlock) block).getMedia(), null,
                        "message.content.video", warnings);
                if (part != null) {
                    parts.add(part);
                }
            } else if (block instanceof DocumentBlock) {
                DocumentBlock doc = (DocumentBlock) block;
                Map<String, Object> part = encodeMediaPart(doc.getMedia(), doc.getMediaType(),
                        "message.content.document", warnings);
                if (part != null) {
                    parts.add(part);
                }
            } else if (block instanceof OpaqueBlock) {
                OpaqueBlock opaque = (OpaqueBlock) block;
                Map<String, Object> payload = new LinkedHashMap<String, Object>(opaque.getPayload());
                if ("GEMINI".equals(payload.get("_protocol"))) {
                    payload.remove("_protocol");
                    parts.add(payload);
                } else {
                    warnings.add(DroppedField.of("message.block.opaque",
                            "opaque block of type " + opaque.getNativeType()
                                    + " is not replay-valid for GEMINI"));
                }
            }
        }
        return parts;
    }

    private Object encodeReasoningBlock(ReasoningBlock block, List<DroppedField> warnings) {
        Protocol source = block.getSourceProtocol();
        if (source != null && source != Protocol.GEMINI) {
            warnings.add(DroppedField.of("message.block.reasoning",
                    "foreign reasoning from " + source + " stripped on GEMINI handoff"));
            return null;
        }
        Map<String, Object> part = new LinkedHashMap<String, Object>();
        part.put("thought", Boolean.TRUE);
        part.put("text", block.getText());
        String sig = block.getSignature();
        if (sig == null) {
            sig = extractCompatibleThoughtSignature(block.getProtocolData(), warnings,
                    "message.block.reasoning");
        }
        if (sig != null) {
            part.put("thoughtSignature", sig);
        }
        return part;
    }

    private Object encodeReasoningItem(ReasoningItem item, List<DroppedField> warnings) {
        Protocol source = item.getSourceProtocol();
        if (source != null && source != Protocol.GEMINI) {
            warnings.add(DroppedField.of("items[reasoning]",
                    "foreign reasoning from " + source + " stripped on GEMINI handoff"));
            return null;
        }
        Map<String, Object> part = new LinkedHashMap<String, Object>();
        part.put("thought", Boolean.TRUE);
        String text = item.getText() != null ? item.getText() : item.getSummary();
        if (text != null) {
            part.put("text", text);
        }
        String sig = item.getSignature();
        if (sig == null) {
            sig = extractCompatibleThoughtSignature(item.getProtocolData(), warnings, "items[reasoning]");
        }
        if (sig != null) {
            part.put("thoughtSignature", sig);
        }
        return part;
    }

    private Map<String, Object> encodeToolCallValue(ToolCallValue value, List<DroppedField> warnings) {
        Map<String, Object> fnCall = new LinkedHashMap<String, Object>();
        fnCall.put("name", value.getName());
        fnCall.put("args", parseObjectArguments(value.getArgumentsJson()));
        Object nativeId = value.getProtocolData().get("gemini_call_id");
        if (nativeId != null && !String.valueOf(nativeId).isEmpty()) {
            fnCall.put("id", String.valueOf(nativeId));
        }

        Map<String, Object> part = new LinkedHashMap<String, Object>();
        part.put("functionCall", fnCall);

        Protocol source = value.getSourceProtocol();
        String thoughtSig = value.getThoughtSignature();
        if (thoughtSig != null) {
            if (source == null || source == Protocol.GEMINI) {
                part.put("thoughtSignature", thoughtSig);
            } else {
                warnings.add(DroppedField.of("toolCall.thoughtSignature",
                        "foreign signature from " + source + " stripped on GEMINI handoff"));
            }
        }
        return part;
    }

    private Map<String, Object> encodeToolResult(ToolResultValue value,
                                               String messageName,
                                               Map<String, Object> wrapperProtocolData,
                                               Map<String, String> callIdToName,
                                               Map<String, String> callIdToNativeId) {
        String name = JsonWire.textAt(wrapperProtocolData, "name");
        if (name == null) {
            name = JsonWire.textAt(value.getProtocolData(), "name");
        }
        if (name == null) {
            name = callIdToName.get(value.getCallId());
        }
        if (name == null) {
            name = messageName;
        }
        if (name == null || name.trim().isEmpty()) {
            name = value.getCallId();
        }

        Map<String, Object> fnResp = new LinkedHashMap<String, Object>();
        fnResp.put("name", name);
        String nativeId = callIdToNativeId.get(value.getCallId());
        if (nativeId != null && !nativeId.isEmpty()) {
            fnResp.put("id", nativeId);
        }
        fnResp.put("response", buildFunctionResponsePayload(value));

        Map<String, Object> part = new LinkedHashMap<String, Object>();
        part.put("functionResponse", fnResp);
        return part;
    }

    private static Map<String, Object> buildFunctionResponsePayload(ToolResultValue value) {
        String text = joinText(value.getContent());
        if (value.isError()) {
            Map<String, Object> errorMap = new LinkedHashMap<String, Object>();
            errorMap.put("error", text);
            return errorMap;
        }
        if (text != null && !text.trim().isEmpty()) {
            String trimmed = text.trim();
            if (trimmed.startsWith("{")) {
                try {
                    Map<String, Object> parsed = JsonWire.readObject(trimmed);
                    if (!parsed.isEmpty()) {
                        return parsed;
                    }
                } catch (IllegalArgumentException ignored) {
                    // fall back to wrapping as text result
                }
            }
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("result", text == null ? "" : text);
        return result;
    }

    private static Map<String, Object> encodeMediaPart(MediaRef media, String overrideMimeType,
                                                       String fieldPath, List<DroppedField> warnings) {
        String mimeType = (overrideMimeType != null && !overrideMimeType.isEmpty())
                ? overrideMimeType
                : media.getMediaType();
        if (media.getKind() == MediaKind.INLINE_BASE64) {
            if (mimeType == null || mimeType.trim().isEmpty()) {
                warnings.add(DroppedField.of(fieldPath,
                        "GEMINI inlineData requires an explicit mimeType"));
                return null;
            }
            Map<String, Object> inlineData = new LinkedHashMap<String, Object>();
            inlineData.put("mimeType", mimeType);
            inlineData.put("data", media.getValue());
            Map<String, Object> part = new LinkedHashMap<String, Object>();
            part.put("inlineData", inlineData);
            return part;
        }
        Map<String, Object> fileData = new LinkedHashMap<String, Object>();
        if (mimeType != null && !mimeType.trim().isEmpty()) {
            fileData.put("mimeType", mimeType);
        }
        fileData.put("fileUri", media.getValue());
        Map<String, Object> part = new LinkedHashMap<String, Object>();
        part.put("fileData", fileData);
        return part;
    }

    private static String extractCompatibleThoughtSignature(Map<String, Object> protocolData,
                                                            List<DroppedField> warnings,
                                                            String fieldPath) {
        if (protocolData == null || protocolData.isEmpty()) {
            return null;
        }
        String sig = JsonWire.textAt(protocolData, "thoughtSignature");
        if (sig == null) {
            return null;
        }
        String proto = JsonWire.textAt(protocolData, "_protocol");
        if (proto != null && !"GEMINI".equals(proto)) {
            warnings.add(DroppedField.of(fieldPath + ".thoughtSignature",
                    "foreign signature from " + proto + " stripped on GEMINI handoff"));
            return null;
        }
        return sig;
    }

    private static List<Object> encodeTools(List<ToolDeclaration> declarations, List<DroppedField> warnings) {
        List<Object> tools = new ArrayList<Object>();
        List<Object> functionDeclarations = new ArrayList<Object>();
        for (ToolDeclaration declaration : declarations) {
            if (declaration.getKind() == ToolKind.FUNCTION) {
                Map<String, Object> fn = new LinkedHashMap<String, Object>();
                fn.put("name", declaration.getName());
                if (declaration.getDescription() != null) {
                    fn.put("description", declaration.getDescription());
                }
                if (declaration.getParameters() != null && !declaration.getParameters().isEmpty()) {
                    fn.put("parameters", declaration.getParameters());
                }
                if (declaration.getStrict() != null) {
                    warnings.add(DroppedField.of("tools[" + declaration.getName() + "].strict",
                            "GEMINI functionDeclarations do not define a strict schema flag"));
                }
                functionDeclarations.add(fn);
            } else if (declaration.getKind() == ToolKind.HOSTED || declaration.getKind() == ToolKind.NATIVE) {
                Map<String, Object> nativeSpec = new LinkedHashMap<String, Object>(declaration.getNativeSpec());
                if (nativeSpec.isEmpty() || nativeSpec.containsKey("type")) {
                    warnings.add(DroppedField.of("tools[" + declaration.getName() + "]",
                            "hosted/native tool shape belongs to another protocol and has no GEMINI shape"));
                } else {
                    tools.add(nativeSpec);
                }
            } else {
                warnings.add(DroppedField.of("tools[" + declaration.getName() + "]",
                        declaration.getKind() + " tools have no GEMINI shape"));
            }
        }
        if (!functionDeclarations.isEmpty()) {
            Map<String, Object> declContainer = new LinkedHashMap<String, Object>();
            declContainer.put("functionDeclarations", functionDeclarations);
            tools.add(0, declContainer);
        }
        return tools;
    }

    private Object toolChoiceToWire(ToolChoice choice, List<DroppedField> warnings) {
        if (choice.isFunctionChoice()) {
            Map<String, Object> fc = new LinkedHashMap<String, Object>();
            fc.put("mode", "ANY");
            fc.put("allowedFunctionNames", Collections.singletonList(choice.getFunctionName()));
            return Collections.singletonMap("functionCallingConfig", fc);
        }
        if (choice.isNativeChoice()) {
            if (choice.getNativeProtocol() == Protocol.GEMINI) {
                return choice.getNativeChoice();
            }
            warnings.add(DroppedField.of("toolConfig",
                    "native tool choice belongs to " + choice.getNativeProtocol()));
            return null;
        }
        if (choice.getMode() == null) {
            return null;
        }
        String mode;
        switch (choice.getMode()) {
            case AUTO:
                mode = "AUTO";
                break;
            case NONE:
                mode = "NONE";
                break;
            case REQUIRED:
                mode = "ANY";
                break;
            default:
                return null;
        }
        return Collections.singletonMap("functionCallingConfig",
                Collections.singletonMap("mode", mode));
    }

    @SuppressWarnings("unchecked")
    private static void applyProviderOptions(ModelRequest request, EndpointPolicy policy,
                                             Map<String, Object> body,
                                             Map<String, Object> generationConfig,
                                             List<DroppedField> warnings) {
        Map<String, Object> merged = new LinkedHashMap<String, Object>();
        if (policy != null && policy.getProfile() != null) {
            merged.putAll(policy.getProfile().getDefaultProviderOptions());
        }
        merged.putAll(request.getProviderOptions());
        for (Map.Entry<String, Object> entry : merged.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (!ALLOWED_PROVIDER_OPTIONS.contains(key)) {
                warnings.add(DroppedField.of("providerOptions." + key,
                        "not in the GEMINI wire whitelist"));
                continue;
            }
            if ("generationConfig".equals(key) || "generation_config".equals(key)) {
                if (value instanceof Map) {
                    generationConfig.putAll((Map<String, Object>) value);
                }
            } else if ("cachedContent".equals(key) || "cached_content".equals(key)) {
                body.put("cachedContent", value);
            } else if ("safetySettings".equals(key) || "safety_settings".equals(key)) {
                body.put("safetySettings", value);
            } else if ("toolConfig".equals(key) || "tool_config".equals(key)) {
                if (!body.containsKey("toolConfig")) {
                    body.put("toolConfig", value);
                }
            } else if ("labels".equals(key)) {
                body.put("labels", value);
            } else if ("topK".equals(key) || "top_k".equals(key)) {
                generationConfig.put("topK", value);
            } else if ("stopSequences".equals(key) || "stop_sequences".equals(key) || "stop".equals(key)) {
                generationConfig.put("stopSequences", value);
            } else if ("responseMimeType".equals(key) || "response_mime_type".equals(key)) {
                generationConfig.put("responseMimeType", value);
            } else if ("responseSchema".equals(key) || "response_schema".equals(key)) {
                generationConfig.put("responseSchema", value);
            } else if ("thinkingConfig".equals(key) || "thinking_config".equals(key)) {
                generationConfig.put("thinkingConfig", value);
            } else if (GENERATION_CONFIG_KEYS.contains(key)) {
                generationConfig.put(key, value);
            }
        }
    }

    @Override
    public ModelResult decode(WireResponse response) {
        Map<String, Object> root = JsonWire.readObject(response.getBodyText());
        ModelResult.Builder builder = ModelResult.builder();
        builder.raw(root);
        builder.responseId(JsonWire.textAt(root, "responseId"));
        builder.usage(decodeUsage(JsonWire.objectAt(root, "usageMetadata")));

        List<Object> candidates = JsonWire.arrayAt(root, "candidates");
        if (!candidates.isEmpty() && candidates.get(0) instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> candidate = (Map<String, Object>) candidates.get(0);
            builder.stopReason(JsonWire.textAt(candidate, "finishReason"));
            Map<String, Object> content = JsonWire.objectAt(candidate, "content");
            List<Object> parts = JsonWire.arrayAt(content, "parts");
            builder.items(decodeParts(parts, new int[]{0}));
        }
        return builder.build();
    }

    private List<ModelItem> decodeParts(List<Object> parts, int[] callCounter) {
        List<ModelItem> items = new ArrayList<ModelItem>();
        List<Block> messageBlocks = new ArrayList<Block>();

        for (Object rawPart : parts) {
            if (!(rawPart instanceof Map)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> part = (Map<String, Object>) rawPart;
            if (Boolean.TRUE.equals(part.get("thought"))) {
                flushAssistantMessage(items, messageBlocks);
                String text = JsonWire.textAt(part, "text");
                String sig = JsonWire.textAt(part, "thoughtSignature");
                items.add(ReasoningItem.of(text == null ? "" : text, null, sig, Protocol.GEMINI,
                        null, null, taggedPayload(part)));
                continue;
            }
            if (part.get("functionCall") instanceof Map) {
                flushAssistantMessage(items, messageBlocks);
                items.add(decodeFunctionCallPart(part, ++callCounter[0]));
                continue;
            }
            Object textObj = part.get("text");
            if (textObj instanceof String) {
                String sig = JsonWire.textAt(part, "thoughtSignature");
                Map<String, Object> protocolData = null;
                if (sig != null) {
                    protocolData = new LinkedHashMap<String, Object>();
                    protocolData.put("_protocol", "GEMINI");
                    protocolData.put("thoughtSignature", sig);
                }
                messageBlocks.add(TextBlock.of((String) textObj, null, null, protocolData));
                continue;
            }
            String firstKey = part.isEmpty() ? "unknown" : part.keySet().iterator().next();
            messageBlocks.add(OpaqueBlock.of(firstKey, taggedPayload(part)));
        }
        flushAssistantMessage(items, messageBlocks);
        return items;
    }

    private static void flushAssistantMessage(List<ModelItem> items, List<Block> blocks) {
        if (blocks.isEmpty()) {
            return;
        }
        items.add(Message.assistant().blocks(blocks).build());
        blocks.clear();
    }

    private static ToolCallItem decodeFunctionCallPart(Map<String, Object> part, int callIndex) {
        Map<String, Object> fnCall = JsonWire.objectAt(part, "functionCall");
        String name = JsonWire.textAt(fnCall, "name");
        Map<String, Object> args = JsonWire.objectAt(fnCall, "args");
        String nativeId = JsonWire.textAt(fnCall, "id");
        String callId = (nativeId != null && !nativeId.isEmpty())
                ? nativeId
                : ("gemini_call_" + callIndex);
        String sig = JsonWire.textAt(part, "thoughtSignature");

        Map<String, Object> valueProtocolData = new LinkedHashMap<String, Object>();
        valueProtocolData.put("_protocol", "GEMINI");
        if (sig != null) {
            valueProtocolData.put("thoughtSignature", sig);
        }
        if (nativeId != null && !nativeId.isEmpty()) {
            valueProtocolData.put("gemini_call_id", nativeId);
        }

        ToolCallValue value = ToolCallValue.of(
                callId,
                name,
                JsonWire.write(args),
                sig,
                Protocol.GEMINI,
                valueProtocolData);

        Map<String, Object> itemProtocolData = new LinkedHashMap<String, Object>(valueProtocolData);
        itemProtocolData.put("gemini", part);
        return ToolCallItem.of(value, callId, null, itemProtocolData);
    }

    private static ModelUsage decodeUsage(Map<String, Object> usage) {
        if (usage.isEmpty()) {
            return null;
        }
        ModelUsage.Builder builder = ModelUsage.builder();
        Long prompt = JsonWire.longAt(usage, "promptTokenCount");
        Long candidates = JsonWire.longAt(usage, "candidatesTokenCount");
        Long thoughts = JsonWire.longAt(usage, "thoughtsTokenCount");
        Long cached = JsonWire.longAt(usage, "cachedContentTokenCount");
        Long total = JsonWire.longAt(usage, "totalTokenCount");
        if (prompt != null) {
            builder.inputTokens(prompt);
        }
        if (candidates != null) {
            builder.outputTokens(candidates);
        }
        if (thoughts != null) {
            builder.reasoningTokens(thoughts);
        }
        if (cached != null) {
            builder.cachedInputTokens(cached);
        }
        if (total != null) {
            builder.totalTokens(total);
        }
        return builder.build();
    }

    @Override
    public ModelEvent decodeEvent(RawSseEvent event) {
        String data = event.getData();
        if (data == null || data.trim().isEmpty()) {
            return null;
        }
        String trimmed = data.trim();
        if ("[DONE]".equals(trimmed)) {
            return ModelEvent.builder(Protocol.GEMINI, ModelEventType.DONE).build();
        }
        Map<String, Object> root = readChunkObject(trimmed);
        if (root.isEmpty()) {
            return null;
        }
        Map<String, Object> error = JsonWire.objectAt(root, "error");
        if (!error.isEmpty()) {
            Integer code = JsonWire.intAt(error, "code");
            String status = JsonWire.textAt(error, "status");
            String msg = JsonWire.textAt(error, "message");
            int httpStatus = code != null ? code : 0;
            boolean retryable = httpStatus == 429 || httpStatus == 408 || httpStatus == 504 || httpStatus >= 500;
            return ModelEvent.builder(Protocol.GEMINI, ModelEventType.ERROR)
                    .failure(ModelCallExceptionMarker.builder(Protocol.GEMINI)
                            .httpStatus(httpStatus)
                            .providerErrorType(status)
                            .retryable(retryable)
                            .message(msg == null ? "Gemini stream error" : msg)
                            .rawSnippet(trimmed.length() <= 500 ? trimmed : trimmed.substring(0, 500))
                            .status(ModelCallStatus.FAILED)
                            .build())
                    .build();
        }

        String responseId = JsonWire.textAt(root, "responseId");
        ModelUsage usage = decodeUsage(JsonWire.objectAt(root, "usageMetadata"));
        List<Object> candidates = JsonWire.arrayAt(root, "candidates");
        String finishReason = null;
        List<Object> parts = Collections.emptyList();
        if (!candidates.isEmpty() && candidates.get(0) instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> candidate = (Map<String, Object>) candidates.get(0);
            finishReason = JsonWire.textAt(candidate, "finishReason");
            Map<String, Object> content = JsonWire.objectAt(candidate, "content");
            parts = JsonWire.arrayAt(content, "parts");
        }

        if (parts.isEmpty()) {
            if (finishReason != null) {
                return ModelEvent.builder(Protocol.GEMINI, ModelEventType.ITEM_DONE)
                        .itemId(responseId)
                        .stopReason(finishReason)
                        .usage(usage)
                        .build();
            }
            if (usage != null) {
                return ModelEvent.builder(Protocol.GEMINI, ModelEventType.USAGE)
                        .itemId(responseId)
                        .usage(usage)
                        .build();
            }
            return ModelEvent.builder(Protocol.GEMINI, ModelEventType.OPAQUE)
                    .itemType("gemini.chunk")
                    .itemId(responseId)
                    .build();
        }

        if (parts.size() == 1 && parts.get(0) instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> part = (Map<String, Object>) parts.get(0);
            if (Boolean.TRUE.equals(part.get("thought"))) {
                String text = JsonWire.textAt(part, "text");
                String sig = JsonWire.textAt(part, "thoughtSignature");
                return ModelEvent.builder(Protocol.GEMINI, ModelEventType.ITEM_DELTA)
                        .outputIndex(0)
                        .delta(EventDeltaKind.REASONING, text == null ? "" : text)
                        .signature(sig)
                        .itemId(responseId)
                        .stopReason(finishReason)
                        .usage(usage)
                        .build();
            }
            if (part.get("functionCall") instanceof Map) {
                ToolCallItem toolCall = decodeFunctionCallPart(part, 1);
                return ModelEvent.builder(Protocol.GEMINI, ModelEventType.ITEM_DONE)
                        .outputIndex(2)
                        .itemId(toolCall.getCallId())
                        .toolName(toolCall.getName())
                        .signature(toolCall.getValue().getThoughtSignature())
                        .item(toolCall)
                        .stopReason(finishReason)
                        .usage(usage)
                        .build();
            }
            Object textObj = part.get("text");
            if (textObj instanceof String) {
                String sig = JsonWire.textAt(part, "thoughtSignature");
                return ModelEvent.builder(Protocol.GEMINI, ModelEventType.ITEM_DELTA)
                        .outputIndex(1)
                        .delta(EventDeltaKind.TEXT, (String) textObj)
                        .signature(sig)
                        .itemId(responseId)
                        .stopReason(finishReason)
                        .usage(usage)
                        .build();
            }
        }

        List<ModelItem> decodedItems = decodeParts(parts, new int[]{0});
        ModelEvent.Builder multi = ModelEvent.builder(Protocol.GEMINI, ModelEventType.ITEM_DONE)
                .items(decodedItems)
                .itemId(responseId)
                .stopReason(finishReason)
                .usage(usage);
        if (!decodedItems.isEmpty()) {
            multi.item(decodedItems.get(0));
            if (decodedItems.get(0) instanceof ToolCallItem) {
                ToolCallItem firstCall = (ToolCallItem) decodedItems.get(0);
                multi.toolName(firstCall.getName())
                        .signature(firstCall.getValue().getThoughtSignature());
            }
        }
        return multi.build();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readChunkObject(String trimmed) {
        if (trimmed.startsWith("[")) {
            Object parsed = JsonWire.readValue(trimmed);
            if (parsed instanceof List) {
                List<?> list = (List<?>) parsed;
                if (!list.isEmpty() && list.get(0) instanceof Map) {
                    return (Map<String, Object>) list.get(0);
                }
            }
            return Collections.emptyMap();
        }
        return JsonWire.readObject(trimmed);
    }

    private static Map<String, Object> taggedPayload(Map<String, Object> part) {
        Map<String, Object> tagged = new LinkedHashMap<String, Object>(part);
        tagged.put("_protocol", "GEMINI");
        return tagged;
    }

    private static Map<String, Object> parseObjectArguments(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.trim().isEmpty()) {
            return new LinkedHashMap<String, Object>();
        }
        try {
            Map<String, Object> parsed = JsonWire.readObject(argumentsJson);
            return parsed.isEmpty() ? new LinkedHashMap<String, Object>() : new LinkedHashMap<String, Object>(parsed);
        } catch (IllegalArgumentException ignored) {
            return new LinkedHashMap<String, Object>();
        }
    }

    private static String joinText(List<Block> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Block block : blocks) {
            if (block instanceof TextBlock) {
                sb.append(((TextBlock) block).getText());
            }
        }
        return sb.toString();
    }

    private static String wireRole(MessageRole role) {
        if (role == MessageRole.ASSISTANT) {
            return "model";
        }
        return "user";
    }
}
