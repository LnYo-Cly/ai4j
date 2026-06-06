package io.github.lnyocly.ai4j.service;

import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletion;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletionResponse;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatMessage;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Assert;
import org.junit.Test;

public class ChatClientTest {

    @Test
    public void openAiShouldSendFirstChatRequestAndReturnAssistantText() throws Exception {
        MockWebServer server = new MockWebServer();
        server.enqueue(chatResponse("chatcmpl_chat_client", "AI4J keeps the first Java chat call short."));
        server.start();

        try {
            ChatClient client = ChatClient.openAi("unit-test-key", server.url("/").toString());

            String text = client.chat("gpt-test", "用一句话介绍 AI4J");

            Assert.assertEquals("AI4J keeps the first Java chat call short.", text);
            Assert.assertEquals(PlatformType.OPENAI, client.getPlatform());
            Assert.assertNotNull(client.getConfiguration());
            Assert.assertNotNull(client.getAiService());
            Assert.assertNotNull(client.getChatService());

            RecordedRequest recordedRequest = server.takeRequest();
            Assert.assertEquals("/v1/chat/completions", recordedRequest.getPath());
            Assert.assertEquals("Bearer unit-test-key", recordedRequest.getHeader("Authorization"));
            Assert.assertTrue(recordedRequest.getBody().readUtf8().contains("\"model\":\"gpt-test\""));
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void chatShouldExposeRawResponseForAdvancedCallers() throws Exception {
        MockWebServer server = new MockWebServer();
        server.enqueue(chatResponse("chatcmpl_raw", "raw response text"));
        server.start();

        try {
            ChatClient client = ChatClient.openAi("unit-test-key", server.url("/").toString());
            ChatCompletion request = ChatCompletion.builder()
                    .model("gpt-test")
                    .message(ChatMessage.withUser("hello"))
                    .build();

            ChatCompletionResponse response = client.chat(request);

            Assert.assertEquals("chatcmpl_raw", response.getId());
            Assert.assertEquals("raw response text",
                    response.getChoices().get(0).getMessage().getContent().getText());
        } finally {
            server.shutdown();
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void openAiShouldRejectBlankApiKey() {
        ChatClient.openAi(" ");
    }

    @Test(expected = IllegalArgumentException.class)
    public void chatShouldRejectBlankModel() throws Exception {
        ChatClient.openAi("unit-test-key").chat(" ", "hello");
    }

    @Test(expected = IllegalArgumentException.class)
    public void chatShouldRejectBlankUserMessage() throws Exception {
        ChatClient.openAi("unit-test-key").chat("gpt-test", " ");
    }

    private MockResponse chatResponse(String id, String text) {
        return new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{"
                        + "\"id\":\"" + id + "\","
                        + "\"object\":\"chat.completion\","
                        + "\"created\":1710000000,"
                        + "\"model\":\"gpt-test\","
                        + "\"choices\":[{"
                        + "\"index\":0,"
                        + "\"message\":{"
                        + "\"role\":\"assistant\","
                        + "\"content\":\"" + text + "\""
                        + "},"
                        + "\"finish_reason\":\"stop\""
                        + "}],"
                        + "\"usage\":{"
                        + "\"prompt_tokens\":8,"
                        + "\"completion_tokens\":9,"
                        + "\"total_tokens\":17"
                        + "}"
                        + "}");
    }
}
