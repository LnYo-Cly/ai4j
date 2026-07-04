package io.github.lnyocly.ai4j.rag;

import io.github.lnyocly.ai4j.listener.SseListener;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletion;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletionResponse;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatMessage;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.Choice;
import io.github.lnyocly.ai4j.service.IChatService;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class ModelRagQueryPlannerTest {

    @Test
    public void shouldParseCommonPlannerStrategiesFromJson() throws Exception {
        FakeChatService chatService = new FakeChatService("{"
                + "\"rewrite\":\"AI4J RAG query planning\","
                + "\"multiQueries\":[\"AI4J retrieval query rewrite\",\"AI4J HyDE step back\"],"
                + "\"hyde\":\"AI4J RAG query planning creates retrieval variants before dense retrieval.\","
                + "\"stepBack\":\"RAG retrieval pipeline architecture\""
                + "}");
        ModelRagQueryPlanner planner = new ModelRagQueryPlanner(chatService, "planner-model");

        RagQueryPlan plan = planner.plan(RagQuery.builder()
                .query("那怎么接？")
                .dataset("docs")
                .build());

        Assert.assertEquals("那怎么接？", plan.getOriginalQuery());
        Assert.assertFalse(plan.isFallback());
        Assert.assertEquals(6, plan.getVariants().size());
        Assert.assertEquals(RagQueryVariantType.ORIGINAL, plan.getVariants().get(0).getType());
        Assert.assertEquals(RagQueryVariantType.REWRITE, plan.getVariants().get(1).getType());
        Assert.assertEquals(RagQueryVariantType.MULTI_QUERY, plan.getVariants().get(2).getType());
        Assert.assertEquals(RagQueryVariantType.MULTI_QUERY, plan.getVariants().get(3).getType());
        Assert.assertEquals(RagQueryVariantType.HYDE, plan.getVariants().get(4).getType());
        Assert.assertEquals(RagQueryVariantType.STEP_BACK, plan.getVariants().get(5).getType());
        Assert.assertEquals("planner-model", chatService.request.getModel());
        Assert.assertNotNull(chatService.request.getResponseFormat());
        Assert.assertTrue(chatService.userPrompt().contains("Dataset: docs"));
        Assert.assertTrue(chatService.userPrompt().contains("REWRITE"));
        Assert.assertTrue(chatService.userPrompt().contains("HYDE"));
    }

    @Test
    public void shouldHonorStrategyAndVariantLimits() throws Exception {
        FakeChatService chatService = new FakeChatService("{"
                + "\"rewrite\":\"AI4J RAG query planning\","
                + "\"multiQueries\":[\"ignored multi query\"],"
                + "\"hyde\":\"ignored hyde\","
                + "\"stepBack\":\"ignored step back\""
                + "}");
        ModelRagQueryPlanner planner = new ModelRagQueryPlanner(
                chatService,
                "planner-model",
                Collections.singletonList(RagQueryVariantType.REWRITE),
                1,
                false);

        RagQueryPlan plan = planner.plan(RagQuery.builder().query("怎么接").build());

        Assert.assertEquals(1, plan.getVariants().size());
        Assert.assertEquals(RagQueryVariantType.REWRITE, plan.getVariants().get(0).getType());
        Assert.assertEquals("AI4J RAG query planning", plan.getVariants().get(0).getQuery());
        Assert.assertFalse(chatService.userPrompt().contains("HYDE"));
    }

    @Test
    public void shouldFallbackWhenModelReturnsNoUsableVariant() throws Exception {
        ModelRagQueryPlanner planner = new ModelRagQueryPlanner(
                new FakeChatService("{}"),
                "planner-model");

        RagQueryPlan plan = planner.plan(RagQuery.builder().query("benefits").build());

        Assert.assertTrue(plan.isFallback());
        Assert.assertEquals("model returned no usable query variant", plan.getFallbackReason());
        Assert.assertEquals(RagQueryVariantType.ORIGINAL, plan.getVariants().get(0).getType());
        Assert.assertEquals("benefits", plan.getVariants().get(0).getQuery());
    }

    private static class FakeChatService implements IChatService {
        private final String content;
        private ChatCompletion request;

        private FakeChatService(String content) {
            this.content = content;
        }

        @Override
        public ChatCompletionResponse chatCompletion(String baseUrl, String apiKey, ChatCompletion chatCompletion) {
            return chatCompletion(chatCompletion);
        }

        @Override
        public ChatCompletionResponse chatCompletion(ChatCompletion chatCompletion) {
            this.request = chatCompletion;
            Choice choice = new Choice();
            choice.setMessage(ChatMessage.withAssistant(content));
            ChatCompletionResponse response = new ChatCompletionResponse();
            response.setChoices(Collections.singletonList(choice));
            return response;
        }

        @Override
        public void chatCompletionStream(String baseUrl, String apiKey, ChatCompletion chatCompletion, SseListener eventSourceListener) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void chatCompletionStream(ChatCompletion chatCompletion, SseListener eventSourceListener) {
            throw new UnsupportedOperationException();
        }

        private String userPrompt() {
            return request.getMessages().get(1).getContent().getText();
        }
    }
}
