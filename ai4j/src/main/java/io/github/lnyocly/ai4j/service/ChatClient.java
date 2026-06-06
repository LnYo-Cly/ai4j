package io.github.lnyocly.ai4j.service;

import io.github.lnyocly.ai4j.config.OpenAiConfig;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletion;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletionResponse;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatMessage;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.Choice;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.Content;
import io.github.lnyocly.ai4j.service.factory.AiService;

import java.util.List;

/**
 * Lightweight chat facade for the first Plain Java request.
 *
 * <p>Use {@link AiService} and {@link IChatService} directly when the application
 * needs streaming, tools, provider switching, RAG, MCP, or other advanced SDK
 * surfaces.</p>
 */
public class ChatClient {

    private final Configuration configuration;
    private final PlatformType platform;
    private final AiService aiService;
    private final IChatService chatService;

    private ChatClient(Configuration configuration, PlatformType platform) {
        this.configuration = configuration;
        this.platform = platform;
        this.aiService = new AiService(configuration);
        this.chatService = aiService.getChatService(platform);
    }

    public static ChatClient openAi(String apiKey) {
        return openAi(apiKey, null);
    }

    public static ChatClient openAi(String apiKey, String apiHost) {
        requireText(apiKey, "OpenAI API key");

        OpenAiConfig openAiConfig = new OpenAiConfig();
        openAiConfig.setApiKey(apiKey);
        if (hasText(apiHost)) {
            openAiConfig.setApiHost(apiHost);
        }
        return openAi(openAiConfig);
    }

    public static ChatClient openAi(OpenAiConfig openAiConfig) {
        if (openAiConfig == null) {
            throw new IllegalArgumentException("OpenAI config is required");
        }
        requireText(openAiConfig.getApiKey(), "OpenAI API key");

        Configuration configuration = new Configuration();
        configuration.setOpenAiConfig(openAiConfig);
        return of(configuration, PlatformType.OPENAI);
    }

    public static ChatClient of(Configuration configuration, PlatformType platform) {
        if (configuration == null) {
            throw new IllegalArgumentException("Configuration is required");
        }
        if (platform == null) {
            throw new IllegalArgumentException("Platform is required");
        }
        return new ChatClient(configuration, platform);
    }

    public String chat(String model, String userMessage) throws Exception {
        requireText(model, "Model");
        requireText(userMessage, "User message");

        ChatCompletion request = ChatCompletion.builder()
                .model(model)
                .message(ChatMessage.withUser(userMessage))
                .build();
        return firstAssistantText(chat(request));
    }

    public ChatCompletionResponse chat(ChatCompletion request) throws Exception {
        if (request == null) {
            throw new IllegalArgumentException("Chat request is required");
        }
        return chatService.chatCompletion(request);
    }

    public Configuration getConfiguration() {
        return configuration;
    }

    public PlatformType getPlatform() {
        return platform;
    }

    public AiService getAiService() {
        return aiService;
    }

    public IChatService getChatService() {
        return chatService;
    }

    private static String firstAssistantText(ChatCompletionResponse response) {
        if (response == null) {
            throw new IllegalStateException("AI4J chat response is empty");
        }

        List<Choice> choices = response.getChoices();
        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("AI4J chat response did not contain choices");
        }

        Choice choice = choices.get(0);
        if (choice == null) {
            throw new IllegalStateException("AI4J chat response did not contain a valid choice");
        }
        ChatMessage message = choice.getMessage() != null ? choice.getMessage() : choice.getDelta();
        if (message == null || message.getContent() == null) {
            throw new IllegalStateException("AI4J chat response did not contain assistant content");
        }

        Content content = message.getContent();
        if (!hasText(content.getText())) {
            throw new IllegalStateException("AI4J chat response did not contain assistant text");
        }
        return content.getText();
    }

    private static void requireText(String value, String label) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(label + " is required");
        }
    }

    private static boolean hasText(String value) {
        return value != null && value.trim().length() > 0;
    }
}
