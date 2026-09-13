package io.github.lnyocly.ai4j.agent.codeact;

import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.AgentToolExecution;
import io.github.lnyocly.ai4j.agent.tool.AgentToolExecutionStatus;
import io.github.lnyocly.ai4j.agent.tool.AgentToolResult;
import io.github.lnyocly.ai4j.agent.tool.AsyncToolExecutors;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.SimpleScriptContext;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

public class NashornCodeExecutor implements CodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(NashornCodeExecutor.class);
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");
    private static final long DEFAULT_TIMEOUT_MS = 8000L;

    @Override
    public CodeExecutionResult execute(CodeExecutionRequest request) {
        if (request == null || request.getCode() == null) {
            return CodeExecutionResult.builder().error("code is required").build();
        }

        String language = normalizeLanguage(request.getLanguage());
        if (!"javascript".equals(language)) {
            return CodeExecutionResult.builder()
                    .error("unsupported language: " + request.getLanguage() + ", only javascript is enabled")
                    .build();
        }

        return executeJavaScript(request);
    }

    private String normalizeLanguage(String language) {
        if (language == null || language.trim().isEmpty()) {
            return "javascript";
        }
        String value = language.trim().toLowerCase();
        if ("js".equals(value) || "ecmascript".equals(value)) {
            return "javascript";
        }
        return value;
    }

    private CodeExecutionResult executeJavaScript(CodeExecutionRequest request) {
        ScriptEngine engine = createHardenedNashornEngine();
        if (engine == null) {
            return CodeExecutionResult.builder()
                    .error("Nashorn engine not found. Use JDK 8 or add nashorn engine dependency.")
                    .build();
        }

        StringWriter stdout = new StringWriter();
        StringWriter stderr = new StringWriter();
        ScriptContext context = new SimpleScriptContext();
        context.setWriter(stdout);
        context.setErrorWriter(stderr);
        Bindings bindings = engine.createBindings();
        ToolBridge toolBridge = new ToolBridge(request.getToolExecutor(), request.getUser(), request.getParentCallId());
        bindings.put("__toolBridge", toolBridge);
        context.setBindings(bindings, ScriptContext.ENGINE_SCOPE);

        String script = buildPrelude(request.getToolNames()) + "\n" + wrapCode(request.getCode());
        Long timeoutMs = request.getTimeoutMs();
        long timeout = timeoutMs == null ? DEFAULT_TIMEOUT_MS : timeoutMs;

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Object> future = executor.submit(new Callable<Object>() {
                @Override
                public Object call() throws Exception {
                    return engine.eval(script, context);
                }
            });

            Object value = future.get(timeout, TimeUnit.MILLISECONDS);
            CodeExecutionResult pending = toolBridge.pendingResult(stdout.toString());
            if (pending != null) {
                return pending;
            }
            Object resultValue = bindings.get("__codeact_result");
            if (resultValue == null) {
                resultValue = value;
            }

            String error = trimError(stderr.toString());
            return CodeExecutionResult.builder()
                    .stdout(stdout.toString())
                    .result(resultValue == null ? null : String.valueOf(resultValue))
                    .error(error)
                    .build();
        } catch (TimeoutException e) {
            CodeExecutionResult pending = toolBridge.pendingResult(stdout.toString());
            if (pending != null) {
                return pending;
            }
            return CodeExecutionResult.builder()
                    .stdout(stdout.toString())
                    .error("code execution timeout")
                    .build();
        } catch (ExecutionException e) {
            CodeExecutionResult pending = toolBridge.pendingResult(stdout.toString());
            if (pending != null) {
                return pending;
            }
            Throwable cause = e.getCause() == null ? e : e.getCause();
            log.warn("Nashorn execution failed", cause);
            return CodeExecutionResult.builder()
                    .stdout(stdout.toString())
                    .error(String.valueOf(cause.getMessage()))
                    .build();
        } catch (Throwable t) {
            CodeExecutionResult pending = toolBridge.pendingResult(stdout.toString());
            if (pending != null) {
                return pending;
            }
            log.warn("Nashorn execution failed", t);
            return CodeExecutionResult.builder()
                    .stdout(stdout.toString())
                    .error(String.valueOf(t.getMessage()))
                    .build();
        } finally {
            executor.shutdownNow();
        }
    }

    private String buildPrelude(List<String> toolNames) {
        StringBuilder builder = new StringBuilder();
        builder.append("var __codeact_result = null;\n");
        builder.append("try {\n");
        builder.append("  this.Java = undefined; this.Packages = undefined; this.java = undefined; this.javax = undefined;\n");
        builder.append("  this.org = undefined; this.com = undefined; this.load = undefined; this.loadWithNewGlobal = undefined;\n");
        builder.append("  this.exit = undefined; this.quit = undefined;\n");
        builder.append("} catch (__codeact_harden_error) {}\n");
        builder.append("function __parseIfJson(value) {\n");
        builder.append("  if (value == null) { return value; }\n");
        builder.append("  var text = null;\n");
        builder.append("  if (typeof value === 'string') { text = value; }\n");
        builder.append("  else { try { text = String(value); } catch (e) { return value; } }\n");
        builder.append("  text = text == null ? '' : text.trim();\n");
        builder.append("  if (text.length < 2) { return value; }\n");
        builder.append("  var quotedJson = text.charAt(0) === '\"' && text.charAt(text.length - 1) === '\"';\n");
        builder.append("  var objJson = text.charAt(0) === '{' && text.charAt(text.length - 1) === '}';\n");
        builder.append("  var arrJson = text.charAt(0) === '[' && text.charAt(text.length - 1) === ']';\n");
        builder.append("  if (!quotedJson && !objJson && !arrJson) { return value; }\n");
        builder.append("  try { return JSON.parse(text); } catch (e) { return value; }\n");
        builder.append("}\n");
        builder.append("function __normalizeToolResult(value) {\n");
        builder.append("  var current = value;\n");
        builder.append("  for (var i = 0; i < 3; i++) {\n");
        builder.append("    var next = __parseIfJson(current);\n");
        builder.append("    if (next === current) { break; }\n");
        builder.append("    current = next;\n");
        builder.append("  }\n");
        builder.append("  return current;\n");
        builder.append("}\n");
        builder.append("function callTool(name, args) {\n");
        builder.append("  var payload = args == null ? '{}': (typeof args === 'string' ? args : JSON.stringify(args));\n");
        builder.append("  var raw = __toolBridge.call(String(name), payload);\n");
        builder.append("  return __normalizeToolResult(raw);\n");
        builder.append("}\n");

        if (toolNames != null) {
            for (String name : toolNames) {
                if (name != null && IDENTIFIER.matcher(name).matches()) {
                    builder.append("function ").append(name).append("(args) {\n")
                            .append("  return callTool(\"")
                            .append(escapeJs(name)).append("\", args);\n")
                            .append("}\n");
                }
            }
        }
        return builder.toString();
    }

    private String wrapCode(String code) {
        StringBuilder builder = new StringBuilder();
        builder.append("var __codeact_return = (function __codeact_main__() {\n");
        builder.append(code).append("\n");
        builder.append("})();\n");
        builder.append("if (__codeact_result == null && typeof __codeact_return !== 'undefined') {\n");
        builder.append("  __codeact_result = __codeact_return;\n");
        builder.append("}\n");
        builder.append("__codeact_result;\n");
        return builder.toString();
    }

    private String escapeJs(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String trimError(String error) {
        if (error == null || error.trim().isEmpty()) {
            return null;
        }
        return error.trim();
    }

    /**
     * Create a Nashorn engine with Java interop disabled via {@code --no-java --no-java-import}
     * command-line flags. Falls back to the plain {@link ScriptEngineManager} lookup only when the
     * factory API is unavailable (non-JDK8 Nashorn distributions), in which case the runtime
     * hardening prelude ({@code this.Java = undefined; ...}) still neutralises the most common
     * RCE vectors.
     */
    private ScriptEngine createHardenedNashornEngine() {
        ScriptEngine hardened = createNashornEngineWithOptions(new String[]{"--no-java", "--no-java-import"});
        if (hardened != null) {
            return hardened;
        }
        return new ScriptEngineManager().getEngineByName("nashorn");
    }

    private ScriptEngine createNashornEngineWithOptions(String[] args) {
        String[] factoryClassNames = new String[]{
                "org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory",
                "jdk.nashorn.api.scripting.NashornScriptEngineFactory"
        };
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        for (String factoryClassName : factoryClassNames) {
            try {
                Class<?> factoryClass = Class.forName(factoryClassName, true, loader);
                Object factory = factoryClass.getDeclaredConstructor().newInstance();
                Method method = factoryClass.getMethod("getScriptEngine", String[].class);
                Object engine = method.invoke(factory, new Object[]{args});
                if (engine instanceof ScriptEngine) {
                    return (ScriptEngine) engine;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    public static class ToolBridge {
        private final ToolExecutor toolExecutor;
        private final String user;
        private final String parentCallId;
        private int invocation;
        private AgentToolResult pendingResult;

        private ToolBridge(ToolExecutor toolExecutor, String user, String parentCallId) {
            this.toolExecutor = toolExecutor;
            this.user = user;
            this.parentCallId = parentCallId;
        }

        public String call(String name, String arguments) throws Exception {
            if (toolExecutor == null) {
                throw new IllegalStateException("toolExecutor is required");
            }
            String payload = arguments == null || arguments.trim().isEmpty() ? "{}" : arguments;
            AgentToolCall call = AgentToolCall.builder()
                    .name(resolveName(name))
                    .arguments(payload)
                    .callId(nextCallId())
                    .metadata(parentMetadata())
                    .build();
            AgentToolExecution execution = AsyncToolExecutors.start(toolExecutor, call);
            if (execution == null) {
                return null;
            }
            if (execution.isPending()) {
                pendingResult = normalize(call, execution.getInitialResult());
                throw new CodeActPendingToolException(call.getCallId());
            }
            AgentToolResult result = execution.await();
            return result == null ? null : result.getOutput();
        }

        private AgentToolResult normalize(AgentToolCall call, AgentToolResult source) {
            AgentToolResult result = source == null ? new AgentToolResult() : source;
            if (result.getName() == null) {
                result.setName(call.getName());
            }
            if (result.getCallId() == null) {
                result.setCallId(call.getCallId());
            }
            if (result.getStatus() == null) {
                result.setStatus(AgentToolExecutionStatus.WAITING);
            }
            return result;
        }

        private CodeExecutionResult pendingResult(String stdout) {
            if (pendingResult == null) {
                return null;
            }
            return CodeExecutionResult.builder()
                    .stdout(stdout)
                    .result(pendingResult.getOutput())
                    .error(pendingResult.getError())
                    .status(AgentToolExecutionStatus.WAITING)
                    .operationId(pendingResult.getOperationId())
                    .waitId(pendingResult.getWaitId())
                    .pendingToolCallId(pendingResult.getCallId())
                    .parentCallId(parentCallId)
                    .build();
        }

        private String nextCallId() {
            String suffix = String.valueOf(invocation++);
            return parentCallId == null || parentCallId.trim().isEmpty()
                    ? "codeact_tool_" + suffix
                    : parentCallId + ":tool:" + suffix;
        }

        private Map<String, Object> parentMetadata() {
            Map<String, Object> metadata = new LinkedHashMap<String, Object>();
            if (parentCallId != null && !parentCallId.trim().isEmpty()) {
                metadata.put(AgentToolCall.METADATA_KEY_PARENT_CALL_ID, parentCallId);
            }
            return metadata;
        }

        private String resolveName(String name) {
            if (user == null || user.trim().isEmpty()) {
                return name;
            }
            return "user_" + user + "_tool_" + name;
        }
    }
}
