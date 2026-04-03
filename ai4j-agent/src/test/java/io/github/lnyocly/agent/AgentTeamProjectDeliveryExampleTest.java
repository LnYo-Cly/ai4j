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
import io.github.lnyocly.ai4j.agent.team.AgentTeamMemberResult;
import io.github.lnyocly.ai4j.agent.team.AgentTeamMessage;
import io.github.lnyocly.ai4j.agent.team.AgentTeamOptions;
import io.github.lnyocly.ai4j.agent.team.AgentTeamPlan;
import io.github.lnyocly.ai4j.agent.team.AgentTeamPlanner;
import io.github.lnyocly.ai4j.agent.team.AgentTeamResult;
import io.github.lnyocly.ai4j.agent.team.AgentTeamSynthesizer;
import io.github.lnyocly.ai4j.agent.team.AgentTeamTask;
import io.github.lnyocly.ai4j.agent.team.AgentTeamTaskState;
import io.github.lnyocly.ai4j.agent.team.AgentTeamTaskStatus;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * End-to-end AgentTeam example for a software delivery team.
 *
 * Team roles: architect, backend, frontend, QA, ops.
 */
public class AgentTeamProjectDeliveryExampleTest {

    @Test
    public void test_project_delivery_team_example() throws Exception {
        ScriptedModelClient architectClient = new ScriptedModelClient();
        architectClient.enqueue(textResult(
                "Architecture: modular monolith + REST API + MySQL + Redis + JWT + CI/CD checkpoints."));

        ScriptedModelClient backendClient = new ScriptedModelClient();
        backendClient.enqueue(toolCallResult(Arrays.asList(
                toolCall("team_send_message",
                        "{\"toMemberId\":\"frontend\",\"type\":\"api.contract\",\"content\":\"Draft API: GET/POST /api/tasks, GET /api/tasks/{id}, PATCH /api/tasks/{id}\"}")
        )));
        backendClient.enqueue(textResult(
                "Backend: Spring Boot modules, DTO validation, OpenAPI spec, and migration scripts are ready."));

        ScriptedModelClient frontendClient = new ScriptedModelClient();
        frontendClient.enqueue(toolCallResult(Arrays.asList(
                toolCall("team_send_message",
                        "{\"toMemberId\":\"backend\",\"type\":\"ui.question\",\"content\":\"Please confirm pagination params page,size and sort format.\"}")
        )));
        frontendClient.enqueue(textResult(
                "Frontend: React pages for task list/detail/edit, API client integration, and error-state UI done."));

        ScriptedModelClient qaClient = new ScriptedModelClient();
        qaClient.enqueue(toolCallResult(Arrays.asList(
                toolCall("team_list_tasks", "{}"),
                toolCall("team_broadcast",
                        "{\"type\":\"qa.sync\",\"content\":\"Integration testing started. Please freeze API changes.\"}")
        )));
        qaClient.enqueue(textResult(
                "QA: smoke/regression cases pass; one minor UI edge case documented with workaround."));

        ScriptedModelClient opsClient = new ScriptedModelClient();
        opsClient.enqueue(textResult(
                "Ops: Docker image, staging deployment, health checks, dashboards, and rollback playbook prepared."));

        AgentTeam team = Agents.team()
                .planner(projectPlanner())
                .synthesizer(projectSynthesizer())
                .member(member("architect", "Architect", "system design and API boundaries", architectClient))
                .member(member("backend", "Backend", "service implementation and persistence", backendClient))
                .member(member("frontend", "Frontend", "UI implementation and API integration", frontendClient))
                .member(member("qa", "QA", "test strategy and release quality gate", qaClient))
                .member(member("ops", "Ops", "deployment, observability, and production readiness", opsClient))
                .options(AgentTeamOptions.builder()
                        .parallelDispatch(true)
                        .maxConcurrency(3)
                        .enableMessageBus(true)
                        .includeMessageHistoryInDispatch(true)
                        .enableMemberTeamTools(true)
                        .build())
                .build();

        AgentTeamResult result = team.run(AgentRequest.builder()
                .input("Deliver a production-ready task management web app in one sprint.")
                .build());

        printResult(result);

        Assert.assertNotNull(result);
        Assert.assertNotNull(result.getPlan());
        Assert.assertEquals(5, result.getPlan().getTasks().size());
        Assert.assertEquals(5, result.getMemberResults().size());
        Assert.assertTrue(result.getOutput().contains("Project Delivery Summary"));
        Assert.assertTrue(result.getOutput().contains("[Architect]"));
        Assert.assertTrue(result.getOutput().contains("[Backend]"));
        Assert.assertTrue(result.getOutput().contains("[Frontend]"));
        Assert.assertTrue(result.getOutput().contains("[QA]"));
        Assert.assertTrue(result.getOutput().contains("[Ops]"));

        for (AgentTeamTaskState state : result.getTaskStates()) {
            Assert.assertEquals("task must complete: " + state.getTaskId(),
                    AgentTeamTaskStatus.COMPLETED,
                    state.getStatus());
        }

        Assert.assertTrue(hasMessage(result.getMessages(), "backend", "frontend", "api.contract"));
        Assert.assertTrue(hasMessage(result.getMessages(), "frontend", "backend", "ui.question"));
        Assert.assertTrue(hasMessage(result.getMessages(), "qa", "*", "qa.sync"));
    }

    private static AgentTeamPlanner projectPlanner() {
        return (objective, members, options) -> AgentTeamPlan.builder()
                .rawPlanText("fixed-project-delivery-plan")
                .tasks(Arrays.asList(
                        AgentTeamTask.builder()
                                .id("architecture")
                                .memberId("architect")
                                .task("Design the target architecture and define API boundaries.")
                                .context("Output architecture notes + API contracts")
                                .build(),
                        AgentTeamTask.builder()
                                .id("backend_impl")
                                .memberId("backend")
                                .task("Implement backend services and persistence schema.")
                                .dependsOn(Arrays.asList("architecture"))
                                .build(),
                        AgentTeamTask.builder()
                                .id("frontend_impl")
                                .memberId("frontend")
                                .task("Implement frontend pages and integrate backend APIs.")
                                .dependsOn(Arrays.asList("architecture"))
                                .build(),
                        AgentTeamTask.builder()
                                .id("qa_validation")
                                .memberId("qa")
                                .task("Run integration/regression tests and publish quality report.")
                                .dependsOn(Arrays.asList("backend_impl", "frontend_impl"))
                                .build(),
                        AgentTeamTask.builder()
                                .id("ops_release")
                                .memberId("ops")
                                .task("Prepare release pipeline, monitoring, and rollback plan.")
                                .dependsOn(Arrays.asList("backend_impl", "frontend_impl"))
                                .build()
                ))
                .build();
    }

    private static AgentTeamSynthesizer projectSynthesizer() {
        return (objective, plan, memberResults, options) -> {
            Map<String, String> byMember = new LinkedHashMap<>();
            if (memberResults != null) {
                for (AgentTeamMemberResult item : memberResults) {
                    if (item == null || item.getMemberId() == null) {
                        continue;
                    }
                    byMember.put(item.getMemberId(), item.isSuccess() ? safe(item.getOutput()) : "FAILED: " + safe(item.getError()));
                }
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Project Delivery Summary\n");
            sb.append("Objective: ").append(safe(objective)).append("\n\n");
            sb.append("[Architect]\n").append(safe(byMember.get("architect"))).append("\n\n");
            sb.append("[Backend]\n").append(safe(byMember.get("backend"))).append("\n\n");
            sb.append("[Frontend]\n").append(safe(byMember.get("frontend"))).append("\n\n");
            sb.append("[QA]\n").append(safe(byMember.get("qa"))).append("\n\n");
            sb.append("[Ops]\n").append(safe(byMember.get("ops"))).append("\n");

            return AgentResult.builder().outputText(sb.toString()).build();
        };
    }

    private static AgentTeamMember member(String id,
                                          String name,
                                          String description,
                                          AgentModelClient modelClient) {
        return AgentTeamMember.builder()
                .id(id)
                .name(name)
                .description(description)
                .agent(Agents.react().model(modelClient.getClass().getSimpleName()).modelClient(modelClient).build())
                .build();
    }

    private static boolean hasMessage(List<AgentTeamMessage> messages,
                                      String from,
                                      String to,
                                      String type) {
        if (messages == null || messages.isEmpty()) {
            return false;
        }
        for (AgentTeamMessage msg : messages) {
            if (msg == null) {
                continue;
            }
            if (equals(msg.getFromMemberId(), from)
                    && equals(msg.getToMemberId(), to)
                    && equals(msg.getType(), type)) {
                return true;
            }
        }
        return false;
    }

    private static boolean equals(String left, String right) {
        if (left == null) {
            return right == null;
        }
        return left.equals(right);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static AgentModelResult textResult(String text) {
        return AgentModelResult.builder()
                .outputText(text)
                .toolCalls(new ArrayList<AgentToolCall>())
                .memoryItems(new ArrayList<Object>())
                .build();
    }

    private static AgentModelResult toolCallResult(List<AgentToolCall> calls) {
        return AgentModelResult.builder()
                .outputText("")
                .toolCalls(calls)
                .memoryItems(new ArrayList<Object>())
                .build();
    }

    private static AgentToolCall toolCall(String name, String arguments) {
        return AgentToolCall.builder()
                .name(name)
                .arguments(arguments)
                .type("function")
                .callId(name + "_" + System.nanoTime())
                .build();
    }

    private static void printResult(AgentTeamResult result) {
        System.out.println("==== TEAM PLAN ====");
        if (result != null && result.getPlan() != null && result.getPlan().getTasks() != null) {
            for (AgentTeamTask task : result.getPlan().getTasks()) {
                System.out.println(task.getId() + " -> " + task.getMemberId() + " | dependsOn=" + task.getDependsOn());
            }
        }

        System.out.println("==== TASK STATES ====");
        if (result != null && result.getTaskStates() != null) {
            for (AgentTeamTaskState state : result.getTaskStates()) {
                System.out.println(state.getTaskId() + " => " + state.getStatus() + " by " + state.getClaimedBy());
            }
        }

        System.out.println("==== TEAM MESSAGES ====");
        if (result != null && result.getMessages() != null) {
            for (AgentTeamMessage message : result.getMessages()) {
                System.out.println("[" + message.getType() + "] "
                        + message.getFromMemberId() + " -> " + message.getToMemberId()
                        + " (task=" + message.getTaskId() + "): "
                        + message.getContent());
            }
        }

        System.out.println("==== FINAL OUTPUT ====");
        System.out.println(result == null ? "" : result.getOutput());
    }

    private static class ScriptedModelClient implements AgentModelClient {
        private final Deque<AgentModelResult> queue = new ArrayDeque<>();

        void enqueue(AgentModelResult result) {
            queue.add(result);
        }

        @Override
        public AgentModelResult create(AgentPrompt prompt) {
            return queue.isEmpty() ? AgentModelResult.builder().build() : queue.poll();
        }

        @Override
        public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
            throw new UnsupportedOperationException("stream not used in this test");
        }
    }
}
