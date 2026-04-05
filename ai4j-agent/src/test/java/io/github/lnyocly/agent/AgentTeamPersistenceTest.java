package io.github.lnyocly.agent;

import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.team.AgentTeam;
import io.github.lnyocly.ai4j.agent.team.AgentTeamMember;
import io.github.lnyocly.ai4j.agent.team.AgentTeamPlan;
import io.github.lnyocly.ai4j.agent.team.AgentTeamResult;
import io.github.lnyocly.ai4j.agent.team.AgentTeamState;
import io.github.lnyocly.ai4j.agent.team.AgentTeamTask;
import io.github.lnyocly.ai4j.agent.team.AgentTeamTaskStatus;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

public class AgentTeamPersistenceTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void shouldPersistAndRestoreTeamStateFromStorageDirectory() throws Exception {
        Path root = temporaryFolder.newFolder("agent-team-storage").toPath();

        AgentTeam firstTeam = buildTeam(root, "travel-team");
        AgentTeamResult result = firstTeam.run("prepare travel delivery package");

        Assert.assertEquals("team synthesis", result.getOutput());
        Assert.assertEquals("travel-team", result.getTeamId());
        Assert.assertEquals(1, result.getTaskStates().size());
        Assert.assertEquals(AgentTeamTaskStatus.COMPLETED, result.getTaskStates().get(0).getStatus());

        AgentTeamState snapshot = firstTeam.snapshotState();
        Assert.assertNotNull(snapshot);
        Assert.assertEquals("travel-team", snapshot.getTeamId());
        Assert.assertTrue(snapshot.getMessages().size() >= 2);

        AgentTeam restoredTeam = buildTeam(root, "travel-team");
        AgentTeamState restored = restoredTeam.loadPersistedState();

        Assert.assertNotNull(restored);
        Assert.assertEquals("travel-team", restored.getTeamId());
        Assert.assertEquals(1, restoredTeam.listTaskStates().size());
        Assert.assertEquals(AgentTeamTaskStatus.COMPLETED, restoredTeam.listTaskStates().get(0).getStatus());
        Assert.assertTrue(restoredTeam.listMessages().size() >= 2);
        Assert.assertEquals("team synthesis", restored.getLastOutput());

        Assert.assertTrue(restoredTeam.clearPersistedState());
        Assert.assertTrue(restoredTeam.listMessages().isEmpty());
        Assert.assertTrue(restoredTeam.listTaskStates().isEmpty());

        AgentTeam afterClear = buildTeam(root, "travel-team");
        Assert.assertNull(afterClear.loadPersistedState());
    }

    private AgentTeam buildTeam(Path root, String teamId) {
        ScriptedModelClient memberClient = new ScriptedModelClient();
        memberClient.enqueue(textResult("backend and frontend delivery ready"));

        ScriptedModelClient synthClient = new ScriptedModelClient();
        synthClient.enqueue(textResult("team synthesis"));

        return Agents.team()
                .teamId(teamId)
                .storageDirectory(root)
                .planner((objective, members, options) -> AgentTeamPlan.builder()
                        .rawPlanText("fixed")
                        .tasks(Arrays.asList(
                                AgentTeamTask.builder()
                                        .id("delivery")
                                        .memberId("builder")
                                        .task("Produce delivery package")
                                        .build()
                        ))
                        .build())
                .synthesizerAgent(newAgent("synth", synthClient))
                .member(AgentTeamMember.builder()
                        .id("builder")
                        .name("Builder")
                        .description("Produces delivery output")
                        .agent(newAgent("member", memberClient))
                        .build())
                .build();
    }

    private Agent newAgent(String model, AgentModelClient client) {
        return Agents.react()
                .modelClient(client)
                .model(model)
                .build();
    }

    private AgentModelResult textResult(String text) {
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
