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
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.cli.CliProtocol;
import io.github.lnyocly.ai4j.cli.CliUiMode;
import io.github.lnyocly.ai4j.cli.command.CodeCommandOptions;
import io.github.lnyocly.ai4j.cli.command.CodeCommandOptionsParser;
import io.github.lnyocly.ai4j.cli.factory.CodingCliAgentFactory;
import io.github.lnyocly.ai4j.cli.factory.CodingCliTuiFactory;
import io.github.lnyocly.ai4j.cli.sandbox.CliSandboxBinding;
import io.github.lnyocly.ai4j.cli.sandbox.CliSandboxSessionResolver;
import io.github.lnyocly.ai4j.cli.session.CodingSessionManager;
import io.github.lnyocly.ai4j.cli.session.DefaultCodingSessionManager;
import io.github.lnyocly.ai4j.cli.session.InMemoryCodingSessionStore;
import io.github.lnyocly.ai4j.cli.session.InMemorySessionEventStore;
import io.github.lnyocly.ai4j.coding.CodingAgent;
import io.github.lnyocly.ai4j.coding.CodingAgentOptions;
import io.github.lnyocly.ai4j.coding.CodingAgents;
import io.github.lnyocly.ai4j.coding.workspace.WorkspaceContext;
import io.github.lnyocly.ai4j.coding.session.ManagedCodingSession;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import io.github.lnyocly.ai4j.tui.TerminalIO;
import io.github.lnyocly.ai4j.tui.TuiConfig;
import io.github.lnyocly.ai4j.tui.TuiConfigManager;
import io.github.lnyocly.ai4j.tui.TuiInteractionState;
import io.github.lnyocly.ai4j.tui.TuiRenderer;
import io.github.lnyocly.ai4j.tui.TuiRuntime;
import io.github.lnyocly.ai4j.tui.TuiScreenModel;
import io.github.lnyocly.ai4j.tui.TuiTheme;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

public class CodingCliSessionRunnerSandboxTest {

    @Test
    public void sandboxAttachRoutesBashThroughResolvedLiveSessionAndDisableClosesIt() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-sandbox-attach");
        RecordingTerminal terminal = new RecordingTerminal();
        Properties properties = new Properties();
        CodeCommandOptions options = options(workspace);
        TuiInteractionState interactionState = new TuiInteractionState();
        SandboxAwareAgentFactory agentFactory = new SandboxAwareAgentFactory();
        RecordingSandboxSession liveSession = new RecordingSandboxSession("sb-live-attach", "cubesandbox");
        RecordingSandboxResolver resolver = new RecordingSandboxResolver(liveSession);
        CodingCliAgentFactory.PreparedCodingAgent prepared = agentFactory.prepare(options, terminal, interactionState);
        CodingCliSessionRunner runner = new CodingCliSessionRunner(
                prepared.getAgent(),
                prepared.getProtocol(),
                options,
                terminal,
                sessionManager(workspace),
                interactionState,
                new NoopTuiFactory(),
                null,
                agentFactory,
                resolver,
                Collections.<String, String>emptyMap(),
                properties
        );
        ManagedCodingSession managed = (ManagedCodingSession) invokePrivateMethod(runner, "openInitialSession", new Class<?>[]{});

        ManagedCodingSession rebound = (ManagedCodingSession) invokePrivateMethod(runner, "handleSandboxCommand",
                new Class<?>[]{ManagedCodingSession.class, String.class},
                managed,
                "attach cubesandbox sb-live-attach /workspace");
        ToolExecutor executor = rebound.getSession().getDelegate().getContext().getToolExecutor();
        String toolOutput = executor.execute(AgentToolCall.builder()
                .name("bash")
                .arguments("{\"action\":\"exec\",\"command\":\"echo routed\"}")
                .build());

        Assert.assertEquals("cubesandbox", resolver.binding.getProviderId());
        Assert.assertEquals("sb-live-attach", resolver.binding.getSessionId());
        Assert.assertTrue(toolOutput.contains("\"executionEnvironment\":\"sandbox\""));
        Assert.assertTrue(toolOutput.contains("\"sandboxSessionId\":\"sb-live-attach\""));
        Assert.assertEquals("echo routed", liveSession.lastCommand);
        Assert.assertTrue(terminal.out().contains("mode=attached-live"));
        Assert.assertTrue(terminal.out().contains("runtime=live-session"));

        invokePrivateMethod(runner, "handleSandboxCommand",
                new Class<?>[]{ManagedCodingSession.class, String.class},
                rebound,
                "disable");

        Assert.assertEquals(1, liveSession.closeCount);
        Assert.assertTrue(terminal.out().contains("mode=direct-host"));
    }

    @Test
    public void sandboxAttachRollbackClosesNewSessionWhenRuntimeSwitchFails() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-sandbox-rollback");
        RecordingTerminal terminal = new RecordingTerminal();
        Properties properties = new Properties();
        CodeCommandOptions options = options(workspace);
        TuiInteractionState interactionState = new TuiInteractionState();
        FailingSwitchAgentFactory agentFactory = new FailingSwitchAgentFactory();
        RecordingSandboxSession liveSession = new RecordingSandboxSession("sb-live-rollback", "cubesandbox");
        CodingCliAgentFactory.PreparedCodingAgent prepared = agentFactory.prepare(options, terminal, interactionState);
        CodingCliSessionRunner runner = new CodingCliSessionRunner(
                prepared.getAgent(),
                prepared.getProtocol(),
                options,
                terminal,
                sessionManager(workspace),
                interactionState,
                new NoopTuiFactory(),
                null,
                agentFactory,
                new RecordingSandboxResolver(liveSession),
                Collections.<String, String>emptyMap(),
                properties
        );
        ManagedCodingSession managed = (ManagedCodingSession) invokePrivateMethod(runner, "openInitialSession", new Class<?>[]{});

        try {
            invokePrivateMethod(runner, "handleSandboxCommand",
                    new Class<?>[]{ManagedCodingSession.class, String.class},
                    managed,
                    "attach cubesandbox sb-live-rollback");
            Assert.fail("Expected runtime switch failure");
        } catch (Exception ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            Assert.assertTrue(cause.getMessage().contains("forced switch failure"));
        }

        Assert.assertEquals(1, liveSession.closeCount);
        Assert.assertNull(readPrivateField(runner, "sandboxBinding"));
        Assert.assertNull(readPrivateField(runner, "sandboxSession"));
    }

    @Test
    public void sandboxAttachResolutionFailureDoesNotSwitchOrCreateBinding() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-sandbox-resolve-fail");
        RecordingTerminal terminal = new RecordingTerminal();
        Properties properties = new Properties();
        CodeCommandOptions options = options(workspace);
        TuiInteractionState interactionState = new TuiInteractionState();
        SandboxAwareAgentFactory agentFactory = new SandboxAwareAgentFactory();
        CodingCliAgentFactory.PreparedCodingAgent prepared = agentFactory.prepare(options, terminal, interactionState);
        CodingCliSessionRunner runner = new CodingCliSessionRunner(
                prepared.getAgent(),
                prepared.getProtocol(),
                options,
                terminal,
                sessionManager(workspace),
                interactionState,
                new NoopTuiFactory(),
                null,
                agentFactory,
                new FailingSandboxResolver(),
                Collections.<String, String>emptyMap(),
                properties
        );
        ManagedCodingSession managed = (ManagedCodingSession) invokePrivateMethod(runner, "openInitialSession", new Class<?>[]{});

        ManagedCodingSession returned = (ManagedCodingSession) invokePrivateMethod(runner, "handleSandboxCommand",
                new Class<?>[]{ManagedCodingSession.class, String.class},
                managed,
                "attach cubesandbox sb-live-fail");

        Assert.assertSame(managed, returned);
        Assert.assertNull(readPrivateField(runner, "sandboxBinding"));
        Assert.assertNull(readPrivateField(runner, "sandboxSession"));
        Assert.assertTrue(terminal.err().contains("Failed to resolve sandbox session"));
    }

    @Test
    public void runClosesAttachedSandboxOnExit() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-sandbox-run-close");
        ScriptedTerminal terminal = new ScriptedTerminal("/sandbox attach cubesandbox sb-run-close", "/exit");
        Properties properties = new Properties();
        CodeCommandOptions options = options(workspace);
        TuiInteractionState interactionState = new TuiInteractionState();
        SandboxAwareAgentFactory agentFactory = new SandboxAwareAgentFactory();
        RecordingSandboxSession liveSession = new RecordingSandboxSession("sb-run-close", "cubesandbox");
        CodingCliAgentFactory.PreparedCodingAgent prepared = agentFactory.prepare(options, terminal, interactionState);
        CodingCliSessionRunner runner = new CodingCliSessionRunner(
                prepared.getAgent(),
                prepared.getProtocol(),
                options,
                terminal,
                sessionManager(workspace),
                interactionState,
                new NoopTuiFactory(),
                null,
                agentFactory,
                new RecordingSandboxResolver(liveSession),
                Collections.<String, String>emptyMap(),
                properties
        );

        Assert.assertEquals(0, runner.run());

        Assert.assertEquals(1, liveSession.closeCount);
        Assert.assertTrue(terminal.out().contains("mode=attached-live"));
    }

    private CodeCommandOptions options(Path workspace) {
        return new CodeCommandOptionsParser().parse(
                java.util.Arrays.asList("--ui", "cli", "--model", "fake-model", "--workspace", workspace.toString()),
                Collections.<String, String>emptyMap(),
                new Properties(),
                workspace
        );
    }

    private CodingSessionManager sessionManager(Path workspace) {
        return new DefaultCodingSessionManager(
                new InMemoryCodingSessionStore(workspace.resolve(".ai4j").resolve("sessions")),
                new InMemorySessionEventStore()
        );
    }

    private Object readPrivateField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private Object invokePrivateMethod(Object target, String methodName, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static final class RecordingSandboxResolver implements CliSandboxSessionResolver {
        private final SandboxSession session;
        private CliSandboxBinding binding;

        private RecordingSandboxResolver(SandboxSession session) {
            this.session = session;
        }

        @Override
        public SandboxSession resolve(CliSandboxBinding binding) {
            this.binding = binding;
            return session;
        }
    }

    private static final class FailingSandboxResolver implements CliSandboxSessionResolver {
        @Override
        public SandboxSession resolve(CliSandboxBinding binding) throws SandboxException {
            throw new SandboxException("forced resolve failure");
        }
    }

    private static class SandboxAwareAgentFactory implements CodingCliAgentFactory {
        protected int prepareCount;

        @Override
        public PreparedCodingAgent prepare(CodeCommandOptions options) {
            return prepare(options, null, null, Collections.<String>emptySet(), null);
        }

        @Override
        public PreparedCodingAgent prepare(CodeCommandOptions options,
                                           TerminalIO terminal,
                                           TuiInteractionState interactionState,
                                           java.util.Collection<String> pausedMcpServers,
                                           SandboxSession sandboxSession) {
            prepareCount++;
            CodingAgent agent = CodingAgents.builder()
                    .modelClient(new EmptyModelClient())
                    .model(options.getModel())
                    .workspaceContext(WorkspaceContext.builder().rootPath(options.getWorkspace()).build())
                    .agentOptions(AgentOptions.builder().stream(options.isStream()).build())
                    .codingOptions(CodingAgentOptions.builder().build())
                    .sandbox(sandboxSession)
                    .build();
            return new PreparedCodingAgent(agent, options.getProtocol() == null ? CliProtocol.CHAT : options.getProtocol());
        }

        @Override
        public PreparedCodingAgent prepare(CodeCommandOptions options,
                                           TerminalIO terminal,
                                           TuiInteractionState interactionState) {
            return prepare(options, terminal, interactionState, Collections.<String>emptySet(), null);
        }
    }

    private static final class FailingSwitchAgentFactory extends SandboxAwareAgentFactory {
        @Override
        public PreparedCodingAgent prepare(CodeCommandOptions options) {
            return prepare(options, null, null, Collections.<String>emptySet(), null);
        }

        @Override
        public PreparedCodingAgent prepare(CodeCommandOptions options,
                                           TerminalIO terminal,
                                           TuiInteractionState interactionState,
                                           java.util.Collection<String> pausedMcpServers,
                                           SandboxSession sandboxSession) {
            if (prepareCount > 0 && sandboxSession != null) {
                throw new IllegalStateException("forced switch failure");
            }
            return super.prepare(options, terminal, interactionState, pausedMcpServers, sandboxSession);
        }
    }

    private static final class EmptyModelClient implements AgentModelClient {
        @Override
        public AgentModelResult create(AgentPrompt prompt) {
            return emptyResult();
        }

        @Override
        public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
            return emptyResult();
        }

        private AgentModelResult emptyResult() {
            return AgentModelResult.builder()
                    .outputText("")
                    .toolCalls(new ArrayList<AgentToolCall>())
                    .memoryItems(new ArrayList<Object>())
                    .build();
        }
    }

    private static class RecordingSandboxSession implements SandboxSession {
        private final String sessionId;
        private final String providerId;
        private String lastCommand;
        private int closeCount;

        private RecordingSandboxSession(String sessionId, String providerId) {
            this.sessionId = sessionId;
            this.providerId = providerId;
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
            return SandboxSpec.builder().providerId(providerId).workspaceId("/workspace").build();
        }

        @Override
        public SandboxStatus getStatus() {
            return closeCount > 0 ? SandboxStatus.CLOSED : SandboxStatus.RUNNING;
        }

        @Override
        public SandboxResult execute(SandboxCommand command) {
            lastCommand = command == null ? null : command.getCommand();
            return SandboxResult.builder()
                    .commandId(command == null ? null : command.getCommandId())
                    .stdout("from-live-sandbox")
                    .stderr("")
                    .exitCode(Integer.valueOf(0))
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
        public void close() {
            closeCount++;
        }
    }

    private static class RecordingTerminal implements TerminalIO {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();
        private final ByteArrayOutputStream err = new ByteArrayOutputStream();

        @Override
        public String readLine(String prompt) throws IOException {
            return null;
        }

        @Override
        public void print(String message) {
            write(out, message);
        }

        @Override
        public void println(String message) {
            write(out, (message == null ? "" : message) + System.lineSeparator());
        }

        @Override
        public void errorln(String message) {
            write(err, (message == null ? "" : message) + System.lineSeparator());
        }

        String out() {
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }

        String err() {
            return new String(err.toByteArray(), StandardCharsets.UTF_8);
        }

        private void write(ByteArrayOutputStream stream, String text) {
            byte[] bytes = (text == null ? "" : text).getBytes(StandardCharsets.UTF_8);
            stream.write(bytes, 0, bytes.length);
        }
    }

    private static final class ScriptedTerminal extends RecordingTerminal {
        private final java.util.Queue<String> lines = new java.util.ArrayDeque<String>();

        private ScriptedTerminal(String... lines) {
            if (lines != null) {
                Collections.addAll(this.lines, lines);
            }
        }

        @Override
        public String readLine(String prompt) throws IOException {
            return lines.isEmpty() ? null : lines.remove();
        }
    }

    private static final class NoopTuiFactory implements CodingCliTuiFactory {
        @Override
        public CodingCliTuiSupport create(CodeCommandOptions options,
                                          TerminalIO terminal,
                                          TuiConfigManager configManager) {
            return new CodingCliTuiSupport(new TuiConfig(), new TuiTheme(), new TuiRenderer() {
                @Override
                public int getMaxEvents() {
                    return 10;
                }

                @Override
                public String getThemeName() {
                    return "noop";
                }

                @Override
                public void updateTheme(TuiConfig config, TuiTheme theme) {
                }

                @Override
                public String render(TuiScreenModel screenModel) {
                    return "";
                }
            }, new TuiRuntime() {
                @Override
                public boolean supportsRawInput() {
                    return false;
                }

                @Override
                public void enter() {
                }

                @Override
                public void exit() {
                }

                @Override
                public io.github.lnyocly.ai4j.tui.TuiKeyStroke readKeyStroke(long timeoutMs) {
                    return null;
                }

                @Override
                public void render(TuiScreenModel screenModel) {
                }
            });
        }
    }
}
