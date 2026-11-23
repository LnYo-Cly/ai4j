package io.github.lnyocly.ai4j.plugin.sandbox.docker;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxCommand;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxProvider;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxResult;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSession;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSpec;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxStatus;
import io.github.lnyocly.ai4j.codec.JsonCodec;
import io.github.lnyocly.ai4j.extension.Ai4jExtension;
import io.github.lnyocly.ai4j.extension.Disposable;
import io.github.lnyocly.ai4j.extension.ExtensionCapability;
import io.github.lnyocly.ai4j.extension.ExtensionContext;
import io.github.lnyocly.ai4j.extension.ExtensionManifest;
import io.github.lnyocly.ai4j.extension.ServiceKey;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolCall;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolExecutor;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolSpec;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Cordis {@link Ai4jExtension} registering Docker sandbox configuration, {@link DockerProcessRunner},
 * {@link SandboxProvider}, {@code docker_execute} tool, and {@link Disposable} container cleanup.
 */
public final class DockerSandboxExtension implements Ai4jExtension {

    public static final String EXTENSION_ID = "sandbox-docker";
    public static final String TOOL_DOCKER_EXECUTE = "docker_execute";
    public static final String NAMESPACED_TOOL_NAME = "plugin__" + EXTENSION_ID + "__" + TOOL_DOCKER_EXECUTE;

    public static final ServiceKey<DockerSandboxConfig> CONFIG_SERVICE_KEY =
            ServiceKey.of("dockerSandbox.config", DockerSandboxConfig.class);
    public static final ServiceKey<DockerProcessRunner> PROCESS_RUNNER_SERVICE_KEY =
            ServiceKey.of("dockerSandbox.processRunner", DockerProcessRunner.class);
    public static final ServiceKey<SandboxProvider> SANDBOX_PROVIDER_SERVICE_KEY =
            ServiceKey.of("dockerSandbox.sandboxProvider", SandboxProvider.class);

    private static final String VERSION = "2.8.2-SNAPSHOT";
    private static final TypeReference<Map<String, Object>> MAP_TYPE =
            new TypeReference<Map<String, Object>>() {
            };

    private static final String TOOL_INPUT_SCHEMA = "{"
            + "\"type\":\"object\","
            + "\"properties\":{"
            + "\"command\":{\"type\":\"string\",\"description\":\"Shell command to execute inside an isolated Docker container\"},"
            + "\"image\":{\"type\":\"string\",\"description\":\"Optional container image override\"},"
            + "\"timeoutMs\":{\"type\":\"integer\",\"description\":\"Optional command timeout in milliseconds\"}"
            + "},"
            + "\"required\":[\"command\"],"
            + "\"additionalProperties\":false"
            + "}";

    private final DockerSandboxConfig defaultConfig;
    private final DockerProcessRunner defaultRunner;
    private final Set<SandboxSession> activeSessions = Collections.synchronizedSet(new LinkedHashSet<SandboxSession>());
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    public DockerSandboxExtension() {
        this(null, null);
    }

    public DockerSandboxExtension(DockerSandboxConfig defaultConfig) {
        this(defaultConfig, null);
    }

    public DockerSandboxExtension(DockerSandboxConfig defaultConfig, DockerProcessRunner defaultRunner) {
        this.defaultConfig = defaultConfig;
        this.defaultRunner = defaultRunner;
    }

    @Override
    public ExtensionManifest manifest() {
        return ExtensionManifest.builder()
                .id(EXTENSION_ID)
                .name("Docker Sandbox Runtime")
                .version(VERSION)
                .vendor("ai4j")
                .capability(ExtensionCapability.TOOL)
                .permission("sandbox:docker")
                .configPrefix("ai4j.extensions.sandbox.docker")
                .build();
    }

    @Override
    public void apply(ExtensionContext context) {
        if (context == null) {
            throw new IllegalArgumentException("extension context must not be null");
        }
        stopped.set(false);
        final DockerSandboxConfig config = resolveConfig(context);
        final DockerProcessRunner runner = resolveRunner(context);
        final DockerSandboxProvider provider = new DockerSandboxProvider(config, runner);
        final AtomicBoolean scopeActive = new AtomicBoolean(true);
        final Object scopeLock = new Object();
        final AtomicReference<SandboxSession> scopeSession = new AtomicReference<SandboxSession>();

        context.provide(CONFIG_SERVICE_KEY, config);
        context.provide(PROCESS_RUNNER_SERVICE_KEY, runner);
        context.provide(SANDBOX_PROVIDER_SERVICE_KEY, provider);
        context.effect(new Disposable() {
            @Override
            public void dispose() {
                scopeActive.set(false);
                closeSession(scopeSession.getAndSet(null));
            }
        });

        context.tools().register(toolSpec(), new ExtensionToolExecutor() {
            @Override
            public String execute(ExtensionToolCall call) throws Exception {
                if (stopped.get() || !scopeActive.get()) {
                    throw new IllegalStateException("Docker sandbox extension is stopped");
                }
                return executeDockerTool(provider, config, scopeSession, scopeLock, call);
            }
        });
    }

    @Override
    public void onStop() {
        stopped.set(true);
        SandboxSession[] snapshot;
        synchronized (activeSessions) {
            snapshot = activeSessions.toArray(new SandboxSession[0]);
            activeSessions.clear();
        }
        for (SandboxSession session : snapshot) {
            closeQuietly(session);
        }
    }

    public boolean isStopped() {
        return stopped.get();
    }

    public static ExtensionToolSpec toolSpec() {
        return ExtensionToolSpec.builder()
                .name(TOOL_DOCKER_EXECUTE)
                .description("Execute a shell command inside a hardened Docker sandbox container.")
                .inputSchema(TOOL_INPUT_SCHEMA)
                .build();
    }

    private DockerSandboxConfig resolveConfig(ExtensionContext context) {
        Optional<DockerSandboxConfig> provided = context.findService(CONFIG_SERVICE_KEY);
        if (provided.isPresent()) {
            return provided.get();
        }
        return defaultConfig != null ? defaultConfig : DockerSandboxConfig.from(null, System.getenv());
    }

    private DockerProcessRunner resolveRunner(ExtensionContext context) {
        Optional<DockerProcessRunner> provided = context.findService(PROCESS_RUNNER_SERVICE_KEY);
        if (provided.isPresent()) {
            return provided.get();
        }
        return defaultRunner != null ? defaultRunner : new ProcessBuilderDockerProcessRunner();
    }

    private String executeDockerTool(DockerSandboxProvider provider,
                                     DockerSandboxConfig config,
                                     AtomicReference<SandboxSession> scopeSession,
                                     Object scopeLock,
                                     ExtensionToolCall call) throws Exception {
        Map<String, Object> args = parseArguments(call == null ? null : call.getArguments());
        Object rawCommand = args.get("command");
        String commandText = rawCommand == null ? null : String.valueOf(rawCommand);
        if (commandText == null || commandText.trim().isEmpty()) {
            throw new IllegalArgumentException("docker_execute requires non-blank 'command' argument");
        }
        Object rawImage = args.get("image");
        String image = rawImage == null ? null : String.valueOf(rawImage).trim();
        Long timeoutMs = parseLong(args.get("timeoutMs"));

        SandboxSession session = ensureScopeSession(provider, config, scopeSession, scopeLock, image);
        SandboxCommand.Builder cmdBuilder = SandboxCommand.builder().command(commandText);
        if (timeoutMs != null && timeoutMs.longValue() > 0L) {
            cmdBuilder.timeoutMillis(timeoutMs);
        }
        SandboxResult result = session.execute(cmdBuilder.build());
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("sessionId", session.getSessionId());
        payload.put("exitCode", result.getExitCode());
        payload.put("stdout", result.getStdout());
        payload.put("stderr", result.getStderr());
        payload.put("timedOut", Boolean.valueOf(result.isTimedOut()));
        payload.put("durationMillis", result.getDurationMillis());
        return JsonCodec.toJson(payload);
    }

    private SandboxSession ensureScopeSession(DockerSandboxProvider provider,
                                              DockerSandboxConfig config,
                                              AtomicReference<SandboxSession> scopeSession,
                                              Object scopeLock,
                                              String imageOverride) throws Exception {
        SandboxSession existing = scopeSession.get();
        if (existing != null && existing.getStatus() == SandboxStatus.RUNNING) {
            return existing;
        }
        synchronized (scopeLock) {
            existing = scopeSession.get();
            if (existing != null && existing.getStatus() == SandboxStatus.RUNNING) {
                return existing;
            }
            SandboxSpec spec = SandboxSpec.builder()
                    .providerId(DockerSandboxProvider.PROVIDER_ID)
                    .image(imageOverride != null && !imageOverride.isEmpty() ? imageOverride : config.getImage())
                    .build();
            SandboxSession created = provider.createSession(spec);
            scopeSession.set(created);
            activeSessions.add(created);
            return created;
        }
    }

    private void closeSession(SandboxSession session) {
        if (session == null) {
            return;
        }
        activeSessions.remove(session);
        closeQuietly(session);
    }

    private static void closeQuietly(SandboxSession session) {
        try {
            session.close();
        } catch (Exception ignored) {
        }
    }

    private static Map<String, Object> parseArguments(String rawArguments) {
        if (rawArguments == null || rawArguments.trim().isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> parsed = JsonCodec.fromJsonLenient(rawArguments, MAP_TYPE);
        return parsed == null ? Collections.<String, Object>emptyMap() : parsed;
    }

    private static Long parseLong(Object value) {
        if (value instanceof Number) {
            long num = ((Number) value).longValue();
            return num > 0L ? Long.valueOf(num) : null;
        }
        if (value instanceof String) {
            try {
                long num = Long.parseLong(((String) value).trim());
                return num > 0L ? Long.valueOf(num) : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
