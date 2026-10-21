package com.example.ai4j.explicit;

import io.github.lnyocly.ai4j.annotation.FunctionCall;
import io.github.lnyocly.ai4j.annotation.FunctionParameter;
import io.github.lnyocly.ai4j.annotation.FunctionRequest;

import java.util.function.Function;

/**
 * 故意定义在 SDK 包名之外：用于验证 {@code ToolUtil.registerFunctionClasses(...)}
 * 显式登记的工具同样能生成 schema 并被执行。
 */
@FunctionCall(name = "explicit_register_echo", description = "Echo tool registered without classpath scanning")
public class ExplicitRegisterEchoFunction implements Function<ExplicitRegisterEchoFunction.Request, String> {

    @Override
    public String apply(Request request) {
        return "echo:" + request.getMessage();
    }

    @FunctionRequest
    public static class Request {
        @FunctionParameter(description = "Message to echo back")
        private String message;

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
