package io.github.lnyocly.ai4j.tools;

import io.github.lnyocly.ai4j.annotation.FunctionParameter;
import io.github.lnyocly.ai4j.annotation.FunctionCall;
import io.github.lnyocly.ai4j.annotation.FunctionRequest;
import lombok.Data;

import java.util.function.Function;

/**
 * @Author cly
 * @Description TODO
 * @Date 2024/8/12 14:45
 */
@FunctionCall(name = "queryTrainInfo", description = "查询火车信息")
public class QueryTrainInfoFunction implements Function<QueryTrainInfoFunction.Request, String> {

    @Data
    @FunctionRequest
    public static class Request {
        @FunctionParameter(description = "订单ID")
        String orderId;
    }


    @Override
    public String apply(Request request) {
        String orderId = request.orderId;
        return "火车发车时间为" + orderId + "-08-12 14:45";
    }
    @Data
    public static class Response {
        String orderId;
    }


}
