package io.github.lnyocly.ai4j.cli;

import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.coding.CodingAgent;
import io.github.lnyocly.ai4j.coding.CodingAgentResult;
import io.github.lnyocly.ai4j.coding.CodingAgents;
import io.github.lnyocly.ai4j.coding.session.CodingSessionDescriptor;
import io.github.lnyocly.ai4j.coding.session.ManagedCodingSession;
import io.github.lnyocly.ai4j.coding.session.SessionEvent;
import io.github.lnyocly.ai4j.coding.session.SessionEventType;
import io.github.lnyocly.ai4j.coding.workspace.WorkspaceContext;
import io.github.lnyocly.ai4j.service.PlatformType;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DefaultCodingSessionManagerTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void shouldCreateSaveResumeAndListSessionEvents() throws Exception {
        Path workspace = temporaryFolder.newFolder("workspace").toPath();
        Path sessionDir = temporaryFolder.newFolder("sessions").toPath();

        CodingAgent agent = CodingAgents.builder()
                .modelClient(new EchoModelClient())
                .model("fake-model")
                .workspaceContext(WorkspaceContext.builder().rootPath(workspace.toString()).build())
                .build();
        DefaultCodingSessionManager manager = new DefaultCodingSessionManager(
                new FileCodingSessionStore(sessionDir),
                new FileSessionEventStore(sessionDir.resolve("events"))
        );

        CodeCommandOptions options = new CodeCommandOptions(
                false,
                CliUiMode.CLI,
                PlatformType.ZHIPU,
                CliProtocol.CHAT,
                "fake-model",
                null,
                null,
                workspace.toString(),
                "workspace",
                null,
                null,
                null,
                8,
                16,
                null,
                null,
                null,
                null,
                false,
                "session-alpha",
                null,
                sessionDir.toString(),
                null,
                true,
                false,
                128000,
                16384,
                20000,
                400,
                false
        );

        ManagedCodingSession managed = manager.create(agent, CliProtocol.CHAT, options);
        CodingAgentResult firstResult = managed.getSession().run("remember alpha");
        manager.appendEvent(managed.getSessionId(), SessionEvent.builder()
                .sessionId(managed.getSessionId())
                .type(SessionEventType.USER_MESSAGE)
                .summary("remember alpha")
                .build());
        manager.save(managed);

        List<CodingSessionDescriptor> descriptors = manager.list();
        List<SessionEvent> events = manager.listEvents(managed.getSessionId(), 10, null);

        assertEquals("Echo: remember alpha", firstResult.getOutputText());
        assertEquals(1, descriptors.size());
        assertEquals("session-alpha", descriptors.get(0).getSessionId());
        assertEquals("session-alpha", descriptors.get(0).getRootSessionId());
        assertEquals(null, descriptors.get(0).getParentSessionId());
        assertFalse(events.isEmpty());
        assertEquals(SessionEventType.SESSION_CREATED, events.get(0).getType());
        assertEquals(SessionEventType.SESSION_SAVED, events.get(events.size() - 1).getType());

        ManagedCodingSession resumed = manager.resume(agent, CliProtocol.CHAT, options, managed.getSessionId());
        CodingAgentResult resumedResult = resumed.getSession().run("what do you remember");
        List<SessionEvent> resumedEvents = manager.listEvents(resumed.getSessionId(), 20, null);

        assertTrue(resumedResult.getOutputText().contains("remember alpha"));
        assertEquals(SessionEventType.SESSION_RESUMED, resumedEvents.get(resumedEvents.size() - 1).getType());

        ManagedCodingSession forked = manager.fork(agent, CliProtocol.CHAT, options, managed.getSessionId(), "session-beta");
        manager.save(forked);
        CodingAgentResult forkedResult = forked.getSession().run("what do you remember");
        List<CodingSessionDescriptor> afterFork = manager.list();
        StoredCodingSession storedFork = manager.load("session-beta");

        assertTrue(forkedResult.getOutputText().contains("remember alpha"));
        assertNotNull(storedFork);
        assertEquals("session-alpha", storedFork.getRootSessionId());
        assertEquals("session-alpha", storedFork.getParentSessionId());
        assertEquals("session-beta", forked.getSessionId());
        assertEquals("session-alpha", forked.getRootSessionId());
        assertEquals("session-alpha", forked.getParentSessionId());
        assertEquals(2, afterFork.size());
    }

    private static final class EchoModelClient implements AgentModelClient {

        @Override
        public AgentModelResult create(AgentPrompt prompt) {
            String userInput = findLastUserText(prompt);
            if (userInput != null && userInput.toLowerCase().contains("what do you remember")) {
                return AgentModelResult.builder().outputText("history: " + findAllUserText(prompt)).build();
            }
            return AgentModelResult.builder().outputText("Echo: " + userInput).build();
        }

        @Override
        public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
            AgentModelResult result = create(prompt);
            if (listener != null) {
                listener.onDeltaText(result.getOutputText());
                listener.onComplete(result);
            }
            return result;
        }

        private String findLastUserText(AgentPrompt prompt) {
            if (prompt == null || prompt.getItems() == null) {
                return "";
            }
            List<Object> items = prompt.getItems();
            for (int i = items.size() - 1; i >= 0; i--) {
                Object item = items.get(i);
                if (!(item instanceof Map)) {
                    continue;
                }
                Map<?, ?> map = (Map<?, ?>) item;
                if (!"message".equals(map.get("type")) || !"user".equals(map.get("role"))) {
                    continue;
                }
                Object content = map.get("content");
                if (!(content instanceof List)) {
                    continue;
                }
                List<?> parts = (List<?>) content;
                for (Object part : parts) {
                    if (!(part instanceof Map)) {
                        continue;
                    }
                    Map<?, ?> partMap = (Map<?, ?>) part;
                    if ("input_text".equals(partMap.get("type"))) {
                        Object text = partMap.get("text");
                        return text == null ? "" : String.valueOf(text);
                    }
                }
            }
            return "";
        }

        private String findAllUserText(AgentPrompt prompt) {
            if (prompt == null || prompt.getItems() == null) {
                return "";
            }
            StringBuilder builder = new StringBuilder();
            for (Object item : prompt.getItems()) {
                if (!(item instanceof Map)) {
                    continue;
                }
                Map<?, ?> map = (Map<?, ?>) item;
                if (!"message".equals(map.get("type")) || !"user".equals(map.get("role"))) {
                    continue;
                }
                Object content = map.get("content");
                if (!(content instanceof List)) {
                    continue;
                }
                for (Object part : (List<?>) content) {
                    if (!(part instanceof Map)) {
                        continue;
                    }
                    Map<?, ?> partMap = (Map<?, ?>) part;
                    if (!"input_text".equals(partMap.get("type"))) {
                        continue;
                    }
                    Object text = partMap.get("text");
                    if (text == null) {
                        continue;
                    }
                    if (builder.length() > 0) {
                        builder.append(" | ");
                    }
                    builder.append(String.valueOf(text));
                }
            }
            return builder.toString();
        }
    }
}
