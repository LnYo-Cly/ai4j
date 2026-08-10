package io.github.lnyocly.ai4j.docs;

import io.github.lnyocly.ai4j.config.OpenAiConfig;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletion;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletionResponse;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatMessage;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.IChatService;
import io.github.lnyocly.ai4j.service.PlatformType;
import io.github.lnyocly.ai4j.service.factory.AiService;
import io.github.lnyocly.ai4j.test.LiveProviderTest;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.Collections;

/**
 * Live smoke test for OpenAI-compatible gateways (e.g. TroveBox).
 *
 * <p>Validates that the documented Chat snippet actually runs against a real
 * OpenAI-compatible endpoint, and that {@code reasoning_effort} is accepted.
 *
 * <p>Requires {@code OPENAI_API_KEY}; honours {@code OPENAI_API_HOST} and
 * {@code OPENAI_CHAT_MODEL}. Skips when the key is absent.
 */
@Category(LiveProviderTest.class)
public class OpenAiCompatibleChatLiveSmokeTest {

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

    @Test
    public void chatCompletionReturnsContentFromRealGateway() throws Exception {
        IChatService chatService = chatService();

        ChatCompletion chatCompletion = ChatCompletion.builder()
                .model(model())
                .messages(Collections.singletonList(ChatMessage.withUser("Reply with exactly: PONG")))
                .build();

        ChatCompletionResponse response = chatService.chatCompletion(chatCompletion);

        Assert.assertNotNull("response should not be null", response);
        Assert.assertNotNull("choices should not be null", response.getChoices());
        Assert.assertFalse("choices should not be empty", response.getChoices().isEmpty());

        String content = response.getChoices().get(0).getMessage().getContent().getText();
        Assert.assertNotNull("content should not be null", content);
        Assert.assertTrue("content should mention PONG, got: " + content,
                content.toUpperCase().contains("PONG"));
    }

    /**
     * reasoning_effort is an OpenAI standard field; a compatible gateway must
     * accept it without erroring even when the model ignores it.
     */
    @Test
    public void reasoningEffortIsAcceptedByGateway() throws Exception {
        IChatService chatService = chatService();

        ChatCompletion chatCompletion = ChatCompletion.builder()
                .model(model())
                .messages(Collections.singletonList(ChatMessage.withUser("Reply with exactly: OK")))
                .reasoningEffort("low")
                .build();

        ChatCompletionResponse response = chatService.chatCompletion(chatCompletion);

        Assert.assertNotNull("request with reasoning_effort should succeed", response);
        Assert.assertFalse(response.getChoices().isEmpty());
    }

    /**
     * Usage must be reported in the OpenAI-standard shape so callers can read
     * cached tokens from prompt_tokens_details regardless of provider.
     */
    @Test
    public void usageIsReportedInOpenAiStandardShape() throws Exception {
        IChatService chatService = chatService();

        ChatCompletion chatCompletion = ChatCompletion.builder()
                .model(model())
                .messages(Collections.singletonList(ChatMessage.withUser("Reply with exactly: OK")))
                .build();

        ChatCompletionResponse response = chatService.chatCompletion(chatCompletion);

        Assert.assertNotNull("usage should be reported", response.getUsage());
        Assert.assertTrue("prompt tokens should be positive",
                response.getUsage().getPromptTokens() > 0);
        Assert.assertTrue("total tokens should be positive",
                response.getUsage().getTotalTokens() > 0);
    }
}
