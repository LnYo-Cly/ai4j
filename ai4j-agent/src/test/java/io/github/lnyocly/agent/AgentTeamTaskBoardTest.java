package io.github.lnyocly.agent;

import io.github.lnyocly.ai4j.agent.team.AgentTeamTask;
import io.github.lnyocly.ai4j.agent.team.AgentTeamTaskBoard;
import io.github.lnyocly.ai4j.agent.team.AgentTeamTaskState;
import io.github.lnyocly.ai4j.agent.team.AgentTeamTaskStatus;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AgentTeamTaskBoardTest {

    @Test
    public void test_claim_release_reassign_and_dependency_flow() {
        List<AgentTeamTask> tasks = new ArrayList<>();
        tasks.add(AgentTeamTask.builder().id("collect").memberId("m1").task("collect facts").build());
        tasks.add(AgentTeamTask.builder().id("format").memberId("m2").task("format final answer").dependsOn(Arrays.asList("collect")).build());

        AgentTeamTaskBoard board = new AgentTeamTaskBoard(tasks);

        List<AgentTeamTaskState> ready = board.nextReadyTasks(10);
        Assert.assertEquals(1, ready.size());
        Assert.assertEquals("collect", ready.get(0).getTaskId());

        Assert.assertTrue(board.claimTask("collect", "m1"));
        Assert.assertEquals(AgentTeamTaskStatus.IN_PROGRESS, board.getTaskState("collect").getStatus());
        Assert.assertEquals("m1", board.getTaskState("collect").getClaimedBy());
        Assert.assertEquals("running", board.getTaskState("collect").getPhase());
        Assert.assertEquals(Integer.valueOf(15), board.getTaskState("collect").getPercent());

        Assert.assertTrue(board.reassignTask("collect", "m1", "m2"));
        Assert.assertEquals("m2", board.getTaskState("collect").getClaimedBy());
        Assert.assertEquals("reassigned", board.getTaskState("collect").getPhase());

        Assert.assertTrue(board.releaseTask("collect", "m2", "handoff"));
        Assert.assertEquals(AgentTeamTaskStatus.READY, board.getTaskState("collect").getStatus());
        Assert.assertEquals("ready", board.getTaskState("collect").getPhase());

        Assert.assertTrue(board.claimTask("collect", "m2"));
        Assert.assertTrue(board.heartbeatTask("collect", "m2"));
        Assert.assertEquals("heartbeat", board.getTaskState("collect").getPhase());
        Assert.assertEquals(1, board.getTaskState("collect").getHeartbeatCount());
        board.markCompleted("collect", "facts", 12L);

        Assert.assertEquals(AgentTeamTaskStatus.COMPLETED, board.getTaskState("collect").getStatus());
        Assert.assertEquals(Integer.valueOf(100), board.getTaskState("collect").getPercent());
        Assert.assertEquals(AgentTeamTaskStatus.READY, board.getTaskState("format").getStatus());

        Assert.assertTrue(board.claimTask("format", "m2"));
        board.markCompleted("format", "final", 8L);

        Assert.assertFalse(board.hasWorkRemaining());
        Assert.assertEquals(AgentTeamTaskStatus.COMPLETED, board.getTaskState("format").getStatus());
    }

    @Test
    public void test_recover_timed_out_claims() throws Exception {
        List<AgentTeamTask> tasks = new ArrayList<>();
        tasks.add(AgentTeamTask.builder().id("t1").memberId("m1").task("run").build());
        AgentTeamTaskBoard board = new AgentTeamTaskBoard(tasks);

        Assert.assertTrue(board.claimTask("t1", "m1"));
        Thread.sleep(5L);

        int recovered = board.recoverTimedOutClaims(1L, "timeout");
        Assert.assertEquals(1, recovered);
        Assert.assertEquals(AgentTeamTaskStatus.READY, board.getTaskState("t1").getStatus());
        Assert.assertNull(board.getTaskState("t1").getClaimedBy());
        Assert.assertEquals("ready", board.getTaskState("t1").getPhase());
    }
}
