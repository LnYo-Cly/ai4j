package io.github.lnyocly.ai4j.interceptor;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.exception.CommonException;
import io.github.lnyocly.ai4j.exception.chain.ErrorHandler;
import io.github.lnyocly.ai4j.exception.error.Error;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.jetbrains.annotations.NotNull;
import okio.Buffer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Intercepts API responses and raises provider-specific exceptions only when
 * the payload contains structured error objects.
 */
@Slf4j
public class ErrorInterceptor implements Interceptor {

    @NotNull
    @Override
    public Response intercept(@NotNull Chain chain) throws IOException {
        Request original = chain.request();
        Response response = chain.proceed(original);

        if (isStreamingResponse(response)) {
            return response;
        }

        ResponseBody responseBody = response.body();
        byte[] contentBytes = getResponseBodyBytes(responseBody);
        String content = new String(contentBytes, StandardCharsets.UTF_8);
        boolean streamingRequest = isStreamingRequest(original);

        if (!response.isSuccessful() && response.code() != 100 && response.code() != 101) {
            if (streamingRequest) {
                return rebuildResponse(response, responseBody, contentBytes);
            }
            throw buildCommonException(response.code(), response.message(), content);
        }

        if (containsStructuredError(content)) {
            if (streamingRequest) {
                return rebuildResponse(response, responseBody, contentBytes);
            }
            ErrorHandler errorHandler = ErrorHandler.getInstance();
            Error error = errorHandler.process(content);
            log.error("AI request failed: {}", error.getMessage());
            throw new CommonException(error.getMessage());
        }

        return rebuildResponse(response, responseBody, contentBytes);
    }

    private CommonException buildCommonException(int code, String message, String payload) {
        String errorMsg = payload == null ? "" : payload;
        if (errorMsg.trim().isEmpty()) {
            errorMsg = code + " " + message;
            log.error("AI request failed: {}", errorMsg);
            return new CommonException(errorMsg);
        }

        try {
            JSONObject object = JSON.parseObject(errorMsg);
            if (object != null) {
                ErrorHandler errorHandler = ErrorHandler.getInstance();
                Error error = errorHandler.process(errorMsg);
                log.error("AI request failed: {}", error.getMessage());
                return new CommonException(error.getMessage());
            }
        } catch (Exception ignored) {
            // Keep raw payload for unknown providers or non-JSON responses.
        }

        log.error("AI request failed: {}", errorMsg);
        return new CommonException(errorMsg);
    }

    private boolean containsStructuredError(String content) {
        if (content == null || content.trim().isEmpty()) {
            return false;
        }

        JSONObject object;
        try {
            object = JSON.parseObject(content);
        } catch (Exception e) {
            return false;
        }
        if (object == null) {
            return false;
        }

        Object openAiError = object.get("error");
        if (openAiError instanceof JSONObject) {
            return true;
        }
        if (openAiError instanceof String && !((String) openAiError).trim().isEmpty()) {
            return true;
        }

        JSONObject hunyuanResponse = object.getJSONObject("Response");
        if (hunyuanResponse != null) {
            Object hunyuanError = hunyuanResponse.get("Error");
            if (hunyuanError instanceof JSONObject) {
                return true;
            }
            if (hunyuanError instanceof String && !((String) hunyuanError).trim().isEmpty()) {
                return true;
            }
        }

        String status = object.getString("status");
        return "failed".equalsIgnoreCase(status);
    }

    private boolean isStreamingResponse(Response response) {
        ResponseBody body = response.body();
        if (body == null) {
            return false;
        }
        MediaType contentType = body.contentType();
        if (contentType == null) {
            return false;
        }
        String type = contentType.toString();
        return type.contains("text/event-stream") || type.contains("application/x-ndjson");
    }

    private boolean isStreamingRequest(Request request) {
        if (request == null) {
            return false;
        }
        String accept = request.header("Accept");
        if (accept != null) {
            String normalizedAccept = accept.toLowerCase();
            if (normalizedAccept.contains("text/event-stream") || normalizedAccept.contains("application/x-ndjson")) {
                return true;
            }
        }
        if (request.body() == null) {
            return false;
        }
        try {
            Buffer buffer = new Buffer();
            request.body().writeTo(buffer);
            String body = buffer.readString(StandardCharsets.UTF_8);
            String normalizedBody = body == null ? "" : body.toLowerCase();
            return normalizedBody.contains("\"stream\":true") || normalizedBody.contains("\"stream\": true");
        } catch (Exception ignored) {
            return false;
        }
    }

    private Response rebuildResponse(Response response, ResponseBody responseBody, byte[] contentBytes) {
        MediaType contentType = responseBody == null ? null : responseBody.contentType();
        ResponseBody newBody = ResponseBody.create(contentType, contentBytes);
        return response.newBuilder().body(newBody).build();
    }

    private byte[] getResponseBodyBytes(ResponseBody responseBody) throws IOException {
        if (responseBody == null) {
            return new byte[0];
        }
        return responseBody.bytes();
    }
}
