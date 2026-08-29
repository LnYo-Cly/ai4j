package io.github.lnyocly.ai4j.coding;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.agent.AgentContext;
import io.github.lnyocly.ai4j.agent.AgentExecutionStatus;
import io.github.lnyocly.ai4j.agent.AgentOptions;
import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.memory.MemorySnapshot;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import io.github.lnyocly.ai4j.harness.HarnessAdapterDelivery;
import io.github.lnyocly.ai4j.harness.HarnessAdapterExecution;
import io.github.lnyocly.ai4j.harness.HarnessAdapterState;
import io.github.lnyocly.ai4j.harness.HarnessExecutionAdapter;
import io.github.lnyocly.ai4j.harness.HarnessExecutionAdapterSession;
import io.github.lnyocly.ai4j.harness.HarnessExecutionContext;
import io.github.lnyocly.ai4j.harness.HarnessPrompts;
import io.github.lnyocly.ai4j.harness.HarnessRunBudget;
import io.github.lnyocly.ai4j.harness.HarnessToolExecutor;
import io.github.lnyocly.ai4j.harness.HarnessToolInterceptor;
import io.github.lnyocly.ai4j.harness.HarnessToolRegistry;
import io.github.lnyocly.ai4j.harness.WaitRecord;
import io.github.lnyocly.ai4j.harness.WaitType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs the existing {@link CodingAgent} through the generic Harness protocol.
 *
 * <p>The adapter deliberately opens a real {@link CodingSession} for every
 * execution slice. This keeps the coding outer loop, compaction, workspace
 * tools, sandbox binding, sub-agent routing and process registry in charge of
 * coding behavior while the Harness owns durable execution lifecycle.</p>
 */
public final class CodingAgentHarnessExecutionAdapter implements HarnessExecutionAdapter {

    public static final String ADAPTER_TYPE = "ai4j-coding";
    public static final String CODING_SESSION_STATE = "codingSessionState";

    private final CodingAgent codingAgent;

    public CodingAgentHarnessExecutionAdapter(CodingAgent codingAgent) {
        if (codingAgent == null) {
            throw new IllegalArgumentException("codingAgent is required");
        }
        this.codingAgent = codingAgent;
    }

    public CodingAgent getCodingAgent() {
        return codingAgent;
    }

    @Override
    public String getAdapterType() {
        return ADAPTER_TYPE;
    }

    @Override
    public HarnessExecutionAdapterSession open(HarnessExecutionContext executionContext,
                                               HarnessRunBudget budget,
                                               HarnessAdapterState previousState) {
        CodingSessionState state = decodeState(previousState);
        String sessionId = firstNonBlank(executionContext.getSessionId(),
                state == null ? null : state.getSessionId());
        if (state != null && state.getSessionId() != null
                && sessionId != null && !sessionId.equals(state.getSessionId())) {
            throw new IllegalArgumentException("coding checkpoint session does not match execution session");
        }
        CodingSession session = codingAgent.newSession(sessionId, state);
        applyHarnessOverlay(session, executionContext, budget);
        return new Session(executionContext, session);
    }

    @Override
    public HarnessAdapterDelivery applyDelivery(HarnessAdapterState state,
                                                WaitRecord wait,
                                                Object input) {
        CodingSessionState codingState = decodeState(state);
        if (codingState == null || codingState.getMemorySnapshot() == null) {
            return HarnessAdapterDelivery.builder()
                    .state(state)
                    .replacedPendingResult(false)
                    .build();
        }
        String callId = callId(wait);
        if (callId == null) {
            return HarnessAdapterDelivery.builder()
                    .state(withState(state, codingState))
                    .replacedPendingResult(false)
                    .build();
        }
        String output = WaitType.APPROVAL.equals(wait == null ? null : wait.getType())
                ? "HARNESS_APPROVAL_GRANTED: retry the approved tool call. delivery=" + stringify(input)
                : stringify(input);
        MemorySnapshot memory = codingState.getMemorySnapshot();
        List<Object> items = memory.getItems();
        if (items == null) {
            return HarnessAdapterDelivery.builder()
                    .state(withState(state, codingState))
                    .replacedPendingResult(false)
                    .build();
        }
        boolean replaced = false;
        for (Object item : items) {
            if (replaceFunctionCallOutput(item, callId, output)
                    || replaceCodeActMarker(item, callId, output)) {
                replaced = true;
                break;
            }
        }
        if (replaced) {
            memory.setItems(items);
            codingState.setMemorySnapshot(memory);
        }
        return HarnessAdapterDelivery.builder()
                .state(withState(state, codingState))
                .replacedPendingResult(replaced)
                .build();
    }

    private void applyHarnessOverlay(CodingSession session,
                                     HarnessExecutionContext executionContext,
                                     HarnessRunBudget budget) {
        if (session == null || session.getDelegate() == null
                || session.getDelegate().getContext() == null) {
            throw new IllegalStateException("coding session context is required");
        }
        AgentContext context = session.getDelegate().getContext();
        AgentOptions options = context.getOptions() == null
                ? AgentOptions.builder().build() : context.getOptions();
        AgentOptions.AgentOptionsBuilder optionsBuilder = options.toBuilder();
        if (budget != null) {
            if (budget.getMaxSteps() > 0) {
                optionsBuilder.maxSteps(budget.getMaxSteps());
            }
            if (budget.getMaxWallTimeMillis() > 0L) {
                optionsBuilder.wallClockTimeoutMillis(budget.getMaxWallTimeMillis());
            }
            if (budget.getMaxTokenBudget() >= 0L) {
                optionsBuilder.maxTokenBudget(budget.getMaxTokenBudget());
            }
        }

        // Capture the coding runtime's resolved tools before replacing the
        // context with the Harness-enforced overlay.
        io.github.lnyocly.ai4j.agent.tool.AgentToolRegistry businessRegistry = context.getToolRegistry();
        ToolExecutor businessExecutor = context.getToolExecutor();
        context.setToolRegistry(new HarnessToolRegistry(businessRegistry));
        context.setToolExecutor(new HarnessToolExecutor(executionContext, businessExecutor));
        context.setToolInterceptor(new HarnessToolInterceptor(context.getToolInterceptor()));
        context.setOptions(optionsBuilder.build());
        context.setSessionId(session.getSessionId());
        context.setSystemPrompt(appendPrompt(context.getSystemPrompt(), HarnessPrompts.instructions()));
    }

    private CodingSessionState decodeState(HarnessAdapterState state) {
        if (state == null || state.getPayload() == null) {
            return null;
        }
        Object raw = state.getPayload().get(CODING_SESSION_STATE);
        if (raw == null) {
            return null;
        }
        return JSON.parseObject(JSON.toJSONString(raw), CodingSessionState.class);
    }

    private HarnessAdapterState withState(HarnessAdapterState source,
                                          CodingSessionState codingState) {
        Map<String, Object> payload = source == null || source.getPayload() == null
                ? new LinkedHashMap<String, Object>()
                : new LinkedHashMap<String, Object>(source.getPayload());
        payload.put(CODING_SESSION_STATE, codingState);
        return HarnessAdapterState.builder()
                .adapterType(ADAPTER_TYPE)
                .sessionId(codingState == null
                        ? source == null ? null : source.getSessionId() : codingState.getSessionId())
                .payload(payload)
                .build();
    }

    private String callId(WaitRecord wait) {
        if (wait == null || wait.getPayload() == null) {
            return null;
        }
        Object raw = wait.getPayload().get(AgentToolCall.METADATA_KEY_PARENT_CALL_ID);
        if (raw == null || String.valueOf(raw).trim().isEmpty()) {
            raw = wait.getPayload().get("callId");
        }
        return raw == null || String.valueOf(raw).trim().isEmpty() ? null : String.valueOf(raw);
    }

    private boolean replaceFunctionCallOutput(Object item, String callId, String output) {
        if (!(item instanceof Map)) {
            return false;
        }
        Map<?, ?> candidate = (Map<?, ?>) item;
        if (!"function_call_output".equals(String.valueOf(candidate.get("type")))
                || !callId.equals(String.valueOf(candidate.get("call_id")))) {
            return false;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> mutable = (Map<String, Object>) item;
        mutable.put("output", output);
        return true;
    }

    private boolean replaceCodeActMarker(Object item, String callId, String output) {
        if (!(item instanceof Map)) {
            return false;
        }
        Map<?, ?> message = (Map<?, ?>) item;
        if (!"message".equals(String.valueOf(message.get("type")))
                || !"system".equals(String.valueOf(message.get("role")))) {
            return false;
        }
        Object rawContent = message.get("content");
        if (!(rawContent instanceof List)) {
            return false;
        }
        for (Object block : (List<?>) rawContent) {
            if (!(block instanceof Map)) {
                continue;
            }
            Map<?, ?> content = (Map<?, ?>) block;
            Object rawText = content.get("text");
            if (rawText == null || !String.valueOf(rawText)
                    .startsWith(AgentToolCall.CODEACT_PENDING_RESULT_PREFIX)) {
                continue;
            }
            String markerJson = String.valueOf(rawText)
                    .substring(AgentToolCall.CODEACT_PENDING_RESULT_PREFIX.length()).trim();
            try {
                JSONObject marker = JSON.parseObject(markerJson);
                if (marker == null || !callId.equals(String.valueOf(marker.get("callId")))) {
                    continue;
                }
            } catch (RuntimeException ignored) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> mutable = (Map<String, Object>) block;
            mutable.put("text", "CODE_RESULT: " + stringify(output));
            return true;
        }
        return false;
    }

    private String appendPrompt(String base, String addition) {
        if (base == null || base.trim().isEmpty()) {
            return addition;
        }
        if (addition == null || addition.trim().isEmpty()) {
            return base;
        }
        return base + "\n" + addition;
    }

    private String stringify(Object value) {
        return value instanceof String ? (String) value : JSON.toJSONString(value);
    }

    private String firstNonBlank(String first, String second) {
        return trimToNull(first) == null ? trimToNull(second) : trimToNull(first);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private final class Session implements HarnessExecutionAdapterSession {
        private final HarnessExecutionContext executionContext;
        private final CodingSession codingSession;

        private Session(HarnessExecutionContext executionContext, CodingSession codingSession) {
            this.executionContext = executionContext;
            this.codingSession = codingSession;
        }

        @Override
        public HarnessAdapterExecution run(AgentRequest request) throws Exception {
            String input = request == null ? null : inputText(request.getInput());
            Map<String, Object> metadata = request == null || request.getMetadata() == null
                    ? new LinkedHashMap<String, Object>()
                    : new LinkedHashMap<String, Object>(request.getMetadata());
            CodingAgentResult result = codingSession.run(CodingAgentRequest.builder()
                    .input(input)
                    .metadata(metadata)
                    .build());
            AgentExecutionStatus status = result == null || result.getExecutionStatus() == null
                    ? AgentExecutionStatus.FAILED : result.getExecutionStatus();
            String error = AgentExecutionStatus.FAILED.equals(status)
                    ? "Coding Agent slice failed" : null;
            return HarnessAdapterExecution.builder()
                    .status(status)
                    .outputText(result == null ? null : result.getOutputText())
                    .error(error)
                    .waitId(result == null ? null : result.getWaitId())
                    .operationId(result == null ? null : result.getOperationId())
                    .checkpointSummary(checkpointSummary(status, result))
                    .checkpointState(checkpointState(status, result))
                    .result(result)
                    .build();
        }

        @Override
        public HarnessAdapterState snapshot() {
            return withState(HarnessAdapterState.builder()
                    .adapterType(ADAPTER_TYPE)
                    .sessionId(executionContext.getSessionId())
                    .payload(new LinkedHashMap<String, Object>())
                    .build(), codingSession.exportState());
        }

        @Override
        public void close() {
            codingSession.close();
        }
    }

    private String inputText(Object input) {
        if (input == null || input instanceof String) {
            return (String) input;
        }
        return JSON.toJSONString(input);
    }

    private String checkpointSummary(AgentExecutionStatus status, CodingAgentResult result) {
        if (AgentExecutionStatus.WAITING.equals(status)) {
            return "Coding slice is waiting for a durable wakeup";
        }
        if (AgentExecutionStatus.CONTINUATION_REQUIRED.equals(status)) {
            return "Coding slice reached its Agent step boundary";
        }
        if (AgentExecutionStatus.FAILED.equals(status)) {
            return "Coding slice failed";
        }
        String output = result == null ? null : result.getOutputText();
        return output == null || output.trim().isEmpty()
                ? "Coding slice completed" : "Coding slice completed: " + output;
    }

    private Map<String, Object> checkpointState(AgentExecutionStatus status,
                                                CodingAgentResult result) {
        Map<String, Object> state = new LinkedHashMap<String, Object>();
        state.put("status", status == null ? null : status.name());
        if (result != null) {
            state.put("sessionId", result.getSessionId());
            state.put("runId", result.getRunId());
            state.put("turnId", result.getTurnId());
            state.put("steps", result.getSteps());
            state.put("turns", result.getTurns());
            state.put("stopReason", result.getStopReason() == null
                    ? null : result.getStopReason().name());
        }
        return state;
    }
}
