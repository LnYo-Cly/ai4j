package io.github.lnyocly.agent;

import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.event.AgentEvent;
import io.github.lnyocly.ai4j.agent.event.AgentEventType;
import io.github.lnyocly.ai4j.agent.event.AgentListener;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.team.AgentTeamMember;
import io.github.lnyocly.ai4j.agent.team.AgentTeamPlan;
import io.github.lnyocly.ai4j.agent.team.AgentTeamState;
import io.github.lnyocly.ai4j.agent.team.AgentTeamTask;
import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

public class AgentTeamAgentAdapterTest {

    @Test
    public void shouldBuildTeamAsStandardAgentAndRun() throws Exception {
        ScriptedModelClient memberClient = new ScriptedModelClient();
        memberClient.enqueue(textResult("requirements-collected"));

        ScriptedModelClient synthClient = new ScriptedModelClient();
        synthClient.enqueue(textResult("team-final-answer"));

        Agent teamAgent = Agents.teamAgent(Agents.team()
                .planner((objective, members, options) -> AgentTeamPlan.builder()
                        .tasks(Arrays.asList(
                                AgentTeamTask.builder()
                                        .id("collect")
                                        .memberId("researcher")
                                        .task("Collect requirements")
                                        .build()
                        ))
                        .build())
                .synthesizerAgent(newAgent("synth", synthClient))
                .member(AgentTeamMember.builder()
                        .id("researcher")
                        .name("Researcher")
                        .agent(newAgent("member", memberClient))
                        .build()));

        AgentResult result = teamAgent.run(AgentRequest.builder().input("prepare plan").build());

        Assert.assertEquals("team-final-answer", result.getOutputText());
        Assert.assertTrue(result.getRawResponse() instanceof io.github.lnyocly.ai4j.agent.team.AgentTeamResult);
    }

    @Test
    public void shouldEmitTeamTaskEventsWhenRunningStream() throws Exception {
        ScriptedModelClient memberClient = new ScriptedModelClient();
        memberClient.enqueue(textResult("collected"));

        ScriptedModelClient synthClient = new ScriptedModelClient();
        synthClient.enqueue(textResult("synthesized"));

        Agent teamAgent = Agents.team()
                .planner((objective, members, options) -> AgentTeamPlan.builder()
                        .tasks(Arrays.asList(
                                AgentTeamTask.builder()
                                        .id("collect")
                                        .memberId("researcher")
                                        .task("Collect requirements")
                                        .build()
                        ))
                        .build())
                .synthesizerAgent(newAgent("synth", synthClient))
                .member(AgentTeamMember.builder()
                        .id("researcher")
                        .name("Researcher")
                        .agent(newAgent("member", memberClient))
                        .build())
                .buildAgent();

        final List<AgentEvent> events = new ArrayList<AgentEvent>();
        teamAgent.runStream(AgentRequest.builder().input("run team").build(), new AgentListener() {
            @Override
            public void onEvent(AgentEvent event) {
                events.add(event);
            }
        });

        Assert.assertNotNull(firstEvent(events, AgentEventType.TEAM_TASK_CREATED));
        Assert.assertNotNull(firstEvent(events, AgentEventType.TEAM_TASK_UPDATED));
        AgentEvent finalEvent = firstEvent(events, AgentEventType.FINAL_OUTPUT);
        Assert.assertNotNull(finalEvent);
        Assert.assertEquals("synthesized", finalEvent.getMessage());
    }

    @Test
    public void shouldPreserveTeamPersistenceSettingsWhenBuildingStandardAgent() throws Exception {
        Path storageRoot = Files.createTempDirectory("agent-team-build-agent-persistence");

        ScriptedModelClient memberClient = new ScriptedModelClient();
        memberClient.enqueue(textResult("delivery-complete"));

        ScriptedModelClient synthClient = new ScriptedModelClient();
        synthClient.enqueue(textResult("persisted-summary"));

        Agent teamAgent = Agents.team()
                .teamId("delivery-team")
                .storageDirectory(storageRoot)
                .planner((objective, members, options) -> AgentTeamPlan.builder()
                        .tasks(Arrays.asList(
                                AgentTeamTask.builder()
                                        .id("deliver")
                                        .memberId("builder")
                                        .task("Deliver travel package")
                                        .build()
                        ))
                        .build())
                .synthesizerAgent(newAgent("synth", synthClient))
                .member(AgentTeamMember.builder()
                        .id("builder")
                        .name("Builder")
                        .agent(newAgent("member", memberClient))
                        .build())
                .buildAgent();

        AgentResult result = teamAgent.run(AgentRequest.builder().input("run delivery").build());

        Assert.assertEquals("persisted-summary", result.getOutputText());

        AgentTeamState restored = Agents.team()
                .teamId("delivery-team")
                .storageDirectory(storageRoot)
                .planner((objective, members, options) -> AgentTeamPlan.builder().tasks(new ArrayList<AgentTeamTask>()).build())
                .synthesizerAgent(newAgent("noop-synth", new ScriptedModelClient()))
                .member(AgentTeamMember.builder()
                        .id("builder")
                        .name("Builder")
                        .agent(newAgent("noop-member", new ScriptedModelClient()))
                        .build())
                .build()
                .loadPersistedState();

        Assert.assertNotNull(restored);
        Assert.assertEquals("delivery-team", restored.getTeamId());
        Assert.assertEquals("persisted-summary", restored.getLastOutput());
        Assert.assertEquals(1, restored.getTaskStates().size());
    }

    private static AgentEvent firstEvent(List<AgentEvent> events, AgentEventType type) {
        if (events == null || type == null) {
            return null;
        }
        for (AgentEvent event : events) {
            if (event != null && type == event.getType()) {
                return event;
            }
        }
        return null;
    }

    private static Agent newAgent(String model, AgentModelClient client) {
        return Agents.react()
                .modelClient(client)
                .model(model)
                .build();
    }

    private static AgentModelResult textResult(String text) {
        return AgentModelResult.builder()
                .outputText(text)
                .memoryItems(new ArrayList<Object>())
                .toolCalls(new ArrayList<io.github.lnyocly.ai4j.agent.tool.AgentToolCall>())
                .build();
    }

    private static class ScriptedModelClient implements AgentModelClient {
        private final Deque<AgentModelResult> queue = new ArrayDeque<AgentModelResult>();

        private void enqueue(AgentModelResult result) {
            queue.add(result);
        }

        @Override
        public AgentModelResult create(AgentPrompt prompt) {
            return queue.isEmpty() ? AgentModelResult.builder().build() : queue.poll();
        }

        @Override
        public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
            throw new UnsupportedOperationException("stream not used in test");
        }
    }
}
