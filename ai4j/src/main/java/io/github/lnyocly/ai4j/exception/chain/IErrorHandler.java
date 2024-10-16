package io.github.lnyocly.ai4j.exception.chain;

import io.github.lnyocly.ai4j.exception.error.Error;
import io.github.lnyocly.ai4j.exception.error.OpenAiError;

/**
 * @Author cly
 * @Description 错误处理接口
 * @Date 2024/9/18 20:55
 */
public interface IErrorHandler {
    void setNext(IErrorHandler handler);
    Error parseError(String errorInfo);
}
