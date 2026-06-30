package io.github.lnyocly.ai4j.agent.runtime;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.agent.AgentContext;
import io.github.lnyocly.ai4j.agent.AgentOptions;
import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.context.ContextProjection;
import io.github.lnyocly.ai4j.agent.context.ContextProjector;
import io.github.lnyocly.ai4j.agent.event.AgentEvent;
import io.github.lnyocly.ai4j.agent.event.AgentEventPublisher;
import io.github.lnyocly.ai4j.agent.event.AgentEventType;
import io.github.lnyocly.ai4j.agent.event.AgentListener;
import io.github.lnyocly.ai4j.agent.lifecycle.AgentLifecycleHookDispatcher;
import io.github.lnyocly.ai4j.agent.memory.AgentMemory;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.subagent.HandoffPolicyException;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCallSanitizer;
import io.github.lnyocly.ai4j.agent.tool.AgentToolResult;
import io.github.lnyocly.ai4j.agent.interceptor.ToolInterceptor;
import io.github.lnyocly.ai4j.agent.interceptor.ToolCallDecision;
import io.github.lnyocly.ai4j.agent.interceptor.PromptInterceptor;
import io.github.lnyocly.ai4j.agent.interceptor.PromptDecision;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxProvider;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSession;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxResult;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import io.github.lnyocly.ai4j.extension.lifecycle.AgentLifecycleEventType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public abstract class BaseAgentRuntime implements io.github.lnyocly.ai4j.agent.AgentRuntime {

    protected String runtimeName() {
        return "base";
    }

    protected String runtimeInstructions() {
        return null;
    }

    @Override
    public AgentResult run(AgentContext context, AgentRequest request) throws Exception {
        return runInternal(context, request, null);
    }

    @Override
    public void runStream(AgentContext context, AgentRequest request, AgentListener listener) throws Exception {
        runInternal(context, request, listener);
    }

    @Override
    public AgentResult runStreamResult(AgentContext context, AgentRequest request, AgentListener listener) throws Exception {
        return runInternal(context, request, listener);
    }

    protected AgentResult runInternal(AgentContext context, AgentRequest request, AgentListener listener) throws Exception {
        AgentOptions options = context.getOptions();
        int maxSteps = options == null ? 0 : options.getMaxSteps();
        boolean stream = listener != null && options != null && options.isStream();
        String sessionId = request == null ? null : trimToNull(request.getMetadataString(AgentRequest.METADATA_KEY_SESSION_ID));
        if (sessionId == null) {
            sessionId = context == null ? null : trimToNull(context.getSessionId());
        }
        String runId = request == null ? null : trimToNull(request.getMetadataString(AgentRequest.METADATA_KEY_RUN_ID));
        if (runId == null) {
            runId = UUID.randomUUID().toString();
        }
        String turnId = request == null ? null : trimToNull(request.getMetadataString(AgentRequest.METADATA_KEY_TURN_ID));
        if (turnId == null) {
            turnId = "turn_" + UUID.randomUUID().toString().replace("-", "");
        }

        AgentMemory memory = context.getMemory();
        if (memory == null) {
            throw new IllegalStateException("memory is required");
        }

        if (request != null && request.getInput() != null) {
            Object rawInput = request.getInput();
            Object effectiveInput = rawInput;
            PromptInterceptor promptInterceptor = context == null ? null : context.getPromptInterceptor();
            if (promptInterceptor != null && rawInput instanceof String) {
                PromptDecision decision = promptInterceptor.beforePrompt((String) rawInput, context);
                if (decision == null) {
                    decision = PromptDecision.allow();
                }
                switch (decision.getType()) {
                    case BLOCK:
                        String reason = decision.getReason() == null ? "blocked by prompt interceptor" : decision.getReason();
                        publish(context, listener, AgentEventType.FINAL_OUTPUT, 0, "PROMPT_BLOCKED: " + reason, null, runId, sessionId, turnId);
                        return AgentResult.builder()
                                .runId(runId)
                                .sessionId(sessionId)
                                .turnId(turnId)
                                .outputText("PROMPT_BLOCKED: " + reason)
                                .steps(0)
                                .build();
                    case MODIFY:
                        effectiveInput = decision.getModifiedInput();
                        break;
                    case ALLOW:
                    default:
                        break;
                }
            }
            memory.addUserInput(effectiveInput);
        }

        List<AgentToolCall> toolCalls = new ArrayList<>();
        List<AgentToolResult> toolResults = new ArrayList<>();
        int step = 0;
        AgentModelResult lastResult = null;
        boolean stepLimited = maxSteps > 0;

        while (!stepLimited || step < maxSteps) {
            throwIfInterrupted();
            publish(context, listener, AgentEventType.STEP_START, step, runtimeName(), null, runId, sessionId, turnId);
            dispatchLifecycle(context, AgentLifecycleEventType.BEFORE_TURN, step, runtimeName(), null);

            AgentPrompt prompt = buildPrompt(context, memory, stream, step, listener, runId, sessionId, turnId);
            AgentModelResult modelResult = executeModel(context, prompt, listener, step, stream, runId, sessionId, turnId);
            throwIfInterrupted();
            lastResult = modelResult;

            if (modelResult != null && modelResult.getMemoryItems() != null) {
                memory.addOutputItems(modelResult.getMemoryItems());
            }

            List<AgentToolCall> calls = normalizeToolCalls(modelResult == null ? null : modelResult.getToolCalls(), step);
            if (calls == null || calls.isEmpty()) {
                String outputText = modelResult == null ? "" : modelResult.getOutputText();
                publish(context, listener, AgentEventType.FINAL_OUTPUT, step, outputText, modelResult == null ? null : modelResult.getRawResponse(), runId, sessionId, turnId);
                dispatchLifecycle(context, AgentLifecycleEventType.AFTER_TURN, step, runtimeName(), modelResult);
                publish(context, listener, AgentEventType.STEP_END, step, runtimeName(), null, runId, sessionId, turnId);
                return AgentResult.builder()
                        .runId(runId)
                        .sessionId(sessionId)
                        .turnId(turnId)
                        .outputText(outputText)
                        .rawResponse(modelResult == null ? null : modelResult.getRawResponse())
                        .toolCalls(toolCalls)
                        .toolResults(toolResults)
                        .steps(step + 1)
                        .build();
            }

            List<AgentToolCall> validatedCalls = new ArrayList<AgentToolCall>();
            for (AgentToolCall call : calls) {
                toolCalls.add(call);
                publish(context, listener, AgentEventType.TOOL_CALL, step, call.getName(), call, runId, sessionId, turnId);
                String validationError = AgentToolCallSanitizer.validationError(call);
                if (validationError == null) {
                    validatedCalls.add(call);
                    continue;
                }
                String output = buildToolValidationErrorOutput(call, validationError);
                AgentToolResult toolResult = AgentToolResult.builder()
                        .name(call.getName())
                        .callId(call.getCallId())
                        .output(output)
                        .build();
                toolResults.add(toolResult);
                memory.addToolOutput(call.getCallId(), output);
                publish(context, listener, AgentEventType.TOOL_RESULT, step, output, toolResult, runId, sessionId, turnId);
            }

            boolean parallelExecution = Boolean.TRUE.equals(context.getParallelToolCalls()) && validatedCalls.size() > 1;
            List<String> outputs = parallelExecution
                    ? executeToolCallsInParallel(context, validatedCalls, step, listener, runId, sessionId, turnId)
                    : executeToolCallsSequential(context, validatedCalls, step, listener, runId, sessionId, turnId);
            throwIfInterrupted();

            for (int i = 0; i < validatedCalls.size(); i++) {
                AgentToolCall call = validatedCalls.get(i);
                String output = outputs.get(i);

                AgentToolResult toolResult = AgentToolResult.builder()
                        .name(call.getName())
                        .callId(call.getCallId())
                        .output(output)
                        .build();
                toolResults.add(toolResult);
                memory.addToolOutput(call.getCallId(), output);
                publish(context, listener, AgentEventType.TOOL_RESULT, step, output, toolResult, runId, sessionId, turnId);
            }

            dispatchLifecycle(context, AgentLifecycleEventType.AFTER_TURN, step, runtimeName(), modelResult);
            publish(context, listener, AgentEventType.STEP_END, step, runtimeName(), null, runId, sessionId, turnId);
            step += 1;
        }

        String outputText = lastResult == null ? "" : lastResult.getOutputText();
        return AgentResult.builder()
                .runId(runId)
                .sessionId(sessionId)
                .turnId(turnId)
                .outputText(outputText)
                .rawResponse(lastResult == null ? null : lastResult.getRawResponse())
                .toolCalls(toolCalls)
                .toolResults(toolResults)
                .steps(step)
                .build();
    }

    private void throwIfInterrupted() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("Agent run interrupted");
        }
    }

    private List<AgentToolCall> normalizeToolCalls(List<AgentToolCall> calls, int step) {
        List<AgentToolCall> normalized = new ArrayList<AgentToolCall>();
        if (calls == null || calls.isEmpty()) {
            return normalized;
        }
        int index = 0;
        for (AgentToolCall call : calls) {
            if (call == null) {
                index++;
                continue;
            }
            String callId = trimToNull(call.getCallId());
            if (callId == null) {
                callId = "tool_step_" + step + "_" + index;
            }
            normalized.add(AgentToolCall.builder()
                    .callId(callId)
                    .name(trimToNull(call.getName()) == null ? "tool" : call.getName().trim())
                    .arguments(call.getArguments())
                    .type(call.getType())
                    .build());
            index++;
        }
        return normalized;
    }

    protected AgentPrompt buildPrompt(AgentContext context, AgentMemory memory, boolean stream) {
        return buildPrompt(context, memory, stream, 0, null, null, context == null ? null : context.getSessionId(), null);
    }

    protected AgentPrompt buildPrompt(AgentContext context,
                                      AgentMemory memory,
                                      boolean stream,
                                      int step,
                                      AgentListener listener) {
        return buildPrompt(context, memory, stream, step, listener, null, context == null ? null : context.getSessionId(), null);
    }

    protected AgentPrompt buildPrompt(AgentContext context,
                                      AgentMemory memory,
                                      boolean stream,
                                      int step,
                                      AgentListener listener,
                                      String runId,
                                      String sessionId,
                                      String turnId) {
        if (context.getModel() == null || context.getModel().trim().isEmpty()) {
            throw new IllegalStateException("model is required");
        }
        AgentOptions options = context.getOptions();
        String systemPrompt = mergeText(context.getSystemPrompt(), runtimeInstructions());

        List<Object> tools = context.getToolRegistry() == null ? null : context.getToolRegistry().getTools();
        List<Object> promptItems = projectItems(context, memory.getItems(), step, listener, runId, sessionId, turnId);
        AgentPrompt.AgentPromptBuilder builder = AgentPrompt.builder()
                .model(context.getModel())
                .items(promptItems)
                .systemPrompt(systemPrompt)
                .instructions(context.getInstructions())
                .tools(tools)
                .toolChoice(context.getToolChoice())
                .parallelToolCalls(context.getParallelToolCalls())
                .temperature(context.getTemperature())
                .topP(context.getTopP())
                .maxOutputTokens(context.getMaxOutputTokens())
                .reasoning(context.getReasoning())
                .store(context.getStore())
                .stream(stream)
                .user(context.getUser())
                .extraBody(context.getExtraBody())
                .streamExecution(options == null ? null : options.getStreamExecution());

        return builder.build();
    }

    protected List<Object> projectItems(AgentContext context, List<Object> items, int step, AgentListener listener) {
        return projectItems(context, items, step, listener, null, context == null ? null : context.getSessionId(), null);
    }

    protected List<Object> projectItems(AgentContext context,
                                        List<Object> items,
                                        int step,
                                        AgentListener listener,
                                        String runId,
                                        String sessionId,
                                        String turnId) {
        ContextProjector projector = context.getContextProjector();
        if (projector == null) {
            return items;
        }
        ContextProjection projection = projector.project(items, context.getContextBudget());
        if (projection != null && projection.getReport() != null) {
            publish(context, listener, AgentEventType.MEMORY_COMPRESS, step, "context projection", projection.getReport(), runId, sessionId, turnId);
        }
        return projection == null ? items : projection.getItems();
    }

    protected AgentModelResult executeModel(AgentContext context, AgentPrompt prompt, AgentListener listener, int step, boolean stream, String runId, String sessionId, String turnId) throws Exception {
        dispatchLifecycle(context, AgentLifecycleEventType.BEFORE_MODEL_REQUEST, step, runtimeName(), prompt);
        publish(context, listener, AgentEventType.MODEL_REQUEST, step, null, prompt, runId, sessionId, turnId);
        AgentModelResult result;
        if (stream) {
            final boolean[] streamedReasoning = new boolean[]{false};
            final boolean[] streamedText = new boolean[]{false};
            AgentModelStreamListener streamListener = new AgentModelStreamListener() {
                @Override
                public void onReasoningDelta(String delta) {
                    if (delta != null && !delta.isEmpty()) {
                        streamedReasoning[0] = true;
                        publish(context, listener, AgentEventType.MODEL_REASONING, step, delta, null, runId, sessionId, turnId);
                    }
                }

                @Override
                public void onDeltaText(String delta) {
                    if (delta != null && !delta.isEmpty()) {
                        streamedText[0] = true;
                        publish(context, listener, AgentEventType.MODEL_RESPONSE, step, delta, null, runId, sessionId, turnId);
                    }
                }

                @Override
                public void onToolCall(AgentToolCall call) {
                    if (call != null) {
                        publish(context, listener, AgentEventType.TOOL_CALL, step, call.getName(), call, runId, sessionId, turnId);
                    }
                }

                @Override
                public void onEvent(Object event) {
                    publish(context, listener, AgentEventType.MODEL_RESPONSE, step, null, event, runId, sessionId, turnId);
                }

                @Override
                public void onError(Throwable t) {
                    publish(context, listener, AgentEventType.ERROR, step, t == null ? null : t.getMessage(), t, runId, sessionId, turnId);
                }

                @Override
                public void onRetry(String message, int attempt, int maxAttempts, Throwable cause) {
                    publish(context, listener, AgentEventType.MODEL_RETRY, step, message, retryPayload(attempt, maxAttempts, cause), runId, sessionId, turnId);
                }
            };
            result = context.getModelClient().createStream(prompt, streamListener);
            if (!streamedReasoning[0] && result != null && result.getReasoningText() != null && !result.getReasoningText().isEmpty()) {
                publish(context, listener, AgentEventType.MODEL_REASONING, step, result.getReasoningText(), null, runId, sessionId, turnId);
            }
            if (!streamedText[0] && result != null && result.getOutputText() != null && !result.getOutputText().isEmpty()) {
                publish(context, listener, AgentEventType.MODEL_RESPONSE, step, result.getOutputText(), null, runId, sessionId, turnId);
            }
            publish(context, listener, AgentEventType.MODEL_RESPONSE, step, null, result == null ? null : result.getRawResponse(), runId, sessionId, turnId);
        } else {
            result = context.getModelClient().create(prompt);
            if (result != null && result.getReasoningText() != null && !result.getReasoningText().isEmpty()) {
                publish(context, listener, AgentEventType.MODEL_REASONING, step, result.getReasoningText(), null, runId, sessionId, turnId);
            }
            if (result != null && result.getOutputText() != null && !result.getOutputText().isEmpty()) {
                publish(context, listener, AgentEventType.MODEL_RESPONSE, step, result.getOutputText(), null, runId, sessionId, turnId);
            }
            publish(context, listener, AgentEventType.MODEL_RESPONSE, step, null, result == null ? null : result.getRawResponse(), runId, sessionId, turnId);
        }
        dispatchLifecycle(context, AgentLifecycleEventType.AFTER_MODEL_RESPONSE, step, runtimeName(), result);
        return result;
    }

    protected AgentModelResult executeModel(AgentContext context, AgentPrompt prompt, AgentListener listener, int step, boolean stream) throws Exception {
        return executeModel(context, prompt, listener, step, stream, null, context == null ? null : context.getSessionId(), null);
    }

    private Map<String, Object> retryPayload(int attempt, int maxAttempts, Throwable cause) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("attempt", attempt);
        payload.put("maxAttempts", maxAttempts);
        if (cause != null && cause.getMessage() != null && !cause.getMessage().trim().isEmpty()) {
            payload.put("reason", cause.getMessage().trim());
        }
        return payload;
    }

    protected String executeTool(AgentContext context, AgentToolCall call) throws Exception {
        return executeTool(context, call, null, null);
    }

    protected String executeTool(AgentContext context,
                                 AgentToolCall call,
                                 Integer step,
                                 AgentListener listener) throws Exception {
        return executeTool(context, call, step, listener, null, context == null ? null : context.getSessionId(), null);
    }

    protected String executeTool(AgentContext context,
                                 AgentToolCall call,
                                 Integer step,
                                 AgentListener listener,
                                 String runId,
                                 String sessionId,
                                 String turnId) throws Exception {
        ToolExecutor executor = context.getToolExecutor();
        if (executor == null) {
            throw new IllegalStateException("toolExecutor is required");
        }
        AgentToolCall effectiveCall = call;
        ToolInterceptor interceptor = context == null ? null : context.getToolInterceptor();
        if (interceptor != null) {
            ToolCallDecision decision = interceptor.beforeToolCall(call, context);
            if (decision == null) {
                decision = ToolCallDecision.allow();
            }
            switch (decision.getType()) {
                case BLOCK:
                    return buildBlockedOutput(call, decision.getReason());
                case MODIFY:
                    effectiveCall = decision.getModifiedCall();
                    break;
                case ROUTE_TO:
                    return routeToSandbox(context, call, decision);
                case ALLOW:
                default:
                    break;
            }
        }
        final AgentToolCall callToRun = effectiveCall;
        try {
            dispatchLifecycle(context, AgentLifecycleEventType.BEFORE_TOOL_CALL, step == null ? 0 : step, callToRun == null ? null : callToRun.getName(), callToRun);
            String output = AgentToolExecutionScope.runWithEmitter(new AgentToolExecutionScope.EventEmitter() {
                @Override
                public void emit(AgentEventType type, String message, Object payload) {
                    publish(context, listener, type, step == null ? 0 : step, message, payload, runId, sessionId, turnId);
                }
            }, new AgentToolExecutionScope.ScopeCallable<String>() {
                @Override
                public String call() throws Exception {
                    return executor.execute(callToRun);
                }
            });
            // PostToolUse interception: a hook may veto the result (e.g. output leaked a secret).
            if (interceptor != null) {
                ToolCallDecision after = interceptor.afterToolCall(callToRun, output, context);
                if (after != null && after.getType() == ToolCallDecision.Type.BLOCK) {
                    return buildBlockedOutput(callToRun, after.getReason());
                }
            }
            return output;
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw interruptedException;
        } catch (HandoffPolicyException handoffPolicyException) {
            throw handoffPolicyException;
        } catch (Exception ex) {
            return buildToolErrorOutput(callToRun, ex);
        } finally {
            dispatchLifecycle(context, AgentLifecycleEventType.AFTER_TOOL_CALL, step == null ? 0 : step, callToRun == null ? null : callToRun.getName(), callToRun);
        }
    }

    protected String buildBlockedOutput(AgentToolCall call, String reason) {
        JSONObject payload = new JSONObject();
        payload.put("blocked", true);
        payload.put("reason", reason == null ? "blocked by tool interceptor" : reason);
        if (call != null) {
            payload.put("tool", call.getName());
            if (call.getCallId() != null) {
                payload.put("callId", call.getCallId());
            }
        }
        return "TOOL_BLOCKED: " + JSON.toJSONString(payload);
    }

    /**
     * Honors a {@link ToolCallDecision.Type#ROUTE_TO}: create a sandbox session from the decision's
     * spec (via the configured {@link SandboxProvider}), run the decision's command there, and feed
     * the output back as the tool result. The beyond-pi capability — pi/Claude Code lack a sandbox
     * SPI; ai4j routes to Daytona/E2B.
     */
    protected String routeToSandbox(AgentContext context, AgentToolCall call, ToolCallDecision decision) {
        SandboxProvider provider = context == null ? null : context.getSandboxProvider();
        if (provider == null) {
            return buildBlockedOutput(call, "route-to-sandbox requested but no sandbox provider is configured");
        }
        SandboxSession session = null;
        try {
            session = provider.createSession(decision.getSandboxSpec());
            SandboxResult result = session.execute(decision.getSandboxCommand());
            return buildSandboxOutput(call, result);
        } catch (Exception e) {
            return buildToolErrorOutput(call, e);
        } finally {
            if (session != null) {
                try {
                    session.close();
                } catch (Exception ignored) {
                    // best-effort close
                }
            }
        }
    }

    protected String buildSandboxOutput(AgentToolCall call, SandboxResult result) {
        JSONObject payload = new JSONObject();
        payload.put("routed", true);
        if (result != null) {
            payload.put("exitCode", result.getExitCode());
            if (result.getStdout() != null) {
                payload.put("stdout", result.getStdout());
            }
            if (result.getStderr() != null) {
                payload.put("stderr", result.getStderr());
            }
        }
        if (call != null) {
            payload.put("tool", call.getName());
        }
        return "SANDBOX_RESULT: " + JSON.toJSONString(payload);
    }

    protected String buildToolErrorOutput(AgentToolCall call, Exception error) {
        JSONObject payload = new JSONObject();
        payload.put("error", safeToolErrorMessage(error));
        if (call != null) {
            payload.put("tool", call.getName());
            if (call.getCallId() != null) {
                payload.put("callId", call.getCallId());
            }
        }
        return "TOOL_ERROR: " + JSON.toJSONString(payload);
    }

    protected String buildToolValidationErrorOutput(AgentToolCall call, String validationError) {
        return buildToolErrorOutput(call, new IllegalArgumentException(validationError));
    }

    private String safeToolErrorMessage(Exception error) {
        if (error == null || error.getMessage() == null || error.getMessage().trim().isEmpty()) {
            return error == null ? "unknown tool failure" : error.getClass().getSimpleName();
        }
        return error.getMessage().trim();
    }

    protected String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private List<String> executeToolCallsSequential(AgentContext context,
                                                    List<AgentToolCall> calls,
                                                    Integer step,
                                                    AgentListener listener,
                                                    String runId,
                                                    String sessionId,
                                                    String turnId) throws Exception {
        List<String> outputs = new ArrayList<>();
        for (AgentToolCall call : calls) {
            outputs.add(executeTool(context, call, step, listener, runId, sessionId, turnId));
        }
        return outputs;
    }

    private List<String> executeToolCallsInParallel(AgentContext context,
                                                    List<AgentToolCall> calls,
                                                    Integer step,
                                                    AgentListener listener,
                                                    String runId,
                                                    String sessionId,
                                                    String turnId) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(calls.size());
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (AgentToolCall call : calls) {
                futures.add(executor.submit(() -> executeTool(context, call, step, listener, runId, sessionId, turnId)));
            }
            List<String> outputs = new ArrayList<>();
            for (Future<String> future : futures) {
                outputs.add(waitForFuture(future));
            }
            return outputs;
        } finally {
            executor.shutdownNow();
        }
    }

    private String waitForFuture(Future<String> future) throws Exception {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw new RuntimeException(cause);
        }
    }

    protected void publish(AgentContext context,
                           AgentListener listener,
                           AgentEventType type,
                           int step,
                           String message,
                           Object payload,
                           String runId,
                           String sessionId,
                           String turnId) {
        AgentEvent event = AgentEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .runId(runId)
                .sessionId(sessionId == null ? (context == null ? null : context.getSessionId()) : sessionId)
                .turnId(turnId)
                .type(type)
                .step(step)
                .message(message)
                .payload(payload)
                .build();
        AgentEventPublisher publisher = context.getEventPublisher();
        if (publisher != null) {
            publisher.publish(event);
        }
        if (listener != null) {
            listener.onEvent(event);
        }
    }

    protected void publish(AgentContext context, AgentListener listener, AgentEventType type, int step, String message, Object payload) {
        publish(context, listener, type, step, message, payload, null, context == null ? null : context.getSessionId(), null);
    }

    protected void dispatchLifecycle(AgentContext context,
                                     AgentLifecycleEventType type,
                                     int step,
                                     String message,
                                     Object payload) {
        AgentLifecycleHookDispatcher dispatcher = context == null ? null : context.getLifecycleHooks();
        if (dispatcher != null) {
            dispatcher.dispatch(context, type, runtimeName(), step, message, payload);
        }
    }

    private String mergeText(String base, String extra) {
        if (base == null || base.trim().isEmpty()) {
            return extra;
        }
        if (extra == null || extra.trim().isEmpty()) {
            return base;
        }
        return base + "\n" + extra;
    }
}
