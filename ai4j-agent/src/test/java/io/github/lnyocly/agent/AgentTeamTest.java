package io.github.lnyocly.agent;

import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.team.AgentTeam;
import io.github.lnyocly.ai4j.agent.team.AgentTeamHook;
import io.github.lnyocly.ai4j.agent.team.AgentTeamMember;
import io.github.lnyocly.ai4j.agent.team.AgentTeamMemberResult;
import io.github.lnyocly.ai4j.agent.team.AgentTeamMessage;
import io.github.lnyocly.ai4j.agent.team.AgentTeamOptions;
import io.github.lnyocly.ai4j.agent.team.AgentTeamResult;
import io.github.lnyocly.ai4j.agent.team.AgentTeamTask;
import io.github.lnyocly.ai4j.agent.team.AgentTeamTaskStatus;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class AgentTeamTest {

    @Test
    public void test_team_plan_delegate_synthesize() throws Exception {
        ScriptedModelClient plannerClient = new ScriptedModelClient();
        plannerClient.enqueue(textResult("{\"tasks\":[{\"memberId\":\"researcher\",\"task\":\"Collect weather facts for Beijing\"},{\"memberId\":\"formatter\",\"task\":\"Format output as concise summary\"}]}"));

        ScriptedModelClient researcherClient = new ScriptedModelClient();
        researcherClient.enqueue(textResult("Beijing weather: cloudy, 9C, light wind."));

        ScriptedModelClient formatterClient = new ScriptedModelClient();
        formatterClient.enqueue(textResult("Summary formatted."));

        ScriptedModelClient synthClient = new ScriptedModelClient();
        synthClient.enqueue(textResult("Final answer from team."));

        Agent planner = newAgent("planner-model", plannerClient);
        Agent researcher = newAgent("researcher-model", researcherClient);
        Agent formatter = newAgent("formatter-model", formatterClient);
        Agent synthesizer = newAgent("synth-model", synthClient);

        AgentTeam team = Agents.team()
                .plannerAgent(planner)
                .synthesizerAgent(synthesizer)
                .member(AgentTeamMember.builder().id("researcher").name("Researcher").description("collect factual weather details").agent(researcher).build())
                .member(AgentTeamMember.builder().id("formatter").name("Formatter").description("format and polish final text").agent(formatter).build())
                .options(AgentTeamOptions.builder().parallelDispatch(true).maxConcurrency(2).build())
                .build();

        AgentTeamResult result = team.run("Give me a weather summary for Beijing.");

        Assert.assertEquals("Final answer from team.", result.getOutput());
        Assert.assertNotNull(result.getPlan());
        Assert.assertEquals(2, result.getPlan().getTasks().size());
        Assert.assertEquals(2, result.getMemberResults().size());
        Assert.assertTrue(result.getMemberResults().get(0).isSuccess());
        Assert.assertTrue(result.getMemberResults().get(1).isSuccess());

        Assert.assertTrue(researcherClient.prompts.get(0).getItems().toString().contains("Collect weather facts"));
        Assert.assertTrue(formatterClient.prompts.get(0).getItems().toString().contains("Format output"));
    }

    @Test
    public void test_team_parallel_dispatch() throws Exception {
        ScriptedModelClient plannerClient = new ScriptedModelClient();
        plannerClient.enqueue(textResult("{\"tasks\":[{\"memberId\":\"m1\",\"task\":\"task one\"},{\"memberId\":\"m2\",\"task\":\"task two\"}]}"));

        ConcurrentModelClient sharedMemberClient = new ConcurrentModelClient(220L, "member-done");
        ScriptedModelClient synthClient = new ScriptedModelClient();
        synthClient.enqueue(textResult("parallel merged"));

        Agent planner = newAgent("planner", plannerClient);
        Agent m1 = newAgent("m1", sharedMemberClient);
        Agent m2 = newAgent("m2", sharedMemberClient);
        Agent synth = newAgent("synth", synthClient);

        AgentTeam team = Agents.team()
                .plannerAgent(planner)
                .synthesizerAgent(synth)
                .member(AgentTeamMember.builder().id("m1").name("M1").agent(m1).build())
                .member(AgentTeamMember.builder().id("m2").name("M2").agent(m2).build())
                .options(AgentTeamOptions.builder().parallelDispatch(true).maxConcurrency(2).build())
                .build();

        AgentTeamResult result = team.run("Run tasks in parallel");

        Assert.assertEquals("parallel merged", result.getOutput());
        Assert.assertTrue("expected parallel member execution", sharedMemberClient.maxConcurrent.get() >= 2);
    }

    @Test
    public void test_team_planner_fallback_broadcast() throws Exception {
        ScriptedModelClient plannerClient = new ScriptedModelClient();
        plannerClient.enqueue(textResult("I will delegate tasks shortly."));

        ScriptedModelClient m1Client = new ScriptedModelClient();
        m1Client.enqueue(textResult("m1 handled objective."));

        ScriptedModelClient m2Client = new ScriptedModelClient();
        m2Client.enqueue(textResult("m2 handled objective."));

        ScriptedModelClient synthClient = new ScriptedModelClient();
        synthClient.enqueue(textResult("fallback merged"));

        Agent planner = newAgent("planner", plannerClient);
        Agent m1 = newAgent("m1", m1Client);
        Agent m2 = newAgent("m2", m2Client);
        Agent synth = newAgent("synth", synthClient);

        AgentTeam team = Agents.team()
                .plannerAgent(planner)
                .synthesizerAgent(synth)
                .member(AgentTeamMember.builder().id("m1").name("M1").description("first domain").agent(m1).build())
                .member(AgentTeamMember.builder().id("m2").name("M2").description("second domain").agent(m2).build())
                .options(AgentTeamOptions.builder().broadcastOnPlannerFailure(true).parallelDispatch(false).build())
                .build();

        AgentTeamResult result = team.run("Prepare combined analysis");

        Assert.assertNotNull(result.getPlan());
        Assert.assertTrue(result.getPlan().isFallback());
        Assert.assertEquals(2, result.getMemberResults().size());
        Assert.assertEquals("fallback merged", result.getOutput());
    }

    @Test
    public void test_team_dependency_and_message_bus() throws Exception {
        ScriptedModelClient plannerClient = new ScriptedModelClient();
        plannerClient.enqueue(textResult("{\"tasks\":[{\"id\":\"collect\",\"memberId\":\"m1\",\"task\":\"collect weather facts\"},{\"id\":\"format\",\"memberId\":\"m2\",\"task\":\"format summary\",\"dependsOn\":[\"collect\"]}]}"));

        ScriptedModelClient m1Client = new ScriptedModelClient();
        m1Client.enqueue(textResult("facts ready"));

        ScriptedModelClient m2Client = new ScriptedModelClient();
        m2Client.enqueue(textResult("formatted from facts"));

        ScriptedModelClient synthClient = new ScriptedModelClient();
        synthClient.enqueue(textResult("dependency merged"));

        AgentTeam team = Agents.team()
                .plannerAgent(newAgent("planner", plannerClient))
                .synthesizerAgent(newAgent("synth", synthClient))
                .member(AgentTeamMember.builder().id("m1").name("collector").agent(newAgent("m1", m1Client)).build())
                .member(AgentTeamMember.builder().id("m2").name("formatter").agent(newAgent("m2", m2Client)).build())
                .options(AgentTeamOptions.builder().parallelDispatch(true).maxConcurrency(2).build())
                .build();

        AgentTeamResult result = team.run("build a weather brief");

        Assert.assertEquals("dependency merged", result.getOutput());
        Assert.assertEquals(2, result.getRounds());
        Assert.assertEquals(2, result.getTaskStates().size());
        Assert.assertEquals(AgentTeamTaskStatus.COMPLETED, result.getTaskStates().get(0).getStatus());
        Assert.assertEquals(AgentTeamTaskStatus.COMPLETED, result.getTaskStates().get(1).getStatus());
        Assert.assertTrue(result.getMessages().size() >= 4);
    }

    @Test
    public void test_team_plan_approval_rejected() throws Exception {
        ScriptedModelClient plannerClient = new ScriptedModelClient();
        plannerClient.enqueue(textResult("{\"tasks\":[{\"memberId\":\"m1\",\"task\":\"do task\"}]}"));

        ScriptedModelClient m1Client = new ScriptedModelClient();
        m1Client.enqueue(textResult("m1 output"));

        ScriptedModelClient synthClient = new ScriptedModelClient();
        synthClient.enqueue(textResult("unused"));

        AgentTeam team = Agents.team()
                .plannerAgent(newAgent("planner", plannerClient))
                .synthesizerAgent(newAgent("synth", synthClient))
                .member(AgentTeamMember.builder().id("m1").name("m1").agent(newAgent("m1", m1Client)).build())
                .options(AgentTeamOptions.builder().requirePlanApproval(true).build())
                .planApproval((objective, plan, members, options) -> false)
                .build();

        try {
            team.run("task");
            Assert.fail("expected plan approval rejection");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage().contains("plan rejected"));
        }
    }

    @Test
    public void test_team_dynamic_member_registration_and_hooks() throws Exception {
        ScriptedModelClient plannerClient = new ScriptedModelClient();
        plannerClient.enqueue(textResult("{\"tasks\":[{\"memberId\":\"m2\",\"task\":\"take over task\"}]}"));

        ScriptedModelClient m1Client = new ScriptedModelClient();
        m1Client.enqueue(textResult("m1 idle"));

        ScriptedModelClient m2Client = new ScriptedModelClient();
        m2Client.enqueue(textResult("m2 handled"));

        ScriptedModelClient synthClient = new ScriptedModelClient();
        synthClient.enqueue(textResult("dynamic merged"));

        AtomicInteger beforeTaskCount = new AtomicInteger();
        AtomicInteger afterTaskCount = new AtomicInteger();
        AtomicInteger messageCount = new AtomicInteger();

        AgentTeamHook hook = new AgentTeamHook() {
            @Override
            public void beforeTask(String objective, AgentTeamTask task, AgentTeamMember member) {
                beforeTaskCount.incrementAndGet();
            }

            @Override
            public void afterTask(String objective, AgentTeamMemberResult result) {
                afterTaskCount.incrementAndGet();
            }

            @Override
            public void onMessage(AgentTeamMessage message) {
                messageCount.incrementAndGet();
            }
        };

        AgentTeam team = Agents.team()
                .plannerAgent(newAgent("planner", plannerClient))
                .synthesizerAgent(newAgent("synth", synthClient))
                .member(AgentTeamMember.builder().id("m1").name("m1").agent(newAgent("m1", m1Client)).build())
                .hook(hook)
                .build();

        team.registerMember(AgentTeamMember.builder().id("m2").name("m2").agent(newAgent("m2", m2Client)).build());
        Assert.assertEquals(2, team.listMembers().size());

        AgentTeamResult result = team.run("delegate to m2");

        Assert.assertEquals("dynamic merged", result.getOutput());
        Assert.assertEquals("m2", result.getMemberResults().get(0).getMemberId());
        Assert.assertTrue(beforeTaskCount.get() >= 1);
        Assert.assertTrue(afterTaskCount.get() >= 1);
        Assert.assertTrue(messageCount.get() >= 1);

        Assert.assertTrue(team.unregisterMember("m2"));
        Assert.assertEquals(1, team.listMembers().size());
    }


    @Test
    public void test_team_message_controls_direct_and_broadcast() {
        ScriptedModelClient sharedClient = new ScriptedModelClient();

        AgentTeam team = Agents.team()
                .plannerAgent(newAgent("planner", sharedClient))
                .synthesizerAgent(newAgent("synth", sharedClient))
                .member(AgentTeamMember.builder().id("m1").name("m1").agent(newAgent("m1", sharedClient)).build())
                .member(AgentTeamMember.builder().id("m2").name("m2").agent(newAgent("m2", sharedClient)).build())
                .build();

        team.sendMessage("m1", "m2", "peer.ask", "task-1", "need your evidence");
        team.broadcastMessage("m2", "peer.broadcast", null, "shared update");

        Assert.assertEquals(2, team.listMessages().size());
        Assert.assertEquals(2, team.listMessagesFor("m2", 10).size());
        Assert.assertEquals(1, team.listMessagesFor("m1", 10).size());
    }

    @Test
    public void test_team_list_task_states_after_run() throws Exception {
        ScriptedModelClient plannerClient = new ScriptedModelClient();
        plannerClient.enqueue(textResult("{\"tasks\":[{\"id\":\"collect\",\"memberId\":\"m1\",\"task\":\"collect\"}]}"));

        ScriptedModelClient memberClient = new ScriptedModelClient();
        memberClient.enqueue(textResult("collected"));

        ScriptedModelClient synthClient = new ScriptedModelClient();
        synthClient.enqueue(textResult("done"));

        AgentTeam team = Agents.team()
                .plannerAgent(newAgent("planner", plannerClient))
                .synthesizerAgent(newAgent("synth", synthClient))
                .member(AgentTeamMember.builder().id("m1").name("m1").agent(newAgent("m1", memberClient)).build())
                .build();

        AgentTeamResult result = team.run("run one task");

        Assert.assertEquals("done", result.getOutput());
        List<io.github.lnyocly.ai4j.agent.team.AgentTeamTaskState> states = team.listTaskStates();
        Assert.assertEquals(1, states.size());
        Assert.assertEquals(AgentTeamTaskStatus.COMPLETED, states.get(0).getStatus());
    }

    @Test
    public void test_team_member_tools_can_message_and_heartbeat() throws Exception {
        ScriptedModelClient plannerClient = new ScriptedModelClient();
        plannerClient.enqueue(textResult("{\"tasks\":[{\"id\":\"task_1\",\"memberId\":\"m1\",\"task\":\"collect and notify\"}]}"));

        ScriptedModelClient m1Client = new ScriptedModelClient();
        m1Client.enqueue(toolCallResult(Arrays.asList(
                toolCall("team_send_message", "{\"toMemberId\":\"m2\",\"type\":\"peer.ask\",\"taskId\":\"task_1\",\"content\":\"Please share your notes\"}"),
                toolCall("team_broadcast", "{\"type\":\"peer.broadcast\",\"content\":\"task_1 is running\"}"),
                toolCall("team_heartbeat_task", "{\"taskId\":\"task_1\"}"),
                toolCall("team_list_tasks", "{}")
        )));
        m1Client.enqueue(textResult("m1 finished"));

        ScriptedModelClient m2Client = new ScriptedModelClient();
        m2Client.enqueue(textResult("m2 standby"));

        ScriptedModelClient synthClient = new ScriptedModelClient();
        synthClient.enqueue(textResult("team final"));

        AgentTeam team = Agents.team()
                .plannerAgent(newAgent("planner", plannerClient))
                .synthesizerAgent(newAgent("synth", synthClient))
                .member(AgentTeamMember.builder().id("m1").name("member-1").agent(newAgent("m1", m1Client)).build())
                .member(AgentTeamMember.builder().id("m2").name("member-2").agent(newAgent("m2", m2Client)).build())
                .build();

        AgentTeamResult result = team.run("execute team tools");

        Assert.assertEquals("team final", result.getOutput());
        Assert.assertTrue(hasToolNamed(m1Client.prompts.get(0).getTools(), "team_send_message"));
        Assert.assertTrue(hasToolNamed(m1Client.prompts.get(0).getTools(), "team_broadcast"));
        Assert.assertTrue(hasToolNamed(m1Client.prompts.get(0).getTools(), "team_heartbeat_task"));

        boolean hasDirect = false;
        boolean hasBroadcast = false;
        for (AgentTeamMessage message : result.getMessages()) {
            if ("peer.ask".equals(message.getType())
                    && "m1".equals(message.getFromMemberId())
                    && "m2".equals(message.getToMemberId())) {
                hasDirect = true;
            }
            if ("peer.broadcast".equals(message.getType())
                    && "m1".equals(message.getFromMemberId())
                    && "*".equals(message.getToMemberId())) {
                hasBroadcast = true;
            }
        }
        Assert.assertTrue(hasDirect);
        Assert.assertTrue(hasBroadcast);
    }

    @Test
    public void test_team_single_lead_agent_defaults_planner_and_synthesizer() throws Exception {
        ScriptedModelClient leadClient = new ScriptedModelClient();
        leadClient.enqueue(textResult("{\"tasks\":[{\"id\":\"collect\",\"memberId\":\"m1\",\"task\":\"collect weather facts\"}]}"));
        leadClient.enqueue(textResult("lead merged output"));

        ScriptedModelClient memberClient = new ScriptedModelClient();
        memberClient.enqueue(textResult("facts done"));

        AgentTeam team = Agents.team()
                .leadAgent(newAgent("lead", leadClient))
                .member(AgentTeamMember.builder().id("m1").name("m1").agent(newAgent("m1", memberClient)).build())
                .build();

        AgentTeamResult result = team.run("build summary with lead agent");

        Assert.assertEquals("lead merged output", result.getOutput());
        Assert.assertEquals(2, leadClient.prompts.size());
        Assert.assertTrue(leadClient.prompts.get(0).getItems().toString().contains("You are a team planner."));
        Assert.assertTrue(leadClient.prompts.get(1).getItems().toString().contains("You are the team lead."));
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

    private static AgentModelResult toolCallResult(List<AgentToolCall> calls) {
        return AgentModelResult.builder()
                .outputText("")
                .memoryItems(new ArrayList<Object>())
                .toolCalls(calls)
                .build();
    }

    private static AgentToolCall toolCall(String name, String arguments) {
        return AgentToolCall.builder()
                .name(name)
                .arguments(arguments)
                .callId(name + "_" + System.nanoTime())
                .type("function")
                .build();
    }

    private static boolean hasToolNamed(List<Object> tools, String name) {
        if (tools == null || tools.isEmpty() || name == null) {
            return false;
        }
        for (Object toolObj : tools) {
            if (!(toolObj instanceof Tool)) {
                continue;
            }
            Tool tool = (Tool) toolObj;
            if (tool.getFunction() != null && name.equals(tool.getFunction().getName())) {
                return true;
            }
        }
        return false;
    }

    private static class ScriptedModelClient implements AgentModelClient {
        private final Deque<AgentModelResult> queue = new ArrayDeque<>();
        private final List<AgentPrompt> prompts = new ArrayList<>();

        private void enqueue(AgentModelResult result) {
            queue.add(result);
        }

        @Override
        public AgentModelResult create(AgentPrompt prompt) {
            prompts.add(prompt);
            return queue.isEmpty() ? AgentModelResult.builder().build() : queue.poll();
        }

        @Override
        public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
            throw new UnsupportedOperationException("stream not used in test");
        }
    }

    private static class ConcurrentModelClient implements AgentModelClient {
        private final long sleepMs;
        private final String output;
        private final AtomicInteger active = new AtomicInteger();
        private final AtomicInteger maxConcurrent = new AtomicInteger();

        private ConcurrentModelClient(long sleepMs, String output) {
            this.sleepMs = sleepMs;
            this.output = output;
        }

        @Override
        public AgentModelResult create(AgentPrompt prompt) {
            int concurrent = active.incrementAndGet();
            maxConcurrent.accumulateAndGet(concurrent, Math::max);
            try {
                Thread.sleep(sleepMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                active.decrementAndGet();
            }
            return textResult(output);
        }

        @Override
        public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
            throw new UnsupportedOperationException("stream not used in test");
        }
    }
}
