package io.github.lnyocly.interceptor;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.lnyocly.ai4j.exception.CommonException;
import io.github.lnyocly.ai4j.interceptor.ErrorInterceptor;
import okhttp3.Call;
import okhttp3.Connection;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ErrorInterceptorTest {

    @Test
    public void test_completed_response_with_text_error_keyword_should_not_throw() throws Exception {
        CountingErrorInterceptor interceptor = new CountingErrorInterceptor();
        Request request = new Request.Builder().url("https://example.com/test").build();
        String payload = "{\"id\":\"resp_x\",\"status\":\"completed\",\"output\":[{\"content\":[{\"text\":\"Error Responses section\"}]}]}";
        Response response = buildResponse(request, 200, "OK", payload, "application/json");

        Response intercepted = interceptor.intercept(new FixedResponseChain(request, response));
        Assert.assertEquals(200, intercepted.code());
        Assert.assertNotNull(intercepted.body());
        Assert.assertTrue(intercepted.body().string().contains("Error Responses section"));
        Assert.assertEquals(1, interceptor.parseCount.get());
    }

    @Test(expected = CommonException.class)
    public void test_hunyuan_error_shape_in_success_response_should_throw() throws Exception {
        ErrorInterceptor interceptor = new ErrorInterceptor();
        Request request = new Request.Builder().url("https://example.com/test").build();
        String payload = "{\"Response\":{\"Error\":{\"Code\":\"InvalidParameter\",\"Message\":\"bad request\"}}}";
        Response response = buildResponse(request, 200, "OK", payload, "application/json");

        interceptor.intercept(new FixedResponseChain(request, response));
    }

    @Test
    public void test_2xx_non_json_responses_fast_skip_json_parsing() throws Exception {
        String[] payloads = new String[]{
                "ok",
                "",
                "   \n\t ",
                "<html><body>hello</body></html>",
                "[1, 2, 3]"
        };
        for (String payload : payloads) {
            CountingErrorInterceptor interceptor = new CountingErrorInterceptor();
            Request request = new Request.Builder().url("https://example.com/test").build();
            Response response = buildResponse(request, 200, "OK", payload, "text/plain");

            Response intercepted = interceptor.intercept(new FixedResponseChain(request, response));
            Assert.assertEquals(200, intercepted.code());
            Assert.assertEquals(0, interceptor.parseCount.get());
            Assert.assertEquals(payload, intercepted.body().string());
        }
    }

    @Test
    public void test_2xx_openai_and_hunyuan_errors_parse_once_and_extract_message() throws Exception {
        CountingErrorInterceptor openAiInterceptor = new CountingErrorInterceptor();
        Request request = new Request.Builder().url("https://example.com/test").build();
        Response openAiResponse = buildResponse(
                request,
                200,
                "OK",
                "{\"error\":{\"message\":\"invalid api key\",\"type\":\"invalid_request_error\"}}",
                "application/json"
        );
        try {
            openAiInterceptor.intercept(new FixedResponseChain(request, openAiResponse));
            Assert.fail("Expected CommonException");
        } catch (CommonException expected) {
            Assert.assertEquals("invalid api key", expected.getMessage());
            Assert.assertEquals(1, openAiInterceptor.parseCount.get());
        }

        CountingErrorInterceptor hunyuanInterceptor = new CountingErrorInterceptor();
        Response hunyuanResponse = buildResponse(
                request,
                200,
                "OK",
                "{\"Response\":{\"Error\":{\"Code\":\"InvalidParameter\",\"Message\":\"bad request\"}}}",
                "application/json"
        );
        try {
            hunyuanInterceptor.intercept(new FixedResponseChain(request, hunyuanResponse));
            Assert.fail("Expected CommonException");
        } catch (CommonException expected) {
            Assert.assertEquals("bad request", expected.getMessage());
            Assert.assertEquals(1, hunyuanInterceptor.parseCount.get());
        }
    }

    @Test
    public void test_2xx_string_error_and_failed_status_parse_once() throws Exception {
        CountingErrorInterceptor stringErrInterceptor = new CountingErrorInterceptor();
        Request request = new Request.Builder().url("https://example.com/test").build();
        String stringErrPayload = "{\"error\":\"upstream gateway timeout\"}";
        try {
            stringErrInterceptor.intercept(new FixedResponseChain(
                    request, buildResponse(request, 200, "OK", stringErrPayload, "application/json")));
            Assert.fail("Expected CommonException");
        } catch (CommonException expected) {
            Assert.assertEquals(stringErrPayload, expected.getMessage());
            Assert.assertEquals(1, stringErrInterceptor.parseCount.get());
        }

        CountingErrorInterceptor failedStatusInterceptor = new CountingErrorInterceptor();
        String failedPayload = "{\"id\":\"resp_1\",\"status\":\"failed\"}";
        try {
            failedStatusInterceptor.intercept(new FixedResponseChain(
                    request, buildResponse(request, 200, "OK", failedPayload, "application/json")));
            Assert.fail("Expected CommonException");
        } catch (CommonException expected) {
            Assert.assertEquals(failedPayload, expected.getMessage());
            Assert.assertEquals(1, failedStatusInterceptor.parseCount.get());
        }
    }

    @Test
    public void test_non_2xx_errors_parse_at_most_once_and_preserve_raw_non_json() throws Exception {
        CountingErrorInterceptor jsonErrorInterceptor = new CountingErrorInterceptor();
        Request request = new Request.Builder().url("https://example.com/test").build();
        try {
            jsonErrorInterceptor.intercept(new FixedResponseChain(
                    request,
                    buildResponse(request, 401, "Unauthorized",
                            "{\"error\":{\"message\":\"unauthorized\"}}", "application/json")));
            Assert.fail("Expected CommonException");
        } catch (CommonException expected) {
            Assert.assertEquals("unauthorized", expected.getMessage());
            Assert.assertEquals(1, jsonErrorInterceptor.parseCount.get());
        }

        CountingErrorInterceptor rawErrorInterceptor = new CountingErrorInterceptor();
        try {
            rawErrorInterceptor.intercept(new FixedResponseChain(
                    request,
                    buildResponse(request, 502, "Bad Gateway", "Bad Gateway", "text/plain")));
            Assert.fail("Expected CommonException");
        } catch (CommonException expected) {
            Assert.assertEquals("Bad Gateway", expected.getMessage());
            Assert.assertEquals(0, rawErrorInterceptor.parseCount.get());
        }

        CountingErrorInterceptor emptyErrorInterceptor = new CountingErrorInterceptor();
        try {
            emptyErrorInterceptor.intercept(new FixedResponseChain(
                    request,
                    buildResponse(request, 503, "Service Unavailable", "   ", "text/plain")));
            Assert.fail("Expected CommonException");
        } catch (CommonException expected) {
            Assert.assertEquals("503 Service Unavailable", expected.getMessage());
            Assert.assertEquals(0, emptyErrorInterceptor.parseCount.get());
        }
    }

    @Test
    public void test_streaming_responses_and_requests_pass_through_without_parsing() throws Exception {
        CountingErrorInterceptor sseResponseInterceptor = new CountingErrorInterceptor();
        Request getRequest = new Request.Builder().url("https://example.com/stream").build();
        Response sseResponse = buildResponse(getRequest, 200, "OK", "data: {\"error\":{\"message\":\"x\"}}\n\n", "text/event-stream");
        Response out1 = sseResponseInterceptor.intercept(new FixedResponseChain(getRequest, sseResponse));
        Assert.assertEquals(0, sseResponseInterceptor.parseCount.get());
        Assert.assertTrue(out1.body().string().contains("data:"));

        CountingErrorInterceptor streamReqInterceptor = new CountingErrorInterceptor();
        Request streamRequest = new Request.Builder()
                .url("https://example.com/chat")
                .post(RequestBody.create(MediaType.get("application/json"), "{\"model\":\"m\",\"stream\":true}"))
                .build();
        Response errorPayloadOnStreamReq = buildResponse(
                streamRequest, 400, "Bad Request", "{\"error\":{\"message\":\"bad\"}}", "application/json");
        Response out2 = streamReqInterceptor.intercept(new FixedResponseChain(streamRequest, errorPayloadOnStreamReq));
        Assert.assertEquals(400, out2.code());
        Assert.assertEquals(0, streamReqInterceptor.parseCount.get());
        Assert.assertTrue(out2.body().string().contains("bad"));
    }

    private static class CountingErrorInterceptor extends ErrorInterceptor {
        private final AtomicInteger parseCount = new AtomicInteger();

        @Override
        protected JsonNode parseErrorPayload(String trimmedJson) {
            parseCount.incrementAndGet();
            return super.parseErrorPayload(trimmedJson);
        }
    }

    private Response buildResponse(Request request, int code, String message, String body, String contentType) {
        return new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(message)
                .body(ResponseBody.create(MediaType.get(contentType), body))
                .build();
    }

    private static class FixedResponseChain implements Interceptor.Chain {
        private final Request request;
        private final Response response;

        private FixedResponseChain(Request request, Response response) {
            this.request = request;
            this.response = response;
        }

        @Override
        public Request request() {
            return request;
        }

        @Override
        public Response proceed(Request request) throws IOException {
            return response.newBuilder().request(request).build();
        }

        @Override
        public Connection connection() {
            return null;
        }

        @Override
        public Call call() {
            return null;
        }

        @Override
        public int connectTimeoutMillis() {
            return 0;
        }

        @Override
        public Interceptor.Chain withConnectTimeout(int timeout, TimeUnit unit) {
            return this;
        }

        @Override
        public int readTimeoutMillis() {
            return 0;
        }

        @Override
        public Interceptor.Chain withReadTimeout(int timeout, TimeUnit unit) {
            return this;
        }

        @Override
        public int writeTimeoutMillis() {
            return 0;
        }

        @Override
        public Interceptor.Chain withWriteTimeout(int timeout, TimeUnit unit) {
            return this;
        }
    }
}
