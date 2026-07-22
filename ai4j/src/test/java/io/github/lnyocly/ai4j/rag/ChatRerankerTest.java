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
import java.util.List;

public class ChatRerankerTest {

    @Test
    public void shouldReorderHitsByChatScoresAndKeepTail() throws Exception {
        FakeChatService chat = new FakeChatService(
                "{\"results\":[{\"index\":1,\"score\":0.93},{\"index\":0,\"score\":0.41}]}"
        );
        ChatReranker reranker = new ChatReranker(chat, "glm-4-flash", 2, null, true, 20);

        List<RagHit> hits = reranker.rerank("vacation policy", Arrays.asList(
                RagHit.builder().id("a").content("doc-a").retrievalScore(0.55f).build(),
                RagHit.builder().id("b").content("doc-b").retrievalScore(0.52f).build(),
                RagHit.builder().id("c").content("doc-c").retrievalScore(0.40f).build()
        ));

        Assert.assertEquals(3, hits.size());
        Assert.assertEquals("b", hits.get(0).getId());
        Assert.assertEquals(Float.valueOf(0.93f), hits.get(0).getRerankScore());
        Assert.assertEquals("a", hits.get(1).getId());
        Assert.assertEquals(Float.valueOf(0.41f), hits.get(1).getRerankScore());
        Assert.assertEquals("c", hits.get(2).getId());
        Assert.assertNull(hits.get(2).getRerankScore());
        Assert.assertEquals("glm-4-flash", chat.request.getModel());
        Assert.assertTrue(chat.request.getMessages().get(1).getContent().getText().contains("vacation policy"));
    }

    @Test
    public void shouldFallbackToOriginalHitsWhenJsonInvalid() throws Exception {
        ChatReranker reranker = new ChatReranker(new FakeChatService("not-json"), "m");
        List<RagHit> input = Arrays.asList(
                RagHit.builder().id("a").content("doc-a").build(),
                RagHit.builder().id("b").content("doc-b").build()
        );

        List<RagHit> hits = reranker.rerank("q", input);

        Assert.assertEquals(2, hits.size());
        Assert.assertEquals("a", hits.get(0).getId());
        Assert.assertEquals("b", hits.get(1).getId());
        Assert.assertNull(hits.get(0).getRerankScore());
    }

    @Test
    public void shouldFallbackWhenChatThrows() throws Exception {
        ChatReranker reranker = new ChatReranker(new FailingChatService(), "m");
        List<RagHit> hits = reranker.rerank("q", Arrays.asList(
                RagHit.builder().id("a").content("doc-a").build()
        ));
        Assert.assertEquals(1, hits.size());
        Assert.assertEquals("a", hits.get(0).getId());
    }

    @Test
    public void shouldNotAppendRemainingHitsWhenDisabled() throws Exception {
        FakeChatService chat = new FakeChatService(
                "{\"results\":[{\"index\":1,\"score\":0.9},{\"index\":0,\"score\":0.2}]}"
        );
        ChatReranker reranker = new ChatReranker(chat, "glm-4-flash", 2, null, false, 20);

        List<RagHit> hits = reranker.rerank("q", Arrays.asList(
                RagHit.builder().id("a").content("doc-a").retrievalScore(0.55f).build(),
                RagHit.builder().id("b").content("doc-b").retrievalScore(0.52f).build(),
                RagHit.builder().id("c").content("doc-c").retrievalScore(0.40f).build()
        ));

        Assert.assertEquals(2, hits.size());
        Assert.assertEquals("b", hits.get(0).getId());
        Assert.assertEquals("a", hits.get(1).getId());
    }

    @Test
    public void shouldKeepAllScoredHitsWhenTopNNull() throws Exception {
        FakeChatService chat = new FakeChatService(
                "{\"results\":[{\"index\":2,\"score\":0.95},{\"index\":0,\"score\":0.5},{\"index\":1,\"score\":0.1}]}"
        );
        ChatReranker reranker = new ChatReranker(chat, "glm-4-flash", null, null, false, 20);

        List<RagHit> hits = reranker.rerank("q", Arrays.asList(
                RagHit.builder().id("a").content("doc-a").build(),
                RagHit.builder().id("b").content("doc-b").build(),
                RagHit.builder().id("c").content("doc-c").build()
        ));

        Assert.assertEquals(3, hits.size());
        Assert.assertEquals("c", hits.get(0).getId());
        Assert.assertEquals(Float.valueOf(0.95f), hits.get(0).getRerankScore());
        Assert.assertEquals("a", hits.get(1).getId());
        Assert.assertEquals("b", hits.get(2).getId());
    }

    @Test
    public void aiServiceShouldCreateChatReranker() {
        AiService aiService = new AiService(new Configuration());
        Assert.assertTrue(aiService.getChatReranker(PlatformType.OPENAI, "glm-4-flash") instanceof ChatReranker);
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
        }

        @Override
        public void chatCompletionStream(ChatCompletion chatCompletion, SseListener eventSourceListener) {
        }

        private ChatCompletionResponse response(String text) {
            Choice choice = new Choice();
            choice.setMessage(ChatMessage.withAssistant(text));
            ChatCompletionResponse response = new ChatCompletionResponse();
            response.setChoices(Collections.singletonList(choice));
            return response;
        }
    }

    private static class FailingChatService implements IChatService {
        @Override
        public ChatCompletionResponse chatCompletion(String baseUrl, String apiKey, ChatCompletion chatCompletion) throws Exception {
            throw new Exception("boom");
        }

        @Override
        public ChatCompletionResponse chatCompletion(ChatCompletion chatCompletion) throws Exception {
            throw new Exception("boom");
        }

        @Override
        public void chatCompletionStream(String baseUrl, String apiKey, ChatCompletion chatCompletion, SseListener eventSourceListener) {
        }

        @Override
        public void chatCompletionStream(ChatCompletion chatCompletion, SseListener eventSourceListener) {
        }
    }
}
