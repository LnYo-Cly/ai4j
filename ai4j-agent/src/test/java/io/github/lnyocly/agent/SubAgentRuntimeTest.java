package io.github.lnyocly.agent;

import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.event.AgentEvent;
import io.github.lnyocly.ai4j.agent.event.AgentEventPublisher;
import io.github.lnyocly.ai4j.agent.event.AgentEventType;
import io.github.lnyocly.ai4j.agent.event.AgentListener;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.subagent.SubAgentDefinition;
import io.github.lnyocly.ai4j.agent.team.AgentTeamMember;
import io.github.lnyocly.ai4j.agent.team.AgentTeamPlan;
import io.github.lnyocly.ai4j.agent.team.AgentTeamTask;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class SubAgentRuntimeTest {

    @Test
    public void testSubagentToolDelegationAndExposure() throws Exception {
        ScriptedModelClient reviewerClient = new ScriptedModelClient();
        reviewerClient.enqueue(resultWithText("subagent-review-ready"));

        Agent reviewer = Agents.react()
                .modelClient(reviewerClient)
                .model("reviewer-model")
                .build();

        String subToolName = "delegate_code_review";
        SubAgentDefinition reviewerSubAgent = SubAgentDefinition.builder()
                .name("code-reviewer")
                .description("Review code quality and risks")
                .toolName(subToolName)
                .agent(reviewer)
                .build();

        ScriptedModelClient parentClient = new ScriptedModelClient();
        parentClient.enqueue(resultWithToolCall("call_1", subToolName, "{\"task\":\"Review the auth flow\"}"));
        parentClient.enqueue(resultWithText("main-final-answer"));

        Agent parent = Agents.react()
                .modelClient(parentClient)
                .model("manager-model")
                .subAgent(reviewerSubAgent)
                .build();

        AgentResult result = parent.run(AgentRequest.builder().input("analyze").build());

        Assert.assertEquals("main-final-answer", result.getOutputText());
        Assert.assertEquals(1, result.getToolResults().size());
        Assert.assertTrue(result.getToolResults().get(0).getOutput().contains("subagent-review-ready"));

        AgentPrompt firstPrompt = parentClient.prompts.get(0);
        Assert.assertTrue(hasTool(firstPrompt.getTools(), subToolName));
    }

    @Test
    public void testParallelSubagentExecutionForCodexStyleDelegation() throws Exception {
        ConcurrentModelClient sharedSubClient = new ConcurrentModelClient(250L, "subagent-output");

        Agent weatherSubAgent = Agents.react()
                .modelClient(sharedSubClient)
                .model("sub-model")
                .build();

        Agent formatSubAgent = Agents.react()
                .modelClient(sharedSubClient)
                .model("sub-model")
                .build();

        SubAgentDefinition weather = SubAgentDefinition.builder()
                .name("weather")
                .toolName("delegate_weather")
                .description("Collect weather details")
                .agent(weatherSubAgent)
                .build();

        SubAgentDefinition format = SubAgentDefinition.builder()
                .name("format")
                .toolName("delegate_format")
                .description("Format weather report")
                .agent(formatSubAgent)
                .build();

        ScriptedModelClient parentClient = new ScriptedModelClient();
        parentClient.enqueue(resultWithToolCalls(Arrays.asList(
                AgentToolCall.builder().callId("c_weather").name("delegate_weather").arguments("{\"task\":\"Beijing\"}").build(),
                AgentToolCall.builder().callId("c_format").name("delegate_format").arguments("{\"task\":\"Shanghai\"}").build()
        )));
        parentClient.enqueue(resultWithText("done"));

        Agent parent = Agents.react()
                .modelClient(parentClient)
                .model("manager-model")
                .parallelToolCalls(true)
                .subAgents(Arrays.asList(weather, format))
                .build();

        AgentResult result = parent.run(AgentRequest.builder().input("parallel").build());

        Assert.assertEquals("done", result.getOutputText());
        Assert.assertTrue("expected parallel subagent execution", sharedSubClient.maxConcurrent.get() >= 2);
    }

    @Test
    public void testSubagentHandoffEventsArePublished() throws Exception {
        ScriptedModelClient reviewerClient = new ScriptedModelClient();
        reviewerClient.enqueue(resultWithText("subagent-review-ready"));

        Agent reviewer = Agents.react()
                .modelClient(reviewerClient)
                .model("reviewer-model")
                .build();

        SubAgentDefinition reviewerSubAgent = SubAgentDefinition.builder()
                .name("code-reviewer")
                .description("Review code quality and risks")
                .toolName("delegate_code_review")
                .agent(reviewer)
                .build();

        ScriptedModelClient parentClient = new ScriptedModelClient();
        parentClient.enqueue(resultWithToolCall("call_1", "delegate_code_review", "{\"task\":\"Review the auth flow\"}"));
        parentClient.enqueue(resultWithText("main-final-answer"));

        final List<AgentEvent> events = new ArrayList<AgentEvent>();
        AgentEventPublisher publisher = new AgentEventPublisher();
        publisher.addListener(new AgentListener() {
            @Override
            public void onEvent(AgentEvent event) {
                events.add(event);
            }
        });

        Agent parent = Agents.react()
                .modelClient(parentClient)
                .model("manager-model")
                .subAgent(reviewerSubAgent)
                .eventPublisher(publisher)
                .build();

        AgentResult result = parent.run(AgentRequest.builder().input("analyze").build());

        Assert.assertEquals("main-final-answer", result.getOutputText());

        AgentEvent startEvent = firstEvent(events, AgentEventType.HANDOFF_START);
        AgentEvent endEvent = firstEvent(events, AgentEventType.HANDOFF_END);
        Assert.assertNotNull(startEvent);
        Assert.assertNotNull(endEvent);

        @SuppressWarnings("unchecked")
        Map<String, Object> startPayload = (Map<String, Object>) startEvent.getPayload();
        @SuppressWarnings("unchecked")
        Map<String, Object> endPayload = (Map<String, Object>) endEvent.getPayload();
        Assert.assertEquals("code-reviewer", startPayload.get("subagent"));
        Assert.assertEquals("delegate_code_review", startPayload.get("tool"));
        Assert.assertEquals("starting", startPayload.get("status"));
        Assert.assertEquals("completed", endPayload.get("status"));
        Assert.assertEquals("subagent-review-ready", endPayload.get("output"));
    }

    @Test
    public void testTeamSubagentPublishesTeamTaskEventsToParent() throws Exception {
        ScriptedModelClient teamMemberClient = new ScriptedModelClient();
        teamMemberClient.enqueue(resultWithText("team-member-ready"));

        ScriptedModelClient teamSynthClient = new ScriptedModelClient();
        teamSynthClient.enqueue(resultWithText("team-subagent-final"));

        Agent teamSubagent = Agents.team()
                .planner((objective, members, options) -> AgentTeamPlan.builder()
                        .tasks(Arrays.asList(
                                AgentTeamTask.builder()
                                        .id("review-task")
                                        .memberId("reviewer")
                                        .task("Review the patch")
                                        .build()
                        ))
                        .build())
                .synthesizerAgent(Agents.react()
                        .modelClient(teamSynthClient)
                        .model("team-synth")
                        .build())
                .member(AgentTeamMember.builder()
                        .id("reviewer")
                        .name("Reviewer")
                        .agent(Agents.react()
                                .modelClient(teamMemberClient)
                                .model("team-member")
                                .build())
                        .build())
                .buildAgent();

        SubAgentDefinition reviewerSubAgent = SubAgentDefinition.builder()
                .name("team-reviewer")
                .description("Review code quality with a small team")
                .toolName("delegate_team_review")
                .agent(teamSubagent)
                .build();

        ScriptedModelClient parentClient = new ScriptedModelClient();
        parentClient.enqueue(resultWithToolCall("call_team", "delegate_team_review", "{\"task\":\"Review the auth flow\"}"));
        parentClient.enqueue(resultWithText("main-final-answer"));

        final List<AgentEvent> events = new ArrayList<AgentEvent>();
        AgentEventPublisher publisher = new AgentEventPublisher();
        publisher.addListener(new AgentListener() {
            @Override
            public void onEvent(AgentEvent event) {
                events.add(event);
            }
        });

        Agent parent = Agents.react()
                .modelClient(parentClient)
                .model("manager-model")
                .subAgent(reviewerSubAgent)
                .eventPublisher(publisher)
                .build();

        AgentResult result = parent.run(AgentRequest.builder().input("analyze").build());

        Assert.assertEquals("main-final-answer", result.getOutputText());
        Assert.assertNotNull(firstEvent(events, AgentEventType.TEAM_TASK_CREATED));
        AgentEvent updated = firstEventByStatus(events, AgentEventType.TEAM_TASK_UPDATED, "completed");
        Assert.assertNotNull(updated);

        @SuppressWarnings("unchecked")
        Map<String, Object> updatedPayload = (Map<String, Object>) updated.getPayload();
        Assert.assertEquals("completed", updatedPayload.get("status"));
        Assert.assertEquals("team-member-ready", updatedPayload.get("output"));
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

    private static AgentEvent firstEventByStatus(List<AgentEvent> events, AgentEventType type, String status) {
        if (events == null || type == null) {
            return null;
        }
        for (AgentEvent event : events) {
            if (event == null || type != event.getType() || !(event.getPayload() instanceof Map)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) event.getPayload();
            Object currentStatus = payload.get("status");
            if (status == null ? currentStatus == null : status.equals(String.valueOf(currentStatus))) {
                return event;
            }
        }
        return null;
    }

    private static boolean hasTool(List<Object> tools, String name) {
        if (tools == null) {
            return false;
        }
        for (Object tool : tools) {
            if (tool instanceof Tool) {
                Tool.Function fn = ((Tool) tool).getFunction();
                if (fn != null && name.equals(fn.getName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static AgentModelResult resultWithText(String text) {
        return AgentModelResult.builder()
                .outputText(text)
                .memoryItems(new ArrayList<Object>())
                .toolCalls(new ArrayList<AgentToolCall>())
                .build();
    }

    private static AgentModelResult resultWithToolCall(String callId, String toolName, String args) {
        return resultWithToolCalls(Arrays.asList(
                AgentToolCall.builder()
                        .callId(callId)
                        .name(toolName)
                        .arguments(args)
                        .type("function_call")
                        .build()
        ));
    }

    private static AgentModelResult resultWithToolCalls(List<AgentToolCall> calls) {
        return AgentModelResult.builder()
                .toolCalls(calls)
                .memoryItems(new ArrayList<Object>())
                .build();
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
            return resultWithText(output);
        }

        @Override
        public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
            throw new UnsupportedOperationException("stream not used in test");
        }
    }
}
