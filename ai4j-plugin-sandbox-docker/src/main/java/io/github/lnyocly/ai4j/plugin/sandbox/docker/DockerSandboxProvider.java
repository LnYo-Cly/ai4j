package io.github.lnyocly.ai4j.plugin.sandbox.docker;

import io.github.lnyocly.ai4j.agent.sandbox.SandboxException;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxProvider;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSession;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSpec;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@link SandboxProvider} SPI implementation backed by isolated Docker containers.
 */
public class DockerSandboxProvider implements SandboxProvider {

    public static final String PROVIDER_ID = "docker";

    private final DockerSandboxConfig defaultConfig;
    private final DockerProcessRunner processRunner;
    private final Map<String, String> environment;

    public DockerSandboxProvider() {
        this(null, null, null);
    }

    public DockerSandboxProvider(DockerSandboxConfig defaultConfig) {
        this(defaultConfig, null, null);
    }

    public DockerSandboxProvider(DockerSandboxConfig defaultConfig, DockerProcessRunner processRunner) {
        this(defaultConfig, processRunner, null);
    }

    public DockerSandboxProvider(DockerSandboxConfig defaultConfig,
                                 DockerProcessRunner processRunner,
                                 Map<String, String> environment) {
        this.defaultConfig = defaultConfig;
        this.processRunner = processRunner == null ? new ProcessBuilderDockerProcessRunner() : processRunner;
        this.environment = environment == null ? null : Collections.unmodifiableMap(environment);
    }

    @Override
    public String getProviderId() {
        return PROVIDER_ID;
    }

    @Override
    public boolean supports(SandboxSpec spec) {
        if (spec == null || spec.getProviderId() == null) {
            return false;
        }
        String providerId = spec.getProviderId().trim();
        return PROVIDER_ID.equalsIgnoreCase(providerId)
                || "docker-sandbox".equalsIgnoreCase(providerId);
    }

    @Override
    public SandboxSession createSession(SandboxSpec spec) throws SandboxException {
        DockerSandboxConfig resolved = resolveConfig(spec);
        String containerName = "ai4j-sbx-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        List<String> runArgs = resolved.buildRunContainerArgs(containerName, spec);
        try {
            DockerProcessOutput output = processRunner.run(runArgs, null, resolved.getStartupTimeoutMillis());
            if (output == null) {
                throw new SandboxException("Docker process runner returned null output when creating container");
            }
            if (output.isTimedOut()) {
                bestEffortRemove(resolved, containerName);
                throw new SandboxException("Timed out creating Docker sandbox container: " + containerName);
            }
            if (output.getExitCode() != 0) {
                bestEffortRemove(resolved, containerName);
                String err = output.getStderr().isEmpty() ? output.getStdout() : output.getStderr();
                throw new SandboxException("Failed to create Docker sandbox container (exit=" + output.getExitCode() + "): " + err);
            }
            String containerId = firstLineOrFallback(output.getStdout(), containerName);
            return new DockerSandboxSession(containerName, containerId, spec, resolved, processRunner);
        } catch (SandboxException ex) {
            throw ex;
        } catch (IOException ex) {
            bestEffortRemove(resolved, containerName);
            throw new SandboxException("Failed to start Docker sandbox container: " + ex.getMessage(), ex);
        } catch (RuntimeException ex) {
            bestEffortRemove(resolved, containerName);
            throw new SandboxException("Failed to start Docker sandbox container: " + ex.getMessage(), ex);
        }
    }

    private DockerSandboxConfig resolveConfig(SandboxSpec spec) {
        DockerSandboxConfig fromSpec = DockerSandboxConfig.from(spec, environment);
        if (defaultConfig == null) {
            return fromSpec;
        }
        return defaultConfig.mergeWith(fromSpec);
    }

    private void bestEffortRemove(DockerSandboxConfig config, String containerName) {
        try {
            processRunner.run(config.buildForceRemoveArgs(containerName), null, config.getCleanupTimeoutMillis());
        } catch (IOException | RuntimeException ignored) {
        }
    }

    private static String firstLineOrFallback(String stdout, String fallback) {
        if (stdout == null) {
            return fallback;
        }
        String trimmed = stdout.trim();
        if (trimmed.isEmpty()) {
            return fallback;
        }
        int newline = trimmed.indexOf('\n');
        String firstLine = newline >= 0 ? trimmed.substring(0, newline).trim() : trimmed;
        return firstLine.isEmpty() ? fallback : firstLine;
    }
}
