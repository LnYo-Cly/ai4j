package io.github.lnyocly;

import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.AgentSession;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.test.LiveProviderTest;
import org.junit.Assume;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Live: {@code AgentBuilder.openAiChat(apiKey, baseUrl)} wires an agent onto the OpenAI Chat
 * Completions surface end-to-end. Uses MiniMax's new OpenAI-compatible gateway
 * (api.minimaxi.com/v1/chat/completions) + MiniMax-M3 + coding-plan key (all verified to interop).
 * <p>Env: {@code MINIMAX_API_KEY} (required), {@code OPENAI_CHAT_BASE_URL} (default minimax gateway),
 * {@code OPENAI_CHAT_MODEL} (default MiniMax-M3).
 */
@Category(LiveProviderTest.class)
public class AgentOpenAiChatConvenienceLiveTest {

    @Test
    public void agentRunsOnOpenAiChatViaConvenience() throws Exception {
        String key = System.getenv("MINIMAX_API_KEY");
        Assume.assumeTrue("skip: MINIMAX_API_KEY not set", key != null && !key.trim().isEmpty());
        String baseUrl = System.getenv("OPENAI_CHAT_BASE_URL");
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            baseUrl = "https://api.minimaxi.com/";
        }
        String model = System.getenv("OPENAI_CHAT_MODEL");
        if (model == null || model.trim().isEmpty()) {
            model = "MiniMax-M3";
        }

        Agent agent = Agents.react()
                .openAiChat(key, baseUrl)
                .model(model)
                .build();

        AgentSession session = agent.newSession();
        AgentResult result = session.run("Introduce yourself in one short sentence.");
        System.out.println("=== openAiChat convenience output ===");
        System.out.println(result.getOutputText());
        assertNotNull(result);
        assertTrue("agent output must be non-empty via openAiChat convenience",
                result.getOutputText() != null && !result.getOutputText().isEmpty());
    }
}
