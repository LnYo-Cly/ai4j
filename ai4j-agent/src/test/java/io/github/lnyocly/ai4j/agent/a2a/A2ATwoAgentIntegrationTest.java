package io.github.lnyocly.ai4j.agent.a2a;

import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** End-to-end A2A exchange between two in-process ai4j agents. */
public class A2ATwoAgentIntegrationTest {

    @Test
    public void twoAgentsDiscoverAndExchangeTasks() throws Exception {
        Agent agentA = fixedAgent("agent-a response");
        Agent agentB = fixedAgent("agent-b response");

        try (A2AServer serverA = new A2AServer(agentA, 0, "agent-a", "analysis agent", "a-key")
                .withCapability("analysis")
                .withSkill("summarize", "Summarize text")
                .withEndpoint("tasks", "POST /tasks/send");
             A2AServer serverB = new A2AServer(agentB, 0, "agent-b", "review agent", "b-key")
                .withCapability("review")
                .withSkill("review", "Review text")
                .withEndpoint("tasks", "POST /tasks/send")) {

            A2AClient bToA = new A2AClient("a-key");
            AgentCard cardA = bToA.discover(serverA.getBaseUrl());
            assertEquals("agent-a", cardA.getName());
            assertTrue(cardA.getCapabilities().contains("analysis"));
            assertEquals("summarize", cardA.getSkills().get(0).getName());
            assertEquals("agent-a response", bToA.sendTask(serverA.getBaseUrl(), "summarize this"));

            A2AClient aToB = new A2AClient("b-key");
            AgentCard cardB = aToB.discover(serverB.getBaseUrl());
            assertEquals("agent-b", cardB.getName());
            assertTrue(cardB.getCapabilities().contains("review"));
            assertEquals("review", cardB.getSkills().get(0).getName());
            assertEquals("agent-b response", aToB.sendTask(serverB.getBaseUrl(), "review this"));

            try {
                new A2AClient("wrong-key").sendTask(serverA.getBaseUrl(), "unauthorized");
                fail("server A accepted an invalid API key");
            } catch (IOException expected) {
                assertTrue(expected.getMessage(), expected.getMessage().contains("401"));
            }
        }
    }

    private static Agent fixedAgent(String response) {
        return Agents.react()
                .modelClient(new FixedModelClient(response))
                .model("fixed-model")
                .build();
    }

    private static final class FixedModelClient implements AgentModelClient {
        private final String response;

        private FixedModelClient(String response) {
            this.response = response;
        }

        @Override
        public AgentModelResult create(AgentPrompt prompt) {
            return AgentModelResult.builder().outputText(response).build();
        }

        @Override
        public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
            return AgentModelResult.builder().outputText(response).build();
        }
    }
}
