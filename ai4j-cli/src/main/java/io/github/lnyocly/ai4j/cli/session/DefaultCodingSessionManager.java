package io.github.lnyocly.ai4j.cli.session;

import io.github.lnyocly.ai4j.cli.CliProtocol;
import io.github.lnyocly.ai4j.cli.command.CodeCommandOptions;

import io.github.lnyocly.ai4j.coding.CodingSession;
import io.github.lnyocly.ai4j.coding.CodingSessionSnapshot;
import io.github.lnyocly.ai4j.coding.CodingSessionState;
import io.github.lnyocly.ai4j.coding.session.CodingSessionDescriptor;
import io.github.lnyocly.ai4j.coding.session.ManagedCodingSession;
import io.github.lnyocly.ai4j.coding.session.SessionEvent;
import io.github.lnyocly.ai4j.coding.session.SessionEventType;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class DefaultCodingSessionManager implements CodingSessionManager {

    private final CodingSessionStore sessionStore;
    private final SessionEventStore eventStore;

    public DefaultCodingSessionManager(CodingSessionStore sessionStore, SessionEventStore eventStore) {
        this.sessionStore = sessionStore;
        this.eventStore = eventStore;
    }

    @Override
    public ManagedCodingSession create(io.github.lnyocly.ai4j.coding.CodingAgent agent,
                                       CliProtocol protocol,
                                       CodeCommandOptions options) throws Exception {
        String sessionId = isBlank(options.getSessionId()) ? null : options.getSessionId();
        CodingSession session = sessionId == null ? agent.newSession() : agent.newSession(sessionId, null);
        long now = System.currentTimeMillis();
        ManagedCodingSession managed = new ManagedCodingSession(
                session,
                options.getProvider().getPlatform(),
                protocol.getValue(),
                options.getModel(),
                options.getWorkspace(),
                options.getWorkspaceDescription(),
                options.getSystemPrompt(),
                options.getInstructions(),
                session.getSessionId(),
                null,
                now,
                now
        );
        appendEvent(managed.getSessionId(), baseEvent(managed.getSessionId(), SessionEventType.SESSION_CREATED, "session created", null));
        return managed;
    }

    @Override
    public ManagedCodingSession resume(io.github.lnyocly.ai4j.coding.CodingAgent agent,
                                       CliProtocol protocol,
                                       CodeCommandOptions options,
                                       String sessionId) throws Exception {
        StoredCodingSession storedSession = load(sessionId);
        if (storedSession == null) {
            throw new IllegalArgumentException("Saved session not found: " + sessionId);
        }
        assertWorkspaceMatches(storedSession, options.getWorkspace());
        CodingSessionState state = storedSession.getState();
        CodingSession session = agent.newSession(storedSession.getSessionId(), state);
        ManagedCodingSession managed = new ManagedCodingSession(
                session,
                storedSession.getProvider(),
                storedSession.getProtocol(),
                storedSession.getModel(),
                storedSession.getWorkspace(),
                storedSession.getWorkspaceDescription(),
                storedSession.getSystemPrompt(),
                storedSession.getInstructions(),
                normalizeRootSessionId(storedSession),
                storedSession.getParentSessionId(),
                storedSession.getCreatedAtEpochMs(),
                storedSession.getUpdatedAtEpochMs()
        );
        appendEvent(managed.getSessionId(), baseEvent(managed.getSessionId(), SessionEventType.SESSION_RESUMED, "session resumed", null));
        return managed;
    }

    @Override
    public ManagedCodingSession fork(io.github.lnyocly.ai4j.coding.CodingAgent agent,
                                     CliProtocol protocol,
                                     CodeCommandOptions options,
                                     String sourceSessionId,
                                     String targetSessionId) throws Exception {
        StoredCodingSession source = load(sourceSessionId);
        if (source == null) {
            throw new IllegalArgumentException("Saved session not found: " + sourceSessionId);
        }
        assertWorkspaceMatches(source, options.getWorkspace());
        String forkSessionId = isBlank(targetSessionId)
                ? source.getSessionId() + "-fork-" + UUID.randomUUID().toString().substring(0, 8).toLowerCase(Locale.ROOT)
                : targetSessionId;
        CodingSession session = agent.newSession(forkSessionId, source.getState());
        long now = System.currentTimeMillis();
        ManagedCodingSession managed = new ManagedCodingSession(
                session,
                source.getProvider(),
                source.getProtocol(),
                source.getModel(),
                source.getWorkspace(),
                source.getWorkspaceDescription(),
                source.getSystemPrompt(),
                source.getInstructions(),
                normalizeRootSessionId(source),
                source.getSessionId(),
                now,
                now
        );
        appendEvent(managed.getSessionId(), baseEvent(
                managed.getSessionId(),
                SessionEventType.SESSION_FORKED,
                "session forked from " + source.getSessionId(),
                java.util.Collections.<String, Object>singletonMap("sourceSessionId", source.getSessionId())
        ));
        return managed;
    }

    @Override
    public StoredCodingSession save(ManagedCodingSession managedSession) throws IOException {
        if (managedSession == null || managedSession.getSession() == null) {
            throw new IllegalArgumentException("managed session is required");
        }
        CodingSessionSnapshot snapshot = managedSession.getSession().snapshot();
        StoredCodingSession stored = sessionStore.save(StoredCodingSession.builder()
                .sessionId(managedSession.getSessionId())
                .rootSessionId(isBlank(managedSession.getRootSessionId()) ? managedSession.getSessionId() : managedSession.getRootSessionId())
                .parentSessionId(managedSession.getParentSessionId())
                .provider(managedSession.getProvider())
                .protocol(managedSession.getProtocol())
                .model(managedSession.getModel())
                .workspace(managedSession.getWorkspace())
                .workspaceDescription(managedSession.getWorkspaceDescription())
                .systemPrompt(managedSession.getSystemPrompt())
                .instructions(managedSession.getInstructions())
                .summary(snapshot.getSummary())
                .memoryItemCount(snapshot.getMemoryItemCount())
                .processCount(snapshot.getProcessCount())
                .activeProcessCount(snapshot.getActiveProcessCount())
                .restoredProcessCount(snapshot.getRestoredProcessCount())
                .createdAtEpochMs(managedSession.getCreatedAtEpochMs())
                .updatedAtEpochMs(System.currentTimeMillis())
                .state(managedSession.getSession().exportState())
                .build());
        managedSession.touch(stored.getUpdatedAtEpochMs());
        appendEvent(managedSession.getSessionId(), baseEvent(managedSession.getSessionId(), SessionEventType.SESSION_SAVED, "session saved", null));
        return stored;
    }

    @Override
    public StoredCodingSession load(String sessionId) throws IOException {
        StoredCodingSession storedSession = sessionStore.load(sessionId);
        if (storedSession == null) {
            return null;
        }
        if (isBlank(storedSession.getRootSessionId())) {
            storedSession.setRootSessionId(storedSession.getSessionId());
        }
        return storedSession;
    }

    @Override
    public List<CodingSessionDescriptor> list() throws IOException {
        List<StoredCodingSession> sessions = sessionStore.list();
        List<CodingSessionDescriptor> descriptors = new ArrayList<CodingSessionDescriptor>();
        for (StoredCodingSession session : sessions) {
            descriptors.add(CodingSessionDescriptor.builder()
                    .sessionId(session.getSessionId())
                    .rootSessionId(normalizeRootSessionId(session))
                    .parentSessionId(session.getParentSessionId())
                    .provider(session.getProvider())
                    .protocol(session.getProtocol())
                    .model(session.getModel())
                    .workspace(session.getWorkspace())
                    .summary(session.getSummary())
                    .memoryItemCount(session.getMemoryItemCount())
                    .processCount(session.getProcessCount())
                    .activeProcessCount(session.getActiveProcessCount())
                    .restoredProcessCount(session.getRestoredProcessCount())
                    .createdAtEpochMs(session.getCreatedAtEpochMs())
                    .updatedAtEpochMs(session.getUpdatedAtEpochMs())
                    .build());
        }
        return descriptors;
    }

    @Override
    public void delete(String sessionId) throws IOException {
        if (isBlank(sessionId)) {
            throw new IllegalArgumentException("sessionId is required");
        }
        Path file = sessionStore.getDirectory().resolve(sessionId.replaceAll("[^a-zA-Z0-9._-]", "_") + ".json");
        java.nio.file.Files.deleteIfExists(file);
        if (eventStore != null) {
            eventStore.delete(sessionId);
        }
    }

    @Override
    public SessionEvent appendEvent(String sessionId, SessionEvent event) throws IOException {
        if (eventStore == null) {
            return event;
        }
        SessionEvent normalized = event == null ? null : event.toBuilder()
                .eventId(isBlank(event.getEventId()) ? UUID.randomUUID().toString() : event.getEventId())
                .sessionId(isBlank(event.getSessionId()) ? sessionId : event.getSessionId())
                .timestamp(event.getTimestamp() <= 0 ? System.currentTimeMillis() : event.getTimestamp())
                .build();
        return eventStore.append(normalized);
    }

    @Override
    public List<SessionEvent> listEvents(String sessionId, Integer limit, Long offset) throws IOException {
        if (eventStore == null) {
            return new ArrayList<SessionEvent>();
        }
        return eventStore.list(sessionId, limit, offset);
    }

    @Override
    public Path getDirectory() {
        return sessionStore.getDirectory();
    }

    public CodingSessionDescriptor describe(ManagedCodingSession session) {
        return session == null ? null : session.toDescriptor();
    }

    private SessionEvent baseEvent(String sessionId, SessionEventType type, String summary, java.util.Map<String, Object> payload) {
        return SessionEvent.builder()
                .sessionId(sessionId)
                .type(type)
                .summary(summary)
                .payload(payload)
                .build();
    }

    private String normalizeRootSessionId(StoredCodingSession session) {
        return session == null || isBlank(session.getRootSessionId()) ? (session == null ? null : session.getSessionId()) : session.getRootSessionId();
    }

    private void assertWorkspaceMatches(StoredCodingSession storedSession, String workspace) {
        if (storedSession == null || isBlank(storedSession.getWorkspace()) || isBlank(workspace)) {
            return;
        }
        if (!storedSession.getWorkspace().equals(workspace)) {
            throw new IllegalArgumentException(
                    "Saved session " + storedSession.getSessionId() + " belongs to workspace " + storedSession.getWorkspace()
                            + ", current workspace is " + workspace
            );
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

