package io.github.lnyocly.agent;

import io.github.lnyocly.ai4j.agent.team.AgentTeamMessage;
import io.github.lnyocly.ai4j.agent.team.AgentTeamState;
import io.github.lnyocly.ai4j.agent.team.FileAgentTeamMessageBus;
import io.github.lnyocly.ai4j.agent.team.FileAgentTeamStateStore;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public class FileAgentTeamStateStoreTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void shouldSaveLoadListAndDeleteState() throws Exception {
        Path root = temporaryFolder.newFolder("team-state-store").toPath();
        FileAgentTeamStateStore store = new FileAgentTeamStateStore(root);

        AgentTeamState state = AgentTeamState.builder()
                .teamId("travel-team")
                .objective("ship travel demo")
                .lastOutput("done")
                .lastRounds(3)
                .updatedAt(123L)
                .build();

        store.save(state);

        AgentTeamState loaded = store.load("travel-team");
        Assert.assertNotNull(loaded);
        Assert.assertEquals("ship travel demo", loaded.getObjective());
        Assert.assertEquals("done", loaded.getLastOutput());

        List<AgentTeamState> listed = store.list();
        Assert.assertEquals(1, listed.size());
        Assert.assertEquals("travel-team", listed.get(0).getTeamId());

        Assert.assertTrue(store.delete("travel-team"));
        Assert.assertNull(store.load("travel-team"));
    }

    @Test
    public void shouldPersistMailboxAcrossInstances() throws Exception {
        Path file = temporaryFolder.newFile("team-mailbox.jsonl").toPath();
        FileAgentTeamMessageBus first = new FileAgentTeamMessageBus(file);
        first.publish(AgentTeamMessage.builder()
                .id("m1")
                .fromMemberId("architect")
                .toMemberId("backend")
                .type("peer.message")
                .taskId("architecture")
                .content("API schema ready")
                .createdAt(1L)
                .build());
        first.publish(AgentTeamMessage.builder()
                .id("m2")
                .fromMemberId("backend")
                .toMemberId("*")
                .type("peer.broadcast")
                .taskId("backend")
                .content("openapi updated")
                .createdAt(2L)
                .build());

        FileAgentTeamMessageBus second = new FileAgentTeamMessageBus(file);
        Assert.assertEquals(2, second.snapshot().size());
        Assert.assertEquals(2, second.historyFor("backend", 10).size());

        second.restore(Arrays.asList(
                AgentTeamMessage.builder()
                        .id("m3")
                        .fromMemberId("qa")
                        .toMemberId("lead")
                        .type("task.result")
                        .taskId("qa")
                        .content("qa plan ready")
                        .createdAt(3L)
                        .build()
        ));

        FileAgentTeamMessageBus third = new FileAgentTeamMessageBus(file);
        Assert.assertEquals(1, third.snapshot().size());
        Assert.assertEquals("m3", third.snapshot().get(0).getId());
    }
}
