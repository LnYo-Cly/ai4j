package io.github.lnyocly.ai4j.rag;

import io.github.lnyocly.ai4j.listener.SseListener;
import io.github.lnyocly.ai4j.memory.ChatMemoryItem;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletion;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletionResponse;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatMessage;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.Choice;
import io.github.lnyocly.ai4j.service.IChatService;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ModelRagQueryPlannerTest {

    @Test
    public void defaultPlannerShouldOnlyCallRewriteStrategy() throws Exception {
        FakeChatService chatService = new FakeChatService("{"
                + "\"rewrite\":\"AI4J RAG query planning\""
                + "}");
        ModelRagQueryPlanner planner = new ModelRagQueryPlanner(chatService, "planner-model");

        RagQueryPlan plan = planner.plan(RagQuery.builder()
                .query("那怎么接？")
                .dataset("docs")
                .build());

        Assert.assertEquals("那怎么接？", plan.getOriginalQuery());
        Assert.assertFalse(plan.isFallback());
        Assert.assertEquals(2, plan.getVariants().size());
        Assert.assertEquals(RagQueryVariantType.ORIGINAL, plan.getVariants().get(0).getType());
        Assert.assertEquals(RagQueryVariantType.REWRITE, plan.getVariants().get(1).getType());
        Assert.assertEquals(1, chatService.requests.size());
        Assert.assertEquals("planner-model", chatService.requests.get(0).getModel());
        Assert.assertNotNull(chatService.requests.get(0).getResponseFormat());
        Assert.assertTrue(chatService.userPrompt(0).contains("Dataset: docs"));
        Assert.assertTrue(chatService.userPrompt(0).contains("Rewrite strategy"));
        Assert.assertFalse(chatService.userPrompt(0).contains("HyDE strategy"));
        Assert.assertFalse(chatService.userPrompt(0).contains("Multi-query expansion strategy"));
    }

    @Test
    public void shouldIncludeHistoryInPromptWhenPresent() throws Exception {
        FakeChatService chatService = new FakeChatService("{"
                + "\"rewrite\":\"ChatFire Suno music generation integration\""
                + "}");
        ModelRagQueryPlanner planner = new ModelRagQueryPlanner(chatService, "planner-model");

        planner.plan(RagQuery.builder()
                .query("那 Suno 呢？")
                .history(Arrays.asList(
                        ChatMemoryItem.user("我想接入 ChatFire 视频生成"),
                        ChatMemoryItem.assistant("可以先接 OpenAI-compatible videos"),
                        ChatMemoryItem.summary("system", "用户在讨论 AI4J 的 ChatFire 媒体服务接入")
                ))
                .build());

        String prompt = chatService.userPrompt(0);
        Assert.assertTrue(prompt.contains("Conversation history:"));
        Assert.assertTrue(prompt.contains("user: 我想接入 ChatFire 视频生成"));
        Assert.assertTrue(prompt.contains("assistant: 可以先接 OpenAI-compatible videos"));
        Assert.assertTrue(prompt.contains("summary: 用户在讨论 AI4J 的 ChatFire 媒体服务接入"));
        Assert.assertTrue(prompt.contains("Original query:"));
        Assert.assertTrue(prompt.contains("那 Suno 呢？"));
    }

    @Test
    public void shouldCallEachEnabledStrategyWithDedicatedPrompt() throws Exception {
        FakeChatService chatService = new FakeChatService(
                "{"
                + "\"rewrite\":\"AI4J RAG query planning\","
                + "\"multiQueries\":[\"ignored multi query\"],"
                + "\"hyde\":\"ignored hyde\","
                + "\"stepBack\":\"ignored step back\""
                + "}",
                "{\"multiQueries\":[\"AI4J retrieval query rewrite\",\"AI4J HyDE step back\"]}",
                "{\"hyde\":\"AI4J RAG query planning creates retrieval variants before dense retrieval.\"}",
                "{\"stepBack\":\"RAG retrieval pipeline architecture\"}");
        ModelRagQueryPlanner planner = new ModelRagQueryPlanner(
                chatService,
                "planner-model",
                Arrays.asList(
                        RagQueryVariantType.REWRITE,
                        RagQueryVariantType.MULTI_QUERY,
                        RagQueryVariantType.HYDE,
                        RagQueryVariantType.STEP_BACK),
                6,
                true);

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
        Assert.assertEquals(4, chatService.requests.size());
        Assert.assertTrue(chatService.userPrompt(0).contains("Rewrite strategy"));
        Assert.assertTrue(chatService.userPrompt(1).contains("Multi-query expansion strategy"));
        Assert.assertTrue(chatService.userPrompt(2).contains("HyDE strategy"));
        Assert.assertTrue(chatService.userPrompt(3).contains("Step-back strategy"));
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
        Assert.assertFalse(chatService.userPrompt(0).contains("HyDE"));
        Assert.assertEquals(1, chatService.requests.size());
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
        private final List<String> contents;
        private final List<ChatCompletion> requests = new ArrayList<ChatCompletion>();
        private int index;

        private FakeChatService(String... contents) {
            this.contents = contents == null || contents.length == 0
                    ? Collections.singletonList("{}")
                    : Arrays.asList(contents);
        }

        @Override
        public ChatCompletionResponse chatCompletion(String baseUrl, String apiKey, ChatCompletion chatCompletion) {
            return chatCompletion(chatCompletion);
        }

        @Override
        public ChatCompletionResponse chatCompletion(ChatCompletion chatCompletion) {
            this.requests.add(chatCompletion);
            String content = contents.get(Math.min(index, contents.size() - 1));
            index++;
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

        private String userPrompt(int index) {
            return requests.get(index).getMessages().get(1).getContent().getText();
        }
    }
}
