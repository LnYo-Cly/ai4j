package io.github.lnyocly.ai4j.interceptor;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;
import okio.Buffer;
import okio.Okio;
import okio.Source;
import okio.Timeout;

import java.io.IOException;

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
                .body(ResponseBody.create(
                        EVENT_STREAM_MEDIA_TYPE,
                        -1L,
                        Okio.buffer(toSseSource(responseBody.source()))))
                .build();
    }

    private boolean isNdjsonResponse(Response response) {
        String contentType = response.header("Content-Type");
        return contentType != null && contentType.contains(NDJSON_CONTENT_TYPE);
    }

    /**
     * 流式转换 NDJSON 为 SSE，逐行读取原始 source 并按 SSE 帧格式输出，
     * 不再缓冲整个响应体。
     */
    private Source toSseSource(final BufferedSource original) {
        return new Source() {
            private final Buffer pending = new Buffer();
            private boolean exhausted = false;

            @Override
            public long read(Buffer sink, long byteCount) throws IOException {
                if (byteCount < 1) {
                    return -1;
                }

                while (pending.size() == 0 && !exhausted) {
                    String line = original.readUtf8Line();
                    if (line == null) {
                        exhausted = true;
                        break;
                    }
                    if (!line.trim().isEmpty()) {
                        pending.writeUtf8("data: ");
                        pending.writeUtf8(line);
                        pending.writeUtf8("\n\n");
                    }
                }

                if (pending.size() == 0) {
                    return -1;
                }

                long toRead = Math.min(byteCount, pending.size());
                return pending.read(sink, toRead);
            }

            @Override
            public Timeout timeout() {
                return original.timeout();
            }

            @Override
            public void close() throws IOException {
                original.close();
            }
        };
    }
}
