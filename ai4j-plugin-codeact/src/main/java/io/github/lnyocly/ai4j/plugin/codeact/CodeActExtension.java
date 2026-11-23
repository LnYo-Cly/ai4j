package io.github.lnyocly.ai4j.plugin.codeact;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.lnyocly.ai4j.agent.codeact.CodeExecutionRequest;
import io.github.lnyocly.ai4j.agent.codeact.CodeExecutionResult;
import io.github.lnyocly.ai4j.agent.codeact.CodeExecutor;
import io.github.lnyocly.ai4j.agent.codeact.CodeExecutorProvider;
import io.github.lnyocly.ai4j.agent.codeact.GraalVmCodeExecutor;
import io.github.lnyocly.ai4j.agent.codeact.NashornCodeExecutor;
import io.github.lnyocly.ai4j.agent.dynamicworkflow.DynamicWorkflowExecutorProvider;
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
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Cordis {@link Ai4jExtension} registering CodeAct and DynamicWorkflow SPI providers,
 * {@link CodeExecutor} instances via {@link ServiceKey}, and the {@code codeact_execute} tool.
 */
public final class CodeActExtension implements Ai4jExtension {

    public static final String EXTENSION_ID = "codeact";
    public static final String TOOL_CODEACT_EXECUTE = "codeact_execute";
    public static final String NAMESPACED_TOOL_NAME = "plugin__" + EXTENSION_ID + "__" + TOOL_CODEACT_EXECUTE;

    public static final ServiceKey<CodeExecutorProvider> CODE_EXECUTOR_PROVIDER_KEY =
            ServiceKey.of("ai4j.codeact.codeExecutorProvider", CodeExecutorProvider.class);
    public static final ServiceKey<DynamicWorkflowExecutorProvider> DYNAMIC_WORKFLOW_PROVIDER_KEY =
            ServiceKey.of("ai4j.codeact.dynamicWorkflowExecutorProvider", DynamicWorkflowExecutorProvider.class);
    public static final ServiceKey<CodeExecutor> DEFAULT_CODE_EXECUTOR_KEY =
            ServiceKey.of("ai4j.codeact.codeExecutor", CodeExecutor.class);
    public static final ServiceKey<CodeExecutor> NASHORN_CODE_EXECUTOR_KEY =
            ServiceKey.of("ai4j.codeact.nashornCodeExecutor", CodeExecutor.class);
    public static final ServiceKey<CodeExecutor> GRAALVM_CODE_EXECUTOR_KEY =
            ServiceKey.of("ai4j.codeact.graalvmCodeExecutor", CodeExecutor.class);

    private static final String VERSION = "2.8.2-SNAPSHOT";
    private static final TypeReference<Map<String, Object>> MAP_TYPE =
            new TypeReference<Map<String, Object>>() {
            };

    private static final String TOOL_INPUT_SCHEMA = "{"
            + "\"type\":\"object\","
            + "\"properties\":{"
            + "\"language\":{\"type\":\"string\",\"description\":\"Target language ('javascript' or 'python')\"},"
            + "\"code\":{\"type\":\"string\",\"description\":\"Source code to execute\"},"
            + "\"timeoutMs\":{\"type\":\"integer\",\"description\":\"Optional execution timeout in milliseconds\"}"
            + "},"
            + "\"required\":[\"code\"],"
            + "\"additionalProperties\":false"
            + "}";

    private final AtomicInteger activeScopes = new AtomicInteger(0);
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    @Override
    public ExtensionManifest manifest() {
        return ExtensionManifest.builder()
                .id(EXTENSION_ID)
                .name("AI4J CodeAct & DynamicWorkflow")
                .version(VERSION)
                .vendor("ai4j")
                .capability(ExtensionCapability.TOOL)
                .permission("codeact.exec")
                .configPrefix("ai4j.extensions.codeact")
                .build();
    }

    @Override
    public void apply(ExtensionContext context) {
        if (context == null) {
            throw new IllegalArgumentException("extension context must not be null");
        }
        stopped.set(false);
        final DefaultCodeExecutorProvider codeExecutorProvider = new DefaultCodeExecutorProvider();
        final NashornDynamicWorkflowExecutorProvider dynamicWorkflowProvider = new NashornDynamicWorkflowExecutorProvider();
        final NashornCodeExecutor nashornExecutor = new NashornCodeExecutor();
        final GraalVmCodeExecutor graalVmExecutor = new GraalVmCodeExecutor();
        final CodeExecutor defaultExecutor = codeExecutorProvider.create();
        final AtomicBoolean scopeActive = new AtomicBoolean(true);
        activeScopes.incrementAndGet();

        context.provide(CODE_EXECUTOR_PROVIDER_KEY, codeExecutorProvider);
        context.provide(DYNAMIC_WORKFLOW_PROVIDER_KEY, dynamicWorkflowProvider);
        context.provide(DEFAULT_CODE_EXECUTOR_KEY, defaultExecutor);
        context.provide(NASHORN_CODE_EXECUTOR_KEY, nashornExecutor);
        context.provide(GRAALVM_CODE_EXECUTOR_KEY, graalVmExecutor);

        context.tools().register(toolSpec(), new ExtensionToolExecutor() {
            @Override
            public String execute(ExtensionToolCall call) throws Exception {
                if (stopped.get() || !scopeActive.get()) {
                    throw new IllegalStateException("CodeAct extension is not active");
                }
                return executeTool(call, nashornExecutor, graalVmExecutor, defaultExecutor);
            }
        });

        context.effect(new Disposable() {
            @Override
            public void dispose() {
                if (scopeActive.compareAndSet(true, false)) {
                    activeScopes.decrementAndGet();
                }
            }
        });
    }

    @Override
    public void onStop() {
        stopped.set(true);
        activeScopes.set(0);
    }

    public boolean isActive() {
        return !stopped.get() && activeScopes.get() > 0;
    }

    public static ExtensionToolSpec toolSpec() {
        return ExtensionToolSpec.builder()
                .name(TOOL_CODEACT_EXECUTE)
                .description("Execute sandboxed JavaScript (Nashorn ES5) or Python (GraalPy) code.")
                .inputSchema(TOOL_INPUT_SCHEMA)
                .build();
    }

    private String executeTool(ExtensionToolCall call,
                               NashornCodeExecutor nashornExecutor,
                               GraalVmCodeExecutor graalVmExecutor,
                               CodeExecutor defaultExecutor) throws Exception {
        Map<String, Object> args = parseArguments(call == null ? null : call.getArguments());
        Object rawCode = args.get("code");
        String code = rawCode == null ? null : String.valueOf(rawCode);
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("codeact_execute requires non-blank 'code' argument");
        }
        Object rawLang = args.get("language");
        String language = rawLang == null ? null : String.valueOf(rawLang).trim();
        CodeExecutor executor = selectExecutor(language, nashornExecutor, graalVmExecutor, defaultExecutor);
        Long timeoutMs = parseTimeout(args.get("timeoutMs"));

        CodeExecutionResult result = executor.execute(CodeExecutionRequest.builder()
                .language(language == null || language.isEmpty() ? executor.getLanguage() : language)
                .code(code)
                .timeoutMs(timeoutMs)
                .build());

        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("success", Boolean.valueOf(result != null && result.isSuccess()));
        payload.put("result", result == null ? null : result.getResult());
        payload.put("stdout", result == null ? null : result.getStdout());
        payload.put("error", result == null ? "no execution result" : result.getError());
        return JsonCodec.toJson(payload);
    }

    private CodeExecutor selectExecutor(String language,
                                        NashornCodeExecutor nashornExecutor,
                                        GraalVmCodeExecutor graalVmExecutor,
                                        CodeExecutor defaultExecutor) {
        if (language == null || language.isEmpty()) {
            return defaultExecutor;
        }
        String normalized = language.toLowerCase(Locale.ROOT);
        if ("javascript".equals(normalized) || "js".equals(normalized) || "ecmascript".equals(normalized)) {
            return nashornExecutor;
        }
        if ("python".equals(normalized) || "py".equals(normalized) || "python3".equals(normalized)) {
            return graalVmExecutor;
        }
        return defaultExecutor;
    }

    private static Map<String, Object> parseArguments(String rawArguments) {
        if (rawArguments == null || rawArguments.trim().isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> parsed = JsonCodec.fromJsonLenient(rawArguments, MAP_TYPE);
        return parsed == null ? Collections.<String, Object>emptyMap() : parsed;
    }

    private static Long parseTimeout(Object value) {
        if (value instanceof Number) {
            long ms = ((Number) value).longValue();
            return ms > 0L ? Long.valueOf(ms) : null;
        }
        if (value instanceof String) {
            try {
                long ms = Long.parseLong(((String) value).trim());
                return ms > 0L ? Long.valueOf(ms) : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
