package io.github.lnyocly.ai4j.coding;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxArtifact;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxCommand;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxException;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxResult;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSession;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSpec;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxStatus;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.coding.process.SessionProcessRegistry;
import io.github.lnyocly.ai4j.coding.shell.SandboxShellCommandExecutor;
import io.github.lnyocly.ai4j.coding.tool.BashToolExecutor;
import io.github.lnyocly.ai4j.coding.tool.CodingToolNames;
import io.github.lnyocly.ai4j.coding.workspace.WorkspaceContext;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class BashToolExecutorTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void shouldExecuteForegroundCommand() throws Exception {
        Path workspaceRoot = temporaryFolder.newFolder("workspace-bash-exec").toPath();
        WorkspaceContext workspaceContext = WorkspaceContext.builder().rootPath(workspaceRoot.toString()).build();

        BashToolExecutor executor = new BashToolExecutor(
                workspaceContext,
                CodingAgentOptions.builder().build(),
                new SessionProcessRegistry(workspaceContext, CodingAgentOptions.builder().build())
        );

        String raw = executor.execute(call(json("action", "exec", "command", "echo hello-ai4j")));
        JSONObject result = JSON.parseObject(raw);

        assertFalse(result.getBooleanValue("timedOut"));
        assertEquals(0, result.getIntValue("exitCode"));
        assertTrue(result.getString("stdout").toLowerCase().contains("hello-ai4j"));
    }

    @Test
    public void shouldRouteForegroundCommandToSandboxExecutor() throws Exception {
        Path workspaceRoot = temporaryFolder.newFolder("workspace-bash-sandbox").toPath();
        WorkspaceContext workspaceContext = WorkspaceContext.builder().rootPath(workspaceRoot.toString()).build();
        CodingAgentOptions options = CodingAgentOptions.builder().defaultCommandTimeoutMs(1234L).build();
        RecordingSandboxSession sandboxSession = new RecordingSandboxSession(
                "sandbox-1",
                "fake-sandbox",
                SandboxSpec.builder()
                        .providerId("fake-sandbox")
                        .workspaceId("/sandbox/workspace")
                        .build()
        );

        BashToolExecutor executor = new BashToolExecutor(
                workspaceContext,
                options,
                new SessionProcessRegistry(workspaceContext, options),
                new SandboxShellCommandExecutor(workspaceContext, sandboxSession, options.getDefaultCommandTimeoutMs())
        );

        String raw = executor.execute(call(json("action", "exec", "command", "echo from-sandbox")));
        JSONObject result = JSON.parseObject(raw);

        assertEquals("sandbox", result.getString("executionEnvironment"));
        assertEquals("sandbox-1", result.getString("sandboxSessionId"));
        assertEquals("fake-sandbox", result.getString("sandboxProviderId"));
        assertEquals("/sandbox/workspace", result.getString("workingDirectory"));
        assertEquals(0, result.getIntValue("exitCode"));
        assertTrue(result.getString("stdout").contains("sandbox executed: echo from-sandbox"));
        assertEquals(1, sandboxSession.commands.size());
        assertEquals(Long.valueOf(1234L), sandboxSession.commands.get(0).getTimeoutMillis());
    }

    @Test
    public void shouldManageBackgroundProcessLifecycle() throws Exception {
        Path workspaceRoot = temporaryFolder.newFolder("workspace-bash-bg").toPath();
        WorkspaceContext workspaceContext = WorkspaceContext.builder().rootPath(workspaceRoot.toString()).build();
        CodingAgentOptions options = CodingAgentOptions.builder().build();
        SessionProcessRegistry registry = new SessionProcessRegistry(workspaceContext, options);
        BashToolExecutor executor = new BashToolExecutor(workspaceContext, options, registry);

        String startRaw = executor.execute(call(json("action", "start", "command", backgroundCommand())));
        JSONObject start = JSON.parseObject(startRaw);
        String processId = start.getString("processId");
        assertEquals("RUNNING", start.getString("status"));

        JSONObject logs = awaitLogs(executor, processId, 3000L);
        assertTrue(logs.getLongValue("nextOffset") > 0L);

        String stopRaw = executor.execute(call(json("action", "stop", "processId", processId)));
        JSONObject stop = JSON.parseObject(stopRaw);
        assertTrue("STOPPED".equals(stop.getString("status")) || "EXITED".equals(stop.getString("status")));

        String listRaw = executor.execute(call(json("action", "list")));
        JSONArray list = JSON.parseArray(listRaw);
        assertEquals(1, list.size());
    }

    @Test
    public void shouldWriteToBackgroundProcess() throws Exception {
        Path workspaceRoot = temporaryFolder.newFolder("workspace-bash-write").toPath();
        WorkspaceContext workspaceContext = WorkspaceContext.builder().rootPath(workspaceRoot.toString()).build();
        CodingAgentOptions options = CodingAgentOptions.builder().build();
        SessionProcessRegistry registry = new SessionProcessRegistry(workspaceContext, options);
        BashToolExecutor executor = new BashToolExecutor(workspaceContext, options, registry);

        String startRaw = executor.execute(call(json("action", "start", "command", stdinEchoCommand())));
        String processId = JSON.parseObject(startRaw).getString("processId");

        executor.execute(call(json("action", "write", "processId", processId, "input", "hello-stdin\n")));
        JSONObject logs = awaitLogs(executor, processId, 3000L);
        assertTrue(logs.getString("content").toLowerCase().contains("hello-stdin"));

        executor.execute(call(json("action", "stop", "processId", processId)));
    }

    @Test
    public void shouldExposeRestoredProcessesAsMetadataOnly() throws Exception {
        Path workspaceRoot = temporaryFolder.newFolder("workspace-bash-restored").toPath();
        WorkspaceContext workspaceContext = WorkspaceContext.builder().rootPath(workspaceRoot.toString()).build();
        CodingAgentOptions options = CodingAgentOptions.builder().build();

        SessionProcessRegistry liveRegistry = new SessionProcessRegistry(workspaceContext, options);
        BashToolExecutor liveExecutor = new BashToolExecutor(workspaceContext, options, liveRegistry);
        String startRaw = liveExecutor.execute(call(json("action", "start", "command", backgroundCommand())));
        String processId = JSON.parseObject(startRaw).getString("processId");
        assertTrue(processId != null && !processId.isEmpty());
        JSONObject liveLogs = awaitLogs(liveExecutor, processId, 3000L);
        assertTrue(liveLogs.getString("content").toLowerCase().contains("ready"));
        liveRegistry.close();

        SessionProcessRegistry restoredRegistry = new SessionProcessRegistry(workspaceContext, options);
        restoredRegistry.restoreSnapshots(liveRegistry.exportSnapshots());
        BashToolExecutor restoredExecutor = new BashToolExecutor(workspaceContext, options, restoredRegistry);

        JSONObject status = JSON.parseObject(restoredExecutor.execute(call(json("action", "status", "processId", processId))));
        assertTrue(status.getBooleanValue("restored"));
        assertFalse(status.getBooleanValue("controlAvailable"));

        JSONObject logs = JSON.parseObject(restoredExecutor.execute(call(json("action", "logs", "processId", processId))));
        assertEquals(processId, logs.getString("processId"));
        assertTrue(logs.getString("content").toLowerCase().contains("ready"));
        assertTrue(logs.getLongValue("nextOffset") > 0L);

        try {
            restoredExecutor.execute(call(json("action", "stop", "processId", processId)));
            fail("Expected metadata-only process stop to be rejected");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("metadata only"));
        }
    }

    private AgentToolCall call(String arguments) {
        return AgentToolCall.builder()
                .name(CodingToolNames.BASH)
                .arguments(arguments)
                .build();
    }

    private String json(Object... pairs) {
        JSONObject object = new JSONObject();
        for (int i = 0; i < pairs.length; i += 2) {
            object.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return JSON.toJSONString(object);
    }

    private JSONObject awaitLogs(BashToolExecutor executor, String processId, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        JSONObject last = null;
        while (System.currentTimeMillis() < deadline) {
            String logsRaw = executor.execute(call(json("action", "logs", "processId", processId, "limit", 8000)));
            last = JSON.parseObject(logsRaw);
            String content = last.getString("content");
            if (content != null && !content.trim().isEmpty()) {
                return last;
            }
            Thread.sleep(150L);
        }
        return last == null ? new JSONObject() : last;
    }

    private String backgroundCommand() {
        if (isWindows()) {
            return "powershell -NoProfile -Command \"Write-Output ready; Start-Sleep -Seconds 10\"";
        }
        return "echo ready && sleep 10";
    }

    private String stdinEchoCommand() {
        if (isWindows()) {
            return "more";
        }
        return "cat";
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    static class RecordingSandboxSession implements SandboxSession {
        final String sessionId;
        final String providerId;
        final SandboxSpec spec;
        final List<SandboxCommand> commands = new ArrayList<SandboxCommand>();

        RecordingSandboxSession(String sessionId, String providerId, SandboxSpec spec) {
            this.sessionId = sessionId;
            this.providerId = providerId;
            this.spec = spec;
        }

        @Override
        public String getSessionId() {
            return sessionId;
        }

        @Override
        public String getProviderId() {
            return providerId;
        }

        @Override
        public SandboxSpec getSpec() {
            return spec;
        }

        @Override
        public SandboxStatus getStatus() {
            return SandboxStatus.RUNNING;
        }

        @Override
        public SandboxResult execute(SandboxCommand command) {
            commands.add(command.copy());
            return SandboxResult.builder()
                    .commandId(command.getCommandId())
                    .exitCode(0)
                    .stdout("sandbox executed: " + command.getCommand())
                    .stderr("")
                    .build();
        }

        @Override
        public boolean cancel(String commandId) {
            return false;
        }

        @Override
        public List<SandboxArtifact> listArtifacts() {
            return new ArrayList<SandboxArtifact>();
        }

        @Override
        public void close() throws SandboxException {
        }
    }
}
