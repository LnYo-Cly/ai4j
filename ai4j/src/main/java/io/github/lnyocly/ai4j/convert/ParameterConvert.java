package io.github.lnyocly.ai4j.convert;

import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletion;

/**
 * @Author cly
 * @Description 处理请求参数 统一的OpenAi格式--->其它模型格式
 * @Date 2024/8/12 1:04
 */
public interface ParameterConvert<T> {
    T convertChatCompletionObject(ChatCompletion chatCompletion);
}
