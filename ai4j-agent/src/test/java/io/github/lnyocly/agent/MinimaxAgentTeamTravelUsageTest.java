package io.github.lnyocly.agent;

import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentOptions;
import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.model.ChatModelClient;
import io.github.lnyocly.ai4j.agent.team.AgentTeam;
import io.github.lnyocly.ai4j.agent.team.AgentTeamMember;
import io.github.lnyocly.ai4j.agent.team.AgentTeamMemberResult;
import io.github.lnyocly.ai4j.agent.team.AgentTeamOptions;
import io.github.lnyocly.ai4j.agent.team.AgentTeamPlan;
import io.github.lnyocly.ai4j.agent.team.AgentTeamResult;
import io.github.lnyocly.ai4j.agent.team.AgentTeamTask;
import io.github.lnyocly.ai4j.agent.team.AgentTeamTaskState;
import io.github.lnyocly.ai4j.agent.team.AgentTeamTaskStatus;
import io.github.lnyocly.ai4j.config.MinimaxConfig;
import io.github.lnyocly.ai4j.interceptor.ErrorInterceptor;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.PlatformType;
import io.github.lnyocly.ai4j.service.factory.AiService;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class MinimaxAgentTeamTravelUsageTest {

    private static final String DEFAULT_MODEL = "MiniMax-M2.7";

    private ChatModelClient modelClient;
    private String model;

    @Before
    public void setupMinimaxClient() {
        String apiKey = readValue("MINIMAX_API_KEY", "minimax.api.key");
        Assume.assumeTrue("Skip because MiniMax API key is not configured", !isBlank(apiKey));

        model = readValue("MINIMAX_MODEL", "minimax.model");
        if (isBlank(model)) {
            model = DEFAULT_MODEL;
        }

        Configuration configuration = new Configuration();
        MinimaxConfig minimaxConfig = new MinimaxConfig();
        minimaxConfig.setApiKey(apiKey);
        configuration.setMinimaxConfig(minimaxConfig);
        configuration.setOkHttpClient(createHttpClient());

        AiService aiService = new AiService(configuration);
        modelClient = new ChatModelClient(aiService.getChatService(PlatformType.MINIMAX));
    }

    @Test
    public void test_travel_demo_delivery_team_with_minimax() throws Exception {
        AgentTeam team = Agents.team()
                .planner((objective, members, options) -> AgentTeamPlan.builder()
                        .rawPlanText("travel-demo-fixed-plan")
                        .tasks(Arrays.asList(
                                AgentTeamTask.builder()
                                        .id("product")
                                        .memberId("product")
                                        .task("Define the MVP for a travel planning demo app, target users, user stories, and acceptance criteria.")
                                        .context("Output should include scope, features, non-goals, and release criteria. Objective: "
                                                + safe(objective))
                                        .build(),
                                AgentTeamTask.builder()
                                        .id("architecture")
                                        .memberId("architect")
                                        .task("Design the system architecture, backend/frontend boundaries, and delivery sequence for the travel demo app.")
                                        .context("Base the design on the product scope. Produce module boundaries, deployment shape, and API contract outline.")
                                        .dependsOn(Arrays.asList("product"))
                                        .build(),
                                AgentTeamTask.builder()
                                        .id("backend")
                                        .memberId("backend")
                                        .task("Design backend APIs, data model, and implementation plan for itinerary planning, destination search, and booking summary.")
                                        .context("Produce concrete REST endpoints, request/response examples, and persistence model.")
                                        .dependsOn(Arrays.asList("product", "architecture"))
                                        .build(),
                                AgentTeamTask.builder()
                                        .id("frontend")
                                        .memberId("frontend")
                                        .task("Design frontend pages, key components, and interaction flow for the travel demo app.")
                                        .context("Cover the landing page, destination discovery, itinerary builder, and booking summary flow.")
                                        .dependsOn(Arrays.asList("product", "architecture"))
                                        .build(),
                                AgentTeamTask.builder()
                                        .id("qa")
                                        .memberId("qa")
                                        .task("Create the QA strategy, core test matrix, and release gate for the travel demo app.")
                                        .context("Base validation on the PRD, architecture, backend API plan, and frontend flow.")
                                        .dependsOn(Arrays.asList("product", "backend", "frontend"))
                                        .build()
                        ))
                        .build())
                .synthesizer((objective, plan, memberResults, options) -> AgentResult.builder()
                        .outputText(renderSummary(objective, memberResults))
                        .build())
                .member(member(
                        "product",
                        "Product Manager",
                        "Owns product scope, user value, and acceptance criteria.",
                        "You are the product manager for a travel demo application.\n"
                                + "Deliver a concise but concrete PRD-style output with: target users, user stories, MVP scope, non-goals, and release acceptance criteria.\n"
                                + "Keep the result structured and implementation-ready."
                ))
                .member(member(
                        "architect",
                        "Architecture Analyst",
                        "Owns system boundaries, technical design, and delivery sequencing.",
                        "You are the architecture analyst for a travel demo application.\n"
                                + "Deliver the technical architecture, core modules, backend/frontend boundaries, data flow, and implementation milestones.\n"
                                + "Reference the product scope and write for engineers."
                ))
                .member(member(
                        "backend",
                        "Backend Engineer",
                        "Owns APIs, domain model, and backend delivery plan.",
                        "You are the backend engineer for a travel demo application.\n"
                                + "Produce concrete REST APIs, data models, service modules, and implementation notes for itinerary planning, destination search, and booking summary.\n"
                                + "Prefer production-like API shape and clear request/response examples."
                ))
                .member(member(
                        "frontend",
                        "Frontend Engineer",
                        "Owns user flows, pages, and component structure.",
                        "You are the frontend engineer for a travel demo application.\n"
                                + "Produce the page map, component breakdown, UI states, and API integration points for the main flows.\n"
                                + "Focus on a realistic MVP that a React web app could implement."
                ))
                .member(member(
                        "qa",
                        "QA Engineer",
                        "Owns verification strategy and release quality gate.",
                        "You are the QA engineer for a travel demo application.\n"
                                + "Produce the test strategy, scenario matrix, risk-based priorities, and release criteria.\n"
                                + "Cover happy path, validation, API failure, and regression scope."
                ))
                .options(AgentTeamOptions.builder()
                        .parallelDispatch(true)
                        .maxConcurrency(3)
                        .enableMessageBus(true)
                        .includeMessageHistoryInDispatch(true)
                        .enableMemberTeamTools(true)
                        .maxRounds(12)
                        .build())
                .build();

        AgentTeamResult result = callWithProviderGuard(new ThrowingSupplier<AgentTeamResult>() {
            @Override
            public AgentTeamResult get() throws Exception {
                return team.run(AgentRequest.builder()
                        .input("Create a concrete delivery package for a travel planning demo app that helps users discover destinations, build itineraries, and review booking summaries.")
                        .build());
            }
        });

        printResult(result);

        Assert.assertNotNull(result);
        Assert.assertNotNull(result.getPlan());
        Assert.assertEquals(5, result.getPlan().getTasks().size());
        Assert.assertEquals(5, result.getTaskStates().size());
        Assert.assertTrue("Team tasks did not all complete: " + describeTaskStates(result.getTaskStates()),
                allTasksCompleted(result.getTaskStates()));
        Assert.assertTrue(result.getMemberResults().size() >= 5);
        Assert.assertNotNull(result.getOutput());
        Assert.assertTrue(result.getOutput().contains("[Product]"));
        Assert.assertTrue(result.getOutput().contains("[Architect]"));
        Assert.assertTrue(result.getOutput().contains("[Backend]"));
        Assert.assertTrue(result.getOutput().contains("[Frontend]"));
        Assert.assertTrue(result.getOutput().contains("[QA]"));

        for (AgentTeamMemberResult memberResult : result.getMemberResults()) {
            Assert.assertNotNull(memberResult);
            Assert.assertTrue("member result should succeed: " + memberResult.getMemberId(), memberResult.isSuccess());
            Assert.assertTrue("member output should not be blank: " + memberResult.getMemberId(),
                    !isBlank(memberResult.getOutput()));
        }
    }

    private AgentTeamMember member(String id,
                                   String name,
                                   String description,
                                   String systemPrompt) {
        return AgentTeamMember.builder()
                .id(id)
                .name(name)
                .description(description)
                .agent(Agents.builder()
                        .modelClient(modelClient)
                        .model(model)
                        .temperature(0.4)
                        .systemPrompt(systemPrompt)
                        .options(AgentOptions.builder()
                                .maxSteps(4)
                                .stream(false)
                                .build())
                        .build())
                .build();
    }

    private OkHttpClient createHttpClient() {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BASIC);
        return new OkHttpClient.Builder()
                .addInterceptor(logging)
                .addInterceptor(new ErrorInterceptor())
                .connectTimeout(300, TimeUnit.SECONDS)
                .writeTimeout(300, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)
                .build();
    }

    private String renderSummary(String objective, List<AgentTeamMemberResult> memberResults) {
        Map<String, String> outputs = new LinkedHashMap<String, String>();
        if (memberResults != null) {
            for (AgentTeamMemberResult item : memberResults) {
                if (item == null || isBlank(item.getMemberId())) {
                    continue;
                }
                outputs.put(item.getMemberId(), item.isSuccess() ? safe(item.getOutput()) : "FAILED: " + safe(item.getError()));
            }
        }

        StringBuilder builder = new StringBuilder();
        builder.append("Travel Demo Team Summary\n");
        builder.append("Objective: ").append(safe(objective)).append("\n\n");
        appendSection(builder, "Product", outputs.get("product"));
        appendSection(builder, "Architect", outputs.get("architect"));
        appendSection(builder, "Backend", outputs.get("backend"));
        appendSection(builder, "Frontend", outputs.get("frontend"));
        appendSection(builder, "QA", outputs.get("qa"));
        return builder.toString().trim();
    }

    private void appendSection(StringBuilder builder, String title, String output) {
        builder.append('[').append(title).append("]\n");
        builder.append(safe(output)).append("\n\n");
    }

    private boolean allTasksCompleted(List<AgentTeamTaskState> states) {
        if (states == null || states.isEmpty()) {
            return false;
        }
        for (AgentTeamTaskState state : states) {
            if (state == null || state.getStatus() != AgentTeamTaskStatus.COMPLETED) {
                return false;
            }
        }
        return true;
    }

    private String describeTaskStates(List<AgentTeamTaskState> states) {
        if (states == null || states.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < states.size(); i++) {
            AgentTeamTaskState state = states.get(i);
            if (i > 0) {
                sb.append(", ");
            }
            if (state == null) {
                sb.append("null");
                continue;
            }
            sb.append(state.getTaskId()).append(":").append(state.getStatus());
            if (!isBlank(state.getClaimedBy())) {
                sb.append("@").append(state.getClaimedBy());
            }
            if (!isBlank(state.getError())) {
                sb.append("(error=").append(state.getError()).append(")");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    private void printResult(AgentTeamResult result) {
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
            for (int i = 0; i < result.getMessages().size(); i++) {
                System.out.println(result.getMessages().get(i));
            }
        }

        System.out.println("==== MEMBER OUTPUTS ====");
        if (result != null && result.getMemberResults() != null) {
            for (AgentTeamMemberResult memberResult : result.getMemberResults()) {
                System.out.println("[" + memberResult.getMemberId() + "] success=" + memberResult.isSuccess());
                System.out.println(safe(memberResult.getOutput()));
                System.out.println();
            }
        }

        System.out.println("==== FINAL OUTPUT ====");
        System.out.println(result == null ? "" : result.getOutput());
    }

    private <T> T callWithProviderGuard(ThrowingSupplier<T> supplier) throws Exception {
        try {
            return supplier.get();
        } catch (Exception ex) {
            skipIfProviderUnavailable(ex);
            throw ex;
        }
    }

    private void skipIfProviderUnavailable(Throwable throwable) {
        if (isProviderUnavailable(throwable)) {
            Assume.assumeTrue("Skip due provider limit/unavailable: " + extractRootMessage(throwable), false);
        }
    }

    private boolean isProviderUnavailable(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (!isBlank(message)) {
                String lower = message.toLowerCase();
                if (lower.contains("timeout")
                        || lower.contains("rate limit")
                        || lower.contains("too many requests")
                        || lower.contains("quota")
                        || lower.contains("tool arguments must be a json object")
                        || message.contains("频次")
                        || message.contains("限流")
                        || message.contains("额度")
                        || message.contains("配额")
                        || message.contains("账户已达到")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private String extractRootMessage(Throwable throwable) {
        Throwable current = throwable;
        Throwable last = throwable;
        while (current != null) {
            last = current;
            current = current.getCause();
        }
        return last == null || isBlank(last.getMessage()) ? "unknown error" : last.getMessage();
    }

    private String readValue(String envKey, String propertyKey) {
        String value = envKey == null ? null : System.getenv(envKey);
        if (isBlank(value) && propertyKey != null) {
            value = System.getProperty(propertyKey);
        }
        return value;
    }

    private String safe(String value) {
        return isBlank(value) ? "" : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
