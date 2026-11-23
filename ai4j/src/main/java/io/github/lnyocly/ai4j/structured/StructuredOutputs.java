package io.github.lnyocly.ai4j.structured;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.lnyocly.ai4j.codec.JsonCodec;
import io.github.lnyocly.ai4j.convert.JsonSchemaGenerator;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletion;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletionResponse;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatMessage;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.Choice;
import io.github.lnyocly.ai4j.service.IChatService;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * One-line structured output over any {@link IChatService}: generate the JSON Schema from a
 * POJO class or {@link TypeReference}, attach it as {@code response_format}, call the model,
 * strip Markdown fences, and deserialize leniently into the target type.
 */
public final class StructuredOutputs {

    private StructuredOutputs() {
    }

    /** Send a chat request and parse the answer into {@code type}. */
    public static <T> T chat(IChatService chatService, ChatCompletion request, Class<T> type) throws Exception {
        return chatDetailed(chatService, request, type, null).getValue();
    }

    /** Send a chat request with an explicit schema name and parse into {@code type}. */
    public static <T> T chat(IChatService chatService, ChatCompletion request, Class<T> type, String schemaName) throws Exception {
        return chatDetailed(chatService, request, type, schemaName).getValue();
    }

    /** Convenience form: model + single user prompt. */
    public static <T> T chat(IChatService chatService, String model, String userPrompt, Class<T> type) throws Exception {
        ChatCompletion request = ChatCompletion.builder()
                .model(model)
                .message(ChatMessage.withUser(userPrompt))
                .build();
        return chat(chatService, request, type);
    }

    /** Send a chat request and parse the answer into {@code typeReference}. */
    public static <T> T chat(IChatService chatService,
                             ChatCompletion request,
                             TypeReference<T> typeReference) throws Exception {
        return chatDetailed(chatService, request, typeReference, null).getValue();
    }

    /** Send a chat request with an explicit schema name and parse into {@code typeReference}. */
    public static <T> T chat(IChatService chatService,
                             ChatCompletion request,
                             TypeReference<T> typeReference,
                             String schemaName) throws Exception {
        return chatDetailed(chatService, request, typeReference, schemaName).getValue();
    }

    /** Convenience form: model + single user prompt + generic {@link TypeReference}. */
    public static <T> T chat(IChatService chatService,
                             String model,
                             String userPrompt,
                             TypeReference<T> typeReference) throws Exception {
        ChatCompletion request = ChatCompletion.builder()
                .model(model)
                .message(ChatMessage.withUser(userPrompt))
                .build();
        return chat(chatService, request, typeReference);
    }

    /** Full form returning the parsed value plus raw text and token usage. */
    public static <T> StructuredResult<T> chatDetailed(IChatService chatService,
                                                     ChatCompletion request,
                                                     Class<T> type) throws Exception {
        return chatDetailed(chatService, request, type, null);
    }

    /** Full form with an explicit schema name. */
    public static <T> StructuredResult<T> chatDetailed(IChatService chatService,
                                                     ChatCompletion request,
                                                     Class<T> type,
                                                     String schemaName) throws Exception {
        validateCommonArgs(chatService, request);
        if (type == null) {
            throw new IllegalArgumentException("type is required");
        }

        ChatCompletion effective = withDefaultResponseFormat(request, type, schemaName);
        ChatCompletionResponse response = chatService.chatCompletion(effective);
        String text = extractText(response);
        T value = parse(text, type);
        return new StructuredResult<T>(value, text, response == null ? null : response.getUsage());
    }

    /** Full form for parameterized target types using {@link TypeReference}. */
    public static <T> StructuredResult<T> chatDetailed(IChatService chatService,
                                                     ChatCompletion request,
                                                     TypeReference<T> typeReference) throws Exception {
        return chatDetailed(chatService, request, typeReference, null);
    }

    /** Full form with an explicit schema name for parameterized target types using {@link TypeReference}. */
    public static <T> StructuredResult<T> chatDetailed(IChatService chatService,
                                                     ChatCompletion request,
                                                     TypeReference<T> typeReference,
                                                     String schemaName) throws Exception {
        validateCommonArgs(chatService, request);
        if (typeReference == null) {
            throw new IllegalArgumentException("typeReference is required");
        }

        ChatCompletion effective = withDefaultResponseFormat(request, typeReference.getType(), schemaName);
        ChatCompletionResponse response = chatService.chatCompletion(effective);
        String text = extractText(response);
        T value = parse(text, typeReference);
        return new StructuredResult<T>(value, text, response == null ? null : response.getUsage());
    }

    private static void validateCommonArgs(IChatService chatService, ChatCompletion request) {
        if (chatService == null) {
            throw new IllegalArgumentException("chatService is required");
        }
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
    }

    private static ChatCompletion withDefaultResponseFormat(ChatCompletion request, Type targetType, String schemaName) {
        if (request.getResponseFormat() != null) {
            return request;
        }
        return request.toBuilder()
                .responseFormat(JsonSchemaGenerator.responseFormat(targetType, schemaName))
                .build();
    }

    private static String extractText(ChatCompletionResponse response) {
        if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
            throw new StructuredOutputException("model returned no choices");
        }
        Choice choice = response.getChoices().get(0);
        if ("length".equals(choice.getFinishReason())) {
            throw new StructuredOutputException(
                    "model output truncated (finish_reason=length); raise max tokens or shrink the schema");
        }
        ChatMessage message = choice.getMessage();
        if (message == null) {
            throw new StructuredOutputException("model returned an empty message");
        }
        if (message.getRefusal() != null && !message.getRefusal().isEmpty()) {
            throw new StructuredOutputException("model refused: " + message.getRefusal(), message.getRefusal());
        }
        if (message.getContent() == null || message.getContent().getText() == null) {
            throw new StructuredOutputException("model returned no text content");
        }
        return stripFence(message.getContent().getText());
    }

    private static <T> T parse(String text, Class<T> type) {
        try {
            T value = JsonCodec.fromJsonLenient(text, type);
            if (value == null) {
                throw new StructuredOutputException("model output parsed to null", text);
            }
            return value;
        } catch (StructuredOutputException e) {
            throw e;
        } catch (Exception e) {
            throw new StructuredOutputException(
                    "model output is not valid " + type.getSimpleName() + " JSON: " + e.getMessage(), text, e);
        }
    }

    private static <T> T parse(String text, TypeReference<T> typeReference) {
        try {
            T value = JsonCodec.fromJsonLenient(text, typeReference);
            if (value == null) {
                throw new StructuredOutputException("model output parsed to null", text);
            }
            return value;
        } catch (StructuredOutputException e) {
            throw e;
        } catch (Exception e) {
            throw new StructuredOutputException(
                    "model output is not valid " + describeType(typeReference.getType()) + " JSON: " + e.getMessage(),
                    text,
                    e);
        }
    }

    private static String describeType(Type type) {
        if (type instanceof Class<?>) {
            return ((Class<?>) type).getSimpleName();
        }
        if (type instanceof ParameterizedType && ((ParameterizedType) type).getRawType() instanceof Class<?>) {
            return ((Class<?>) ((ParameterizedType) type).getRawType()).getSimpleName();
        }
        return type == null ? "target" : type.toString();
    }

    /**
     * Strip a Markdown code fence ({@code ```json ... ```} or {@code ``` ... ```}) if the model
     * wrapped its JSON answer in one. Returns the trimmed inner text otherwise.
     */
    static String stripFence(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstNewline = trimmed.indexOf('\n');
        if (firstNewline < 0) {
            return trimmed;
        }
        String body = trimmed.substring(firstNewline + 1);
        int fenceEnd = body.lastIndexOf("```");
        if (fenceEnd >= 0) {
            body = body.substring(0, fenceEnd);
        }
        return body.trim();
    }
}
