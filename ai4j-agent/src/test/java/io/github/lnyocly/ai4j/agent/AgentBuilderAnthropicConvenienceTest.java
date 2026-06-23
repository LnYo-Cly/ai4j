package io.github.lnyocly.ai4j.agent;

import io.github.lnyocly.ai4j.agent.model.ChatModelClient;
import io.github.lnyocly.ai4j.agent.model.MessagesModelClient;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class AgentBuilderAnthropicConvenienceTest {

    @Test
    public void anthropicMessagesShouldWireMessagesModelClient() {
        assertTrue("apiKey-only overload must wire MessagesModelClient",
                Agents.builder().anthropicMessages("key").peekModelClient() instanceof MessagesModelClient);
        assertTrue("apiKey+baseUrl overload must wire MessagesModelClient",
                Agents.builder().anthropicMessages("key", "https://open.bigmodel.cn/api/anthropic/").peekModelClient() instanceof MessagesModelClient);
        assertTrue("apiKey+baseUrl+version overload must wire MessagesModelClient",
                Agents.builder().anthropicMessages("key", "https://x/", "2023-06-01").peekModelClient() instanceof MessagesModelClient);
    }

    @Test
    public void buildShouldSucceedAfterConvenience() {
        // build() throws "modelClient is required" if null; the convenience must set it.
        Agent agent = Agents.builder().anthropicMessages("key").model("test-model").build();
        assertNotNull(agent);
    }

    @Test
    public void openAiChatShouldWireChatModelClient() {
        assertTrue("openAiChat(apiKey) must wire ChatModelClient",
                Agents.builder().openAiChat("key").peekModelClient() instanceof ChatModelClient);
        assertTrue("openAiChat(apiKey, baseUrl) must wire ChatModelClient",
                Agents.builder().openAiChat("key", "https://my-openai-compatible/").peekModelClient() instanceof ChatModelClient);
    }
}
