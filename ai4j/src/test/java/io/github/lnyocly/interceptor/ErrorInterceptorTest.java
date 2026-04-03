package io.github.lnyocly.interceptor;

import io.github.lnyocly.ai4j.exception.CommonException;
import io.github.lnyocly.ai4j.interceptor.ErrorInterceptor;
import okhttp3.Call;
import okhttp3.Connection;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class ErrorInterceptorTest {

    @Test
    public void test_completed_response_with_text_error_keyword_should_not_throw() throws Exception {
        ErrorInterceptor interceptor = new ErrorInterceptor();
        Request request = new Request.Builder().url("https://example.com/test").build();
        String payload = "{\"id\":\"resp_x\",\"status\":\"completed\",\"output\":[{\"content\":[{\"text\":\"Error Responses section\"}]}]}";
        Response response = buildResponse(request, 200, "OK", payload, "application/json");

        Response intercepted = interceptor.intercept(new FixedResponseChain(request, response));
        Assert.assertEquals(200, intercepted.code());
        Assert.assertNotNull(intercepted.body());
        Assert.assertTrue(intercepted.body().string().contains("Error Responses section"));
    }

    @Test(expected = CommonException.class)
    public void test_hunyuan_error_shape_in_success_response_should_throw() throws Exception {
        ErrorInterceptor interceptor = new ErrorInterceptor();
        Request request = new Request.Builder().url("https://example.com/test").build();
        String payload = "{\"Response\":{\"Error\":{\"Code\":\"InvalidParameter\",\"Message\":\"bad request\"}}}";
        Response response = buildResponse(request, 200, "OK", payload, "application/json");

        interceptor.intercept(new FixedResponseChain(request, response));
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
