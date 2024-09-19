package io.github.lnyocly.ai4j.interceptor;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.exception.CommonException;
import io.github.lnyocly.ai4j.exception.chain.ErrorHandler;
import io.github.lnyocly.ai4j.exception.error.Error;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * @Author cly
 * @Description 错误处理器
 * @Date 2024/8/29 14:55
 */
@Slf4j
public class ErrorInterceptor implements Interceptor {
    @NotNull
    @Override
    public Response intercept(@NotNull Chain chain) throws IOException {
        Request original = chain.request();

        Response response = chain.proceed(original);

        if(!response.isSuccessful()){
            //response.close();
            String errorMsg = response.body().string();

            JSONObject object;
            try {
                object = JSON.parseObject(errorMsg);
            } catch (Exception e) {
                throw new CommonException(errorMsg);
            }
            // 处理错误信息
            ErrorHandler errorHandler = ErrorHandler.getInstance();
            Error error = errorHandler.process(errorMsg);

            log.error("AI服务请求异常：{}", error.getMessage());
            throw new CommonException(error.getMessage());

        }else{
            // 对混元特殊处理
            // {"Response":{"RequestId":"e4650694-f018-4490-b4d0-d5242cd68106","Error":{"Code":"InvalidParameterValue.Model","Message":"模型不存在"}}}
            String errorMsg = response.body().string();
            if (errorMsg.contains("Response") && errorMsg.contains("Error")){
                // 处理错误信息
                ErrorHandler errorHandler = ErrorHandler.getInstance();
                Error error = errorHandler.process(errorMsg);
                log.error("AI服务请求异常：{}", error.getMessage());
                throw new CommonException(error.getMessage());
            }


        }


        return response;
    }
}
