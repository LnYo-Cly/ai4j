package io.github.lnyocly.ai4j.plugin.sandbox.docker;

import io.github.lnyocly.ai4j.agent.sandbox.SandboxCommand;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSpec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hardened Docker container configuration for {@code ai4j-plugin-sandbox-docker}.
 * Always enforces {@code --read-only}, {@code --network=none}, {@code --memory},
 * {@code --cpus}, and {@code --pids-limit}.
 */
public final class DockerSandboxConfig {

    public static final String DEFAULT_DOCKER_BINARY = "docker";
    public static final String DEFAULT_IMAGE = "alpine:3.20";
    public static final String DEFAULT_MEMORY = "512m";
    public static final String DEFAULT_CPUS = "1.0";
    public static final int DEFAULT_PIDS_LIMIT = 64;
    public static final String DEFAULT_WORKING_DIRECTORY = "/workspace";
    public static final long DEFAULT_STARTUP_TIMEOUT_MS = 30_000L;
    public static final long DEFAULT_COMMAND_TIMEOUT_MS = 30_000L;
    public static final long DEFAULT_CLEANUP_TIMEOUT_MS = 10_000L;

    public static final String ENV_DOCKER_BINARY = "AI4J_DOCKER_BINARY";
    public static final String ENV_DOCKER_IMAGE = "AI4J_DOCKER_IMAGE";
    public static final String ENV_DOCKER_MEMORY = "AI4J_DOCKER_MEMORY";
    public static final String ENV_DOCKER_CPUS = "AI4J_DOCKER_CPUS";
    public static final String ENV_DOCKER_PIDS_LIMIT = "AI4J_DOCKER_PIDS_LIMIT";

    private final String dockerBinary;
    private final String image;
    private final String memory;
    private final String cpus;
    private final int pidsLimit;
    private final String workingDirectory;
    private final long startupTimeoutMillis;
    private final long commandTimeoutMillis;
    private final long cleanupTimeoutMillis;
    private final Map<String, String> environment;

    private DockerSandboxConfig(Builder builder) {
        this.dockerBinary = firstNonBlank(builder.dockerBinary, DEFAULT_DOCKER_BINARY);
        this.image = firstNonBlank(builder.image, DEFAULT_IMAGE);
        this.memory = firstNonBlank(builder.memory, DEFAULT_MEMORY);
        this.cpus = firstNonBlank(builder.cpus, DEFAULT_CPUS);
        this.pidsLimit = builder.pidsLimit > 0 ? builder.pidsLimit : DEFAULT_PIDS_LIMIT;
        this.workingDirectory = firstNonBlank(builder.workingDirectory, DEFAULT_WORKING_DIRECTORY);
        this.startupTimeoutMillis = positiveOrDefault(builder.startupTimeoutMillis, DEFAULT_STARTUP_TIMEOUT_MS);
        this.commandTimeoutMillis = positiveOrDefault(builder.commandTimeoutMillis, DEFAULT_COMMAND_TIMEOUT_MS);
        this.cleanupTimeoutMillis = positiveOrDefault(builder.cleanupTimeoutMillis, DEFAULT_CLEANUP_TIMEOUT_MS);
        this.environment = builder.environment == null
                ? Collections.<String, String>emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<String, String>(builder.environment));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static DockerSandboxConfig from(SandboxSpec spec, Map<String, String> env) {
        Map<String, String> resolvedEnv = env == null ? System.getenv() : env;
        Map<String, Object> specConfig = spec == null || spec.getConfig() == null
                ? Collections.<String, Object>emptyMap()
                : spec.getConfig();

        String dockerBinary = firstNonBlank(
                stringValue(specConfig.get("dockerBinary")),
                resolvedEnv.get(ENV_DOCKER_BINARY),
                DEFAULT_DOCKER_BINARY
        );
        String image = firstNonBlank(
                spec == null ? null : spec.getImage(),
                stringValue(specConfig.get("image")),
                resolvedEnv.get(ENV_DOCKER_IMAGE),
                DEFAULT_IMAGE
        );
        String memory = firstNonBlank(
                stringValue(specConfig.get("memory")),
                stringValue(specConfig.get("memoryLimit")),
                resolvedEnv.get(ENV_DOCKER_MEMORY),
                DEFAULT_MEMORY
        );
        String cpus = firstNonBlank(
                stringValue(specConfig.get("cpus")),
                resolvedEnv.get(ENV_DOCKER_CPUS),
                DEFAULT_CPUS
        );
        Integer pidsLimit = intValue(
                specConfig.get("pidsLimit"),
                specConfig.get("pids_limit"),
                resolvedEnv.get(ENV_DOCKER_PIDS_LIMIT)
        );
        String workingDirectory = firstNonBlank(
                stringValue(specConfig.get("workingDirectory")),
                stringValue(specConfig.get("workdir")),
                DEFAULT_WORKING_DIRECTORY
        );
        Long startupTimeout = longValue(specConfig.get("startupTimeoutMillis"), specConfig.get("startupTimeoutMs"));
        Long commandTimeout = longValue(specConfig.get("commandTimeoutMillis"), specConfig.get("timeoutMs"));
        Long cleanupTimeout = longValue(specConfig.get("cleanupTimeoutMillis"), specConfig.get("cleanupTimeoutMs"));

        Builder builder = builder()
                .dockerBinary(dockerBinary)
                .image(image)
                .memory(memory)
                .cpus(cpus)
                .workingDirectory(workingDirectory);
        if (pidsLimit != null) {
            if (pidsLimit.intValue() <= 0) {
                throw new IllegalArgumentException("pidsLimit must be > 0");
            }
            builder.pidsLimit(pidsLimit.intValue());
        }
        if (startupTimeout != null) {
            builder.startupTimeoutMillis(startupTimeout.longValue());
        }
        if (commandTimeout != null) {
            builder.commandTimeoutMillis(commandTimeout.longValue());
        }
        if (cleanupTimeout != null) {
            builder.cleanupTimeoutMillis(cleanupTimeout.longValue());
        }
        Object rawEnv = specConfig.get("environment");
        if (rawEnv instanceof Map) {
            Map<String, String> envMap = new LinkedHashMap<String, String>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) rawEnv).entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    String key = String.valueOf(entry.getKey()).trim();
                    if (!key.isEmpty()) {
                        envMap.put(key, String.valueOf(entry.getValue()));
                    }
                }
            }
            builder.environment(envMap);
        }
        return builder.build();
    }

    public DockerSandboxConfig mergeWith(DockerSandboxConfig override) {
        if (override == null) {
            return this;
        }
        Map<String, String> mergedEnv = new LinkedHashMap<String, String>(this.environment);
        mergedEnv.putAll(override.environment);
        return builder()
                .dockerBinary(firstNonBlank(override.dockerBinary, this.dockerBinary))
                .image(firstNonBlank(override.image, this.image))
                .memory(firstNonBlank(override.memory, this.memory))
                .cpus(firstNonBlank(override.cpus, this.cpus))
                .pidsLimit(override.pidsLimit > 0 ? override.pidsLimit : this.pidsLimit)
                .workingDirectory(firstNonBlank(override.workingDirectory, this.workingDirectory))
                .startupTimeoutMillis(override.startupTimeoutMillis)
                .commandTimeoutMillis(override.commandTimeoutMillis)
                .cleanupTimeoutMillis(override.cleanupTimeoutMillis)
                .environment(mergedEnv)
                .build();
    }

    public List<String> buildRunContainerArgs(String containerName, SandboxSpec spec) {
        if (containerName == null || containerName.trim().isEmpty()) {
            throw new IllegalArgumentException("containerName must not be blank");
        }
        List<String> args = new ArrayList<String>();
        args.add(dockerBinary);
        args.add("run");
        args.add("-d");
        args.add("--name");
        args.add(containerName.trim());
        args.add("--read-only");
        args.add("--network=none");
        args.add("--memory");
        args.add(memory);
        args.add("--cpus");
        args.add(cpus);
        args.add("--pids-limit");
        args.add(String.valueOf(pidsLimit));
        args.add("--cap-drop=ALL");
        args.add("--security-opt=no-new-privileges:true");
        if (workingDirectory != null) {
            args.add("--workdir");
            args.add(workingDirectory);
        }
        for (Map.Entry<String, String> entry : environment.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                args.add("-e");
                args.add(entry.getKey() + "=" + entry.getValue());
            }
        }
        if (spec != null && spec.getLabels() != null) {
            for (Map.Entry<String, String> entry : spec.getLabels().entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    args.add("--label");
                    args.add(entry.getKey() + "=" + entry.getValue());
                }
            }
        }
        args.add(image);
        args.add("sh");
        args.add("-c");
        args.add("trap 'exit 0' TERM INT; while true; do sleep 3600; done");
        return Collections.unmodifiableList(args);
    }

    public List<String> buildExecArgs(String containerId, SandboxCommand command) {
        if (containerId == null || containerId.trim().isEmpty()) {
            throw new IllegalArgumentException("containerId must not be blank");
        }
        if (command == null || command.getCommand() == null || command.getCommand().trim().isEmpty()) {
            throw new IllegalArgumentException("command must not be blank");
        }
        List<String> args = new ArrayList<String>();
        args.add(dockerBinary);
        args.add("exec");
        if (command.getStdin() != null) {
            args.add("-i");
        }
        String effectiveWorkdir = firstNonBlank(command.getWorkingDirectory(), workingDirectory);
        if (effectiveWorkdir != null) {
            args.add("-w");
            args.add(effectiveWorkdir);
        }
        Map<String, String> cmdEnv = command.getEnvironment();
        if (cmdEnv != null) {
            for (Map.Entry<String, String> entry : cmdEnv.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    args.add("-e");
                    args.add(entry.getKey() + "=" + entry.getValue());
                }
            }
        }
        args.add(containerId.trim());
        args.add("sh");
        args.add("-c");
        args.add(command.getCommand());
        return Collections.unmodifiableList(args);
    }

    public List<String> buildForceRemoveArgs(String containerId) {
        if (containerId == null || containerId.trim().isEmpty()) {
            throw new IllegalArgumentException("containerId must not be blank");
        }
        List<String> args = new ArrayList<String>(4);
        args.add(dockerBinary);
        args.add("rm");
        args.add("-f");
        args.add(containerId.trim());
        return Collections.unmodifiableList(args);
    }

    public String getDockerBinary() {
        return dockerBinary;
    }

    public String getImage() {
        return image;
    }

    public String getMemory() {
        return memory;
    }

    public String getCpus() {
        return cpus;
    }

    public int getPidsLimit() {
        return pidsLimit;
    }

    public String getWorkingDirectory() {
        return workingDirectory;
    }

    public long getStartupTimeoutMillis() {
        return startupTimeoutMillis;
    }

    public long getCommandTimeoutMillis() {
        return commandTimeoutMillis;
    }

    public long getCleanupTimeoutMillis() {
        return cleanupTimeoutMillis;
    }

    public Map<String, String> getEnvironment() {
        return environment;
    }

    private static long positiveOrDefault(long candidate, long fallback) {
        return candidate > 0L ? candidate : fallback;
    }

    private static Long longValue(Object primary, Object secondary) {
        Long first = parseLong(primary);
        return first != null ? first : parseLong(secondary);
    }

    private static Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return Long.valueOf(((Number) value).longValue());
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(Long.parseLong(text));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Integer intValue(Object primary, Object secondary, String envValue) {
        Integer first = parseInt(primary);
        if (first != null) {
            return first;
        }
        Integer second = parseInt(secondary);
        if (second != null) {
            return second;
        }
        return parseInt(envValue);
    }

    private static Integer parseInt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return Integer.valueOf(((Number) value).intValue());
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(Integer.parseInt(text));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }

    public static final class Builder {
        private String dockerBinary = DEFAULT_DOCKER_BINARY;
        private String image = DEFAULT_IMAGE;
        private String memory = DEFAULT_MEMORY;
        private String cpus = DEFAULT_CPUS;
        private int pidsLimit = DEFAULT_PIDS_LIMIT;
        private String workingDirectory = DEFAULT_WORKING_DIRECTORY;
        private long startupTimeoutMillis = DEFAULT_STARTUP_TIMEOUT_MS;
        private long commandTimeoutMillis = DEFAULT_COMMAND_TIMEOUT_MS;
        private long cleanupTimeoutMillis = DEFAULT_CLEANUP_TIMEOUT_MS;
        private Map<String, String> environment;

        private Builder() {
        }

        public Builder dockerBinary(String dockerBinary) {
            this.dockerBinary = dockerBinary;
            return this;
        }

        public Builder image(String image) {
            this.image = image;
            return this;
        }

        public Builder memory(String memory) {
            this.memory = memory;
            return this;
        }

        public Builder cpus(String cpus) {
            this.cpus = cpus;
            return this;
        }

        public Builder pidsLimit(int pidsLimit) {
            if (pidsLimit <= 0) {
                throw new IllegalArgumentException("pidsLimit must be > 0");
            }
            this.pidsLimit = pidsLimit;
            return this;
        }

        public Builder workingDirectory(String workingDirectory) {
            this.workingDirectory = workingDirectory;
            return this;
        }

        public Builder startupTimeoutMillis(long startupTimeoutMillis) {
            this.startupTimeoutMillis = startupTimeoutMillis;
            return this;
        }

        public Builder commandTimeoutMillis(long commandTimeoutMillis) {
            this.commandTimeoutMillis = commandTimeoutMillis;
            return this;
        }

        public Builder cleanupTimeoutMillis(long cleanupTimeoutMillis) {
            this.cleanupTimeoutMillis = cleanupTimeoutMillis;
            return this;
        }

        public Builder environment(Map<String, String> environment) {
            this.environment = environment == null ? null : new LinkedHashMap<String, String>(environment);
            return this;
        }

        public DockerSandboxConfig build() {
            return new DockerSandboxConfig(this);
        }
    }
}
