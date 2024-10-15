package io.github.lnyocly.ai4j.exception.chain;

import io.github.lnyocly.ai4j.exception.chain.impl.HunyuanErrorHandler;
import io.github.lnyocly.ai4j.exception.chain.impl.OpenAiErrorHandler;
import io.github.lnyocly.ai4j.exception.chain.impl.UnknownErrorHandler;
import io.github.lnyocly.ai4j.exception.error.Error;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author cly
 * @Description 创建错误处理的单例
 * @Date 2024/9/18 21:09
 */
public class ErrorHandler {
    private List<IErrorHandler> handlers;
    private IErrorHandler chain;

    private ErrorHandler() {
        handlers = new ArrayList<>();
        // 添加错误处理器
        handlers.add(new OpenAiErrorHandler());
        handlers.add(new HunyuanErrorHandler());

        // 兜底的错误处理
        handlers.add(new UnknownErrorHandler());

        // 组装链
        this.assembleChain();
    }

    private void assembleChain(){
        chain = handlers.get(0);
        IErrorHandler curr = handlers.get(0);
        for (int i = 1; i < handlers.size(); i++) {
            curr.setNext(handlers.get(i));
            curr = handlers.get(i);
        }
    }

    private static class ErrorHandlerHolder {
        private static final ErrorHandler INSTANCE = new ErrorHandler();
    }

    public static ErrorHandler getInstance() {
        return ErrorHandlerHolder.INSTANCE;
    }

    public Error process(String errorSring){
        return chain.parseError(errorSring);
    }
}
