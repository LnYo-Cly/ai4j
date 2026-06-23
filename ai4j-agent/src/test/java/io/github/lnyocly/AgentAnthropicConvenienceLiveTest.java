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
 * Live: the {@code AgentBuilder.anthropicMessages(apiKey, baseUrl)} convenience wires a full agent
 * that runs on the Anthropic native wire protocol end-to-end.
 * <p>Env: {@code ANTHROPIC_API_KEY} (required), {@code ANTHROPIC_BASE_URL} (default zhipu coding plan),
 * {@code ANTHROPIC_MODEL} (default glm-5.1).
 */
@Category(LiveProviderTest.class)
public class AgentAnthropicConvenienceLiveTest {

    @Test
    public void agentRunsOnAnthropicNativeViaConvenience() throws Exception {
        String key = System.getenv("ANTHROPIC_API_KEY");
        Assume.assumeTrue("skip: ANTHROPIC_API_KEY not set", key != null && !key.trim().isEmpty());
        String baseUrl = System.getenv("ANTHROPIC_BASE_URL");
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            baseUrl = "https://open.bigmodel.cn/api/anthropic/";
        }
        String model = System.getenv("ANTHROPIC_MODEL");
        if (model == null || model.trim().isEmpty()) {
            model = "glm-5.1";
        }

        Agent agent = Agents.react()
                .anthropicMessages(key, baseUrl)
                .model(model)
                .build();

        AgentSession session = agent.newSession();
        AgentResult result = session.run("Introduce yourself in one short sentence.");
        System.out.println("=== agent anthropic convenience output ===");
        System.out.println(result);
        assertNotNull(result);
        assertTrue("agent output must be non-empty via anthropicMessages convenience",
                result.getOutputText() != null && !result.getOutputText().isEmpty());
    }
}
