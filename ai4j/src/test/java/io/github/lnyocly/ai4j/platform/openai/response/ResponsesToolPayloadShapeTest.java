package io.github.lnyocly.ai4j.platform.openai.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lnyocly.ai4j.annotation.FunctionCall;
import io.github.lnyocly.ai4j.annotation.FunctionParameter;
import io.github.lnyocly.ai4j.annotation.FunctionRequest;
import io.github.lnyocly.ai4j.config.OpenAiConfig;
import io.github.lnyocly.ai4j.platform.openai.response.entity.ResponseRequest;
import io.github.lnyocly.ai4j.service.Configuration;
import lombok.Data;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * The Responses API declares function tools <em>flat</em>:
 *
 * <pre>{"type":"function","name":"...","description":"...","parameters":{...}}</pre>
 *
 * per the official function-calling guide, while Chat Completions nests the same
 * fields under a {@code function} key. Lenient gateways accept either, so a wrong
 * shape can ship unnoticed — these tests pin the wire payload so the two
 * mainlines cannot drift back into each other.
 */
public class ResponsesToolPayloadShapeTest {

    @FunctionCall(name = "getStockPrice", description = "查询股票当前价格")
    public static class GetStockPrice implements Function<GetStockPrice.Request, String> {

        @Data
        @FunctionRequest
        public static class Request {
            @FunctionParameter(description = "股票代码")
            private String symbol;
        }

        @Override
        public String apply(Request request) {
            return "{}";
        }
    }

    @Test
    public void functionToolsAreSerializedInResponsesFlatShape() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        OpenAiResponsesService service = serviceCapturing(body);

        service.create(ResponseRequest.builder()
                .model("gpt-4o-mini")
                .input("AAPL 现在多少钱？")
                .functions("getStockPrice")
                .build());

        JsonNode tools = new ObjectMapper().readTree(body.get()).path("tools");
        Assert.assertTrue("tools 应被解析进 payload", tools.isArray() && tools.size() > 0);

        JsonNode tool = tools.get(0);
        Assert.assertEquals("function", tool.path("type").asText());

        Assert.assertTrue("Responses 要求 name 在顶层，实际 payload: " + tool,
                tool.hasNonNull("name"));
        Assert.assertEquals("getStockPrice", tool.path("name").asText());
        Assert.assertTrue("parameters 应在顶层", tool.path("parameters").isObject());
        Assert.assertTrue("properties 应保留参数定义",
                tool.path("parameters").path("properties").has("symbol"));

        Assert.assertFalse("不应出现 Chat Completions 的嵌套 function 包装，实际 payload: " + tool,
                tool.has("function"));
    }

    /** 调用方手写的扁平 tool（Map/自定义对象）必须原样保留。 */
    @Test
    public void callerSuppliedFlatToolIsPassedThroughUnchanged() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        OpenAiResponsesService service = serviceCapturing(body);

        java.util.Map<String, Object> flatTool = new java.util.LinkedHashMap<>();
        flatTool.put("type", "function");
        flatTool.put("name", "handWritten");
        flatTool.put("parameters", java.util.Collections.singletonMap("type", "object"));

        service.create(ResponseRequest.builder()
                .model("gpt-4o-mini")
                .input("hi")
                .tools(java.util.Collections.<Object>singletonList(flatTool))
                .build());

        JsonNode tool = new ObjectMapper().readTree(body.get()).path("tools").get(0);
        Assert.assertEquals("handWritten", tool.path("name").asText());
        Assert.assertFalse(tool.has("function"));
    }

    /** 内置工具（web_search 等）没有 function 包装，不能被误改。 */
    @Test
    public void builtInToolTypesAreNotRewritten() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        OpenAiResponsesService service = serviceCapturing(body);

        service.create(ResponseRequest.builder()
                .model("gpt-4o-mini")
                .input("hi")
                .tools(java.util.Collections.<Object>singletonList(
                        java.util.Collections.singletonMap("type", "web_search")))
                .build());

        JsonNode tool = new ObjectMapper().readTree(body.get()).path("tools").get(0);
        Assert.assertEquals("web_search", tool.path("type").asText());
        Assert.assertFalse(tool.has("name"));
    }

    // ---- helpers ----

    private OpenAiResponsesService serviceCapturing(final AtomicReference<String> body) {
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    Buffer buffer = new Buffer();
                    if (chain.request().body() != null) {
                        chain.request().body().writeTo(buffer);
                    }
                    body.set(buffer.readUtf8());
                    return new Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(ResponseBody.create(
                                    "{\"id\":\"resp_1\",\"object\":\"response\",\"status\":\"completed\"}",
                                    MediaType.get("application/json")))
                            .build();
                })
                .build();

        OpenAiConfig config = new OpenAiConfig();
        config.setApiKey("test-key");
        config.setApiHost("https://unit.test/");

        Configuration configuration = new Configuration();
        configuration.setOpenAiConfig(config);
        configuration.setOkHttpClient(client);
        return new OpenAiResponsesService(configuration);
    }
}
