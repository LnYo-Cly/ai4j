package io.github.lnyocly.ai4j.plugin.colab;

import io.github.lnyocly.ai4j.agent.sandbox.SandboxArtifact;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxCommand;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxEvent;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxEventType;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxException;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxResult;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSession;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSpec;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@link SandboxSession} implementation backed by a live Google Colab / Jupyter kernel session.
 */
public class ColabSandboxSession implements SandboxSession {

    private final String sessionId;
    private final String jupyterSessionId;
    private final String kernelId;
    private final SandboxSpec spec;
    private final ColabKernelClient client;
    private final boolean ownsClient;
    private final List<SandboxArtifact> artifacts = new ArrayList<SandboxArtifact>();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private volatile SandboxStatus status;

    public ColabSandboxSession(String sessionId,
                               String jupyterSessionId,
                               String kernelId,
                               SandboxSpec spec,
                               ColabKernelClient client,
                               boolean ownsClient) {
        if (kernelId == null || kernelId.trim().isEmpty()) {
            throw new IllegalArgumentException("kernelId must not be blank");
        }
        if (client == null) {
            throw new IllegalArgumentException("ColabKernelClient must not be null");
        }
        this.sessionId = sessionId == null || sessionId.trim().isEmpty() ? kernelId : sessionId;
        this.jupyterSessionId = jupyterSessionId;
        this.kernelId = kernelId;
        this.spec = spec;
        this.client = client;
        this.ownsClient = ownsClient;
        this.status = SandboxStatus.RUNNING;
    }

    @Override
    public String getSessionId() {
        return sessionId;
    }

    @Override
    public String getProviderId() {
        return ColabSandboxProvider.PROVIDER_ID;
    }

    @Override
    public SandboxSpec getSpec() {
        return spec;
    }

    @Override
    public SandboxStatus getStatus() {
        return status;
    }

    public String getJupyterSessionId() {
        return jupyterSessionId;
    }

    public String getKernelId() {
        return kernelId;
    }

    @Override
    public SandboxResult execute(SandboxCommand command) throws SandboxException {
        if (closed.get() || status == SandboxStatus.CLOSED) {
            throw new SandboxException("Colab sandbox session is closed: " + sessionId);
        }
        if (command == null || command.getCommand() == null || command.getCommand().trim().isEmpty()) {
            throw new IllegalArgumentException("SandboxCommand command text must not be blank");
        }

        String commandId = command.getCommandId() == null || command.getCommandId().trim().isEmpty()
                ? "colab_cmd_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12)
                : command.getCommandId();
        List<SandboxEvent> events = new ArrayList<SandboxEvent>();
        events.add(event(SandboxEventType.COMMAND_STARTED, commandId, "Executing Python cell on Colab kernel " + kernelId));

        long timeoutMillis = command.getTimeoutMillis() == null
                ? client.getConfig().getExecutionTimeoutMillis()
                : command.getTimeoutMillis().longValue();

        try {
            ColabKernelClient.ExecutionResult execResult = client.executePython(
                    kernelId,
                    command.getCommand(),
                    timeoutMillis,
                    command.getEnvironment(),
                    command.getWorkingDirectory()
            );

            if (!execResult.getStdout().isEmpty() || !execResult.getStderr().isEmpty()) {
                events.add(event(SandboxEventType.COMMAND_OUTPUT, commandId,
                        "Captured kernel output (stdout=" + execResult.getStdout().length()
                                + " chars, stderr=" + execResult.getStderr().length() + " chars)"));
            }

            List<SandboxArtifact> commandArtifacts = execResult.getArtifacts();
            if (!commandArtifacts.isEmpty()) {
                synchronized (artifacts) {
                    artifacts.addAll(commandArtifacts);
                }
                for (SandboxArtifact artifact : commandArtifacts) {
                    events.add(event(SandboxEventType.ARTIFACT_CREATED, commandId,
                            "Extracted artifact " + artifact.getName() + " (" + artifact.getMimeType() + ")"));
                }
            }

            if (execResult.isCanceled()) {
                events.add(event(SandboxEventType.COMMAND_CANCELED, commandId, "Colab execution canceled"));
            } else {
                events.add(event(SandboxEventType.COMMAND_FINISHED, commandId,
                        "Colab execution completed with exitCode=" + execResult.getExitCode()));
            }

            return SandboxResult.builder()
                    .commandId(commandId)
                    .exitCode(Integer.valueOf(execResult.getExitCode()))
                    .stdout(execResult.getStdout())
                    .stderr(execResult.getStderr())
                    .timedOut(execResult.isTimedOut())
                    .canceled(execResult.isCanceled())
                    .durationMillis(Long.valueOf(execResult.getDurationMillis()))
                    .artifacts(commandArtifacts)
                    .events(events)
                    .build();
        } catch (SandboxException ex) {
            events.add(event(SandboxEventType.ERROR, commandId, ex.getMessage()));
            throw ex;
        }
    }

    @Override
    public boolean cancel(String commandId) {
        if (closed.get() || status == SandboxStatus.CLOSED) {
            return false;
        }
        try {
            client.interruptKernel(kernelId);
            return true;
        } catch (SandboxException | RuntimeException ex) {
            return false;
        }
    }

    @Override
    public List<SandboxArtifact> listArtifacts() {
        synchronized (artifacts) {
            return artifacts.isEmpty()
                    ? Collections.<SandboxArtifact>emptyList()
                    : Collections.unmodifiableList(new ArrayList<SandboxArtifact>(artifacts));
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        status = SandboxStatus.CLOSED;
        try {
            if (jupyterSessionId != null && !jupyterSessionId.trim().isEmpty()) {
                try {
                    client.deleteSession(jupyterSessionId);
                } catch (SandboxException | RuntimeException ignored) {
                    bestEffortDeleteKernel();
                }
            } else {
                bestEffortDeleteKernel();
            }
        } finally {
            if (ownsClient) {
                client.close();
            }
        }
    }

    private void bestEffortDeleteKernel() {
        try {
            client.deleteKernel(kernelId);
        } catch (SandboxException | RuntimeException ignored) {
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
