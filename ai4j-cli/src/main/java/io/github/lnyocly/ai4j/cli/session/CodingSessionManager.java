package io.github.lnyocly.ai4j.cli.session;

import io.github.lnyocly.ai4j.cli.CliProtocol;
import io.github.lnyocly.ai4j.cli.command.CodeCommandOptions;

import io.github.lnyocly.ai4j.coding.CodingAgent;
import io.github.lnyocly.ai4j.coding.session.CodingSessionDescriptor;
import io.github.lnyocly.ai4j.coding.session.ManagedCodingSession;
import io.github.lnyocly.ai4j.coding.session.SessionEvent;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface CodingSessionManager {

    ManagedCodingSession create(CodingAgent agent, CliProtocol protocol, CodeCommandOptions options) throws Exception;

    ManagedCodingSession resume(CodingAgent agent, CliProtocol protocol, CodeCommandOptions options, String sessionId) throws Exception;

    ManagedCodingSession fork(CodingAgent agent,
                              CliProtocol protocol,
                              CodeCommandOptions options,
                              String sourceSessionId,
                              String targetSessionId) throws Exception;

    StoredCodingSession save(ManagedCodingSession session) throws IOException;

    StoredCodingSession load(String sessionId) throws IOException;

    List<CodingSessionDescriptor> list() throws IOException;

    void delete(String sessionId) throws IOException;

    SessionEvent appendEvent(String sessionId, SessionEvent event) throws IOException;

    List<SessionEvent> listEvents(String sessionId, Integer limit, Long offset) throws IOException;

    Path getDirectory();
}

