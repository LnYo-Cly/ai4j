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
@FunctionCall(name = "queryTrainInfo", description = "查询火车是否发车信息")
public class QueryTrainInfoFunction implements Function<QueryTrainInfoFunction.Request, String> {

    @Data
    @FunctionRequest
    public static class Request {
        @FunctionParameter(description = "根据天气的情况进行查询是否发车，此参数为天气的最高气温")
        Integer type;
    }
    public enum Type{
        hao,
        cha
    }

    @Override
    public String apply(Request request) {
        if (request.type > 35) {
            return "天气情况正常，允许发车";
        }
        return "天气情况较差，不允许发车";
    }
    @Data
    public static class Response {
        String orderId;
    }


}
