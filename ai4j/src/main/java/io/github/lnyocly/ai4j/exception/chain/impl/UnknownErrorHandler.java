package io.github.lnyocly.ai4j.exception.chain.impl;

import io.github.lnyocly.ai4j.exception.chain.AbstractErrorHandler;
import io.github.lnyocly.ai4j.exception.error.Error;

/**
 * @Author cly
 * @Description 未知的错误处理，用于兜底处理
 * @Date 2024/9/18 21:08
 */
public class UnknownErrorHandler extends AbstractErrorHandler {
    @Override
    public Error parseError(String errorInfo) {
        Error error = new Error();

        error.setParam(null);
        error.setType("Unknown Type");
        error.setCode("Unknown Code");
        error.setMessage(errorInfo);

        return error;
    }
}
