package io.github.lnyocly.ai4j.coding;

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
import io.github.lnyocly.ai4j.agent.tool.AgentToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import io.github.lnyocly.ai4j.coding.process.SessionProcessRegistry;
import io.github.lnyocly.ai4j.coding.prompt.CodingContextPromptAssembler;
import io.github.lnyocly.ai4j.coding.workspace.WorkspaceContext;
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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class MinimaxCodingAgentTeamWorkspaceUsageTest {

    private static final String DEFAULT_MODEL = "MiniMax-M2.7";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

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
    public void test_travel_demo_team_delivers_workspace_artifacts() throws Exception {
        Path workspaceRoot = temporaryFolder.newFolder("minimax-travel-team-workspace").toPath();
        seedWorkspace(workspaceRoot);
        WorkspaceContext workspaceContext = WorkspaceContext.builder()
                .rootPath(workspaceRoot.toString())
                .description("Temporary travel demo workspace for MiniMax team delivery verification")
                .build();
        CodingAgentOptions codingOptions = CodingAgentOptions.builder()
                .autoCompactEnabled(false)
                .build();

        AgentTeam team = Agents.team()
                .planner((objective, members, options) -> AgentTeamPlan.builder()
                        .rawPlanText("travel-workspace-delivery-plan")
                        .tasks(Arrays.asList(
                                AgentTeamTask.builder()
                                        .id("product")
                                        .memberId("product")
                                        .task("Fill docs/product/prd.md with a concise travel app PRD. Replace TODO_PRODUCT only.")
                                        .context("Read README.md first. Keep the product scope MVP-level and implementation-ready.")
                                        .build(),
                                AgentTeamTask.builder()
                                        .id("architecture")
                                        .memberId("architect")
                                        .task("Fill docs/architecture/system-design.md with the architecture and file ownership plan. Replace TODO_ARCHITECTURE only.")
                                        .context("Read README.md and docs/product/prd.md first. Tell backend/frontend exactly which files they own.")
                                        .dependsOn(Arrays.asList("product"))
                                        .build(),
                                AgentTeamTask.builder()
                                        .id("backend")
                                        .memberId("backend")
                                        .task("Replace TODO_BACKEND_OPENAPI in backend/openapi.yaml and TODO_BACKEND_DATA in backend/mock-destinations.json.")
                                        .context("Read the product and architecture docs first. Produce a small but valid travel API contract and destination dataset.")
                                        .dependsOn(Arrays.asList("product", "architecture"))
                                        .build(),
                                AgentTeamTask.builder()
                                        .id("frontend")
                                        .memberId("frontend")
                                        .task("Replace TODO_FRONTEND_HTML in frontend/index.html, TODO_FRONTEND_CSS in frontend/styles.css, and TODO_FRONTEND_JS in frontend/app.js.")
                                        .context("Read the product doc, architecture doc, and backend files first. Produce a minimal static travel planner demo page that uses the mock destination data shape.")
                                        .dependsOn(Arrays.asList("product", "architecture", "backend"))
                                        .build(),
                                AgentTeamTask.builder()
                                        .id("qa")
                                        .memberId("qa")
                                        .task("Replace TODO_QA in qa/test-plan.md with a practical QA plan for the generated travel demo workspace.")
                                        .context("Read all generated docs and app files first. Cover smoke, API-contract, UI, and regression checks.")
                                        .dependsOn(Arrays.asList("product", "architecture", "backend", "frontend"))
                                        .build()
                        ))
                        .build())
                .synthesizer((objective, plan, memberResults, options) -> AgentResult.builder()
                        .outputText(renderSummary(objective, memberResults))
                        .build())
                .member(member(
                        workspaceContext,
                        codingOptions,
                        "product",
                        "Product Manager",
                        "Owns the product scope and acceptance criteria.",
                        "You are the product manager for a travel planning demo app.\n"
                                + "You must read README.md, then update only docs/product/prd.md.\n"
                                + "Replace TODO_PRODUCT with a compact PRD that covers target users, user stories, MVP scope, non-goals, and acceptance criteria.\n"
                                + "Do not modify any other file."
                ))
                .member(member(
                        workspaceContext,
                        codingOptions,
                        "architect",
                        "Architecture Analyst",
                        "Owns architecture and delivery boundaries.",
                        "You are the architecture analyst for a travel planning demo app.\n"
                                + "You must read README.md and docs/product/prd.md, then update only docs/architecture/system-design.md.\n"
                                + "Replace TODO_ARCHITECTURE with the system design, module boundaries, and exact file ownership for backend/frontend.\n"
                                + "Do not modify any other file."
                ))
                .member(member(
                        workspaceContext,
                        codingOptions,
                        "backend",
                        "Backend Engineer",
                        "Owns the mock API contract and seed data.",
                        "You are the backend engineer for a travel planning demo app.\n"
                                + "You must read README.md, docs/product/prd.md, and docs/architecture/system-design.md.\n"
                                + "Update only backend/openapi.yaml and backend/mock-destinations.json.\n"
                                + "Replace the TODO markers with a valid OpenAPI skeleton and a small JSON destination dataset for a travel planner."
                ))
                .member(member(
                        workspaceContext,
                        codingOptions,
                        "frontend",
                        "Frontend Engineer",
                        "Owns the static demo UI.",
                        "You are the frontend engineer for a travel planning demo app.\n"
                                + "You must read README.md, docs/product/prd.md, docs/architecture/system-design.md, backend/openapi.yaml, and backend/mock-destinations.json.\n"
                                + "Update only frontend/index.html, frontend/styles.css, and frontend/app.js.\n"
                                + "Replace the TODO markers with a minimal static travel planner demo that matches the mock data shape and references app.js/styles.css correctly."
                ))
                .member(member(
                        workspaceContext,
                        codingOptions,
                        "qa",
                        "QA Engineer",
                        "Owns verification strategy for the generated workspace.",
                        "You are the QA engineer for a travel planning demo app.\n"
                                + "You must read all generated docs and app files, then update only qa/test-plan.md.\n"
                                + "Replace TODO_QA with a practical test plan covering smoke, API contract checks, UI behavior, and regression scope.\n"
                                + "Do not modify any other file."
                ))
                .options(AgentTeamOptions.builder()
                        .parallelDispatch(true)
                        .maxConcurrency(3)
                        .enableMessageBus(true)
                        .includeMessageHistoryInDispatch(true)
                        .enableMemberTeamTools(true)
                        .maxRounds(16)
                        .build())
                .build();

        AgentTeamResult result = callWithProviderGuard(new ThrowingSupplier<AgentTeamResult>() {
            @Override
            public AgentTeamResult get() throws Exception {
                return team.run(AgentRequest.builder()
                        .input("Collaboratively deliver the files for the travel demo workspace without changing files outside the assigned ownership.")
                        .build());
            }
        });

        printResult(result, workspaceRoot);

        Assert.assertNotNull(result);
        Assert.assertEquals(5, result.getTaskStates().size());
        Assert.assertTrue("Team tasks did not all complete: " + describeTaskStates(result.getTaskStates()),
                allTasksCompleted(result.getTaskStates()));

        assertFileContains(workspaceRoot.resolve("docs/product/prd.md"), "MVP");
        assertFileContains(workspaceRoot.resolve("docs/architecture/system-design.md"), "backend/openapi.yaml");
        assertFileContains(workspaceRoot.resolve("backend/openapi.yaml"), "openapi:");
        assertFileContains(workspaceRoot.resolve("backend/mock-destinations.json"), "\"destinations\"");
        assertFileContains(workspaceRoot.resolve("frontend/index.html"), "app.js");
        assertFileContainsAny(workspaceRoot.resolve("frontend/styles.css"),
                ".grid",
                ".destinations-grid",
                "grid-template-columns");
        assertFileContainsIgnoreCase(workspaceRoot.resolve("frontend/app.js"), "destination");
        assertFileContainsIgnoreCase(workspaceRoot.resolve("qa/test-plan.md"), "smoke");

        assertTodoRemoved(workspaceRoot.resolve("docs/product/prd.md"), "TODO_PRODUCT");
        assertTodoRemoved(workspaceRoot.resolve("docs/architecture/system-design.md"), "TODO_ARCHITECTURE");
        assertTodoRemoved(workspaceRoot.resolve("backend/openapi.yaml"), "TODO_BACKEND_OPENAPI");
        assertTodoRemoved(workspaceRoot.resolve("backend/mock-destinations.json"), "TODO_BACKEND_DATA");
        assertTodoRemoved(workspaceRoot.resolve("frontend/index.html"), "TODO_FRONTEND_HTML");
        assertTodoRemoved(workspaceRoot.resolve("frontend/styles.css"), "TODO_FRONTEND_CSS");
        assertTodoRemoved(workspaceRoot.resolve("frontend/app.js"), "TODO_FRONTEND_JS");
        assertTodoRemoved(workspaceRoot.resolve("qa/test-plan.md"), "TODO_QA");
    }

    private AgentTeamMember member(WorkspaceContext workspaceContext,
                                   CodingAgentOptions codingOptions,
                                   String id,
                                   String name,
                                   String description,
                                   String systemPrompt) {
        return AgentTeamMember.builder()
                .id(id)
                .name(name)
                .description(description)
                .agent(buildWorkspaceCodingAgent(workspaceContext, codingOptions, systemPrompt))
                .build();
    }

    private Agent buildWorkspaceCodingAgent(WorkspaceContext workspaceContext,
                                            CodingAgentOptions codingOptions,
                                            String systemPrompt) {
        SessionProcessRegistry processRegistry = new SessionProcessRegistry(workspaceContext, codingOptions);
        AgentToolRegistry toolRegistry = CodingAgentBuilder.createBuiltInRegistry(codingOptions);
        ToolExecutor toolExecutor = CodingAgentBuilder.createBuiltInToolExecutor(
                workspaceContext,
                codingOptions,
                processRegistry
        );
        return Agents.react()
                .modelClient(modelClient)
                .model(model)
                .temperature(0.2)
                .systemPrompt(CodingContextPromptAssembler.mergeSystemPrompt(systemPrompt, workspaceContext))
                .toolRegistry(toolRegistry)
                .toolExecutor(toolExecutor)
                .options(AgentOptions.builder()
                        .maxSteps(10)
                        .stream(false)
                        .build())
                .build();
    }

    private void seedWorkspace(Path workspaceRoot) throws IOException {
        Files.createDirectories(workspaceRoot.resolve("docs/product"));
        Files.createDirectories(workspaceRoot.resolve("docs/architecture"));
        Files.createDirectories(workspaceRoot.resolve("backend"));
        Files.createDirectories(workspaceRoot.resolve("frontend"));
        Files.createDirectories(workspaceRoot.resolve("qa"));

        write(workspaceRoot.resolve("README.md"),
                "# Travel Demo Workspace\n"
                        + "\n"
                        + "Goal: deliver a small travel planning demo workspace.\n"
                        + "\n"
                        + "Required outputs:\n"
                        + "- docs/product/prd.md\n"
                        + "- docs/architecture/system-design.md\n"
                        + "- backend/openapi.yaml\n"
                        + "- backend/mock-destinations.json\n"
                        + "- frontend/index.html\n"
                        + "- frontend/styles.css\n"
                        + "- frontend/app.js\n"
                        + "- qa/test-plan.md\n"
                        + "\n"
                        + "Rules:\n"
                        + "- Only modify the files you own.\n"
                        + "- Replace TODO markers rather than inventing new paths.\n"
                        + "- Keep the demo small, concrete, and internally consistent.\n");

        write(workspaceRoot.resolve("docs/product/prd.md"), "# Travel Demo PRD\n\nTODO_PRODUCT\n");
        write(workspaceRoot.resolve("docs/architecture/system-design.md"), "# Travel Demo Architecture\n\nTODO_ARCHITECTURE\n");
        write(workspaceRoot.resolve("backend/openapi.yaml"), "TODO_BACKEND_OPENAPI\n");
        write(workspaceRoot.resolve("backend/mock-destinations.json"), "{\n  \"destinations\": TODO_BACKEND_DATA\n}\n");
        write(workspaceRoot.resolve("frontend/index.html"),
                "<!DOCTYPE html>\n<html>\n<head>\n  <meta charset=\"UTF-8\" />\n  <title>Travel Demo</title>\n  <link rel=\"stylesheet\" href=\"styles.css\" />\n</head>\n<body>\nTODO_FRONTEND_HTML\n<script src=\"app.js\"></script>\n</body>\n</html>\n");
        write(workspaceRoot.resolve("frontend/styles.css"), "TODO_FRONTEND_CSS\n");
        write(workspaceRoot.resolve("frontend/app.js"), "TODO_FRONTEND_JS\n");
        write(workspaceRoot.resolve("qa/test-plan.md"), "# QA Test Plan\n\nTODO_QA\n");
    }

    private void write(Path path, String content) throws IOException {
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
    }

    private void assertFileContains(Path path, String expected) throws IOException {
        Assert.assertTrue("Expected file to exist: " + path, Files.exists(path));
        String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        Assert.assertTrue("Expected [" + expected + "] in " + path + " but content was:\n" + content,
                content.contains(expected));
    }

    private void assertFileContainsAny(Path path, String... expectedValues) throws IOException {
        Assert.assertTrue("Expected file to exist: " + path, Files.exists(path));
        String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        if (expectedValues != null) {
            for (String expected : expectedValues) {
                if (expected != null && content.contains(expected)) {
                    return;
                }
            }
        }
        Assert.fail("Expected one of " + Arrays.toString(expectedValues) + " in " + path + " but content was:\n" + content);
    }

    private void assertFileContainsIgnoreCase(Path path, String expected) throws IOException {
        Assert.assertTrue("Expected file to exist: " + path, Files.exists(path));
        String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        Assert.assertTrue("Expected [" + expected + "] in " + path + " but content was:\n" + content,
                content.toLowerCase().contains(expected.toLowerCase()));
    }

    private void assertTodoRemoved(Path path, String todoMarker) throws IOException {
        String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        Assert.assertFalse("Expected TODO marker to be removed from " + path + ": " + todoMarker,
                content.contains(todoMarker));
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
        builder.append("Travel Workspace Delivery Summary\n");
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

    private void printResult(AgentTeamResult result, Path workspaceRoot) throws IOException {
        System.out.println("==== TASK STATES ====");
        if (result != null && result.getTaskStates() != null) {
            for (AgentTeamTaskState state : result.getTaskStates()) {
                System.out.println(state.getTaskId() + " => " + state.getStatus() + " by " + state.getClaimedBy());
            }
        }

        System.out.println("==== GENERATED FILES ====");
        Files.walk(workspaceRoot)
                .filter(Files::isRegularFile)
                .forEach(path -> System.out.println(workspaceRoot.relativize(path).toString()));

        System.out.println("==== FINAL OUTPUT ====");
        System.out.println(result == null ? "" : result.getOutput());
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
