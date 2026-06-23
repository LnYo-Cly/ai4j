package io.github.lnyocly.ai4j.agent.sandbox.daytona;

import io.github.lnyocly.ai4j.agent.sandbox.SandboxArtifact;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxCommand;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxEvent;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxEventType;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxException;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxResult;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSession;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSpec;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxStatus;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Live Daytona sandbox session.
 */
public class DaytonaSandboxSession implements SandboxSession {

    private final DaytonaSandboxClient client;
    private final DaytonaSandboxConfig config;
    private final SandboxSpec spec;
    private final String sessionId;
    private final String toolboxProxyUrl;
    private volatile SandboxStatus status;

    DaytonaSandboxSession(DaytonaSandboxClient client,
                          DaytonaSandboxConfig config,
                          DaytonaSandbox sandbox,
                          String toolboxProxyUrl) {
        if (client == null) {
            throw new IllegalArgumentException("daytona sandbox client must not be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("daytona sandbox config must not be null");
        }
        if (sandbox == null || DaytonaSandboxConfig.trimToNull(sandbox.getId()) == null) {
            throw new IllegalArgumentException("daytona sandbox id must not be blank");
        }
        this.client = client;
        this.config = config;
        this.spec = config.getSpec();
        this.sessionId = sandbox.getId();
        this.toolboxProxyUrl = DaytonaSandboxConfig.trimTrailingSlash(toolboxProxyUrl);
        this.status = toStatus(sandbox.getState());
    }

    @Override
    public String getSessionId() {
        return sessionId;
    }

    @Override
    public String getProviderId() {
        return config.getProviderId();
    }

    @Override
    public SandboxSpec getSpec() {
        return spec.copy();
    }

    @Override
    public SandboxStatus getStatus() {
        return status;
    }

    @Override
    public synchronized SandboxResult execute(SandboxCommand command) throws SandboxException {
        if (status == SandboxStatus.CLOSED) {
            throw new SandboxException("Daytona sandbox session is closed");
        }
        SandboxCommand safeCommand = command.copy();
        DaytonaExecuteRequest request = new DaytonaExecuteRequest();
        request.setCommand(safeCommand.getCommand());
        request.setCwd(safeCommand.getWorkingDirectory());
        request.setStdin(safeCommand.getStdin());
        request.setEnvs(safeCommand.getEnvironment());
        request.setTimeout(timeoutSeconds(safeCommand.getTimeoutMillis()));

        long startedAt = System.currentTimeMillis();
        SandboxEvent started = event(SandboxEventType.COMMAND_STARTED, safeCommand.getCommandId(), "Daytona command started");
        try {
            status = SandboxStatus.RUNNING;
            DaytonaExecuteResponse response = client.execute(sessionId, toolboxProxyUrl, request);
            long duration = System.currentTimeMillis() - startedAt;
            SandboxEvent finished = event(SandboxEventType.COMMAND_FINISHED, safeCommand.getCommandId(), "Daytona command finished");
            return SandboxResult.builder()
                    .commandId(safeCommand.getCommandId())
                    .exitCode(response == null ? null : response.getExitCode())
                    .stdout(response == null ? null : response.stdoutOrResult())
                    .stderr(response == null ? null : response.getStderr())
                    .durationMillis(Long.valueOf(duration))
                    .events(Arrays.asList(started, finished))
                    .build();
        } catch (IOException e) {
            SandboxEvent error = event(SandboxEventType.ERROR, safeCommand.getCommandId(), e.getMessage());
            throw new SandboxException("failed to execute command in Daytona sandbox. event=" + error.getEventId(), e);
        }
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
    public synchronized void close() throws SandboxException {
        if (status == SandboxStatus.CLOSED) {
            return;
        }
        try {
            if (config.isDeleteOnClose()) {
                client.deleteSandbox(sessionId);
            }
            status = SandboxStatus.CLOSED;
        } catch (IOException e) {
            status = SandboxStatus.FAILED;
            throw new SandboxException("failed to close Daytona sandbox session", e);
        }
    }

    static SandboxStatus toStatus(String state) {
        if ("started".equalsIgnoreCase(state) || "starting".equalsIgnoreCase(state)
                || "resuming".equalsIgnoreCase(state) || "restoring".equalsIgnoreCase(state)) {
            return SandboxStatus.RUNNING;
        }
        if ("stopped".equalsIgnoreCase(state) || "destroyed".equalsIgnoreCase(state)
                || "archived".equalsIgnoreCase(state) || "paused".equalsIgnoreCase(state)) {
            return SandboxStatus.CLOSED;
        }
        if ("error".equalsIgnoreCase(state) || "build_failed".equalsIgnoreCase(state)) {
            return SandboxStatus.FAILED;
        }
        return SandboxStatus.CREATED;
    }

    private SandboxEvent event(SandboxEventType type, String commandId, String message) {
        return SandboxEvent.builder()
                .type(type)
                .sessionId(sessionId)
                .commandId(commandId)
                .message(message)
                .build();
    }

    private static Integer timeoutSeconds(Long timeoutMillis) {
        if (timeoutMillis == null || timeoutMillis.longValue() <= 0L) {
            return null;
        }
        long seconds = (timeoutMillis.longValue() + 999L) / 1000L;
        if (seconds > Integer.MAX_VALUE) {
            return Integer.valueOf(Integer.MAX_VALUE);
        }
        return Integer.valueOf((int) seconds);
    }
}
