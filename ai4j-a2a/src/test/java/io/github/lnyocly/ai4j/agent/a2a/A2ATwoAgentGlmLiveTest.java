package io.github.lnyocly.ai4j.agent.a2a;

import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.tool.AgentToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.StaticToolRegistry;
import io.github.lnyocly.ai4j.test.LiveProviderTest;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import org.junit.Assume;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.Collections;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

/** Live opt-in A2A exchange between two GLM agents. */
@Category(LiveProviderTest.class)
public class A2ATwoAgentGlmLiveTest {

    @Test
    public void glmAgentBCallsGlmAgentAOverA2A() throws Exception {
        String key = System.getenv("ANTHROPIC_API_KEY");
        Assume.assumeTrue("skip: ANTHROPIC_API_KEY not set", key != null && !key.trim().isEmpty());
        String baseUrl = System.getenv().getOrDefault(
                "ANTHROPIC_BASE_URL", "https://open.bigmodel.cn/api/anthropic/");
        String model = System.getenv().getOrDefault("ANTHROPIC_MODEL", "glm-5.1");

        Agent agentA = Agents.react()
                .anthropicMessages(key, baseUrl)
                .model(model)
                .maxOutputTokens(256)
                .build();

        try (A2AServer serverA = new A2AServer(
                agentA, 0, "glm-agent-a", "A live GLM analysis agent")) {
            A2AClient client = new A2AClient();
            AgentCard card = client.discover(serverA.getBaseUrl());
            assertEquals("glm-agent-a", card.getName());

            A2ATool remoteAgent = new A2ATool(serverA.getBaseUrl());
            Tool.Function function = new Tool.Function();
            function.setName("ask_remote_agent");
            function.setDescription("Ask GLM Agent A to analyze a request over A2A.");
            Tool.Function.Parameter parameter = new Tool.Function.Parameter();
            Tool.Function.Property message = new Tool.Function.Property();
            message.setType("string");
            message.setDescription("The request to send to GLM Agent A");
            parameter.setProperties(Collections.singletonMap("message", message));
            parameter.setRequired(Collections.singletonList("message"));
            function.setParameters(parameter);
            AgentToolRegistry registry = new StaticToolRegistry(
                    Collections.<Object>singletonList(new Tool("function", function)));

            Agent agentB = Agents.react()
                    .anthropicMessages(key, baseUrl)
                    .model(model)
                    .maxOutputTokens(512)
                    .toolRegistry(registry)
                    .toolExecutor(remoteAgent)
                    .build();

            AgentResult result = agentB.newSession().run(
                    "Use ask_remote_agent exactly once. Ask Agent A to return the word A2A-OK, "
                            + "then report Agent A's response briefly.");

            assertNotNull(result);
            assertNotNull(result.getOutputText());
            assertTrue("Agent B should execute the A2A tool", result.getToolResults() != null
                    && !result.getToolResults().isEmpty());
            System.out.println("Live two-agent A2A result: " + result.getOutputText());
        }
    }

}
