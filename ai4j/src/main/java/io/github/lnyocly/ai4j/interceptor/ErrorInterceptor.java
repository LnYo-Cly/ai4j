package io.github.lnyocly.ai4j.interceptor;

import io.github.lnyocly.ai4j.exception.CommonException;
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

            log.error("AI服务请求异常：{}", errorMsg);
            throw new CommonException(errorMsg);


        }


        return response;
    }
}
