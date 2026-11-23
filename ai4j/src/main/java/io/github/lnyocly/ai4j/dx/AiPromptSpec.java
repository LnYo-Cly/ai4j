package io.github.lnyocly.ai4j.dx;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import io.github.lnyocly.ai4j.model.CallExecutionOptions;
import io.github.lnyocly.ai4j.model.Message;
import io.github.lnyocly.ai4j.model.ModelItem;
import io.github.lnyocly.ai4j.model.ToolDeclaration;
import io.github.lnyocly.ai4j.stream.AiReply;
import io.github.lnyocly.ai4j.stream.AiStream;

import java.util.List;
import java.util.Map;

/**
 * Fluent prompt builder shared by blocking {@link #call} and streaming {@link #stream}
 * invocations.
 */
public interface AiPromptSpec {

    AiPromptSpec model(String modelSpec);

    AiPromptSpec sessionId(String sessionId);

    AiPromptSpec system(String systemPrompt);

    AiPromptSpec user(String userPrompt);

    AiPromptSpec user(String userTemplate, Map<String, ?> variables);

    AiPromptSpec message(Message message);

    AiPromptSpec items(List<ModelItem> items);

    AiPromptSpec attach(Attachment... attachments);

    AiPromptSpec attachments(List<Attachment> attachments);

    AiPromptSpec tools(Object... toolBeans);

    AiPromptSpec toolRegistry(InstanceToolRegistry registry);

    AiPromptSpec tool(ToolDeclaration declaration);

    AiPromptSpec temperature(Double temperature);

    AiPromptSpec topP(Double topP);

    AiPromptSpec maxOutputTokens(Integer maxOutputTokens);

    AiPromptSpec maxToolRounds(int maxToolRounds);

    AiPromptSpec maxSelfHealRetries(int maxSelfHealRetries);

    AiPromptSpec providerOption(String key, Object value);

    AiPromptSpec metadata(String key, Object value);

    AiPromptSpec execution(CallExecutionOptions execution);

    AiReply<String> call();

    <T> AiReply<T> call(Class<T> responseType);

    <T> AiReply<T> call(TypeReference<T> typeReference);

    <T> AiReply<T> call(JavaType javaType);

    AiStream<String> stream();

    <T> AiStream<T> stream(Class<T> responseType);

    <T> AiStream<T> stream(TypeReference<T> typeReference);

    <T> AiStream<T> stream(JavaType javaType);
}
