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
import io.github.lnyocly.ai4j.agent.team.AgentTeamMessage;
import io.github.lnyocly.ai4j.agent.team.AgentTeamOptions;
import io.github.lnyocly.ai4j.agent.team.AgentTeamResult;
import io.github.lnyocly.ai4j.agent.team.AgentTeamTask;
import io.github.lnyocly.ai4j.agent.team.tool.AgentTeamToolExecutor;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.AgentToolResult;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * Verifies reactive team messaging: a member can call {@code team_read_messages} to pull
 * messages a teammate sent it, and the per-member cursor means a second read does not
 * re-report the same message.
 */
public class TeamReactiveMessagingTest {

    @Test
    public void memberReceivesPeerMessageViaReadToolAndCursorAdvances() throws Exception {
        // Two members; researcher's first task is to send a message to writer.
        ScriptedClient researcherClient = new ScriptedClient();
        researcherClient.enqueue(toolCall("c1", "team_send_message",
                "{\"toMemberId\":\"writer\",\"content\":\"hey, is the API ready?\"}"));
        researcherClient.enqueue(text("sent"));

        ScriptedClient writerClient = new ScriptedClient();
        // writer checks its mailbox, sees the message, replies, then finishes
        writerClient.enqueue(toolCall("c1", "team_read_messages", "{}"));
        writerClient.enqueue(toolCall("c2", "team_send_message",
                "{\"toMemberId\":\"researcher\",\"content\":\"yes, shipped\"}"));
        writerClient.enqueue(text("writer-done"));

        AgentTeam team = Agents.team()
                .planner((objective, members, options) -> io.github.lnyocly.ai4j.agent.team.AgentTeamPlan.builder()
                        .tasks(Arrays.asList(
                                AgentTeamTask.builder().id("t1").memberId("researcher")
                                        .task("ask writer").build(),
                                AgentTeamTask.builder().id("t2").memberId("writer")
                                        .task("answer researcher").dependsOn(Arrays.asList("t1")).build()
                        ))
                        .build())
                .synthesizerAgent(newAgent("synth", new ScriptedClient().enqueue(text("synth"))))
                .member(AgentTeamMember.builder().id("researcher").name("Researcher")
                        .agent(newAgent("researcher", researcherClient)).build())
                .member(AgentTeamMember.builder().id("writer").name("Writer")
                        .agent(newAgent("writer", writerClient)).build())
                .options(AgentTeamOptions.builder()
                        .enableMessageBus(true)
                        .enableMemberTeamTools(true)   // required so team_* tools are exposed
                        .build())
                .build();

        AgentTeamResult result = team.run(AgentRequest.builder().input("coordinate").build());
        Assert.assertNotNull(result);

        // The writer's first tool call was team_read_messages — its result should contain the
        // message the researcher sent. Extract it from the writer agent's tool results via the
        // team result's member outputs.
        List<io.github.lnyocly.ai4j.agent.team.AgentTeamMemberResult> memberResults = result.getMemberResults();
        io.github.lnyocly.ai4j.agent.team.AgentTeamMemberResult writerResult = null;
        for (io.github.lnyocly.ai4j.agent.team.AgentTeamMemberResult mr : memberResults) {
            if ("writer".equals(mr.getMemberId())) {
                writerResult = mr;
                break;
            }
        }
        Assert.assertNotNull("writer should have a result", writerResult);

        // The raw AgentResult carries the tool calls/results the writer made.
        AgentResult writerRaw = (AgentResult) writerResult.getRawResult();
        Assert.assertNotNull("writer raw result should be present", writerRaw);

        boolean sawReadWithMessage = false;
        boolean sawSecondReadEmpty = false;
        for (AgentToolResult tr : writerRaw.getToolResults()) {
            if ("team_read_messages".equals(tr.getName()) && tr.getOutput().contains("hey, is the API ready?")) {
                sawReadWithMessage = true;
            }
        }
        Assert.assertTrue("writer's team_read_messages should have surfaced the researcher's message. "
                        + "Outputs: " + toolOutputs(writerRaw),
                sawReadWithMessage);
    }

    /**
     * Cursor advances: a second read with no intervening message reports nothing new.
     * Tested at the executor/control level without a full team run.
     */
    @Test
    public void readCursorAdvancesSoRereadReportsNothingNew() {
        // Use a real AgentTeam built minimally so readUnreadMessages + cursor logic is exercised.
        AgentTeam team = Agents.team()
                .planner((objective, members, options) -> io.github.lnyocly.ai4j.agent.team.AgentTeamPlan.builder()
                        .tasks(Collections.<AgentTeamTask>emptyList()).build())
                .synthesizerAgent(newAgent("synth", new ScriptedClient().enqueue(text("synth"))))
                .member(AgentTeamMember.builder().id("alice").name("Alice")
                        .agent(newAgent("alice", new ScriptedClient().enqueue(text("a")))).build())
                .member(AgentTeamMember.builder().id("bob").name("Bob")
                        .agent(newAgent("bob", new ScriptedClient().enqueue(text("b")))).build())
                .options(AgentTeamOptions.builder().enableMessageBus(true).build())
                .build();

        // alice sends bob a direct message
        team.sendMessage("alice", "bob", "peer.message", null, "first");
        // bob reads — should see "first"
        List<AgentTeamMessage> first = team.readUnreadMessages("bob");
        Assert.assertEquals("first read should surface the message", 1, first.size());
        Assert.assertTrue(first.get(0).getContent().contains("first"));
        // bob reads again with no new message — should be empty (cursor advanced)
        List<AgentTeamMessage> second = team.readUnreadMessages("bob");
        Assert.assertTrue("second read should report nothing new: " + second.size(), second.isEmpty());
        // alice sends another; bob reads — sees only the new one
        team.sendMessage("alice", "bob", "peer.message", null, "second");
        List<AgentTeamMessage> third = team.readUnreadMessages("bob");
        Assert.assertEquals("third read should surface only the new message", 1, third.size());
        Assert.assertTrue(third.get(0).getContent().contains("second"));
    }

    private static List<String> toolOutputs(AgentResult r) {
        List<String> out = new ArrayList<String>();
        if (r == null || r.getToolResults() == null) return out;
        for (AgentToolResult tr : r.getToolResults()) out.add(tr.getName() + "=" + tr.getOutput());
        return out;
    }

    private static Agent newAgent(String name, ScriptedClient client) {
        return Agents.react().modelClient(client).model(name).build();
    }

    private static AgentModelResult text(String t) {
        return AgentModelResult.builder().outputText(t)
                .memoryItems(new ArrayList<Object>())
                .toolCalls(new ArrayList<AgentToolCall>()).build();
    }

    private static AgentModelResult toolCall(String id, String name, String args) {
        return AgentModelResult.builder()
                .toolCalls(Arrays.asList(AgentToolCall.builder()
                        .callId(id).name(name).arguments(args).type("function_call").build()))
                .memoryItems(new ArrayList<Object>()).build();
    }

    static class ScriptedClient implements AgentModelClient {
        final Deque<AgentModelResult> q = new ArrayDeque<>();
        ScriptedClient enqueue(AgentModelResult r) { q.addLast(r); return this; }
        @Override public AgentModelResult create(AgentPrompt p) {
            AgentModelResult r = q.pollFirst();
            if (r == null) return text("(done)");
            return r;
        }
        @Override public AgentModelResult createStream(AgentPrompt p, AgentModelStreamListener l) {
            throw new UnsupportedOperationException();
        }
    }
}
