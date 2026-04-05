package io.github.lnyocly.ai4j.agent.codeact;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

public class GraalVmCodeExecutor implements CodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(GraalVmCodeExecutor.class);
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");
    private static final long DEFAULT_TIMEOUT_MS = 8000L;

    public GraalVmCodeExecutor() {
    }

    // Keep this constructor for compatibility with existing caller code.
    public GraalVmCodeExecutor(String ignored) {
    }

    @Override
    public CodeExecutionResult execute(CodeExecutionRequest request) {
        if (request == null || request.getCode() == null) {
            return CodeExecutionResult.builder().error("code is required").build();
        }

        String language = normalizeLanguage(request.getLanguage());
        if (!"python".equals(language)) {
            return CodeExecutionResult.builder()
                    .error("unsupported language: " + request.getLanguage() + ", only python is enabled")
                    .build();
        }

        CodeExecutionResult pyResult = executePythonWithGraalPy(request);
        if (pyResult == null) {
            return CodeExecutionResult.builder()
                    .error("Python engine not found (GraalPy). Ensure GraalPy runtime is available.")
                    .build();
        }
        return pyResult;
    }

    private String normalizeLanguage(String language) {
        if (language == null || language.trim().isEmpty()) {
            return "python";
        }
        String value = language.trim().toLowerCase();
        if ("py".equals(value) || "python3".equals(value)) {
            return "python";
        }
        return value;
    }

    private String buildPythonPrelude(List<String> toolNames) {
        StringBuilder builder = new StringBuilder();
        builder.append("__codeact_result = None\n");
        builder.append("def callTool(name, args=None, **kwargs):\n");
        builder.append("    if args is None and kwargs:\n");
        builder.append("        return tools.call(name, kwargs)\n");
        builder.append("    if kwargs and isinstance(args, dict):\n");
        builder.append("        merged = dict(args)\n");
        builder.append("        merged.update(kwargs)\n");
        builder.append("        return tools.call(name, merged)\n");
        builder.append("    return tools.call(name, args)\n");
        if (toolNames != null) {
            for (String name : toolNames) {
                if (name != null && IDENTIFIER.matcher(name).matches()) {
                    builder.append("def ").append(name).append("(args=None, **kwargs):\n")
                            .append("    if args is None and kwargs:\n")
                            .append("        return tools.call(\"")
                            .append(escapePython(name)).append("\", kwargs)\n")
                            .append("    if kwargs and isinstance(args, dict):\n")
                            .append("        merged = dict(args)\n")
                            .append("        merged.update(kwargs)\n")
                            .append("        return tools.call(\"")
                            .append(escapePython(name)).append("\", merged)\n")
                            .append("    return tools.call(\"")
                            .append(escapePython(name)).append("\", args)\n");
                }
            }
        }
        return builder.toString();
    }

    private String wrapPythonCode(String code) {
        String[] lines = code.split("\r?\n");
        StringBuilder builder = new StringBuilder();
        builder.append("def __codeact_main():\n");
        builder.append("    global __codeact_result\n");
        if (lines.length == 0) {
            builder.append("    pass\n");
        } else {
            for (String line : lines) {
                builder.append("    ").append(line).append("\n");
            }
        }
        builder.append("__codeact_tmp = __codeact_main()\n");
        builder.append("if __codeact_tmp is not None:\n");
        builder.append("    __codeact_result = __codeact_tmp\n");
        return builder.toString();
    }

    private String escapePython(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String trimError(String error) {
        if (error == null || error.trim().isEmpty()) {
            return null;
        }
        return error.trim();
    }

    private CodeExecutionResult executePythonWithGraalPy(CodeExecutionRequest request) {
        ByteArrayOutputStream stdoutBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream stderrBytes = new ByteArrayOutputStream();
        ToolExecutor toolExecutor = request.getToolExecutor();
        ToolBridge toolBridge = new ToolBridge(toolExecutor, request.getUser());

        ProxyExecutable callTool = new ProxyExecutable() {
            @Override
            public Object execute(Value... args) {
                try {
                    String name = args != null && args.length > 0 && args[0] != null ? args[0].asString() : null;
                    Object payload = null;
                    if (args != null && args.length > 1 && args[1] != null) {
                        Value value = args[1];
                        if (!value.isNull() && !isUndefined(value)) {
                            if (value.isString()) {
                                payload = value.asString();
                            } else if (value.hasArrayElements()) {
                                payload = value.as(List.class);
                            } else if (value.hasMembers()) {
                                payload = value.as(Map.class);
                            } else if (value.isNumber()) {
                                payload = value.as(Double.class);
                            } else if (value.isBoolean()) {
                                payload = value.asBoolean();
                            } else {
                                payload = value.toString();
                            }
                        }
                    }
                    return toolBridge.call(name, payload);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };

        Map<String, Object> toolMap = new HashMap<String, Object>();
        toolMap.put("call", callTool);
        ProxyObject tools = ProxyObject.fromMap(toolMap);

        Context context = null;
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            HostAccess hostAccess = HostAccess.newBuilder()
                    .allowAccessAnnotatedBy(HostAccess.Export.class)
                    .build();
            context = Context.newBuilder("python")
                    .allowHostAccess(hostAccess)
                    .option("engine.WarnInterpreterOnly", "false")
                    .out(new PrintStream(stdoutBytes, true))
                    .err(new PrintStream(stderrBytes, true))
                    .build();
            context.getBindings("python").putMember("tools", tools);

            String prelude = buildPythonPrelude(request.getToolNames());
            String wrapped = wrapPythonCode(request.getCode());
            String script = prelude + "\n" + wrapped;

            Long timeoutMs = request.getTimeoutMs();
            long timeout = timeoutMs == null ? DEFAULT_TIMEOUT_MS : timeoutMs;

            final Context runContext = context;
            final String runScript = script;
            Future<Value> future = executor.submit(new Callable<Value>() {
                @Override
                public Value call() {
                    return runContext.eval("python", runScript);
                }
            });

            Value value = future.get(timeout, TimeUnit.MILLISECONDS);
            Value fallback = context.getBindings("python").getMember("__codeact_result");
            String resolved = resolveValue(fallback);
            if (resolved == null) {
                resolved = resolveValue(value);
            }

            String stderrText = new String(stderrBytes.toByteArray(), StandardCharsets.UTF_8);
            String filteredError = filterPolyglotWarnings(stderrText);
            return CodeExecutionResult.builder()
                    .stdout(new String(stdoutBytes.toByteArray(), StandardCharsets.UTF_8))
                    .result(resolved)
                    .error(trimError(filteredError))
                    .build();
        } catch (IllegalArgumentException e) {
            return null;
        } catch (TimeoutException e) {
            return CodeExecutionResult.builder()
                    .stdout(new String(stdoutBytes.toByteArray(), StandardCharsets.UTF_8))
                    .error("code execution timeout")
                    .build();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            log.warn("GraalPy execution failed", cause);
            return CodeExecutionResult.builder()
                    .stdout(new String(stdoutBytes.toByteArray(), StandardCharsets.UTF_8))
                    .error(String.valueOf(cause.getMessage()))
                    .build();
        } catch (Throwable t) {
            log.warn("GraalPy execution failed", t);
            return CodeExecutionResult.builder()
                    .stdout(new String(stdoutBytes.toByteArray(), StandardCharsets.UTF_8))
                    .error(String.valueOf(t.getMessage()))
                    .build();
        } finally {
            if (context != null) {
                try {
                    context.close(true);
                } catch (Exception ignored) {
                }
            }
            executor.shutdownNow();
        }
    }

    private String resolveValue(Value value) {
        if (value == null) {
            return null;
        }
        if (value.isNull() || isUndefined(value)) {
            return null;
        }
        if (value.isString()) {
            return value.asString();
        }
        return value.toString();
    }

    private boolean isUndefined(Value value) {
        if (value == null) {
            return true;
        }
        try {
            String text = value.toString();
            return "undefined".equalsIgnoreCase(text) || "null".equalsIgnoreCase(text);
        } catch (Exception e) {
            return false;
        }
    }

    private String filterPolyglotWarnings(String stderr) {
        if (stderr == null || stderr.trim().isEmpty()) {
            return stderr;
        }
        String[] lines = stderr.split("\r?\n");
        StringBuilder filtered = new StringBuilder();
        for (String line : lines) {
            if (line == null || line.trim().isEmpty()) {
                continue;
            }
            String text = line.trim();
            if (text.startsWith("[engine] WARNING:")
                    || text.contains("polyglot engine uses a fallback runtime")
                    || text.contains("JVMCI is not enabled")
                    || text.contains("WarnInterpreterOnly")
                    || text.startsWith("[To redirect Truffle log output")
                    || text.startsWith("* '--log.file=")
                    || text.startsWith("* '-Dpolyglot.log.file=")
                    || text.startsWith("* Configure logging using the polyglot embedding API")
                    || text.startsWith("Execution without runtime compilation will negatively impact")
                    || text.startsWith("For more information see:")) {
                continue;
            }
            if (filtered.length() > 0) {
                filtered.append("\n");
            }
            filtered.append(text);
        }
        return filtered.length() == 0 ? null : filtered.toString();
    }

    private static class ToolBridge {
        private final ToolExecutor toolExecutor;
        private final String user;

        private ToolBridge(ToolExecutor toolExecutor, String user) {
            this.toolExecutor = toolExecutor;
            this.user = user;
        }

        @HostAccess.Export
        public String call(String name, Object args) throws Exception {
            if (toolExecutor == null) {
                throw new IllegalStateException("toolExecutor is required");
            }
            String arguments;
            if (args == null) {
                arguments = "{}";
            } else if (args instanceof String) {
                arguments = (String) args;
            } else {
                arguments = JSON.toJSONString(args);
            }
            AgentToolCall call = AgentToolCall.builder()
                    .name(resolveName(name))
                    .arguments(arguments)
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
