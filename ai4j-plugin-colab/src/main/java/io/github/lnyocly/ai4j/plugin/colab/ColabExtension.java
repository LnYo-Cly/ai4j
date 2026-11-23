package io.github.lnyocly.ai4j.plugin.colab;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxArtifact;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxException;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxProvider;
import io.github.lnyocly.ai4j.extension.Ai4jExtension;
import io.github.lnyocly.ai4j.extension.Disposable;
import io.github.lnyocly.ai4j.extension.ExtensionCapability;
import io.github.lnyocly.ai4j.extension.ExtensionContext;
import io.github.lnyocly.ai4j.extension.ExtensionManifest;
import io.github.lnyocly.ai4j.extension.ServiceKey;
import io.github.lnyocly.ai4j.extension.prompt.ExtensionPromptResource;
import io.github.lnyocly.ai4j.extension.skill.ExtensionSkillResource;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolCall;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolExecutor;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolSpec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Cordis {@link Ai4jExtension} registering Google Colab kernel client services,
 * {@link SandboxProvider}, {@code colab_execute} tool, skill, prompt, and lifecycle cleanup.
 */
public class ColabExtension implements Ai4jExtension {

    public static final String EXTENSION_ID = "colab";
    public static final String TOOL_COLAB_EXECUTE = "colab_execute";
    public static final String NAMESPACED_TOOL_NAME = "plugin__" + EXTENSION_ID + "__" + TOOL_COLAB_EXECUTE;
    public static final String SKILL_NAME = "colab";
    public static final String PROMPT_NAME = "colab-notebook";

    public static final ServiceKey<ColabSandboxConfig> CONFIG_SERVICE_KEY =
            ServiceKey.of("colab.config", ColabSandboxConfig.class);
    public static final ServiceKey<ColabKernelClient> KERNEL_CLIENT_SERVICE_KEY =
            ServiceKey.of("colab.kernelClient", ColabKernelClient.class);
    public static final ServiceKey<SandboxProvider> SANDBOX_PROVIDER_SERVICE_KEY =
            ServiceKey.of("colab.sandboxProvider", SandboxProvider.class);

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<Map<String, Object>>() {
    };

    private static final String TOOL_INPUT_SCHEMA = "{"
            + "\"type\":\"object\","
            + "\"properties\":{"
            + "\"code\":{\"type\":\"string\",\"description\":\"Python source code to execute on the Colab kernel\"},"
            + "\"kernelId\":{\"type\":\"string\",\"description\":\"Optional existing Jupyter kernel id to reuse\"},"
            + "\"timeoutMs\":{\"type\":\"integer\",\"description\":\"Optional execution timeout in milliseconds\"}"
            + "},"
            + "\"required\":[\"code\"],"
            + "\"additionalProperties\":false"
            + "}";

    private final ColabSandboxConfig defaultConfig;
    private final Object kernelLock = new Object();
    private final AtomicReference<ColabKernelClient> activeClient = new AtomicReference<ColabKernelClient>();
    private final AtomicReference<String> managedKernelId = new AtomicReference<String>();
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    public ColabExtension() {
        this(null);
    }

    public ColabExtension(ColabSandboxConfig defaultConfig) {
        this.defaultConfig = defaultConfig;
    }

    @Override
    public ExtensionManifest manifest() {
        return ExtensionManifest.builder()
                .id(EXTENSION_ID)
                .name("Google Colab Runtime")
                .version("2.8.2-SNAPSHOT")
                .vendor("ai4j")
                .capability(ExtensionCapability.TOOL)
                .capability(ExtensionCapability.SKILL)
                .capability(ExtensionCapability.PROMPT)
                .permission("network:http")
                .permission("sandbox:colab")
                .configPrefix("ai.extensions.colab")
                .build();
    }

    @Override
    public void apply(ExtensionContext context) {
        stopped.set(false);
        ColabSandboxConfig config = resolveConfig(context);
        final ColabKernelClient client = new ColabKernelClient(config);
        final ColabSandboxProvider sandboxProvider = new ColabSandboxProvider(config);
        activeClient.set(client);

        context.provide(KERNEL_CLIENT_SERVICE_KEY, client);
        context.provide(SANDBOX_PROVIDER_SERVICE_KEY, sandboxProvider);
        context.effect(new Disposable() {
            @Override
            public void dispose() {
                cleanupRuntime(client);
            }
        });

        context.tools().register(
                ExtensionToolSpec.builder()
                        .name(TOOL_COLAB_EXECUTE)
                        .description("Execute Python code inside a Google Colab / Jupyter runtime kernel and extract structured outputs and artifacts.")
                        .inputSchema(TOOL_INPUT_SCHEMA)
                        .build(),
                new ExtensionToolExecutor() {
                    @Override
                    public String execute(ExtensionToolCall call) throws Exception {
                        return executeColabTool(client, call);
                    }
                }
        );

        context.skills().register(
                ExtensionSkillResource.builder()
                        .name(SKILL_NAME)
                        .description("Execute Python notebooks and extract rich outputs in Google Colab / Jupyter.")
                        .resourcePath("skills/colab/SKILL.md")
                        .build()
        );

        context.prompts().register(
                ExtensionPromptResource.builder()
                        .name(PROMPT_NAME)
                        .description("Prompt guidance for running reproducible Colab notebook cells.")
                        .resourcePath("prompts/colab-notebook.md")
                        .build()
        );
    }

    @Override
    public void onStop() {
        stopped.set(true);
        cleanupRuntime(activeClient.get());
    }

    public boolean isStopped() {
        return stopped.get();
    }

    private ColabSandboxConfig resolveConfig(ExtensionContext context) {
        if (context != null) {
            Optional<ColabSandboxConfig> provided = context.findService(CONFIG_SERVICE_KEY);
            if (provided.isPresent()) {
                return provided.get();
            }
        }
        return defaultConfig != null ? defaultConfig : ColabSandboxConfig.from(null, System.getenv());
    }

    private String executeColabTool(ColabKernelClient client, ExtensionToolCall call) throws Exception {
        Map<String, Object> args = parseArguments(call == null ? null : call.getArguments());
        String code = stringValue(args.get("code"));
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("colab_execute requires non-blank 'code' argument");
        }
        String kernelId = stringValue(args.get("kernelId"));
        if (kernelId == null || kernelId.trim().isEmpty()) {
            kernelId = ensureManagedKernel(client);
        }
        long timeoutMs = longValue(args.get("timeoutMs"), client.getConfig().getExecutionTimeoutMillis());

        ColabKernelClient.ExecutionResult result = client.executePython(kernelId, code, timeoutMs, null, null);
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("kernelId", result.getKernelId());
        payload.put("exitCode", Integer.valueOf(result.getExitCode()));
        payload.put("stdout", result.getStdout());
        payload.put("stderr", result.getStderr());
        payload.put("timedOut", Boolean.valueOf(result.isTimedOut()));
        payload.put("canceled", Boolean.valueOf(result.isCanceled()));
        payload.put("durationMillis", Long.valueOf(result.getDurationMillis()));
        payload.put("artifacts", formatArtifacts(result.getArtifacts()));
        return MAPPER.writeValueAsString(payload);
    }

    private String ensureManagedKernel(ColabKernelClient client) throws SandboxException {
        String existing = managedKernelId.get();
        if (existing != null && !existing.trim().isEmpty()) {
            return existing;
        }
        synchronized (kernelLock) {
            existing = managedKernelId.get();
            if (existing != null && !existing.trim().isEmpty()) {
                return existing;
            }
            ColabKernelClient.KernelInfo kernel = client.createKernel(client.getConfig().getKernelName());
            if (kernel == null || kernel.getId() == null || kernel.getId().trim().isEmpty()) {
                throw new IllegalStateException("Colab runtime did not return a kernel id");
            }
            managedKernelId.set(kernel.getId());
            return kernel.getId();
        }
    }

    private void cleanupRuntime(ColabKernelClient client) {
        stopped.set(true);
        String kernelId = managedKernelId.getAndSet(null);
        if (client != null) {
            if (kernelId != null && !kernelId.trim().isEmpty()) {
                try {
                    client.deleteKernel(kernelId);
                } catch (SandboxException | RuntimeException ignored) {
                }
            }
            client.close();
        }
        activeClient.compareAndSet(client, null);
    }

    private static List<Map<String, Object>> formatArtifacts(List<SandboxArtifact> artifacts) {
        if (artifacts == null || artifacts.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>(artifacts.size());
        for (SandboxArtifact artifact : artifacts) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("artifactId", artifact.getArtifactId());
            item.put("name", artifact.getName());
            item.put("path", artifact.getPath());
            item.put("mimeType", artifact.getMimeType());
            item.put("sizeBytes", artifact.getSizeBytes());
            item.put("sha256", artifact.getSha256());
            list.add(item);
        }
        return list;
    }

    private static Map<String, Object> parseArguments(String rawArguments) throws Exception {
        if (rawArguments == null || rawArguments.trim().isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> parsed = MAPPER.readValue(rawArguments, MAP_TYPE);
        return parsed == null ? Collections.<String, Object>emptyMap() : parsed;
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static long longValue(Object value, long fallback) {
        if (value instanceof Number) {
            long num = ((Number) value).longValue();
            return num > 0L ? num : fallback;
        }
        if (value instanceof String) {
            try {
                long num = Long.parseLong(((String) value).trim());
                return num > 0L ? num : fallback;
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }
}
