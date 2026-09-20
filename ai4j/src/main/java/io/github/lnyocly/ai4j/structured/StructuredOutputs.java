package io.github.lnyocly.ai4j.structured;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.convert.JsonSchemaGenerator;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletion;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletionResponse;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatMessage;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.Choice;
import io.github.lnyocly.ai4j.service.IChatService;

import java.util.List;

/**
 * One-line structured output over any {@link IChatService}: generate the JSON Schema from a
 * POJO class, attach it as {@code response_format}, call the model, strip Markdown fences, and
 * deserialize into the target type.
 *
 * <pre>{@code
 * CityWeather weather = StructuredOutputs.chat(chatService,
 *         ChatCompletion.builder().model("gpt-4o").message("weather in shanghai?").build(),
 *         CityWeather.class);
 * }</pre>
 *
 * <p>The request's own {@code response_format} wins when already set — the facade only fills it
 * in when absent, so callers can still hand-tune the schema.
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
        if (chatService == null) {
            throw new IllegalArgumentException("chatService is required");
        }
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        if (type == null) {
            throw new IllegalArgumentException("type is required");
        }

        ChatCompletion effective = request;
        if (request.getResponseFormat() == null) {
            effective = request.toBuilder()
                    .responseFormat(JsonSchemaGenerator.responseFormat(type, schemaName))
                    .build();
        }

        ChatCompletionResponse response = chatService.chatCompletion(effective);
        String text = extractText(response);
        T value = parse(text, type);
        return new StructuredResult<T>(value, text, response == null ? null : response.getUsage());
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
            T value = JSON.parseObject(text, type);
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
