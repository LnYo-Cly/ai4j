package io.github.lnyocly.ai4j.docs;

import io.github.lnyocly.ai4j.annotation.FunctionCall;
import io.github.lnyocly.ai4j.annotation.FunctionParameter;
import io.github.lnyocly.ai4j.annotation.FunctionRequest;
import io.github.lnyocly.ai4j.config.OpenAiConfig;
import io.github.lnyocly.ai4j.exception.AiHttpException;
import io.github.lnyocly.ai4j.listener.SseListener;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletion;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletionResponse;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatMessage;
import io.github.lnyocly.ai4j.platform.openai.tool.ToolCall;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.IChatService;
import io.github.lnyocly.ai4j.service.PlatformType;
import io.github.lnyocly.ai4j.service.factory.AiService;
import io.github.lnyocly.ai4j.test.LiveProviderTest;
import lombok.Data;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * Executable source of truth for the snippets embedded in
 * {@code docs/core-sdk/model-access/chat.md}.
 *
 * <p>Every code block on that page is copied from a method here, so a snippet
 * cannot silently rot: if the SDK API changes, this stops compiling, and if the
 * runtime behaviour changes, this stops passing.
 *
 * <p>Requires {@code OPENAI_API_KEY}; honours {@code OPENAI_API_HOST} and
 * {@code OPENAI_CHAT_MODEL}. Skips when the key is absent.
 */
@Category(LiveProviderTest.class)
public class ChatDocExamplesLiveTest {

    private IChatService chatService() {
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
        return new AiService(configuration).getChatService(PlatformType.OPENAI);
    }

    private String model() {
        String model = System.getenv("OPENAI_CHAT_MODEL");
        return (model == null || model.trim().isEmpty()) ? "gpt-4o-mini" : model;
    }

    // ---- §3 最小可跑通调用 ----

    @Test
    public void minimalChatCall() throws Exception {
        IChatService chatService = chatService();

        ChatCompletion chatCompletion = ChatCompletion.builder()
                .model(model())
                .message(ChatMessage.withUser("用一句话解释什么是向量数据库"))
                .build();

        ChatCompletionResponse response = chatService.chatCompletion(chatCompletion);

        String answer = response.getChoices().get(0).getMessage().getContent().getText();
        System.out.println(answer);

        Assert.assertNotNull(answer);
        Assert.assertFalse(answer.trim().isEmpty());
    }

    // ---- §3 多轮对话：把回答拼回 messages ----

    @Test
    public void multiTurnByAppendingMessages() throws Exception {
        IChatService chatService = chatService();

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.withUser("记住这个数字：42。只回复 OK"));

        ChatCompletionResponse first = chatService.chatCompletion(ChatCompletion.builder()
                .model(model())
                .messages(messages)
                .build());

        // 把 assistant 回复拼回会话，再问下一轮
        messages.add(first.getChoices().get(0).getMessage());
        messages.add(ChatMessage.withUser("我刚才让你记住的数字是多少？只回复数字"));

        ChatCompletionResponse second = chatService.chatCompletion(ChatCompletion.builder()
                .model(model())
                .messages(messages)
                .build());

        String answer = second.getChoices().get(0).getMessage().getContent().getText();
        System.out.println(answer);

        Assert.assertTrue("应记得 42，实际：" + answer, answer.contains("42"));
    }

    // ---- §5 自动 tool loop ----

    /**
     * 注册一个本地函数：类上标 {@code @FunctionCall}，实现 {@code Function<Request, String>}。
     * AI4J 通过 name 解析并在收到 tool_calls 时自动执行。
     */
    @FunctionCall(name = "getOrderStatus", description = "根据订单号查询订单状态")
    public static class GetOrderStatus implements Function<GetOrderStatus.Request, String> {

        @Data
        @FunctionRequest
        public static class Request {
            @FunctionParameter(description = "订单号")
            private String orderId;
        }

        @Override
        public String apply(Request request) {
            // 真实项目里这里查库；示例直接返回结构化结果
            return "{\"orderId\":\"" + request.getOrderId() + "\",\"status\":\"已发货\",\"eta\":\"2026-08-12\"}";
        }
    }

    @Test
    public void autoToolLoopExecutesLocalFunction() throws Exception {
        IChatService chatService = chatService();

        ChatCompletion chatCompletion = ChatCompletion.builder()
                .model(model())
                .message(ChatMessage.withUser("订单 A1001 现在什么状态？"))
                .functions("getOrderStatus")
                .build();

        // 收到 tool_calls 时 SDK 会自动执行 getOrderStatus 并把结果回填，
        // 这里拿到的已经是工具执行之后的最终回答
        ChatCompletionResponse response = chatService.chatCompletion(chatCompletion);

        String answer = response.getChoices().get(0).getMessage().getContent().getText();
        System.out.println(answer);

        Assert.assertTrue("最终回答应包含工具返回的状态，实际：" + answer,
                answer.contains("已发货"));
    }

    // ---- §6 passThroughToolCalls：把控制权交回上层 ----

    @Test
    public void passThroughToolCallsHandsControlBackToCaller() throws Exception {
        IChatService chatService = chatService();

        ChatCompletion chatCompletion = ChatCompletion.builder()
                .model(model())
                .message(ChatMessage.withUser("订单 A1001 现在什么状态？"))
                .functions("getOrderStatus")
                .passThroughToolCalls(Boolean.TRUE)
                .build();

        ChatCompletionResponse response = chatService.chatCompletion(chatCompletion);

        // SDK 不再自动执行，而是把 tool_calls 原样交回，供上层审批 / 沙箱执行 / trace
        List<ToolCall> toolCalls = response.getChoices().get(0).getMessage().getToolCalls();

        Assert.assertNotNull("passThrough 模式应拿到未执行的 toolCalls", toolCalls);
        Assert.assertFalse(toolCalls.isEmpty());
        System.out.println("待执行工具: " + toolCalls.get(0).getFunction().getName());
        System.out.println("参数: " + toolCalls.get(0).getFunction().getArguments());

        Assert.assertEquals("getOrderStatus", toolCalls.get(0).getFunction().getName());
    }

    // ---- §7 SseListener 流式 ----

    @Test
    public void streamingWithSseListener() throws Exception {
        IChatService chatService = chatService();

        ChatCompletion chatCompletion = ChatCompletion.builder()
                .model(model())
                .message(ChatMessage.withUser("从 1 数到 5，只输出数字"))
                .stream(Boolean.TRUE)
                .build();

        SseListener sseListener = new SseListener() {
            @Override
            protected void send() {
                // 每个 delta 到达时触发；getCurrStr() 是本次增量
                System.out.print(getCurrStr());
            }
        };

        chatService.chatCompletionStream(chatCompletion, sseListener);

        // 流结束后，聚合结果都在 listener 上
        String full = sseListener.getOutput().toString();
        System.out.println("\n完整输出: " + full);
        System.out.println("finishReason: " + sseListener.getFinishReason());

        Assert.assertTrue("流式应产出内容", full.length() > 0);
        Assert.assertNotNull(sseListener.getFinishReason());
    }

    // ---- §8 多模态 ----

    /** 8x8 纯红 PNG，避免示例依赖外部图床。 */
    private static final String RED_PNG_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAIAAABLbSncAAAAEklEQVR4nGP4z8CAFWEXHbQSACj/P8Fu7N9hAAAAAElFTkSuQmCC";

    @Test
    public void multiModalUserMessageWithImage() throws Exception {
        IChatService chatService = chatService();

        // 实际项目里通常是读本地文件：
        //   byte[] bytes = Files.readAllBytes(Paths.get("photo.png"));
        //   String dataUrl = "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
        String dataUrl = "data:image/png;base64," + RED_PNG_BASE64;

        // withUser(text, images...) 会编码成 1 段 text + N 个 image_url
        ChatMessage message = ChatMessage.withUser("这张图是什么颜色？只回答颜色名", dataUrl);

        ChatCompletionResponse response = chatService.chatCompletion(ChatCompletion.builder()
                .model(model())
                .message(message)
                .build());

        String answer = response.getChoices().get(0).getMessage().getContent().getText();
        System.out.println("多模态回答: " + answer);

        // 断言模型确实读到了图片内容，而不是只回了一句无关的话
        String normalized = answer.toLowerCase();
        Assert.assertTrue("模型应识别出红色，实际：" + answer,
                normalized.contains("red") || answer.contains("红"));
    }

    /**
     * 记录一个真实的网关差异：部分 OpenAI 兼容网关不会代拉远程图片 URL，
     * 只接受内联 base64 data URL。文档因此以 data URL 为推荐写法。
     */
    @Test
    public void remoteImageUrlMayBeRejectedByGateway() throws Exception {
        IChatService chatService = chatService();

        ChatMessage message = ChatMessage.withUser("这张图是什么颜色？只回答颜色名",
                "https://upload.wikimedia.org/wikipedia/commons/thumb/b/b0/Solid_red.svg/120px-Solid_red.svg.png");

        try {
            ChatCompletionResponse response = chatService.chatCompletion(ChatCompletion.builder()
                    .model(model())
                    .message(message)
                    .build());
            System.out.println("该网关支持远程图片 URL: "
                    + response.getChoices().get(0).getMessage().getContent().getText());
        } catch (AiHttpException e) {
            // 不是 SDK 缺陷，是网关能力差异；错误信息现在能说清楚原因
            System.out.println("网关拒绝远程图片 URL (" + e.getStatusCode() + "): " + e.getMessage());
            Assume.assumeNoException("gateway does not fetch remote image URLs", e);
        }
    }

    // ---- §4 usage 读取 ----

    @Test
    public void usageIsReadableInOpenAiStandardShape() throws Exception {
        IChatService chatService = chatService();

        ChatCompletionResponse response = chatService.chatCompletion(ChatCompletion.builder()
                .model(model())
                .messages(Collections.singletonList(ChatMessage.withUser("只回复 OK")))
                .build());

        System.out.println("prompt=" + response.getUsage().getPromptTokens()
                + " completion=" + response.getUsage().getCompletionTokens()
                + " total=" + response.getUsage().getTotalTokens());

        Assert.assertTrue(response.getUsage().getPromptTokens() > 0);
        Assert.assertTrue(response.getUsage().getTotalTokens() > 0);
    }
}
