package io.github.lnyocly.ai4j.plugin.sandbox.docker;

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
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@link SandboxSession} backed by an isolated Docker container with strict resource/network flags
 * and forced {@code docker rm -f} cleanup on timeout, cancel, or close.
 */
public class DockerSandboxSession implements SandboxSession {

    private final String sessionId;
    private final String containerId;
    private final SandboxSpec spec;
    private final DockerSandboxConfig config;
    private final DockerProcessRunner processRunner;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicBoolean containerRemoved = new AtomicBoolean(false);
    private volatile SandboxStatus status;

    public DockerSandboxSession(String sessionId,
                                String containerId,
                                SandboxSpec spec,
                                DockerSandboxConfig config,
                                DockerProcessRunner processRunner) {
        if (containerId == null || containerId.trim().isEmpty()) {
            throw new IllegalArgumentException("containerId must not be blank");
        }
        if (config == null) {
            throw new IllegalArgumentException("DockerSandboxConfig must not be null");
        }
        if (processRunner == null) {
            throw new IllegalArgumentException("DockerProcessRunner must not be null");
        }
        this.containerId = containerId.trim();
        this.sessionId = sessionId == null || sessionId.trim().isEmpty() ? this.containerId : sessionId.trim();
        this.spec = spec;
        this.config = config;
        this.processRunner = processRunner;
        this.status = SandboxStatus.RUNNING;
    }

    @Override
    public String getSessionId() {
        return sessionId;
    }

    @Override
    public String getProviderId() {
        return DockerSandboxProvider.PROVIDER_ID;
    }

    @Override
    public SandboxSpec getSpec() {
        return spec == null ? null : spec.copy();
    }

    @Override
    public SandboxStatus getStatus() {
        return status;
    }

    public String getContainerId() {
        return containerId;
    }

    @Override
    public synchronized SandboxResult execute(SandboxCommand command) throws SandboxException {
        if (closed.get() || status == SandboxStatus.CLOSED) {
            throw new SandboxException("Docker sandbox session is closed: " + sessionId);
        }
        if (status == SandboxStatus.FAILED || containerRemoved.get()) {
            throw new SandboxException("Docker sandbox container is no longer running: " + containerId);
        }
        if (command == null || command.getCommand() == null || command.getCommand().trim().isEmpty()) {
            throw new IllegalArgumentException("SandboxCommand command text must not be blank");
        }

        SandboxCommand safeCommand = command.copy();
        String commandId = safeCommand.getCommandId() == null || safeCommand.getCommandId().trim().isEmpty()
                ? "docker_cmd_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12)
                : safeCommand.getCommandId();
        long timeoutMillis = safeCommand.getTimeoutMillis() != null && safeCommand.getTimeoutMillis().longValue() > 0L
                ? safeCommand.getTimeoutMillis().longValue()
                : config.getCommandTimeoutMillis();

        List<SandboxEvent> events = new ArrayList<SandboxEvent>();
        events.add(event(SandboxEventType.COMMAND_STARTED, commandId,
                "Executing command inside Docker container " + containerId));

        List<String> execArgs = config.buildExecArgs(containerId, safeCommand);
        try {
            DockerProcessOutput output = processRunner.run(execArgs, safeCommand.getStdin(), timeoutMillis);
            if (output == null) {
                throw new SandboxException("Docker process runner returned null output");
            }
            if (output.isTimedOut()) {
                boolean removed = forceRemoveContainer();
                this.status = SandboxStatus.FAILED;
                events.add(event(SandboxEventType.ERROR, commandId,
                        "Command timed out after " + timeoutMillis + " ms; docker rm -f cleanup=" + removed));
                return SandboxResult.builder()
                        .commandId(commandId)
                        .exitCode(Integer.valueOf(output.getExitCode() == 0 ? 124 : output.getExitCode()))
                        .stdout(output.getStdout())
                        .stderr(output.getStderr())
                        .timedOut(true)
                        .canceled(false)
                        .durationMillis(Long.valueOf(output.getDurationMillis()))
                        .events(events)
                        .build();
            }

            if (!output.getStdout().isEmpty() || !output.getStderr().isEmpty()) {
                events.add(event(SandboxEventType.COMMAND_OUTPUT, commandId,
                        "Captured container output (stdout=" + output.getStdout().length()
                                + " chars, stderr=" + output.getStderr().length() + " chars)"));
            }
            events.add(event(SandboxEventType.COMMAND_FINISHED, commandId,
                    "Docker execution completed with exitCode=" + output.getExitCode()));

            return SandboxResult.builder()
                    .commandId(commandId)
                    .exitCode(Integer.valueOf(output.getExitCode()))
                    .stdout(output.getStdout())
                    .stderr(output.getStderr())
                    .timedOut(false)
                    .canceled(false)
                    .durationMillis(Long.valueOf(output.getDurationMillis()))
                    .events(events)
                    .build();
        } catch (IOException ex) {
            events.add(event(SandboxEventType.ERROR, commandId, ex.getMessage()));
            throw new SandboxException("Failed to execute command in Docker container " + containerId + ": " + ex.getMessage(), ex);
        }
    }

    @Override
    public boolean cancel(String commandId) {
        if (closed.get() || status == SandboxStatus.CLOSED) {
            return false;
        }
        boolean removed = forceRemoveContainer();
        if (removed) {
            this.status = SandboxStatus.CLOSED;
        }
        return removed;
    }

    @Override
    public List<SandboxArtifact> listArtifacts() {
        return Collections.emptyList();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            forceRemoveContainer();
        } finally {
            this.status = SandboxStatus.CLOSED;
        }
    }

    private boolean forceRemoveContainer() {
        if (!containerRemoved.compareAndSet(false, true)) {
            return true;
        }
        try {
            DockerProcessOutput output = processRunner.run(
                    config.buildForceRemoveArgs(containerId),
                    null,
                    config.getCleanupTimeoutMillis()
            );
            return output != null && output.getExitCode() == 0;
        } catch (IOException | RuntimeException ex) {
            return false;
        }
    }

    private SandboxEvent event(SandboxEventType type, String commandId, String message) {
        return SandboxEvent.builder()
                .eventId("evt_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12))
                .type(type)
                .sessionId(sessionId)
                .commandId(commandId)
                .timestampEpochMs(System.currentTimeMillis())
                .message(message)
                .build();
    }
}
