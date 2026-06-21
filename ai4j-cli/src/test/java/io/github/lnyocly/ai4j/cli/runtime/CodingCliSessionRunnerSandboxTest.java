package io.github.lnyocly.ai4j.cli.runtime;

import io.github.lnyocly.ai4j.agent.AgentOptions;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxArtifact;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxCommand;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxException;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxResult;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSession;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSpec;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxStatus;
import io.github.lnyocly.ai4j.cli.ApprovalMode;
import io.github.lnyocly.ai4j.cli.CliProtocol;
import io.github.lnyocly.ai4j.cli.CliUiMode;
import io.github.lnyocly.ai4j.cli.command.CodeCommandOptions;
import io.github.lnyocly.ai4j.cli.factory.CodingCliAgentFactory;
import io.github.lnyocly.ai4j.cli.sandbox.CliSandboxBinding;
import io.github.lnyocly.ai4j.cli.sandbox.CliSandboxCommand;
import io.github.lnyocly.ai4j.cli.sandbox.CliSandboxSessionResolver;
import io.github.lnyocly.ai4j.cli.session.DefaultCodingSessionManager;
import io.github.lnyocly.ai4j.cli.session.InMemoryCodingSessionStore;
import io.github.lnyocly.ai4j.cli.session.InMemorySessionEventStore;
import io.github.lnyocly.ai4j.coding.CodingAgent;
import io.github.lnyocly.ai4j.coding.CodingAgentOptions;
import io.github.lnyocly.ai4j.coding.CodingAgents;
import io.github.lnyocly.ai4j.coding.workspace.WorkspaceContext;
import io.github.lnyocly.ai4j.service.PlatformType;
import io.github.lnyocly.ai4j.tui.TerminalIO;
import io.github.lnyocly.ai4j.tui.TuiInteractionState;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class CodingCliSessionRunnerSandboxTest {

    @Test
    public void sandboxEnableStatusAndDisableRebindsRuntimeWithActiveSession() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-sandbox-runner");
        CodeCommandOptions options = options(workspace);
        RecordingAgentFactory agentFactory = new RecordingAgentFactory();
        FakeSandboxSession sandboxSession = new FakeSandboxSession("sbx-test", "ai4j-work");
        FakeSandboxResolver resolver = new FakeSandboxResolver(sandboxSession);
        RecordingTerminalIO terminal = new RecordingTerminalIO(
                "/sandbox status",
                "/sandbox enable daytona --workspace ai4j-work --delete-on-close",
                "/status",
                "/sandbox disable",
                "/exit"
        );

        CodingCliSessionRunner runner = new CodingCliSessionRunner(
                buildAgent(options, null),
                CliProtocol.CHAT,
                options,
                terminal,
                new DefaultCodingSessionManager(
                        new InMemoryCodingSessionStore(workspace.resolve(".sessions")),
                        new InMemorySessionEventStore()
                ),
                new TuiInteractionState(),
                null,
                null,
                agentFactory,
                Collections.<String, String>emptyMap(),
                new Properties(),
                resolver
        );

        int exitCode = runner.run();

        Assert.assertEquals(0, exitCode);
        Assert.assertEquals(1, resolver.openCount);
        Assert.assertEquals("daytona", resolver.lastCommand.getProviderId());
        Assert.assertEquals("ai4j-work", resolver.lastCommand.getWorkspaceId());
        Assert.assertTrue(resolver.lastCommand.isDeleteOnClose());
        Assert.assertEquals(2, agentFactory.sandboxSessions.size());
        Assert.assertSame(sandboxSession, agentFactory.sandboxSessions.get(0));
        Assert.assertNull(agentFactory.sandboxSessions.get(1));
        Assert.assertEquals(1, sandboxSession.closeCount);

        String output = terminal.output();
        Assert.assertTrue(output.contains("sandbox:\n- mode=direct-host"));
        Assert.assertTrue(output.contains("sandbox enabled:\n- mode=sandbox"));
        Assert.assertTrue(output.contains("sandbox=daytona/running/sbx-test"));
        Assert.assertTrue(output.contains("sandbox disabled:\n- mode=direct-host"));
    }

    @Test
    public void sandboxReattachSameRemoteSessionDoesNotClosePreviousHandle() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-sandbox-reattach");
        CodeCommandOptions options = options(workspace);
        RecordingAgentFactory agentFactory = new RecordingAgentFactory();
        FakeSandboxSession firstHandle = new FakeSandboxSession("sbx-same", "ai4j-work");
        FakeSandboxSession secondHandle = new FakeSandboxSession("sbx-same", "ai4j-work");
        FakeSandboxResolver resolver = new FakeSandboxResolver(firstHandle, secondHandle);
        RecordingTerminalIO terminal = new RecordingTerminalIO(
                "/sandbox enable daytona --workspace ai4j-work --delete-on-close",
                "/sandbox attach daytona sbx-same --workspace ai4j-work",
                "/exit"
        );

        CodingCliSessionRunner runner = new CodingCliSessionRunner(
                buildAgent(options, null),
                CliProtocol.CHAT,
                options,
                terminal,
                new DefaultCodingSessionManager(
                        new InMemoryCodingSessionStore(workspace.resolve(".sessions")),
                        new InMemorySessionEventStore()
                ),
                new TuiInteractionState(),
                null,
                null,
                agentFactory,
                Collections.<String, String>emptyMap(),
                new Properties(),
                resolver
        );

        int exitCode = runner.run();

        Assert.assertEquals(0, exitCode);
        Assert.assertEquals(2, resolver.openCount);
        Assert.assertEquals(0, firstHandle.closeCount);
        Assert.assertEquals(1, secondHandle.closeCount);
        Assert.assertSame(firstHandle, agentFactory.sandboxSessions.get(0));
        Assert.assertSame(secondHandle, agentFactory.sandboxSessions.get(1));
    }

    private CodeCommandOptions options(Path workspace) {
        return new CodeCommandOptions(
                false,
                CliUiMode.CLI,
                PlatformType.OPENAI,
                CliProtocol.CHAT,
                "fake-model",
                "fake-key",
                null,
                workspace.toString(),
                null,
                null,
                null,
                null,
                4,
                null,
                null,
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                null,
                ApprovalMode.AUTO,
                true,
                false,
                false,
                128000,
                16384,
                20000,
                400,
                false
        );
    }

    private static CodingAgent buildAgent(CodeCommandOptions options, SandboxSession sandboxSession) {
        io.github.lnyocly.ai4j.coding.CodingAgentBuilder builder = CodingAgents.builder()
                .modelClient(new FakeModelClient())
                .model(options.getModel())
                .workspaceContext(WorkspaceContext.builder().rootPath(options.getWorkspace()).build())
                .agentOptions(AgentOptions.builder().stream(false).build())
                .codingOptions(CodingAgentOptions.builder().build());
        if (sandboxSession != null) {
            builder.sandbox(sandboxSession);
        }
        return builder.build();
    }

    private static final class RecordingAgentFactory implements CodingCliAgentFactory {
        private final List<SandboxSession> sandboxSessions = new ArrayList<SandboxSession>();

        @Override
        public PreparedCodingAgent prepare(CodeCommandOptions options) {
            return new PreparedCodingAgent(buildAgent(options, null), CliProtocol.CHAT);
        }

        @Override
        public PreparedCodingAgent prepare(CodeCommandOptions options,
                                           TerminalIO terminal,
                                           TuiInteractionState interactionState,
                                           Collection<String> pausedMcpServers,
                                           SandboxSession sandboxSession) {
            sandboxSessions.add(sandboxSession);
            return new PreparedCodingAgent(buildAgent(options, sandboxSession), CliProtocol.CHAT);
        }
    }

    private static final class FakeSandboxResolver extends CliSandboxSessionResolver {
        private final List<FakeSandboxSession> sessions;
        private int openCount;
        private CliSandboxCommand lastCommand;

        private FakeSandboxResolver(FakeSandboxSession... sessions) {
            this.sessions = new ArrayList<FakeSandboxSession>();
            if (sessions != null) {
                Collections.addAll(this.sessions, sessions);
            }
        }

        @Override
        public OpenedSandboxSession open(CliSandboxCommand command, Map<String, String> env) {
            FakeSandboxSession session = sessions.isEmpty()
                    ? null
                    : sessions.get(Math.min(openCount, sessions.size() - 1));
            openCount++;
            lastCommand = command;
            return new OpenedSandboxSession(session, CliSandboxBinding.from(session, command));
        }
    }

    private static final class FakeSandboxSession implements SandboxSession {
        private final String sessionId;
        private final SandboxSpec spec;
        private SandboxStatus status = SandboxStatus.RUNNING;
        private int closeCount;

        private FakeSandboxSession(String sessionId, String workspaceId) {
            this.sessionId = sessionId;
            this.spec = SandboxSpec.builder()
                    .providerId("daytona")
                    .workspaceId(workspaceId)
                    .build();
        }

        @Override
        public String getSessionId() {
            return sessionId;
        }

        @Override
        public String getProviderId() {
            return "daytona";
        }

        @Override
        public SandboxSpec getSpec() {
            return spec;
        }

        @Override
        public SandboxStatus getStatus() {
            return status;
        }

        @Override
        public SandboxResult execute(SandboxCommand command) {
            return SandboxResult.builder()
                    .commandId(command == null ? null : command.getCommandId())
                    .exitCode(Integer.valueOf(0))
                    .stdout("")
                    .stderr("")
                    .build();
        }

        @Override
        public boolean cancel(String commandId) {
            return false;
        }

        @Override
        public List<SandboxArtifact> listArtifacts() {
            return Collections.emptyList();
        }

        @Override
        public void close() throws SandboxException {
            closeCount++;
            status = SandboxStatus.CLOSED;
        }
    }

    private static final class FakeModelClient implements AgentModelClient {
        @Override
        public AgentModelResult create(AgentPrompt prompt) {
            return AgentModelResult.builder()
                    .outputText("ok")
                    .build();
        }

        @Override
        public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) throws Exception {
            AgentModelResult result = create(prompt);
            if (listener != null) {
                listener.onDeltaText(result.getOutputText());
                listener.onComplete(result);
            }
            return result;
        }
    }

    private static final class RecordingTerminalIO implements TerminalIO {
        private final List<String> lines;
        private int index;
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private final ByteArrayOutputStream error = new ByteArrayOutputStream();

        private RecordingTerminalIO(String... lines) {
            this.lines = new ArrayList<String>();
            if (lines != null) {
                Collections.addAll(this.lines, lines);
            }
        }

        @Override
        public String readLine(String prompt) {
            return index >= lines.size() ? null : lines.get(index++);
        }

        @Override
        public void print(String message) {
            write(output, message);
        }

        @Override
        public void println(String message) {
            write(output, message);
            write(output, System.lineSeparator());
        }

        @Override
        public void errorln(String message) {
            write(error, message);
            write(error, System.lineSeparator());
        }

        private String output() {
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }

        @SuppressWarnings("unused")
        private String error() {
            return new String(error.toByteArray(), StandardCharsets.UTF_8);
        }

        private void write(ByteArrayOutputStream stream, String message) {
            if (stream == null || message == null) {
                return;
            }
            try {
                stream.write(message.getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
