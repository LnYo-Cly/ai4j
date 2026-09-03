package io.github.lnyocly.ai4j.harnessbench;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentContext;
import io.github.lnyocly.ai4j.agent.AgentOptions;
import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.memory.InMemoryAgentMemory;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.model.ChatModelClient;
import io.github.lnyocly.ai4j.agent.runtime.ReActRuntime;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.AgentToolExecution;
import io.github.lnyocly.ai4j.agent.tool.AgentToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.StaticToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.AsyncToolExecutor;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import io.github.lnyocly.ai4j.coding.CodingAgent;
import io.github.lnyocly.ai4j.coding.CodingAgentBuilder;
import io.github.lnyocly.ai4j.coding.CodingAgentHarness;
import io.github.lnyocly.ai4j.coding.CodingAgentOptions;
import io.github.lnyocly.ai4j.coding.CodingAgents;
import io.github.lnyocly.ai4j.coding.workspace.WorkspaceContext;
import io.github.lnyocly.ai4j.config.OpenAiConfig;
import io.github.lnyocly.ai4j.harness.CheckpointRecord;
import io.github.lnyocly.ai4j.harness.ExecutionRecord;
import io.github.lnyocly.ai4j.harness.FileHarnessConfig;
import io.github.lnyocly.ai4j.harness.FileHarnessStore;
import io.github.lnyocly.ai4j.harness.GateRecord;
import io.github.lnyocly.ai4j.harness.HarnessPersistence;
import io.github.lnyocly.ai4j.harness.HarnessRunBudget;
import io.github.lnyocly.ai4j.harness.HarnessRunRequest;
import io.github.lnyocly.ai4j.harness.HarnessRunResult;
import io.github.lnyocly.ai4j.harness.HarnessRunStatus;
import io.github.lnyocly.ai4j.harness.HarnessState;
import io.github.lnyocly.ai4j.harness.ReviewRecord;
import io.github.lnyocly.ai4j.harness.SubmissionRecord;
import io.github.lnyocly.ai4j.harness.TaskRecord;
import io.github.lnyocly.ai4j.harness.ToolInvocationRecord;
import io.github.lnyocly.ai4j.harness.WaitRecord;
import io.github.lnyocly.ai4j.harness.WakeupRecord;
import io.github.lnyocly.ai4j.platform.openai.chat.OpenAiChatService;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.IChatService;

import okhttp3.OkHttpClient;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * HarnessBench adapter bridge for the ai4j SDK.
 *
 * <p>Invoked once per benchmark round by the HarnessBench {@code generic_cli}
 * adapter. All inputs arrive through {@code HARNESSBENCH_*} environment
 * variables; benchmark configuration arrives through {@code AI4J_BENCH_*}
 * variables. The bridge never reads provider secrets from the workspace and
 * never writes into the source tree: durable state and audit artifacts live
 * under the benchmark sandbox directory.</p>
 *
 * <p>Modes (AI4J_BENCH_MODE):</p>
 * <ul>
 *   <li>{@code harness} (default): runs the round through a durable
 *       {@link CodingAgentHarness} keyed by the benchmark session id. Each
 *       round is a fresh JVM process; continuation across rounds and across
 *       processes happens through the file-backed harness persistence.</li>
 *   <li>{@code bare}: runs a plain {@link Agent} per round. Continuity is a
 *       JSONL transcript replay under the sandbox state dir, so the bare
 *       baseline intentionally lacks durable tasks, checkpoints, waits,
 *       gates, and audit state.</li>
 * </ul>
 *
 * <p>Exit codes: {@code 0} the round completed (any status the runner may
 * continue from, including WAITING/BLOCKED/IN_REVIEW), {@code 1} the round
 * failed, {@code 2} the execution ended UNKNOWN/CANCELLED (operators must
 * reconcile; the bridge refuses to blindly retry).</p>
 */
public final class HarnessBenchBridge {

    private static final String AUDIT_SCHEMA = "ai4j-harness-audit/v1";
    private static final String TRACE_SCHEMA = "ai4j-execution-trace/v1";
    private static final int OUTPUT_PREVIEW_CHARS = 2000;

    public static void main(String[] args) {
        BridgeConfig cfg = BridgeConfig.fromEnv(System.getenv());
        RunOutcome outcome;
        try {
            outcome = "bare".equalsIgnoreCase(cfg.mode) ? runBare(cfg) : runHarness(cfg);
        } catch (BenchFailure failure) {
            outcome = new RunOutcome("FAILED", failure.getMessage(), null, null, null, null);
        } catch (Exception failure) {
            outcome = new RunOutcome("FAILED", String.valueOf(failure), null, null, null, null);
        }
        AuditArtifacts artifacts = writeAudit(cfg, outcome);
        JSONObject line = new JSONObject();
        line.put("mode", cfg.mode);
        line.put("benchTaskId", cfg.benchTaskId);
        line.put("sessionId", cfg.sessionId);
        line.put("round", cfg.round);
        line.put("status", outcome.status);
        line.put("executionId", outcome.executionId);
        line.put("taskId", outcome.taskId);
        line.put("waitId", outcome.waitId);
        line.put("error", outcome.error);
        line.put("audit", artifacts.auditPath);
        line.put("trace", artifacts.tracePath);
        System.out.println(line.toJSONString());
        System.exit(exitCode(outcome.status));
    }

    static int exitCode(String status) {
        if ("UNKNOWN".equals(status) || "CANCELLED".equals(status)) {
            return 2;
        }
        return "FAILED".equals(status) ? 1 : 0;
    }

    // ------------------------------------------------------------------
    // Harness mode
    // ------------------------------------------------------------------

    private static RunOutcome runHarness(BridgeConfig cfg) throws Exception {
        Path stateDir = cfg.stateDir.resolve("harness");
        CodingAgent codingAgent = buildCodingAgent(cfg);
        CodingAgentHarness harness = CodingAgentHarness.builder()
                .codingAgent(codingAgent)
                .persistence(HarnessPersistence.file(stateDir))
                .autoResume(cfg.autoResume)
                .build();
        HarnessRunResult result;
        try {
            String prompt = readPrompt(cfg);
            String idempotencyKey = cfg.benchTaskId + "-r" + cfg.round;
            String taskId = resolveSessionTaskId(harness, cfg.sessionId);
            HarnessRunRequest request;
            if (cfg.maxSteps > 0) {
                request = HarnessRunRequest.builder()
                        .taskId(taskId)
                        .sessionId(cfg.sessionId)
                        .idempotencyKey(idempotencyKey)
                        .input(prompt)
                        .budget(HarnessRunBudget.builder().maxSteps(cfg.maxSteps)
                                .maxWallTimeMillis(cfg.wallMillis).build())
                        .build();
            } else {
                request = HarnessRunRequest.builder()
                        .taskId(taskId)
                        .sessionId(cfg.sessionId)
                        .idempotencyKey(idempotencyKey)
                        .input(prompt)
                        .build();
            }
            result = harness.run(request);
        } finally {
            harness.close();
        }
        return new RunOutcome(String.valueOf(result.getStatus()),
                result.getError(),
                result.getExecution() == null ? null : result.getExecution().getExecutionId(),
                result.getTask() == null ? null : result.getTask().getTaskId(),
                result.getWaitId(),
                result.getOutputText());
    }

    /**
     * Multi-round benchmark prompts arrive in fresh processes under one bench
     * session. If an earlier round already bound this session to a harness
     * task (typically runtime-created), later rounds continue THAT task
     * instead of drifting into session-only runs.
     */
    private static String resolveSessionTaskId(CodingAgentHarness harness, String sessionId) {
        ExecutionRecord best = null;
        for (ExecutionRecord e : harness.getGateway().listExecutions()) {
            if (!sessionId.equals(e.getSessionId()) || e.getTaskId() == null) {
                continue;
            }
            if (best == null || e.getAttempt() >= best.getAttempt()) {
                best = e;
            }
        }
        if (best == null) {
            return null;
        }
        TaskRecord task = harness.getGateway().getTask(best.getTaskId());
        if (task == null) {
            return null;
        }
        switch (task.getStatus()) {
            case DONE:
            case CANCELLED:
                return null; // terminal: let the round start fresh
            case IN_REVIEW:
                // Governance state: the kernel refuses new executions here.
                // Continue the round session-scoped instead of bypassing the
                // review; the IN_REVIEW task stays visible in the audit.
                return null;
            default:
                return task.getTaskId();
        }
    }

    private static CodingAgent buildCodingAgent(BridgeConfig cfg) {
        ToolSupport tools = ToolSupport.fromConfig(cfg);
        WorkspaceContext.WorkspaceContextBuilder workspace = WorkspaceContext.builder()
                .rootPath(cfg.workspace.toString());
        // Caller write policy: protected path globs from the environment are
        // appended to the workspace excluded paths so write/patch tools reject
        // them with guidance (e.g. the task contract's "do not modify inputs").
        String protectedPaths = System.getenv("AI4J_BENCH_PROTECTED_PATHS");
        if (protectedPaths != null && !protectedPaths.trim().isEmpty()) {
            List<String> patterns = new ArrayList<>(WorkspaceContext.defaultExcludedPaths());
            for (String pattern : protectedPaths.split(",")) {
                if (!pattern.trim().isEmpty()) {
                    patterns.add(pattern.trim());
                }
            }
            workspace.excludedPaths(patterns);
        }
        CodingAgentBuilder builder = CodingAgents.builder()
                .modelClient(modelClient(cfg))
                .model(cfg.model)
                .workspaceContext(workspace.build());
        if (cfg.reasoning != null) {
            builder.reasoning(cfg.reasoning);
        }
        if (tools != null) {
            // Scripted scenario: the scenario owns the single benchmark tool.
            builder.codingOptions(CodingAgentOptions.builder()
                    .includeBuiltInTools(false)
                    .build());
            builder.toolRegistry(tools.registry).toolExecutor(tools.executor);
        }
        // Live runs keep the coding agent's built-in tools (read/write/bash
        // and friends) so the model can actually operate on the workspace.
        return builder.build();
    }

    // ------------------------------------------------------------------
    // Bare agent mode
    // ------------------------------------------------------------------

    private static RunOutcome runBare(BridgeConfig cfg) throws Exception {
        ToolSupport tools = ToolSupport.fromConfig(cfg);
        AgentContext context;
        if (tools != null) {
            context = AgentContext.builder()
                    .modelClient(modelClient(cfg))
                    .memory(new InMemoryAgentMemory())
                    .options(AgentOptions.builder().maxSteps(cfg.maxSteps > 0 ? cfg.maxSteps : 24).build())
                    .model(cfg.model)
                    .toolRegistry(tools.registry)
                    .toolExecutor(tools.executor)
                    .build();
        } else {
            context = AgentContext.builder()
                    .modelClient(modelClient(cfg))
                    .memory(new InMemoryAgentMemory())
                    .options(AgentOptions.builder().maxSteps(cfg.maxSteps > 0 ? cfg.maxSteps : 24).build())
                    .model(cfg.model)
                    .build();
        }
        Agent agent = new Agent(new ReActRuntime(), context, InMemoryAgentMemory::new);

        Path transcriptFile = cfg.stateDir.resolve("bare").resolve(cfg.sessionId + ".jsonl");
        String prompt = readPrompt(cfg);
        String history = readTranscript(transcriptFile);
        String input = history.isEmpty()
                ? prompt
                : "Previous rounds of this task (oldest first):\n" + history
                        + "\n\nCurrent task input:\n" + prompt;
        AgentResult result = agent.run(AgentRequest.builder().input(input).build());

        JSONObject entry = new JSONObject();
        entry.put("round", cfg.round);
        entry.put("prompt", prompt);
        entry.put("output", result.getOutputText());
        Files.createDirectories(transcriptFile.getParent());
        Files.write(transcriptFile, Collections.singletonList(entry.toJSONString()),
                StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);

        return new RunOutcome("BARE_" + result.getExecutionStatus(), null, null, null,
                result.getWaitId(), result.getOutputText());
    }

    private static String readTranscript(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (line.trim().isEmpty()) {
                continue;
            }
            JSONObject row = JSON.parseObject(line);
            sb.append("round ").append(row.getIntValue("round")).append(" prompt: ")
                    .append(row.getString("prompt")).append('\n');
            sb.append("round ").append(row.getIntValue("round")).append(" output: ")
                    .append(row.getString("output")).append('\n');
        }
        return sb.toString().trim();
    }

    // ------------------------------------------------------------------
    // Model client selection
    // ------------------------------------------------------------------

    private static AgentModelClient modelClient(BridgeConfig cfg) {
        if (cfg.scriptPath != null) {
            return new ScriptedModelClient(cfg.scriptPath);
        }
        Configuration configuration = new Configuration();
        OpenAiConfig openAi = new OpenAiConfig();
        openAi.setApiKey(require(cfg.apiKey, "AI4J_BENCH_API_KEY"));
        if (cfg.baseUrl != null && !cfg.baseUrl.trim().isEmpty()) {
            openAi.setApiHost(cfg.baseUrl);
        }
        configuration.setOpenAiConfig(openAi);
        configuration.setOkHttpClient(new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(600, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .build());
        IChatService chatService = new OpenAiChatService(configuration);
        // Match the retry opportunity the stock agent CLIs have internally:
        // gateways flake with transient 5xx; retry those, not real failures.
        return new RetryingModelClient(new ChatModelClient(chatService), 3);
    }

    /** Retries transient provider failures with backoff; surfaces the last error. */
    private static final class RetryingModelClient implements AgentModelClient {
        private final AgentModelClient delegate;
        private final int maxAttempts;

        RetryingModelClient(AgentModelClient delegate, int maxAttempts) {
            this.delegate = delegate;
            this.maxAttempts = maxAttempts;
        }

        private static boolean isTransient(Throwable t) {
            String m = String.valueOf(t);
            return m.contains("temporarily unavailable")
                    || m.contains("Upstream service")
                    || m.contains("502") || m.contains("503") || m.contains("504")
                    || m.contains("timeout") || m.contains("Timeout");
        }

        @Override
        public AgentModelResult create(AgentPrompt prompt) throws Exception {
            Exception last = null;
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                try {
                    return delegate.create(prompt);
                } catch (Exception e) {
                    if (!isTransient(e) || attempt == maxAttempts) {
                        throw e;
                    }
                    last = e;
                    Thread.sleep(attempt * 5_000L);
                }
            }
            throw last;
        }

        @Override
        public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) throws Exception {
            return create(prompt);
        }
    }

    private static String require(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new BenchFailure("missing required environment variable " + name);
        }
        return value.trim();
    }

    // ------------------------------------------------------------------
    // Benchmark tool plumbing (scripted scenarios may declare one tool)
    // ------------------------------------------------------------------

    private static final class ToolSupport {
        final AgentToolRegistry registry;
        final ToolExecutor executor;

        private ToolSupport(AgentToolRegistry registry, ToolExecutor executor) {
            this.registry = registry;
            this.executor = executor;
        }

        static ToolSupport fromConfig(BridgeConfig cfg) {
            if (cfg.scriptPath == null) {
                return null; // live runs use the model's own function-calling surface
            }
            JSONObject scenario = ScriptedModelClient.loadScenario(cfg.scriptPath);
            JSONObject tool = scenario.getJSONObject("tool");
            if (tool == null) {
                return null;
            }
            final String name = tool.getString("name");
            Tool.Function function = new Tool.Function();
            function.setName(name);
            function.setDescription(tool.getString("description") == null
                    ? "HarnessBench scenario tool" : tool.getString("description"));
            AgentToolRegistry registry = new StaticToolRegistry(
                    Collections.<Object>singletonList(new Tool("function", function)));
            final String output = tool.getString("output");
            ToolExecutor executor;
            if (tool.getBooleanValue("async")) {
                executor = new AsyncToolExecutor() {
                    @Override
                    public AgentToolExecution start(AgentToolCall call) {
                        // Never completes: the round ends WAITING with a durable wait record.
                        return AgentToolExecution.pending("bench-op-" + name, null,
                                "async scenario operation pending");
                    }
                };
            } else {
                executor = new ToolExecutor() {
                    @Override
                    public String execute(AgentToolCall call) {
                        return output == null ? "bench-tool-ok" : output;
                    }
                };
            }
            return new ToolSupport(registry, executor);
        }
    }

    /** Marker step that simulates a provider-level failure inside the model call. */
    private static final class FailStep {
        final String text;

        FailStep(String text) {
            this.text = text;
        }
    }

    /** Deterministic no-network model client driven by a scenario JSON file. */
    static final class ScriptedModelClient implements AgentModelClient {

        private final Iterator<Object> steps;

        ScriptedModelClient(Path scriptPath) {
            this.steps = loadSteps(scriptPath);
        }

        static JSONObject loadScenario(Path scriptPath) {
            try {
                return JSON.parseObject(new String(Files.readAllBytes(scriptPath), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new BenchFailure("cannot read scenario script " + scriptPath + ": " + e.getMessage());
            }
        }

        private static Iterator<Object> loadSteps(Path scriptPath) {
            JSONObject scenario = loadScenario(scriptPath);
            JSONArray steps = scenario.getJSONArray("steps");
            List<Object> results = new ArrayList<Object>();
            if (steps != null) {
                for (int i = 0; i < steps.size(); i++) {
                    JSONObject step = steps.getJSONObject(i);
                    String type = step.getString("type");
                    if ("tool".equals(type)) {
                        AgentToolCall call = AgentToolCall.builder()
                                .callId(step.getString("callId") == null ? "call-" + i : step.getString("callId"))
                                .name(step.getString("name"))
                                .arguments(step.getString("arguments") == null ? "{}" : step.getString("arguments"))
                                .type("function")
                                .build();
                        results.add(AgentModelResult.builder()
                                .toolCalls(Collections.singletonList(call))
                                .memoryItems(Collections.<Object>emptyList())
                                .build());
                    } else if ("fail".equals(type)) {
                        results.add(new FailStep(step.getString("text") == null
                                ? "scripted provider failure" : step.getString("text")));
                    } else {
                        results.add(AgentModelResult.builder()
                                .outputText(step.getString("text") == null ? "done" : step.getString("text"))
                                .toolCalls(Collections.<AgentToolCall>emptyList())
                                .memoryItems(Collections.<Object>emptyList())
                                .build());
                    }
                }
            }
            return results.iterator();
        }

        @Override
        public AgentModelResult create(AgentPrompt prompt) {
            if (!steps.hasNext()) {
                return AgentModelResult.builder()
                        .outputText("script exhausted")
                        .toolCalls(Collections.<AgentToolCall>emptyList())
                        .memoryItems(Collections.<Object>emptyList())
                        .build();
            }
            Object next = steps.next();
            if (next instanceof FailStep) {
                throw new IllegalStateException(((FailStep) next).text);
            }
            return (AgentModelResult) next;
        }

        @Override
        public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
            return create(prompt);
        }
    }

    // ------------------------------------------------------------------
    // Audit export
    // ------------------------------------------------------------------

    private static final class RunOutcome {
        final String status;
        final String error;
        final String executionId;
        final String taskId;
        final String waitId;
        final String outputText;

        RunOutcome(String status, String error, String executionId, String taskId,
                   String waitId, String outputText) {
            this.status = status;
            this.error = error;
            this.executionId = executionId;
            this.taskId = taskId;
            this.waitId = waitId;
            this.outputText = outputText;
        }
    }

    private static final class AuditArtifacts {
        final String auditPath;
        final String tracePath;

        AuditArtifacts(String auditPath, String tracePath) {
            this.auditPath = auditPath;
            this.tracePath = tracePath;
        }
    }

    private static AuditArtifacts writeAudit(BridgeConfig cfg, RunOutcome outcome) {
        Path auditDir = cfg.auditDir;
        try {
            Files.createDirectories(auditDir);
            JSONObject audit = new JSONObject();
            audit.put("schema", AUDIT_SCHEMA);
            audit.put("benchTaskId", cfg.benchTaskId);
            audit.put("sessionId", cfg.sessionId);
            audit.put("round", cfg.round);
            audit.put("mode", cfg.mode);
            audit.put("modelId", cfg.modelId);
            audit.put("workspace", cfg.workspace.toString());
            audit.put("status", outcome.status);
            audit.put("error", outcome.error);
            audit.put("outputPreview", preview(outcome.outputText));
            audit.put("state", projectState(cfg));
            Path auditFile = auditDir.resolve("harness_audit.json");
            Files.write(auditFile, JSON.toJSONString(audit,
                    com.alibaba.fastjson2.JSONWriter.Feature.PrettyFormat).getBytes(StandardCharsets.UTF_8));

            JSONObject trace = new JSONObject();
            trace.put("schema", TRACE_SCHEMA);
            trace.put("benchTaskId", cfg.benchTaskId);
            trace.put("sessionId", cfg.sessionId);
            trace.put("round", cfg.round);
            trace.put("mode", cfg.mode);
            trace.put("status", outcome.status);
            trace.put("executionId", outcome.executionId);
            trace.put("taskId", outcome.taskId);
            trace.put("waitId", outcome.waitId);
            trace.put("outputPreview", preview(outcome.outputText));
            trace.put("error", outcome.error);
            Path traceFile = auditDir.resolve("execution_trace.json");
            Files.write(traceFile, JSON.toJSONString(trace,
                    com.alibaba.fastjson2.JSONWriter.Feature.PrettyFormat).getBytes(StandardCharsets.UTF_8));
            return new AuditArtifacts(auditFile.toString(), traceFile.toString());
        } catch (IOException e) {
            throw new BenchFailure("cannot write audit artifacts under " + auditDir + ": " + e.getMessage(), e);
        }
    }

    /** Reads the durable store back from disk; never from the live object graph. */
    private static JSONObject projectState(BridgeConfig cfg) {
        JSONObject state = new JSONObject();
        if (!"harness".equalsIgnoreCase(cfg.mode)) {
            state.put("note", "bare mode has no durable harness state");
            return state;
        }
        Path stateDir = cfg.stateDir.resolve("harness");
        if (!Files.isDirectory(stateDir)) {
            state.put("note", "harness state dir not created");
            return state;
        }
        FileHarnessStore store = new FileHarnessStore(new FileHarnessConfig(stateDir, "default"));
        try {
            HarnessState snapshot = store.load();
            state.put("harnessId", snapshot.getHarnessId());
            state.put("version", snapshot.getVersion());
            state.put("updatedAtEpochMs", snapshot.getUpdatedAtEpochMs());
            state.put("tasks", projectCollection(snapshot.getTasks(), new RecordProjector<TaskRecord>() {
                @Override
                public JSONObject apply(TaskRecord r) {
                    JSONObject o = new JSONObject();
                    o.put("taskId", r.getTaskId());
                    o.put("title", r.getTitle());
                    o.put("status", String.valueOf(r.getStatus()));
                    o.put("createdBy", r.getCreatedBy());
                    o.put("createdAtEpochMs", r.getCreatedAtEpochMs());
                    return o;
                }
            }));
            state.put("executions", projectCollection(snapshot.getExecutions(), new RecordProjector<ExecutionRecord>() {
                @Override
                public JSONObject apply(ExecutionRecord r) {
                    JSONObject o = new JSONObject();
                    o.put("executionId", r.getExecutionId());
                    o.put("taskId", r.getTaskId());
                    o.put("sessionId", r.getSessionId());
                    o.put("status", String.valueOf(r.getStatus()));
                    o.put("attempt", r.getAttempt());
                    o.put("checkpointId", r.getCheckpointId());
                    return o;
                }
            }));
            state.put("checkpoints", projectCollection(snapshot.getCheckpoints(), new RecordProjector<CheckpointRecord>() {
                @Override
                public JSONObject apply(CheckpointRecord r) {
                    JSONObject o = new JSONObject();
                    o.put("checkpointId", r.getCheckpointId());
                    o.put("executionId", r.getExecutionId());
                    o.put("createdAtEpochMs", r.getCreatedAtEpochMs());
                    return o;
                }
            }));
            state.put("waits", projectCollection(snapshot.getWaits(), new RecordProjector<WaitRecord>() {
                @Override
                public JSONObject apply(WaitRecord r) {
                    JSONObject o = new JSONObject();
                    o.put("waitId", r.getWaitId());
                    o.put("executionId", r.getExecutionId());
                    o.put("type", String.valueOf(r.getType()));
                    o.put("status", String.valueOf(r.getStatus()));
                    o.put("operationId", r.getOperationId());
                    return o;
                }
            }));
            state.put("wakeups", projectCollection(snapshot.getWakeups(), new RecordProjector<WakeupRecord>() {
                @Override
                public JSONObject apply(WakeupRecord r) {
                    JSONObject o = new JSONObject();
                    o.put("wakeupId", r.getWakeupId());
                    o.put("waitId", r.getWaitId());
                    o.put("type", String.valueOf(r.getType()));
                    o.put("deliveredAtEpochMs", r.getDeliveredAtEpochMs());
                    return o;
                }
            }));
            state.put("gates", projectCollection(snapshot.getGates(), new RecordProjector<GateRecord>() {
                @Override
                public JSONObject apply(GateRecord r) {
                    JSONObject o = new JSONObject();
                    o.put("gateId", r.getGateId());
                    o.put("taskId", r.getTaskId());
                    o.put("name", r.getName());
                    o.put("status", String.valueOf(r.getStatus()));
                    return o;
                }
            }));
            state.put("submissions", projectCollection(snapshot.getSubmissions(), new RecordProjector<SubmissionRecord>() {
                @Override
                public JSONObject apply(SubmissionRecord r) {
                    JSONObject o = new JSONObject();
                    o.put("submissionId", r.getSubmissionId());
                    o.put("taskId", r.getTaskId());
                    o.put("executionId", r.getExecutionId());
                    o.put("createdAtEpochMs", r.getCreatedAtEpochMs());
                    return o;
                }
            }));
            state.put("reviews", projectCollection(snapshot.getReviews(), new RecordProjector<ReviewRecord>() {
                @Override
                public JSONObject apply(ReviewRecord r) {
                    JSONObject o = new JSONObject();
                    o.put("reviewId", r.getReviewId());
                    o.put("taskId", r.getTaskId());
                    o.put("verdict", String.valueOf(r.getVerdict()));
                    return o;
                }
            }));
            state.put("toolInvocations", projectCollection(snapshot.getToolInvocations(), new RecordProjector<ToolInvocationRecord>() {
                @Override
                public JSONObject apply(ToolInvocationRecord r) {
                    JSONObject o = new JSONObject();
                    o.put("invocationId", r.getInvocationId());
                    o.put("toolName", r.getToolName());
                    o.put("callId", r.getCallId());
                    o.put("status", String.valueOf(r.getStatus()));
                    o.put("operationId", r.getOperationId());
                    o.put("waitId", r.getWaitId());
                    return o;
                }
            }));
            state.put("idempotencyKeyCount",
                    snapshot.getIdempotency() == null ? 0 : snapshot.getIdempotency().size());
            state.put("sessionCount",
                    snapshot.getSessions() == null ? 0 : snapshot.getSessions().size());
            state.put("eventCount",
                    snapshot.getEvents() == null ? 0 : snapshot.getEvents().size());
        } finally {
            store.close();
        }
        return state;
    }

    private interface RecordProjector<T> {
        JSONObject apply(T record);
    }

    private static <T> JSONArray projectCollection(Map<String, T> records, RecordProjector<T> projector) {
        JSONArray array = new JSONArray();
        if (records != null) {
            for (T record : records.values()) {
                array.add(projector.apply(record));
            }
        }
        return array;
    }

    private static String preview(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= OUTPUT_PREVIEW_CHARS ? text : text.substring(0, OUTPUT_PREVIEW_CHARS) + "...";
    }

    // ------------------------------------------------------------------
    // Configuration and helpers
    // ------------------------------------------------------------------

    private static final class BridgeConfig {
        final String mode;
        final boolean autoResume;
        final int maxSteps;
        final Path workspace;
        final Path stateDir;
        final Path auditDir;
        final String sessionId;
        final String benchTaskId;
        final String modelId;
        final int round;
        final Path promptFile;
        final String apiKey;
        final String baseUrl;
        final String model;
        final String reasoning;
        final long wallMillis;
        final Path scriptPath;

        BridgeConfig(String mode, boolean autoResume, int maxSteps, Path workspace, Path stateDir,
                     Path auditDir, String sessionId, String benchTaskId, String modelId, int round,
                     Path promptFile, String apiKey, String baseUrl, String model, String reasoning,
                     long wallMillis, Path scriptPath) {
            this.mode = mode;
            this.autoResume = autoResume;
            this.maxSteps = maxSteps;
            this.workspace = workspace;
            this.stateDir = stateDir;
            this.auditDir = auditDir;
            this.sessionId = sessionId;
            this.benchTaskId = benchTaskId;
            this.modelId = modelId;
            this.round = round;
            this.promptFile = promptFile;
            this.apiKey = apiKey;
            this.baseUrl = baseUrl;
            this.model = model;
            this.reasoning = reasoning;
            this.wallMillis = wallMillis;
            this.scriptPath = scriptPath;
        }

        static BridgeConfig fromEnv(Map<String, String> env) {
            String sandbox = env.get("HARNESSBENCH_SANDBOX");
            String workspace = env.get("HARNESSBENCH_WORKSPACE");
            String sessionId = env.get("HARNESSBENCH_SESSION_ID");
            String promptFile = env.get("HARNESSBENCH_PROMPT_FILE");
            if (sandbox == null || workspace == null || sessionId == null || promptFile == null) {
                throw new BenchFailure("HARNESSBENCH_SANDBOX, HARNESSBENCH_WORKSPACE, "
                        + "HARNESSBENCH_SESSION_ID and HARNESSBENCH_PROMPT_FILE are required");
            }
            Path sandboxDir = Paths.get(sandbox);
            Path stateDir = Paths.get(envVal(env, "AI4J_BENCH_STATE_DIR",
                    sandboxDir.resolve("ai4j-state").toString()));
            return new BridgeConfig(
                    envVal(env, "AI4J_BENCH_MODE", "harness"),
                    Boolean.parseBoolean(envVal(env, "AI4J_BENCH_AUTO_RESUME", "true")),
                    Integer.parseInt(envVal(env, "AI4J_BENCH_MAX_STEPS", "32")),
                    Paths.get(workspace),
                    stateDir,
                    Paths.get(envVal(env, "AI4J_BENCH_AUDIT_DIR",
                            sandboxDir.resolve("ai4j-audit").toString())),
                    sessionId,
                    env.get("HARNESSBENCH_TASK_ID"),
                    envVal(env, "HARNESSBENCH_MODEL_ID", "unknown"),
                    parseRound(envVal(env, "AI4J_BENCH_ROUND", roundFromPromptFile(promptFile))),
                    Paths.get(promptFile),
                    env.get("AI4J_BENCH_API_KEY"),
                    env.get("AI4J_BENCH_BASE_URL"),
                    envVal(env, "AI4J_BENCH_MODEL", "gpt-4o-mini"),
                    env.get("AI4J_BENCH_REASONING"),
                    Long.parseLong(envVal(env, "AI4J_BENCH_WALL_SECONDS", "900")) * 1000L,
                    env.get("AI4J_BENCH_SCRIPT") == null ? null : Paths.get(env.get("AI4J_BENCH_SCRIPT")));
        }
    }

    /** Derives the round number from the runner's prompt-round{N}.txt naming. */
    private static String roundFromPromptFile(String promptFile) {
        String name = new File(promptFile).getName();
        if (name.startsWith("prompt-round")) {
            String rest = name.substring("prompt-round".length());
            int dot = rest.indexOf('.');
            if (dot > 0) {
                return rest.substring(0, dot);
            }
        }
        return "1";
    }

    private static int parseRound(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private static String envVal(Map<String, String> env, String key, String fallback) {
        String value = env.get(key);
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static String readPrompt(BridgeConfig cfg) throws IOException {
        if (!Files.isRegularFile(cfg.promptFile)) {
            throw new BenchFailure("prompt file not found: " + cfg.promptFile);
        }
        return new String(Files.readAllBytes(cfg.promptFile), StandardCharsets.UTF_8);
    }

    static final class BenchFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;

        BenchFailure(String message) {
            super(message);
        }

        BenchFailure(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
