package io.github.lnyocly.ai4j.agent.sandbox.cubesandbox;

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
import java.util.Map;

/**
 * Live CubeSandbox session bound to one AI4J Agent/Coding Agent session.
 */
public final class CubeSandboxSession implements SandboxSession {

    private final CubeSandboxClient client;
    private final CubeSandboxRemote remote;
    private final SandboxSpec spec;
    private final CubeSandboxConfig config;
    private final boolean connectedExisting;
    private final List<SandboxArtifact> artifacts = new ArrayList<SandboxArtifact>();
    private SandboxStatus status = SandboxStatus.RUNNING;

    CubeSandboxSession(CubeSandboxClient client,
                       CubeSandboxRemote remote,
                       SandboxSpec spec,
                       CubeSandboxConfig config,
                       boolean connectedExisting) {
        this.client = client;
        this.remote = remote;
        this.spec = withRemoteLabels(spec, config, remote);
        this.config = config;
        this.connectedExisting = connectedExisting;
    }

    @Override
    public String getSessionId() {
        return remote.getSandboxId();
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

    public boolean isConnectedExisting() {
        return connectedExisting;
    }

    public String getTemplateId() {
        return remote.getTemplateId();
    }

    public String getDomain() {
        return remote.getDomain() == null ? config.getSandboxDomain() : remote.getDomain();
    }

    @Override
    public synchronized SandboxResult execute(SandboxCommand command) throws SandboxException {
        if (status == SandboxStatus.CLOSED) {
            throw new SandboxException("CubeSandbox session is closed: " + getSessionId());
        }
        if (status == SandboxStatus.FAILED) {
            throw new SandboxException("CubeSandbox session is failed: " + getSessionId());
        }
        SandboxCommand safeCommand = command.copy();
        List<SandboxEvent> events = new ArrayList<SandboxEvent>();
        events.add(event(SandboxEventType.COMMAND_STARTED, safeCommand.getCommandId(), "CubeSandbox command started"));
        try {
            CubeSandboxClient.ProcessRun run = client.runProcess(remote,
                    safeCommand.getCommand(),
                    firstNonBlank(safeCommand.getWorkingDirectory(), spec.getWorkspaceId()),
                    safeCommand.getEnvironment(),
                    safeCommand.getTimeoutMillis());
            SandboxArtifact stdoutArtifact = SandboxArtifact.builder()
                    .artifactId("cubesandbox-stdout-" + safeCommand.getCommandId())
                    .name("stdout.txt")
                    .path("cubesandbox://" + getSessionId() + "/commands/" + safeCommand.getCommandId() + "/stdout.txt")
                    .mimeType("text/plain")
                    .sizeBytes(Long.valueOf(run.getStdout() == null ? 0 : run.getStdout().length()))
                    .build();
            artifacts.add(stdoutArtifact);
            events.add(event(SandboxEventType.COMMAND_FINISHED, safeCommand.getCommandId(), "CubeSandbox command finished"));
            return SandboxResult.builder()
                    .commandId(safeCommand.getCommandId())
                    .exitCode(run.getExitCode())
                    .stdout(run.getStdout())
                    .stderr(run.getStderr())
                    .durationMillis(run.getDurationMillis())
                    .artifact(stdoutArtifact)
                    .events(events)
                    .build();
        } catch (SandboxException e) {
            events.add(event(SandboxEventType.ERROR, safeCommand.getCommandId(), e.getMessage()));
            throw e;
        }
    }

    @Override
    public boolean cancel(String commandId) {
        // CubeSandbox envd process start is synchronous in this adapter. There
        // is no stable command-id kill endpoint in the current public SDK path.
        return false;
    }

    @Override
    public synchronized List<SandboxArtifact> listArtifacts() {
        List<SandboxArtifact> copy = new ArrayList<SandboxArtifact>();
        for (SandboxArtifact artifact : artifacts) {
            copy.add(artifact.copy());
        }
        return copy;
    }

    @Override
    public synchronized void close() throws SandboxException {
        if (status == SandboxStatus.CLOSED) {
            return;
        }
        try {
            if (config.isCloseDestroysSandbox() && !connectedExisting) {
                client.kill(getSessionId());
            }
            status = SandboxStatus.CLOSED;
        } catch (SandboxException e) {
            status = SandboxStatus.FAILED;
            throw e;
        } finally {
            client.close();
        }
    }

    private SandboxEvent event(SandboxEventType type, String commandId, String message) {
        return SandboxEvent.builder()
                .type(type)
                .sessionId(getSessionId())
                .commandId(commandId)
                .message(message)
                .build();
    }

    private static SandboxSpec withRemoteLabels(SandboxSpec spec, CubeSandboxConfig config, CubeSandboxRemote remote) {
        SandboxSpec safe = spec == null ? SandboxSpec.builder().providerId(config.getProviderId()).build() : spec.copy();
        SandboxSpec.Builder builder = SandboxSpec.builder()
                .providerId(config.getProviderId())
                .profile(safe.getProfile())
                .image(firstNonBlank(safe.getImage(), remote.getTemplateId()))
                .workspaceId(safe.getWorkspaceId())
                .labels(CubeSandboxSanitizer.nonSensitiveStringMap(safe.getLabels()))
                .config(Collections.<String, Object>emptyMap());
        for (Map.Entry<String, String> entry : config.safeConfigLabels().entrySet()) {
            builder.label("cube." + entry.getKey(), entry.getValue());
        }
        builder.label("cube.templateId", remote.getTemplateId());
        builder.label("cube.clientId", remote.getClientId());
        builder.label("cube.envdVersion", remote.getEnvdVersion());
        builder.label("cube.domain", remote.getDomain());
        return builder.build();
    }

    private static String firstNonBlank(String first, String second) {
        return first == null || first.trim().isEmpty() ? second : first.trim();
    }
}
