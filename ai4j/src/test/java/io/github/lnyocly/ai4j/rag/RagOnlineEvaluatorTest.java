package io.github.lnyocly.ai4j.rag;

import io.github.lnyocly.ai4j.listener.SseListener;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletion;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletionResponse;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatMessage;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.Choice;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.IChatService;
import io.github.lnyocly.ai4j.service.PlatformType;
import io.github.lnyocly.ai4j.service.factory.AiService;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class RagOnlineEvaluatorTest {

    @Test
    public void shouldJudgeAnswerAndAttachEvaluationToTrace() throws Exception {
        FakeChatService chatService = new FakeChatService("{\"faithfulnessScore\":0.9,\"contextRelevanceScore\":0.8,\"answerRelevanceScore\":0.7,\"reason\":\"supported\"}");
        RagOnlineEvaluator evaluator = new RagOnlineEvaluator(new ChatRagJudge(chatService, "judge-model"));
        RagResult result = RagResult.builder()
                .query("What is PTO?")
                .context("PTO means paid time off.")
                .hits(Collections.singletonList(RagHit.builder().id("h1").content("PTO means paid time off.").build()))
                .trace(RagTrace.builder().retrievedHits(Collections.<RagHit>emptyList()).rerankedHits(Collections.<RagHit>emptyList()).build())
                .build();

        RagJudgeEvaluation evaluation = evaluator.evaluate(result, "PTO is paid time off.");

        Assert.assertEquals("judge-model", chatService.request.getModel());
        Assert.assertEquals(2, chatService.request.getMessages().size());
        Assert.assertTrue(chatService.request.getMessages().get(1).getContent().getText().contains("PTO is paid time off"));
        Assert.assertEquals(0.9d, evaluation.getFaithfulnessScore(), 0.0001d);
        Assert.assertEquals(0.8d, evaluation.getContextRelevanceScore(), 0.0001d);
        Assert.assertEquals(0.7d, evaluation.getAnswerRelevanceScore(), 0.0001d);
        Assert.assertEquals("supported", evaluation.getReason());
        Assert.assertSame(evaluation, result.getTrace().getJudgeEvaluation());
    }

    @Test
    public void shouldCreateTraceWhenResultHasNoTrace() throws Exception {
        RagOnlineEvaluator evaluator = new RagOnlineEvaluator(new ChatRagJudge(new FakeChatService("{\"faithfulnessScore\":1}"), "judge-model"));
        RagResult result = RagResult.builder().query("q").context("c").build();

        evaluator.evaluate(result, "a");

        Assert.assertNotNull(result.getTrace());
        Assert.assertEquals(1.0d, result.getTrace().getJudgeEvaluation().getFaithfulnessScore(), 0.0001d);
    }

    @Test
    public void aiServiceShouldCreateRagOnlineEvaluator() {
        AiService aiService = new AiService(new Configuration());

        Assert.assertNotNull(aiService.getRagOnlineEvaluator(PlatformType.OPENAI, "judge-model"));
    }

    private static class FakeChatService implements IChatService {
        private final String output;
        private ChatCompletion request;

        private FakeChatService(String output) {
            this.output = output;
        }

        @Override
        public ChatCompletionResponse chatCompletion(String baseUrl, String apiKey, ChatCompletion chatCompletion) {
            this.request = chatCompletion;
            return response(output);
        }

        @Override
        public ChatCompletionResponse chatCompletion(ChatCompletion chatCompletion) {
            this.request = chatCompletion;
            return response(output);
        }

        @Override
        public void chatCompletionStream(String baseUrl, String apiKey, ChatCompletion chatCompletion, SseListener eventSourceListener) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void chatCompletionStream(ChatCompletion chatCompletion, SseListener eventSourceListener) {
            throw new UnsupportedOperationException();
        }

        private ChatCompletionResponse response(String text) {
            ChatCompletionResponse response = new ChatCompletionResponse();
            Choice choice = new Choice();
            choice.setMessage(ChatMessage.withAssistant(text));
            response.setChoices(Arrays.asList(choice));
            return response;
        }
    }
}