package io.github.lnyocly.ai4j.agent.sandbox.e2b;

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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Live E2B sandbox session.
 *
 * <p>By default the agent-supplied command string is wrapped in {@code sh -c} so the full shell
 * semantics (pipes, redirection, multi-statement scripts) match other providers; an optional
 * {@code stdin} is piped into the command. Direct exec ({@code useShellWrap=false}) splits the
 * command into {@code cmd + args} on whitespace.</p>
 */
public class E2BSandboxSession implements SandboxSession {

    private final E2BSandboxClient client;
    private final E2BSandboxConfig config;
    private final SandboxSpec spec;
    private final String sessionId;
    private final String sandboxHost;
    private final String executionToken;
    private final String accessToken;
    private volatile SandboxStatus status;

    E2BSandboxSession(E2BSandboxClient client,
                      E2BSandboxConfig config,
                      E2BCreateSandboxResponse created) {
        if (client == null) {
            throw new IllegalArgumentException("e2b sandbox client must not be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("e2b sandbox config must not be null");
        }
        if (created == null || E2BSandboxConfig.trimToNull(created.getSandboxID()) == null) {
            throw new IllegalArgumentException("e2b sandbox id must not be blank");
        }
        this.client = client;
        this.config = config;
        this.spec = config.getSpec();
        this.sessionId = created.getSandboxID();
        this.sandboxHost = config.buildSandboxHost(sessionId);
        // Execution auth: prefer a per-sandbox envd access token when the create response or config
        // supplied one, otherwise fall back to the API key as the bearer token.
        String token = E2BSandboxConfig.firstNonBlank(
                E2BSandboxConfig.firstNonBlank(created.getEnvdAccessToken(), config.getEnvdAccessToken()),
                config.getApiKey());
        this.executionToken = token;
        // X-Access-Token is only sent when an envd access token was explicitly provided.
        this.accessToken = E2BSandboxConfig.firstNonBlank(created.getEnvdAccessToken(), config.getEnvdAccessToken());
        this.status = SandboxStatus.RUNNING;
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
            throw new SandboxException("E2B sandbox session is closed");
        }
        SandboxCommand safeCommand = command.copy();
        String[] resolved = resolveCommand(safeCommand);
        String cmd = resolved[0];
        List<String> args = resolved.length > 1
                ? Arrays.asList(Arrays.copyOfRange(resolved, 1, resolved.length))
                : Collections.<String>emptyList();
        Map<String, String> envs = mergeEnvironments(safeCommand);
        long readTimeoutMillis = resolveReadTimeoutMillis(safeCommand);

        long startedAt = System.currentTimeMillis();
        SandboxEvent started = event(SandboxEventType.COMMAND_STARTED, safeCommand.getCommandId(), "E2B command started");
        try {
            status = SandboxStatus.RUNNING;
            E2BProcessResult result = client.execute(
                    sandboxHost, executionToken, accessToken,
                    cmd, args, envs, safeCommand.getWorkingDirectory(), readTimeoutMillis);
            long duration = System.currentTimeMillis() - startedAt;
            SandboxEvent finished = event(SandboxEventType.COMMAND_FINISHED, safeCommand.getCommandId(),
                    "E2B command finished (exitCode=" + result.getExitCode() + ")");
            return SandboxResult.builder()
                    .commandId(safeCommand.getCommandId())
                    .exitCode(result.getExitCode())
                    .stdout(result.getStdout())
                    .stderr(result.getStderr())
                    .durationMillis(Long.valueOf(duration))
                    .events(Arrays.asList(started, finished))
                    .build();
        } catch (IOException e) {
            SandboxEvent error = event(SandboxEventType.ERROR, safeCommand.getCommandId(), e.getMessage());
            throw new SandboxException("failed to execute command in E2B sandbox. event=" + error.getEventId(), e);
        }
    }

    @Override
    public boolean cancel(String commandId) {
        // Cancellation via process.Process/SendSignal (by pid) is not wired in v1.
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
            throw new SandboxException("failed to close E2B sandbox session", e);
        }
    }

    private String[] resolveCommand(SandboxCommand command) {
        String raw = command.getCommand();
        if (config.isUseShellWrap()) {
            String shellScript = raw;
            String stdin = command.getStdin();
            if (stdin != null && !stdin.isEmpty()) {
                // Feed stdin via a pipe so the command's exit code is preserved (last pipeline stage).
                shellScript = "printf '%s' '" + escapeSingleQuotes(stdin) + "' | ( " + raw + " )";
            }
            return new String[]{"sh", "-c", shellScript};
        }
        // Direct exec: naive whitespace tokenization (opt-in escape hatch, no quoting support).
        return raw.split("\\s+");
    }

    private Map<String, String> mergeEnvironments(SandboxCommand command) {
        Map<String, String> merged = new LinkedHashMap<String, String>();
        Map<String, String> base = config.getEnvironment();
        if (base != null) {
            merged.putAll(base);
        }
        Map<String, String> commandEnv = command.getEnvironment();
        if (commandEnv != null) {
            merged.putAll(commandEnv);
        }
        return merged;
    }

    private long resolveReadTimeoutMillis(SandboxCommand command) {
        long base = config.getReadTimeoutMillis();
        Long commandTimeout = command.getTimeoutMillis();
        if (commandTimeout != null && commandTimeout.longValue() > 0L) {
            long withBuffer = commandTimeout.longValue() + 5000L;
            return Math.max(base, withBuffer);
        }
        return base;
    }

    private static String escapeSingleQuotes(String value) {
        return value.replace("'", "'\\''");
    }

    private SandboxEvent event(SandboxEventType type, String commandId, String message) {
        return SandboxEvent.builder()
                .type(type)
                .sessionId(sessionId)
                .commandId(commandId)
                .message(message)
                .build();
    }
}
