package io.github.lnyocly.ai4j.agent.dynamicworkflow;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.SimpleScriptContext;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Java 8 compatible dynamic-workflow runtime.
 *
 * <p>The standalone plugin remains host-mediated and never executes script by
 * itself. This class is the opt-in host side: it runs a deterministic JavaScript
 * subset in Nashorn and delegates {@code agent(...)} calls through a host-owned
 * {@link DynamicWorkflowAgentBridge}.</p>
 */
public class NashornDynamicWorkflowExecutor implements DynamicWorkflowExecutor {

    private static final Logger log = LoggerFactory.getLogger(NashornDynamicWorkflowExecutor.class);
    private static final String RESULT_TYPE = "ai4j.dynamic_workflow.execution_result";
    private static final long DEFAULT_TIMEOUT_MS = 30000L;
    private static final int DEFAULT_MAX_AGENTS = 32;
    private static final String BRIDGE_BINDING = "__ai4j_dynamic_workflow_bridge";

    private final DynamicWorkflowAgentBridge agentBridge;
    private final DynamicWorkflowRuntimeOptions options;

    public NashornDynamicWorkflowExecutor(DynamicWorkflowAgentBridge agentBridge) {
        this(agentBridge, DynamicWorkflowRuntimeOptions.builder().build());
    }

    public NashornDynamicWorkflowExecutor(DynamicWorkflowAgentBridge agentBridge,
                                          DynamicWorkflowRuntimeOptions options) {
        this.agentBridge = agentBridge;
        this.options = options == null ? DynamicWorkflowRuntimeOptions.builder().build() : options;
    }

    @Override
    public DynamicWorkflowExecutionResult execute(DynamicWorkflowRequest request) {
        long startedAt = System.currentTimeMillis();
        RuntimeState state = new RuntimeState();
        if (request == null || isBlank(request.getScript())) {
            return failedResult(request, state, "dynamic workflow script is required", null, startedAt);
        }
        if (agentBridge == null) {
            return failedResult(request, state, "dynamic workflow agentBridge is required", null, startedAt);
        }

        ScriptEngine engine = createScriptEngine();
        if (engine == null) {
            return failedResult(request, state,
                    missingEngineMessage(),
                    null,
                    startedAt);
        }

        StringWriter stdout = new StringWriter();
        StringWriter stderr = new StringWriter();
        ScriptContext context = new SimpleScriptContext();
        context.setWriter(stdout);
        context.setErrorWriter(stderr);
        Bindings bindings = engine.createBindings();
        bindings.put(BRIDGE_BINDING, new Bridge(request, state));
        context.setBindings(bindings, ScriptContext.ENGINE_SCOPE);

        String script = buildPrelude(request) + "\n" + wrapCode(normalizeScript(request.getScript()));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Object> future = executor.submit(new Callable<Object>() {
                @Override
                public Object call() throws Exception {
                    return engine.eval(script, context);
                }
            });
            Object output = future.get(resolveTimeoutMs(), TimeUnit.MILLISECONDS);
            String stderrText = trimToNull(stderr.toString());
            if (stderrText != null) {
                return failedResult(request, state, stderrText, stdout.toString(), startedAt);
            }
            return DynamicWorkflowExecutionResult.builder()
                    .type(RESULT_TYPE)
                    .workflowSpecVersion(request.getWorkflowSpecVersion())
                    .status(DynamicWorkflowConstants.STATUS_COMPLETED)
                    .output(output == null ? null : String.valueOf(output))
                    .runtime("nashorn")
                    .phases(state.phases)
                    .logs(state.logs)
                    .agentCalls(state.agentCalls)
                    .trace(state.trace)
                    .stdout(emptyToNull(stdout.toString()))
                    .durationMillis(Long.valueOf(System.currentTimeMillis() - startedAt))
                    .build();
        } catch (TimeoutException e) {
            return failedResult(request, state, "dynamic workflow execution timeout", stdout.toString(), startedAt);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            log.debug("Dynamic workflow execution failed: {}", safeMessage(cause));
            return failedResult(request, state, safeMessage(cause), stdout.toString(), startedAt);
        } catch (Throwable t) {
            log.debug("Dynamic workflow execution failed: {}", safeMessage(t));
            return failedResult(request, state, safeMessage(t), stdout.toString(), startedAt);
        } finally {
            executor.shutdownNow();
        }
    }

    private String buildPrelude(DynamicWorkflowRequest request) {
        String argsJson = request.getArgs() == null ? "{}" : JSON.toJSONString(request.getArgs());
        StringBuilder builder = new StringBuilder();
        builder.append("var __dynamic_workflow_result = null;\n");
        builder.append("var args = JSON.parse(").append(JSON.toJSONString(argsJson)).append(");\n");
        builder.append("try {\n");
        builder.append("  this.Java = undefined; this.Packages = undefined; this.java = undefined; this.javax = undefined;\n");
        builder.append("  this.org = undefined; this.com = undefined; this.load = undefined; this.loadWithNewGlobal = undefined;\n");
        builder.append("  this.exit = undefined; this.quit = undefined;\n");
        builder.append("} catch (__ai4j_harden_error) {}\n");
        builder.append("(function(__bridge) {\n");
        builder.append("this.phase = function(name) { return __bridge.phase(String(name)); };\n");
        builder.append("this.log = function(message, data) {\n");
        builder.append("  var dataJson = null;\n");
        builder.append("  if (typeof data !== 'undefined' && data !== null) { dataJson = JSON.stringify(data); }\n");
        builder.append("  return __bridge.log(String(message), dataJson);\n");
        builder.append("};\n");
        builder.append("this.agent = function(prompt, options) {\n");
        builder.append("  var optionsJson = null;\n");
        builder.append("  if (typeof options !== 'undefined' && options !== null) { optionsJson = JSON.stringify(options); }\n");
        builder.append("  return __bridge.agent(String(prompt), optionsJson);\n");
        builder.append("};\n");
        builder.append("this.parallel = function(tasks) {\n");
        builder.append("  var count = tasks == null ? 0 : tasks.length;\n");
        builder.append("  __bridge.parallelStart(count);\n");
        builder.append("  var results = [];\n");
        builder.append("  for (var i = 0; i < count; i++) {\n");
        builder.append("    var task = tasks[i];\n");
        builder.append("    results.push(typeof task === 'function' ? task() : task);\n");
        builder.append("  }\n");
        builder.append("  __bridge.parallelEnd(JSON.stringify(results));\n");
        builder.append("  return results;\n");
        builder.append("};\n");
        builder.append("this.pipeline = function(steps, input) {\n");
        builder.append("  var current = input;\n");
        builder.append("  var count = steps == null ? 0 : steps.length;\n");
        builder.append("  __bridge.pipelineStart(count);\n");
        builder.append("  for (var i = 0; i < count; i++) {\n");
        builder.append("    var step = steps[i];\n");
        builder.append("    current = typeof step === 'function' ? step(current) : step;\n");
        builder.append("  }\n");
        builder.append("  __bridge.pipelineEnd(typeof current === 'object' ? JSON.stringify(current) : String(current));\n");
        builder.append("  return current;\n");
        builder.append("};\n");
        builder.append("}).call(this, ").append(BRIDGE_BINDING).append(");\n");
        builder.append("try { this.").append(BRIDGE_BINDING).append(" = undefined; delete this.")
                .append(BRIDGE_BINDING).append("; } catch (__ai4j_bridge_delete_error) {}\n");
        return builder.toString();
    }

    private ScriptEngine createScriptEngine() {
        if (allowJavaInterop()) {
            return new ScriptEngineManager().getEngineByName("nashorn");
        }
        return createNashornEngineWithOptions(new String[]{"--no-java"});
    }

    private ScriptEngine createNashornEngineWithOptions(String[] args) {
        String[] factories = new String[]{
                "org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory",
                "jdk.nashorn.api.scripting.NashornScriptEngineFactory"
        };
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        for (String factoryClassName : factories) {
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
            try {
                Class<?> factoryClass = Class.forName(factoryClassName);
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

    private String missingEngineMessage() {
        if (allowJavaInterop()) {
            return "Nashorn engine not found. Use JDK 8 or add nashorn-core runtime dependency.";
        }
        return "Nashorn engine with Java interop disabled not found. Use JDK 8/nashorn-core, "
                + "or set allowJavaInterop(true) only for trusted scripts.";
    }

    private boolean allowJavaInterop() {
        return options != null && Boolean.TRUE.equals(options.getAllowJavaInterop());
    }

    private String wrapCode(String code) {
        StringBuilder builder = new StringBuilder();
        builder.append("var __dynamic_workflow_return = (function __dynamic_workflow_main__() {\n");
        builder.append(code).append("\n");
        builder.append("})();\n");
        builder.append("if (__dynamic_workflow_result == null && typeof __dynamic_workflow_return !== 'undefined') {\n");
        builder.append("  __dynamic_workflow_result = __dynamic_workflow_return;\n");
        builder.append("}\n");
        builder.append("(function(value) {\n");
        builder.append("  if (value == null || typeof value === 'undefined') { return null; }\n");
        builder.append("  if (typeof value === 'string') { return value; }\n");
        builder.append("  return JSON.stringify(value);\n");
        builder.append("})(__dynamic_workflow_result);\n");
        return builder.toString();
    }

    private String normalizeScript(String script) {
        if (!Boolean.TRUE.equals(options.getNormalizeModernSyntax()) || script == null) {
            return script;
        }
        String normalized = script.replace("\uFEFF", "");
        normalized = normalized.replaceAll("(?m)^\\s*export\\s+const\\s+meta\\s*=", "var meta =");
        normalized = normalized.replaceAll("(?m)^\\s*export\\s+let\\s+meta\\s*=", "var meta =");
        normalized = normalized.replaceAll("(?m)^\\s*export\\s+var\\s+meta\\s*=", "var meta =");
        normalized = normalized.replaceAll("\\bconst\\b", "var");
        normalized = normalized.replaceAll("\\blet\\b", "var");
        normalized = normalized.replaceAll("\\bawait\\s+", "");
        normalized = replaceZeroArgArrowCall(normalized, "agent");
        normalized = replaceZeroArgArrowCall(normalized, "log");
        normalized = replaceZeroArgArrowCall(normalized, "phase");
        return normalized;
    }

    private String replaceZeroArgArrowCall(String script, String functionName) {
        Pattern pattern = Pattern.compile("\\(\\s*\\)\\s*=>\\s*" + Pattern.quote(functionName) + "\\s*\\(");
        String current = script;
        while (true) {
            Matcher matcher = pattern.matcher(current);
            if (!matcher.find()) {
                return current;
            }
            int openParen = matcher.end() - 1;
            int closeParen = findMatchingParen(current, openParen);
            if (closeParen < 0) {
                return current;
            }
            String args = current.substring(openParen + 1, closeParen);
            String replacement = "function(){ return " + functionName + "(" + args + "); }";
            current = current.substring(0, matcher.start()) + replacement + current.substring(closeParen + 1);
        }
    }

    private int findMatchingParen(String text, int openParen) {
        int depth = 0;
        boolean inString = false;
        char quote = 0;
        boolean escape = false;
        for (int i = openParen; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == quote) {
                    inString = false;
                }
                continue;
            }
            if (c == '\'' || c == '"') {
                inString = true;
                quote = c;
                continue;
            }
            if (c == '(') {
                depth += 1;
            } else if (c == ')') {
                depth -= 1;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private DynamicWorkflowExecutionResult failedResult(DynamicWorkflowRequest request,
                                                        RuntimeState state,
                                                        String error,
                                                        String stdout,
                                                        long startedAt) {
        return DynamicWorkflowExecutionResult.builder()
                .type(RESULT_TYPE)
                .workflowSpecVersion(request == null ? null : request.getWorkflowSpecVersion())
                .status(DynamicWorkflowConstants.STATUS_FAILED)
                .error(error)
                .runtime("nashorn")
                .phases(state == null ? new ArrayList<String>() : state.phases)
                .logs(state == null ? new ArrayList<DynamicWorkflowLogEntry>() : state.logs)
                .agentCalls(state == null ? new ArrayList<DynamicWorkflowAgentCallRecord>() : state.agentCalls)
                .trace(state == null ? new ArrayList<DynamicWorkflowTraceEvent>() : state.trace)
                .stdout(emptyToNull(stdout))
                .durationMillis(Long.valueOf(System.currentTimeMillis() - startedAt))
                .build();
    }

    private long resolveTimeoutMs() {
        Long timeout = options == null ? null : options.getTimeoutMs();
        return timeout == null || timeout.longValue() <= 0L ? DEFAULT_TIMEOUT_MS : timeout.longValue();
    }

    private int resolveMaxAgents(DynamicWorkflowRequest request) {
        Integer fromRequest = request == null ? null : request.getMaxAgents();
        if (fromRequest != null && fromRequest.intValue() > 0) {
            return fromRequest.intValue();
        }
        Integer fromOptions = options == null ? null : options.getMaxAgents();
        return fromOptions == null || fromOptions.intValue() <= 0 ? DEFAULT_MAX_AGENTS : fromOptions.intValue();
    }

    private String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String safeMessage(Throwable throwable) {
        if (throwable == null) {
            return "unknown dynamic workflow failure";
        }
        Throwable current = throwable;
        String message = null;
        while (current != null) {
            if (!isBlank(current.getMessage())) {
                message = current.getMessage().trim();
            }
            current = current.getCause();
        }
        return message == null ? throwable.getClass().getSimpleName() : message;
    }

    private static class RuntimeState {
        private final List<String> phases = new ArrayList<String>();
        private final List<DynamicWorkflowLogEntry> logs = new ArrayList<DynamicWorkflowLogEntry>();
        private final List<DynamicWorkflowAgentCallRecord> agentCalls = new ArrayList<DynamicWorkflowAgentCallRecord>();
        private final List<DynamicWorkflowTraceEvent> trace = new ArrayList<DynamicWorkflowTraceEvent>();
        private String currentPhase;
        private int agentCount;
    }

    public class Bridge {
        private final DynamicWorkflowRequest request;
        private final RuntimeState state;

        Bridge(DynamicWorkflowRequest request, RuntimeState state) {
            this.request = request;
            this.state = state;
        }

        public Object phase(String name) {
            String phase = trimToNull(name);
            if (phase == null) {
                return null;
            }
            state.currentPhase = phase;
            state.phases.add(phase);
            state.trace.add(traceEvent("phase", phase, phase, null));
            return phase;
        }

        public Object log(String message, String dataJson) {
            DynamicWorkflowLogEntry entry = DynamicWorkflowLogEntry.builder()
                    .phase(state.currentPhase)
                    .message(message)
                    .dataJson(trimToNull(dataJson))
                    .timestampMillis(Long.valueOf(System.currentTimeMillis()))
                    .build();
            state.logs.add(entry);
            state.trace.add(traceEvent("log", state.currentPhase, message, trimToNull(dataJson)));
            return message;
        }

        public String agent(String prompt, String optionsJson) throws Exception {
            int index = state.agentCount + 1;
            int maxAgents = resolveMaxAgents(request);
            if (index > maxAgents) {
                throw new IllegalStateException("dynamic workflow maxAgents exceeded: " + maxAgents);
            }
            state.agentCount = index;
            Map<String, Object> callOptions = parseOptions(optionsJson);
            String label = firstNonBlank(asString(callOptions.get("label")), asString(callOptions.get("name")), "agent-" + index);
            String callId = "dynamic_workflow_agent_" + index + "_" + UUID.randomUUID().toString().replace("-", "");
            long startedAt = System.currentTimeMillis();
            DynamicWorkflowAgentCallRecord record = DynamicWorkflowAgentCallRecord.builder()
                    .callId(callId)
                    .index(Integer.valueOf(index))
                    .label(label)
                    .prompt(prompt)
                    .options(callOptions)
                    .status("running")
                    .startedAtMillis(Long.valueOf(startedAt))
                    .build();
            state.agentCalls.add(record);
            state.trace.add(traceEvent("agent_start", state.currentPhase, label, record));
            try {
                String output = agentBridge.runAgent(DynamicWorkflowAgentCallRequest.builder()
                        .callId(callId)
                        .index(Integer.valueOf(index))
                        .label(label)
                        .prompt(prompt)
                        .options(callOptions)
                        .workflowRequest(request)
                        .build());
                long completedAt = System.currentTimeMillis();
                record.setStatus(DynamicWorkflowConstants.STATUS_COMPLETED);
                record.setOutput(output);
                record.setCompletedAtMillis(Long.valueOf(completedAt));
                record.setDurationMillis(Long.valueOf(completedAt - startedAt));
                state.trace.add(traceEvent("agent_end", state.currentPhase, label, record));
                return output;
            } catch (Exception e) {
                long completedAt = System.currentTimeMillis();
                record.setStatus(DynamicWorkflowConstants.STATUS_FAILED);
                record.setError(safeMessage(e));
                record.setCompletedAtMillis(Long.valueOf(completedAt));
                record.setDurationMillis(Long.valueOf(completedAt - startedAt));
                state.trace.add(traceEvent("agent_error", state.currentPhase, label, record));
                throw e;
            }
        }

        public Object parallelStart(Object count) {
            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("count", count);
            state.trace.add(traceEvent("parallel_start", state.currentPhase, "parallel", payload));
            return null;
        }

        public Object parallelEnd(String resultJson) {
            state.trace.add(traceEvent("parallel_end", state.currentPhase, "parallel", trimToNull(resultJson)));
            return null;
        }

        public Object pipelineStart(Object count) {
            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("count", count);
            state.trace.add(traceEvent("pipeline_start", state.currentPhase, "pipeline", payload));
            return null;
        }

        public Object pipelineEnd(String output) {
            state.trace.add(traceEvent("pipeline_end", state.currentPhase, "pipeline", trimToNull(output)));
            return null;
        }

        private Map<String, Object> parseOptions(String optionsJson) {
            if (isBlank(optionsJson)) {
                return new LinkedHashMap<String, Object>();
            }
            JSONObject object = JSON.parseObject(optionsJson);
            return object == null ? new LinkedHashMap<String, Object>() : new LinkedHashMap<String, Object>(object);
        }

        private DynamicWorkflowTraceEvent traceEvent(String type, String phase, String message, Object payload) {
            return DynamicWorkflowTraceEvent.builder()
                    .type(type)
                    .phase(phase)
                    .message(message)
                    .payload(payload)
                    .timestampMillis(Long.valueOf(System.currentTimeMillis()))
                    .build();
        }

        private String asString(Object value) {
            return value == null ? null : String.valueOf(value);
        }

        private String firstNonBlank(String first, String second, String fallback) {
            String value = trimToNull(first);
            if (value != null) {
                return value;
            }
            value = trimToNull(second);
            return value == null ? fallback : value;
        }
    }
}
