package io.github.lnyocly.ai4j.interceptor;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * @Author cly
 * @Description TODO
 * @Date 2024/9/20 18:56
 */
public class ContentTypeInterceptor implements Interceptor {

    private static final MediaType EVENT_STREAM_MEDIA_TYPE = MediaType.get("text/event-stream");
    private static final String NDJSON_CONTENT_TYPE = "application/x-ndjson";
    private static final String SSE_CONTENT_TYPE = "text/event-stream";

    @Override
    public Response intercept(Chain chain) throws IOException {
        Response response = chain.proceed(chain.request());
        if (!isNdjsonResponse(response)) {
            return response;
        }

        ResponseBody responseBody = response.body();
        if (responseBody == null) {
            return response;
        }

        return response.newBuilder()
                .header("Content-Type", SSE_CONTENT_TYPE)
                .body(ResponseBody.create(toSseBody(readBody(responseBody)), EVENT_STREAM_MEDIA_TYPE))
                .build();
    }

    private boolean isNdjsonResponse(Response response) {
        String contentType = response.header("Content-Type");
        return contentType != null && contentType.contains(NDJSON_CONTENT_TYPE);
    }

    private String readBody(ResponseBody responseBody) throws IOException {
        BufferedSource source = responseBody.source();
        source.request(Long.MAX_VALUE);
        Buffer buffer = source.getBuffer();
        return buffer.clone().readString(StandardCharsets.UTF_8);
    }

    private String toSseBody(String ndjsonBody) {
        StringBuilder sseBody = new StringBuilder();
        String[] ndjsonLines = ndjsonBody.split("\n");
        for (String jsonLine : ndjsonLines) {
            if (!jsonLine.trim().isEmpty()) {
                sseBody.append("data: ").append(jsonLine).append("\n\n");
            }
        }
        return sseBody.toString();
    }
}
