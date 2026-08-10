package io.github.lnyocly.ai4j.docs;

import io.github.lnyocly.ai4j.annotation.FunctionCall;
import io.github.lnyocly.ai4j.annotation.FunctionParameter;
import io.github.lnyocly.ai4j.annotation.FunctionRequest;
import io.github.lnyocly.ai4j.config.OpenAiConfig;
import io.github.lnyocly.ai4j.exception.AiHttpException;
import io.github.lnyocly.ai4j.listener.ResponseSseListener;
import io.github.lnyocly.ai4j.platform.openai.response.entity.Response;
import io.github.lnyocly.ai4j.platform.openai.response.entity.ResponseContentPart;
import io.github.lnyocly.ai4j.platform.openai.response.entity.ResponseItem;
import io.github.lnyocly.ai4j.platform.openai.response.entity.ResponseRequest;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.IResponsesService;
import io.github.lnyocly.ai4j.service.PlatformType;
import io.github.lnyocly.ai4j.service.factory.AiService;
import io.github.lnyocly.ai4j.test.LiveProviderTest;
import lombok.Data;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.List;
import java.util.function.Function;

/**
 * Executable source of truth for the snippets embedded in
 * {@code docs/core-sdk/model-access/responses.md}.
 *
 * <p>Requires {@code OPENAI_API_KEY}; honours {@code OPENAI_API_HOST} and
 * {@code OPENAI_CHAT_MODEL}. Skips when the key is absent.
 */
@Category(LiveProviderTest.class)
public class ResponsesDocExamplesLiveTest {

    private IResponsesService responsesService() {
        String apiKey = System.getenv("OPENAI_API_KEY");
        Assume.assumeTrue("OPENAI_API_KEY not set", apiKey != null && !apiKey.trim().isEmpty());

        OpenAiConfig openAiConfig = new OpenAiConfig();
        openAiConfig.setApiKey(apiKey);
        String apiHost = System.getenv("OPENAI_API_HOST");
        if (apiHost != null && !apiHost.trim().isEmpty()) {
            openAiConfig.setApiHost(apiHost);
        }

        Configuration configuration = new Configuration();
        configuration.setOpenAiConfig(openAiConfig);
        return new AiService(configuration).getResponsesService(PlatformType.OPENAI);
    }

    private String model() {
        String model = System.getenv("OPENAI_CHAT_MODEL");
        return (model == null || model.trim().isEmpty()) ? "gpt-4o-mini" : model;
    }

    // ---- §0 最小调用 + 读取输出 ----

    /**
     * Responses 的输出是 item 列表，每个 item 带 content parts。
     * 这是读取助手文本必须走的结构。
     */
    private static String outputTextOf(Response response) {
        StringBuilder text = new StringBuilder();
        if (response == null || response.getOutput() == null) {
            return "";
        }
        for (ResponseItem item : response.getOutput()) {
            if (item == null || item.getContent() == null) {
                continue;
            }
            for (ResponseContentPart part : item.getContent()) {
                if (part != null && part.getText() != null) {
                    text.append(part.getText());
                }
            }
        }
        return text.toString();
    }

    @Test
    public void minimalCreateAndReadOutput() throws Exception {
        IResponsesService responsesService = responsesService();

        ResponseRequest request = ResponseRequest.builder()
                .model(model())
                .input("用一句话解释什么是响应式编程")
                .build();

        Response response = responsesService.create(request);

        System.out.println("id     = " + response.getId());
        System.out.println("status = " + response.getStatus());
        System.out.println("输出   = " + outputTextOf(response));

        Assert.assertEquals("response", response.getObject());
        Assert.assertEquals("completed", response.getStatus());
        Assert.assertFalse(outputTextOf(response).trim().isEmpty());
    }

    @Test
    public void usageIsReportedWithInputOutputTokens() throws Exception {
        IResponsesService responsesService = responsesService();

        Response response = responsesService.create(ResponseRequest.builder()
                .model(model())
                .input("只回复 OK")
                .build());

        System.out.println("input=" + response.getUsage().getInputTokens()
                + " output=" + response.getUsage().getOutputTokens()
                + " total=" + response.getUsage().getTotalTokens());

        Assert.assertNotNull(response.getUsage());
        Assert.assertTrue(response.getUsage().getInputTokens() > 0);
    }

    // ---- §2 instructions 与 maxOutputTokens ----

    @Test
    public void instructionsActAsSystemLevelSteering() throws Exception {
        IResponsesService responsesService = responsesService();

        Response response = responsesService.create(ResponseRequest.builder()
                .model(model())
                .instructions("你只能用中文回答，且不超过 20 个字。")
                .input("What is a vector database?")
                .maxOutputTokens(200)
                .build());

        String text = outputTextOf(response);
        System.out.println(text);

        Assert.assertFalse(text.trim().isEmpty());
    }

    // ---- §3 工具解析：functions 与 Chat 共享同一基座 ----

    @FunctionCall(name = "getStockPrice", description = "查询股票当前价格")
    public static class GetStockPrice implements Function<GetStockPrice.Request, String> {

        @Data
        @FunctionRequest
        public static class Request {
            @FunctionParameter(description = "股票代码，例如 AAPL")
            private String symbol;
        }

        @Override
        public String apply(Request request) {
            return "{\"symbol\":\"" + request.getSymbol() + "\",\"price\":195.42,\"currency\":\"USD\"}";
        }
    }

    /**
     * Responses 不做 Chat 那种服务内自动 tool loop：它把工具解析进 request，
     * 把 function_call item 交回上层，由 runtime 决定怎么编排。
     */
    @Test
    public void functionsAreResolvedIntoTheRequest() throws Exception {
        IResponsesService responsesService = responsesService();

        ResponseRequest request = ResponseRequest.builder()
                .model(model())
                .input("AAPL 现在多少钱？")
                .functions("getStockPrice")
                .build();

        Response response;
        try {
            response = responsesService.create(request);
        } catch (AiHttpException e) {
            // 该网关的 /v1/responses 稳定性较差（502/503 间歇性），
            // 工具形状本身由 ResponsesToolPayloadShapeTest 确定性地钉住。
            Assume.assumeNoException("gateway unavailable (" + e.getStatusCode() + ")", e);
            return;
        }

        // 输出 item 里可能出现 function_call，由上层决定是否执行
        boolean sawFunctionCall = false;
        if (response.getOutput() != null) {
            for (ResponseItem item : response.getOutput()) {
                if (item != null && "function_call".equals(item.getType())) {
                    sawFunctionCall = true;
                    System.out.println("待执行: " + item.getName() + " 参数: " + item.getArguments());
                }
            }
        }
        System.out.println("是否产生 function_call: " + sawFunctionCall);

        Assert.assertNotNull("请求应成功返回", response.getId());
    }

    // ---- §6 流式：事件聚合而非单纯 token 输出 ----

    @Test
    public void streamAggregatesEventsAndText() throws Exception {
        IResponsesService responsesService = responsesService();

        ResponseRequest request = ResponseRequest.builder()
                .model(model())
                .input("从 1 数到 5，只输出数字")
                .stream(Boolean.TRUE)
                .build();

        ResponseSseListener listener = new ResponseSseListener() {
            @Override
            protected void onEvent() {
                // currText 是本次事件带来的文本增量
                String delta = getCurrText();
                if (delta != null && !delta.isEmpty()) {
                    System.out.print(delta);
                }
            }
        };

        responsesService.createStream(request, listener);

        // 流结束后，聚合状态都在 listener 上
        System.out.println("\n完整文本: " + listener.getOutputText());
        System.out.println("事件条数: " + listener.getEvents().size());

        Assert.assertTrue("流式应产出文本", listener.getOutputText().length() > 0);
        Assert.assertFalse("应收到事件", listener.getEvents().isEmpty());
    }

    // ---- §8 previousResponseId + store ----

    /**
     * previous_response_id 是 Responses 原生的续接方式：不用重发历史。
     *
     * <p>并非所有 OpenAI 兼容网关都在 HTTP 端点上实现它，因此这里把链式调用
     * 的失败视为网关能力差异而跳过——第一次调用已经证明请求/响应契约可用。
     */
    @Test
    public void previousResponseIdContinuesWithoutResendingHistory() throws Exception {
        IResponsesService responsesService = responsesService();

        Response first = responsesService.create(ResponseRequest.builder()
                .model(model())
                .input("记住数字 42。只回复 STORED")
                .store(Boolean.TRUE)          // 让 provider 侧留存这次响应
                .build());

        System.out.println("first id = " + first.getId());

        Response second;
        try {
            second = responsesService.create(ResponseRequest.builder()
                    .model(model())
                    .previousResponseId(first.getId())   // 只带 id，不重发历史
                    .input("我让你记住的数字是多少？只回复数字")
                    .build());
        } catch (AiHttpException e) {
            System.out.println("网关不支持 HTTP 端点的 previous_response_id ("
                    + e.getStatusCode() + "): " + e.getMessage());
            Assume.assumeNoException("gateway capability gap, not an SDK defect", e);
            return;
        }

        String text = outputTextOf(second);
        System.out.println("续接回答: " + text);
        Assert.assertTrue("应记得 42，实际：" + text, text.contains("42"));
    }

    // ---- retrieve / delete ----

    @Test
    public void retrieveAndDeleteAStoredResponse() throws Exception {
        IResponsesService responsesService = responsesService();

        Response created = responsesService.create(ResponseRequest.builder()
                .model(model())
                .input("只回复 OK")
                .store(Boolean.TRUE)
                .build());

        try {
            Response fetched = responsesService.retrieve(created.getId());
            System.out.println("retrieve status = " + fetched.getStatus());
            Assert.assertEquals(created.getId(), fetched.getId());

            responsesService.delete(created.getId());
            System.out.println("deleted " + created.getId());
        } catch (AiHttpException e) {
            System.out.println("网关不支持 retrieve/delete (" + e.getStatusCode() + "): " + e.getMessage());
            Assume.assumeNoException("gateway capability gap", e);
        }
    }
}
