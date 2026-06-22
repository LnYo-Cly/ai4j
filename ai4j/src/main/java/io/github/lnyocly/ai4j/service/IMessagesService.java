package io.github.lnyocly.ai4j.service;

import io.github.lnyocly.ai4j.platform.anthropic.chat.entity.AnthropicChatCompletion;
import io.github.lnyocly.ai4j.platform.anthropic.chat.entity.AnthropicChatCompletionResponse;
import io.github.lnyocly.ai4j.platform.anthropic.stream.AnthropicStreamHandler;

/**
 * Anthropic Messages（{@code /v1/messages}）协议族服务接口，与 {@link IChatService}（OpenAI Chat Completions）
 * / {@code IResponsesService}（OpenAI Responses）并列的一等公民。
 * <p>
 * 原生 in / 原生 out，零 OpenAI 格式转换。系统本就说 Anthropic 方言的调用方直接用此接口；
 * 想跨 provider 统一调用者走 {@link IChatService} 统一适配器。
 * <p>
 * {@code messages(...)} 为单轮语义（一次请求一次响应），调用方自行驱动工具循环；
 * 统一适配器 {@code AnthropicChatService} 在其上实现自动工具循环。
 */
public interface IMessagesService {

    AnthropicChatCompletionResponse messages(String baseUrl, String apiKey, AnthropicChatCompletion request) throws Exception;

    AnthropicChatCompletionResponse messages(AnthropicChatCompletion request) throws Exception;

    void messagesStream(String baseUrl, String apiKey, AnthropicChatCompletion request, AnthropicStreamHandler handler) throws Exception;

    void messagesStream(AnthropicChatCompletion request, AnthropicStreamHandler handler) throws Exception;
}
