package io.github.lnyocly.ai4j.agent.runtime;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.agent.AgentContext;
import io.github.lnyocly.ai4j.agent.AgentOptions;
import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.event.AgentEvent;
import io.github.lnyocly.ai4j.agent.event.AgentEventPublisher;
import io.github.lnyocly.ai4j.agent.event.AgentEventType;
import io.github.lnyocly.ai4j.agent.event.AgentListener;
import io.github.lnyocly.ai4j.agent.memory.AgentMemory;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCallSanitizer;
import io.github.lnyocly.ai4j.agent.tool.AgentToolResult;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

        AgentMemory memory = context.getMemory();
        if (memory == null) {
            throw new IllegalStateException("memory is required");
        }

        if (request != null && request.getInput() != null) {
            memory.addUserInput(request.getInput());
        }

        List<AgentToolCall> toolCalls = new ArrayList<>();
        List<AgentToolResult> toolResults = new ArrayList<>();
        int step = 0;
        AgentModelResult lastResult = null;
        boolean stepLimited = maxSteps > 0;

        while (!stepLimited || step < maxSteps) {
            throwIfInterrupted();
            publish(context, listener, AgentEventType.STEP_START, step, runtimeName(), null);

            AgentPrompt prompt = buildPrompt(context, memory, stream);
            AgentModelResult modelResult = executeModel(context, prompt, listener, step, stream);
            throwIfInterrupted();
            lastResult = modelResult;

            if (modelResult != null && modelResult.getMemoryItems() != null) {
                memory.addOutputItems(modelResult.getMemoryItems());
            }

            List<AgentToolCall> calls = normalizeToolCalls(modelResult == null ? null : modelResult.getToolCalls(), step);
            if (calls == null || calls.isEmpty()) {
                String outputText = modelResult == null ? "" : modelResult.getOutputText();
                publish(context, listener, AgentEventType.FINAL_OUTPUT, step, outputText, modelResult == null ? null : modelResult.getRawResponse());
                publish(context, listener, AgentEventType.STEP_END, step, runtimeName(), null);
                return AgentResult.builder()
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
                publish(context, listener, AgentEventType.TOOL_CALL, step, call.getName(), call);
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
                publish(context, listener, AgentEventType.TOOL_RESULT, step, output, toolResult);
            }

            boolean parallelExecution = Boolean.TRUE.equals(context.getParallelToolCalls()) && validatedCalls.size() > 1;
            List<String> outputs = parallelExecution
                    ? executeToolCallsInParallel(context, validatedCalls, step, listener)
                    : executeToolCallsSequential(context, validatedCalls, step, listener);
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
                publish(context, listener, AgentEventType.TOOL_RESULT, step, output, toolResult);
            }

            publish(context, listener, AgentEventType.STEP_END, step, runtimeName(), null);
            step += 1;
        }

        String outputText = lastResult == null ? "" : lastResult.getOutputText();
        return AgentResult.builder()
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
        if (context.getModel() == null || context.getModel().trim().isEmpty()) {
            throw new IllegalStateException("model is required");
        }
        AgentOptions options = context.getOptions();
        String systemPrompt = mergeText(context.getSystemPrompt(), runtimeInstructions());

        List<Object> tools = context.getToolRegistry() == null ? null : context.getToolRegistry().getTools();
        AgentPrompt.AgentPromptBuilder builder = AgentPrompt.builder()
                .model(context.getModel())
                .items(memory.getItems())
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

    protected AgentModelResult executeModel(AgentContext context, AgentPrompt prompt, AgentListener listener, int step, boolean stream) throws Exception {
        publish(context, listener, AgentEventType.MODEL_REQUEST, step, null, prompt);
        AgentModelResult result;
        if (stream) {
            final boolean[] streamedReasoning = new boolean[]{false};
            final boolean[] streamedText = new boolean[]{false};
            AgentModelStreamListener streamListener = new AgentModelStreamListener() {
                @Override
                public void onReasoningDelta(String delta) {
                    if (delta != null && !delta.isEmpty()) {
                        streamedReasoning[0] = true;
                        publish(context, listener, AgentEventType.MODEL_REASONING, step, delta, null);
                    }
                }

                @Override
                public void onDeltaText(String delta) {
                    if (delta != null && !delta.isEmpty()) {
                        streamedText[0] = true;
                        publish(context, listener, AgentEventType.MODEL_RESPONSE, step, delta, null);
                    }
                }

                @Override
                public void onToolCall(AgentToolCall call) {
                    if (call != null) {
                        publish(context, listener, AgentEventType.TOOL_CALL, step, call.getName(), call);
                    }
                }

                @Override
                public void onEvent(Object event) {
                    publish(context, listener, AgentEventType.MODEL_RESPONSE, step, null, event);
                }

                @Override
                public void onError(Throwable t) {
                    publish(context, listener, AgentEventType.ERROR, step, t == null ? null : t.getMessage(), t);
                }

                @Override
                public void onRetry(String message, int attempt, int maxAttempts, Throwable cause) {
                    publish(context, listener, AgentEventType.MODEL_RETRY, step, message, retryPayload(attempt, maxAttempts, cause));
                }
            };
            result = context.getModelClient().createStream(prompt, streamListener);
            if (!streamedReasoning[0] && result != null && result.getReasoningText() != null && !result.getReasoningText().isEmpty()) {
                publish(context, listener, AgentEventType.MODEL_REASONING, step, result.getReasoningText(), null);
            }
            if (!streamedText[0] && result != null && result.getOutputText() != null && !result.getOutputText().isEmpty()) {
                publish(context, listener, AgentEventType.MODEL_RESPONSE, step, result.getOutputText(), null);
            }
            publish(context, listener, AgentEventType.MODEL_RESPONSE, step, null, result == null ? null : result.getRawResponse());
        } else {
            result = context.getModelClient().create(prompt);
            if (result != null && result.getReasoningText() != null && !result.getReasoningText().isEmpty()) {
                publish(context, listener, AgentEventType.MODEL_REASONING, step, result.getReasoningText(), null);
            }
            if (result != null && result.getOutputText() != null && !result.getOutputText().isEmpty()) {
                publish(context, listener, AgentEventType.MODEL_RESPONSE, step, result.getOutputText(), null);
            }
            publish(context, listener, AgentEventType.MODEL_RESPONSE, step, null, result == null ? null : result.getRawResponse());
        }
        return result;
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
        ToolExecutor executor = context.getToolExecutor();
        if (executor == null) {
            throw new IllegalStateException("toolExecutor is required");
        }
        try {
            return AgentToolExecutionScope.runWithEmitter(new AgentToolExecutionScope.EventEmitter() {
                @Override
                public void emit(AgentEventType type, String message, Object payload) {
                    publish(context, listener, type, step == null ? 0 : step, message, payload);
                }
            }, new AgentToolExecutionScope.ScopeCallable<String>() {
                @Override
                public String call() throws Exception {
                    return executor.execute(call);
                }
            });
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw interruptedException;
        } catch (Exception ex) {
            return buildToolErrorOutput(call, ex);
        }
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

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private List<String> executeToolCallsSequential(AgentContext context,
                                                    List<AgentToolCall> calls,
                                                    Integer step,
                                                    AgentListener listener) throws Exception {
        List<String> outputs = new ArrayList<>();
        for (AgentToolCall call : calls) {
            outputs.add(executeTool(context, call, step, listener));
        }
        return outputs;
    }

    private List<String> executeToolCallsInParallel(AgentContext context,
                                                    List<AgentToolCall> calls,
                                                    Integer step,
                                                    AgentListener listener) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(calls.size());
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (AgentToolCall call : calls) {
                futures.add(executor.submit(() -> executeTool(context, call, step, listener)));
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

    protected void publish(AgentContext context, AgentListener listener, AgentEventType type, int step, String message, Object payload) {
        AgentEvent event = AgentEvent.builder()
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
