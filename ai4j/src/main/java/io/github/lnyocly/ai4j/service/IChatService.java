package io.github.lnyocly.ai4j.service;

import io.github.lnyocly.ai4j.listener.SseListener;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletion;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletionResponse;
import io.github.lnyocly.ai4j.platform.openai.embedding.entity.Embedding;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;

/**
 * @Author cly
 * @Description TODO
 * @Date 2024/8/2 23:15
 */
public interface IChatService {

    ChatCompletionResponse chatCompletion(String baseUrl, String apiKey, ChatCompletion chatCompletion) throws Exception;
    ChatCompletionResponse chatCompletion(ChatCompletion chatCompletion) throws Exception;
    void chatCompletionStream(String baseUrl, String apiKey, ChatCompletion chatCompletion, SseListener eventSourceListener) throws Exception;
    void chatCompletionStream(ChatCompletion chatCompletion, SseListener eventSourceListener) throws Exception;

}
