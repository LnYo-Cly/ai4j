package io.github.lnyocly.ai4j.agent.a2a;

import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.test.LiveProviderTest;
import org.junit.After;
import org.junit.Assume;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Tests {@link A2AServer} — an ai4j agent exposed as an A2A HTTP service, called by {@link A2AClient}. */
public class A2AServerTest {

    private A2AServer server;

    @After
    public void stop() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    public void serverRespondsToDiscoverAndSendTask() throws Exception {
        // fake agent that always says "bonjour"
        Agent agent = Agents.react()
                .modelClient(new FixedModelClient("server agent says: bonjour"))
                .model("test-model")
                .build();
        server = new A2AServer(agent, 0, "test-server", "a test agent");

        A2AClient client = new A2AClient();
        AgentCard card = client.discover(server.getBaseUrl());
        assertEquals("test-server", card.getName());

        String response = client.sendTask(server.getBaseUrl(), "hello");
        assertTrue("server must relay the agent's response: " + response, response.contains("bonjour"));
    }

    @Test
    @Category(LiveProviderTest.class)
    public void liveGlmAgentServedViaA2a() throws Exception {
        String key = System.getenv("ANTHROPIC_API_KEY");
        Assume.assumeTrue("skip: ANTHROPIC_API_KEY not set", key != null && !key.trim().isEmpty());
        String anthropicUrl = System.getenv().getOrDefault("ANTHROPIC_BASE_URL", "https://open.bigmodel.cn/api/anthropic/");
        String model = System.getenv().getOrDefault("ANTHROPIC_MODEL", "glm-5.1");

        Agent agent = Agents.react()
                .anthropicMessages(key, anthropicUrl)
                .model(model)
                .maxOutputTokens(256)
                .build();
        server = new A2AServer(agent, 0, "ai4j-glm", "GLM via A2A");

        A2AClient client = new A2AClient();
        String response = client.sendTask(server.getBaseUrl(), "Say exactly: A2A round-trip OK");
        assertTrue("live GLM via A2A must respond: " + response,
                response != null && !response.trim().isEmpty());
    }

    private static final class FixedModelClient implements AgentModelClient {
        private final String fixedOutput;
        FixedModelClient(String fixedOutput) { this.fixedOutput = fixedOutput; }
        public AgentModelResult create(AgentPrompt prompt) {
            return AgentModelResult.builder().outputText(fixedOutput).build();
        }
        public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
            return AgentModelResult.builder().outputText(fixedOutput).build();
        }
    }
}
