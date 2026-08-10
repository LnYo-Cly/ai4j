package io.github.lnyocly.agent;

import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.team.AgentTeam;
import io.github.lnyocly.ai4j.agent.team.AgentTeamMember;
import io.github.lnyocly.ai4j.agent.team.AgentTeamPlan;
import io.github.lnyocly.ai4j.agent.team.AgentTeamResult;
import io.github.lnyocly.ai4j.agent.team.AgentTeamTask;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Executable source of truth for the snippets in
 * {@code docs/agent/agent-teams-api-reference.md}.
 *
 * <p>Inline scripted clients, zero network; runs in CI.
 */
public class AgentTeamsDocExamplesTest {

    // ---- §1 buildAgent()：把 Team 包成普通 Agent ----

    @Test
    public void teamAsStandardAgentPlansAndSynthesizes() throws Exception {
        ScriptedClient memberClient = new ScriptedClient();
        memberClient.enqueue(text("requirements-collected"));

        ScriptedClient synthClient = new ScriptedClient();
        synthClient.enqueue(text("team-final-answer"));

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
        Assert.assertTrue("应返回 AgentTeamResult", result.getRawResponse() instanceof AgentTeamResult);
    }

    // ---- §1 build()：直接拿 AgentTeam，能读任务板 ----

    @Test
    public void buildReturnsAgentTeamWithTaskBoard() throws Exception {
        ScriptedClient memberClient = new ScriptedClient();
        memberClient.enqueue(text("done"));
        ScriptedClient synthClient = new ScriptedClient();
        synthClient.enqueue(text("synthesized"));

        AgentTeam team = Agents.team()
                .planner((objective, members, options) -> AgentTeamPlan.builder()
                        .tasks(Collections.singletonList(
                                AgentTeamTask.builder()
                                        .id("t1").memberId("worker").task("do work").build()))
                        .build())
                .synthesizerAgent(newAgent("synth", synthClient))
                .member(AgentTeamMember.builder()
                        .id("worker").name("Worker")
                        .agent(newAgent("w", memberClient)).build())
                .build();

        AgentTeamResult result = team.run(AgentRequest.builder().input("go").build());

        Assert.assertEquals("synthesized", result.getOutput());
        // build() 返回的 AgentTeam 暴露任务状态、消息总线、持久化状态——这是选 build() 而非 buildAgent() 的理由
        Assert.assertNotNull(team.snapshotState());
        Assert.assertNotNull(team.listTaskStates());
    }

    // ---- helpers ----

    private static Agent newAgent(String name, ScriptedClient client) {
        return Agents.react().modelClient(client).model(name).build();
    }

    private static AgentModelResult text(String t) {
        return AgentModelResult.builder()
                .outputText(t)
                .memoryItems(new ArrayList<Object>())
                .toolCalls(new ArrayList<AgentToolCall>())
                .build();
    }

    static final class ScriptedClient implements AgentModelClient {
        final Deque<AgentModelResult> queue = new ConcurrentLinkedDeque<AgentModelResult>();

        void enqueue(AgentModelResult r) { queue.addLast(r); }

        @Override
        public AgentModelResult create(AgentPrompt prompt) {
            AgentModelResult r = queue.pollFirst();
            if (r == null) throw new IllegalStateException("scripted client exhausted");
            return r;
        }

        @Override
        public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
            throw new UnsupportedOperationException();
        }
    }
}
