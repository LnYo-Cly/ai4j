package io.github.lnyocly.ai4j.coding;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        ObjectNode result = CodingJson.parseObject(raw);

        assertFalse(CodingJson.getBooleanValue(result, "timedOut"));
        assertEquals(0, CodingJson.getIntValue(result, "exitCode"));
        assertTrue(CodingJson.getString(result, "stdout").toLowerCase().contains("hello-ai4j"));
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
        ObjectNode result = CodingJson.parseObject(raw);

        assertEquals("sandbox", CodingJson.getString(result, "executionEnvironment"));
        assertEquals("sandbox-1", CodingJson.getString(result, "sandboxSessionId"));
        assertEquals("fake-sandbox", CodingJson.getString(result, "sandboxProviderId"));
        assertEquals("/sandbox/workspace", CodingJson.getString(result, "workingDirectory"));
        assertEquals(0, CodingJson.getIntValue(result, "exitCode"));
        assertTrue(CodingJson.getString(result, "stdout").contains("sandbox executed: echo from-sandbox"));
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
        ObjectNode start = CodingJson.parseObject(startRaw);
        String processId = CodingJson.getString(start, "processId");
        assertEquals("RUNNING", CodingJson.getString(start, "status"));

        ObjectNode logs = awaitLogs(executor, processId, 3000L);
        assertTrue(CodingJson.getLongValue(logs, "nextOffset") > 0L);

        String stopRaw = executor.execute(call(json("action", "stop", "processId", processId)));
        ObjectNode stop = CodingJson.parseObject(stopRaw);
        assertTrue("STOPPED".equals(CodingJson.getString(stop, "status")) || "EXITED".equals(CodingJson.getString(stop, "status")));

        String listRaw = executor.execute(call(json("action", "list")));
        ArrayNode list = CodingJson.parseArray(listRaw);
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
        String processId = CodingJson.getString(CodingJson.parseObject(startRaw), "processId");

        executor.execute(call(json("action", "write", "processId", processId, "input", "hello-stdin\n")));
        ObjectNode logs = awaitLogs(executor, processId, 3000L);
        assertTrue(CodingJson.getString(logs, "content").toLowerCase().contains("hello-stdin"));

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
        String processId = CodingJson.getString(CodingJson.parseObject(startRaw), "processId");
        assertTrue(processId != null && !processId.isEmpty());
        ObjectNode liveLogs = awaitLogs(liveExecutor, processId, 3000L);
        assertTrue(CodingJson.getString(liveLogs, "content").toLowerCase().contains("ready"));
        liveRegistry.close();

        SessionProcessRegistry restoredRegistry = new SessionProcessRegistry(workspaceContext, options);
        restoredRegistry.restoreSnapshots(liveRegistry.exportSnapshots());
        BashToolExecutor restoredExecutor = new BashToolExecutor(workspaceContext, options, restoredRegistry);

        ObjectNode status = CodingJson.parseObject(restoredExecutor.execute(call(json("action", "status", "processId", processId))));
        assertTrue(CodingJson.getBooleanValue(status, "restored"));
        assertFalse(CodingJson.getBooleanValue(status, "controlAvailable"));

        ObjectNode logs = CodingJson.parseObject(restoredExecutor.execute(call(json("action", "logs", "processId", processId))));
        assertEquals(processId, CodingJson.getString(logs, "processId"));
        assertTrue(CodingJson.getString(logs, "content").toLowerCase().contains("ready"));
        assertTrue(CodingJson.getLongValue(logs, "nextOffset") > 0L);

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
        Map<String, Object> object = new LinkedHashMap<String, Object>();
        for (int i = 0; i < pairs.length; i += 2) {
            object.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return CodingJson.toJson(object);
    }

    private ObjectNode awaitLogs(BashToolExecutor executor, String processId, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        ObjectNode last = null;
        while (System.currentTimeMillis() < deadline) {
            String logsRaw = executor.execute(call(json("action", "logs", "processId", processId, "limit", 8000)));
            last = CodingJson.parseObject(logsRaw);
            String content = CodingJson.getString(last, "content");
            if (content != null && !content.trim().isEmpty()) {
                return last;
            }
            Thread.sleep(150L);
        }
        return last == null ? CodingJson.newObject() : last;
    }

    private String backgroundCommand() {
        if (isWindows()) {
            return "echo ready & timeout /t 10 /nobreak > nul";
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
