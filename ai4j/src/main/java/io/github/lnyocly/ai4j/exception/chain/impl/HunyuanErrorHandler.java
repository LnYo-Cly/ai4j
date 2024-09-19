package io.github.lnyocly.ai4j.exception.chain.impl;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.exception.chain.AbstractErrorHandler;
import io.github.lnyocly.ai4j.exception.error.Error;
import io.github.lnyocly.ai4j.exception.error.HunyuanError;
import io.github.lnyocly.ai4j.exception.error.OpenAiError;
import org.apache.commons.lang3.ObjectUtils;

/**
 * @Author cly
 * @Description 混元错误处理
 * @Date 2024/9/18 23:59
 */
public class HunyuanErrorHandler extends AbstractErrorHandler {
    @Override
    public Error parseError(String errorInfo) {
        // 解析json字符串
        HunyuanError hunyuanError = JSON.parseObject(errorInfo, HunyuanError.class);

        HunyuanError.Response response = hunyuanError.getResponse();

        if(ObjectUtils.isEmpty(response)){
            // 交给下一个节点处理
            return nextHandler.parseError(errorInfo);
        }

        HunyuanError.Response.Error error = response.getError();
        if(ObjectUtils.isEmpty(error)){
            // 交给下一个节点处理
            return nextHandler.parseError(errorInfo);
        }

        return new Error(error.getMessage(),error.getCode(),null,error.getCode());
    }
}
