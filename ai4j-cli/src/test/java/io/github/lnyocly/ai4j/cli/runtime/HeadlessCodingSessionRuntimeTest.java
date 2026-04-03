package io.github.lnyocly.ai4j.cli.runtime;

import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.AgentToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.StaticToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import io.github.lnyocly.ai4j.agent.util.AgentInputItem;
import io.github.lnyocly.ai4j.cli.ApprovalMode;
import io.github.lnyocly.ai4j.cli.CliProtocol;
import io.github.lnyocly.ai4j.cli.CliUiMode;
import io.github.lnyocly.ai4j.cli.command.CodeCommandOptions;
import io.github.lnyocly.ai4j.cli.session.DefaultCodingSessionManager;
import io.github.lnyocly.ai4j.cli.session.InMemoryCodingSessionStore;
import io.github.lnyocly.ai4j.cli.session.InMemorySessionEventStore;
import io.github.lnyocly.ai4j.coding.CodingAgent;
import io.github.lnyocly.ai4j.coding.CodingAgentOptions;
import io.github.lnyocly.ai4j.coding.CodingAgents;
import io.github.lnyocly.ai4j.coding.CodingSession;
import io.github.lnyocly.ai4j.coding.loop.CodingStopReason;
import io.github.lnyocly.ai4j.coding.session.ManagedCodingSession;
import io.github.lnyocly.ai4j.coding.session.SessionEvent;
import io.github.lnyocly.ai4j.coding.session.SessionEventType;
import io.github.lnyocly.ai4j.coding.workspace.WorkspaceContext;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import io.github.lnyocly.ai4j.service.PlatformType;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HeadlessCodingSessionRuntimeTest {

    private static final String STUB_TOOL = "stub_tool";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void shouldMapApprovalBlockToBlockedStopReasonAndAppendBlockedEvent() throws Exception {
        DefaultCodingSessionManager sessionManager = new DefaultCodingSessionManager(
                new InMemoryCodingSessionStore(Paths.get("(memory-sessions)")),
                new InMemorySessionEventStore()
        );
        CodeCommandOptions options = testOptions();
        HeadlessCodingSessionRuntime runtime = new HeadlessCodingSessionRuntime(options, sessionManager);
        CollectingObserver observer = new CollectingObserver();

        InspectableQueueModelClient modelClient = new InspectableQueueModelClient();
        modelClient.enqueue(toolCallResult(STUB_TOOL, "call-1"));
        modelClient.enqueue(assistantResult("Approval required before proceeding."));

        try (ManagedCodingSession session = managedSession(newAgent(modelClient, approvalRejectedToolExecutor()), options)) {
            HeadlessCodingSessionRuntime.PromptResult result = runtime.runPrompt(session, "Run the protected operation.", null, observer);
            List<SessionEvent> events = sessionManager.listEvents(session.getSessionId(), null, null);

            assertEquals("blocked", result.getStopReason());
            assertEquals(CodingStopReason.BLOCKED_BY_APPROVAL, result.getCodingStopReason());
            assertTrue(hasEvent(events, SessionEventType.BLOCKED));
            assertTrue(hasEvent(observer.sessionEvents, SessionEventType.BLOCKED));
            assertFalse(hasEvent(events, SessionEventType.AUTO_CONTINUE));
        }
    }

    @Test
    public void shouldAppendAutoContinueAndAutoStopEventsForLoopedPrompt() throws Exception {
        DefaultCodingSessionManager sessionManager = new DefaultCodingSessionManager(
                new InMemoryCodingSessionStore(Paths.get("(memory-sessions)")),
                new InMemorySessionEventStore()
        );
        CodeCommandOptions options = testOptions();
        HeadlessCodingSessionRuntime runtime = new HeadlessCodingSessionRuntime(options, sessionManager);
        CollectingObserver observer = new CollectingObserver();

        InspectableQueueModelClient modelClient = new InspectableQueueModelClient();
        modelClient.enqueue(toolCallResult(STUB_TOOL, "call-1"));
        modelClient.enqueue(assistantResult("Continuing with remaining work."));
        modelClient.enqueue(assistantResult("Completed the requested change."));

        try (ManagedCodingSession session = managedSession(newAgent(modelClient, okToolExecutor()), options)) {
            HeadlessCodingSessionRuntime.PromptResult result = runtime.runPrompt(session, "Implement the requested change.", null, observer);
            List<SessionEvent> events = sessionManager.listEvents(session.getSessionId(), null, null);

            assertEquals("end_turn", result.getStopReason());
            assertEquals(CodingStopReason.COMPLETED, result.getCodingStopReason());
            assertTrue(hasEvent(events, SessionEventType.AUTO_CONTINUE));
            assertTrue(hasEvent(events, SessionEventType.AUTO_STOP));
            assertTrue(hasEvent(observer.sessionEvents, SessionEventType.AUTO_CONTINUE));
            assertTrue(hasEvent(observer.sessionEvents, SessionEventType.AUTO_STOP));
        }
    }

    private CodeCommandOptions testOptions() {
        return new CodeCommandOptions(
                false,
                CliUiMode.CLI,
                PlatformType.OPENAI,
                CliProtocol.CHAT,
                "glm-4.5-flash",
                null,
                null,
                "(workspace)",
                "JUnit workspace",
                null,
                null,
                null,
                0,
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

    private ManagedCodingSession managedSession(CodingAgent agent, CodeCommandOptions options) throws Exception {
        Path workspaceRoot = temporaryFolder.newFolder("headless-runtime-workspace").toPath();
        CodingSession session = agent.newSession();
        long now = System.currentTimeMillis();
        return new ManagedCodingSession(
                session,
                options.getProvider().getPlatform(),
                options.getProtocol().getValue(),
                options.getModel(),
                workspaceRoot.toString(),
                options.getWorkspaceDescription(),
                options.getSystemPrompt(),
                options.getInstructions(),
                session.getSessionId(),
                null,
                now,
                now
        );
    }

    private CodingAgent newAgent(InspectableQueueModelClient modelClient, ToolExecutor toolExecutor) throws Exception {
        Path workspaceRoot = temporaryFolder.newFolder("headless-agent-workspace").toPath();
        WorkspaceContext workspaceContext = WorkspaceContext.builder()
                .rootPath(workspaceRoot.toString())
                .description("JUnit headless runtime workspace")
                .build();
        return CodingAgents.builder()
                .modelClient(modelClient)
                .model("glm-4.5-flash")
                .workspaceContext(workspaceContext)
                .codingOptions(CodingAgentOptions.builder()
                        .autoCompactEnabled(false)
                        .autoContinueEnabled(true)
                        .maxAutoFollowUps(2)
                        .maxTotalTurns(6)
                        .build())
                .toolRegistry(singleToolRegistry(STUB_TOOL))
                .toolExecutor(toolExecutor)
                .build();
    }

    private AgentToolRegistry singleToolRegistry(String toolName) {
        Tool.Function function = new Tool.Function();
        function.setName(toolName);
        function.setDescription("Stub tool for testing");
        return new StaticToolRegistry(Collections.<Object>singletonList(new Tool("function", function)));
    }

    private ToolExecutor okToolExecutor() {
        return new ToolExecutor() {
            @Override
            public String execute(AgentToolCall call) {
                return "{\"ok\":true}";
            }
        };
    }

    private ToolExecutor approvalRejectedToolExecutor() {
        return new ToolExecutor() {
            @Override
            public String execute(AgentToolCall call) {
                return "[approval-rejected] protected action";
            }
        };
    }

    private boolean hasEvent(List<SessionEvent> events, SessionEventType type) {
        if (events == null) {
            return false;
        }
        for (SessionEvent event : events) {
            if (event != null && event.getType() == type) {
                return true;
            }
        }
        return false;
    }

    private AgentModelResult toolCallResult(String toolName, String callId) {
        return AgentModelResult.builder()
                .toolCalls(Collections.singletonList(AgentToolCall.builder()
                        .name(toolName)
                        .arguments("{}")
                        .callId(callId)
                        .type("function")
                        .build()))
                .memoryItems(Collections.<Object>emptyList())
                .build();
    }

    private AgentModelResult assistantResult(String text) {
        return AgentModelResult.builder()
                .outputText(text)
                .memoryItems(Collections.<Object>singletonList(AgentInputItem.message("assistant", text)))
                .build();
    }

    private static final class CollectingObserver extends HeadlessTurnObserver.Adapter {

        private final List<SessionEvent> sessionEvents = new ArrayList<SessionEvent>();

        @Override
        public void onSessionEvent(ManagedCodingSession session, SessionEvent event) {
            if (event != null) {
                sessionEvents.add(event);
            }
        }
    }

    private static final class InspectableQueueModelClient implements AgentModelClient {

        private final Deque<AgentModelResult> results = new ArrayDeque<AgentModelResult>();
        private final List<AgentPrompt> prompts = new ArrayList<AgentPrompt>();

        private void enqueue(AgentModelResult result) {
            results.addLast(result);
        }

        @Override
        public AgentModelResult create(AgentPrompt prompt) {
            prompts.add(prompt == null ? null : prompt.toBuilder().build());
            AgentModelResult result = results.removeFirst();
            return result == null ? AgentModelResult.builder().build() : result;
        }

        @Override
        public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
            AgentModelResult result = create(prompt);
            if (listener != null && result != null && result.getOutputText() != null) {
                listener.onDeltaText(result.getOutputText());
                listener.onComplete(result);
            }
            return result;
        }
    }
}
