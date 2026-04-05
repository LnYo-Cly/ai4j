package io.github.lnyocly.ai4j.agent.codeact;

import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.SimpleScriptContext;
import java.io.StringWriter;
import java.util.List;
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
        ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");
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
        bindings.put("__toolBridge", new ToolBridge(request.getToolExecutor(), request.getUser()));
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
            return CodeExecutionResult.builder()
                    .stdout(stdout.toString())
                    .error("code execution timeout")
                    .build();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            log.warn("Nashorn execution failed", cause);
            return CodeExecutionResult.builder()
                    .stdout(stdout.toString())
                    .error(String.valueOf(cause.getMessage()))
                    .build();
        } catch (Throwable t) {
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

    public static class ToolBridge {
        private final ToolExecutor toolExecutor;
        private final String user;

        private ToolBridge(ToolExecutor toolExecutor, String user) {
            this.toolExecutor = toolExecutor;
            this.user = user;
        }

        public String call(String name, String arguments) throws Exception {
            if (toolExecutor == null) {
                throw new IllegalStateException("toolExecutor is required");
            }
            String payload = arguments == null || arguments.trim().isEmpty() ? "{}" : arguments;
            AgentToolCall call = AgentToolCall.builder()
                    .name(resolveName(name))
                    .arguments(payload)
                    .build();
            return toolExecutor.execute(call);
        }

        private String resolveName(String name) {
            if (user == null || user.trim().isEmpty()) {
                return name;
            }
            return "user_" + user + "_tool_" + name;
        }
    }
}
