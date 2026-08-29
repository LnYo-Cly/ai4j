package io.github.lnyocly.ai4j.harness;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentContext;
import io.github.lnyocly.ai4j.agent.AgentExecutionStatus;
import io.github.lnyocly.ai4j.agent.AgentOptions;
import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.AgentSession;
import io.github.lnyocly.ai4j.agent.memory.MemorySnapshot;
import io.github.lnyocly.ai4j.agent.session.AgentSessionSnapshot;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Default adapter that runs the existing {@link Agent} runtime inside the
 * generic Harness adapter protocol.
 */
public final class AgentHarnessExecutionAdapter implements HarnessExecutionAdapter {

    public static final String ADAPTER_TYPE = "ai4j-agent";
    private static final String SESSION_SNAPSHOT = "agentSessionSnapshot";

    private final Agent agent;

    public AgentHarnessExecutionAdapter(Agent agent) {
        if (agent == null) {
            throw new IllegalArgumentException("agent is required");
        }
        this.agent = agent;
    }

    public Agent getAgent() {
        return agent;
    }

    @Override
    public String getAdapterType() {
        return ADAPTER_TYPE;
    }

    @Override
    public HarnessExecutionAdapterSession open(HarnessExecutionContext executionContext,
                                               HarnessRunBudget budget,
                                               HarnessAdapterState previousState) {
        AgentContext context = createContext(executionContext, budget);
        AgentSessionSnapshot snapshot = decodeSessionSnapshot(previousState);
        if (snapshot == null && executionContext.getSessionId() != null) {
            snapshot = executionContext.getGateway().getSessionSnapshot(
                    executionContext.getSessionId());
        }
        AgentSession session = snapshot == null
                ? agent.newSessionWithIdentity(executionContext.getSessionId(),
                        executionContext.getRunId(), context)
                : agent.newSession(snapshot, context);
        return new Session(executionContext, session);
    }

    @Override
    public HarnessAdapterDelivery applyDelivery(HarnessAdapterState state,
                                                WaitRecord wait,
                                                Object input) {
        AgentSessionSnapshot snapshot = decodeSessionSnapshot(state);
        if (snapshot == null) {
            return HarnessAdapterDelivery.builder()
                    .state(state)
                    .replacedPendingResult(false)
                    .build();
        }
        String callId = callId(wait);
        if (callId == null || snapshot.getMemory() == null) {
            return HarnessAdapterDelivery.builder()
                    .state(state)
                    .replacedPendingResult(false)
                    .build();
        }
        String output = WaitType.APPROVAL.equals(wait == null ? null : wait.getType())
                ? approvalDeliveryOutput(input)
                : stringify(input);
        MemorySnapshot memory = snapshot.getMemory();
        List<Object> items = memory.getItems();
        if (items == null) {
            return HarnessAdapterDelivery.builder().state(state).build();
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
            snapshot.setMemory(memory);
        }
        return HarnessAdapterDelivery.builder()
                .state(withSessionSnapshot(state, snapshot))
                .replacedPendingResult(replaced)
                .build();
    }

    /** Compatibility bridge for callers that still inspect the old snapshot API. */
    public AgentSessionSnapshot sessionSnapshot(HarnessAdapterState state) {
        return decodeSessionSnapshot(state);
    }

    private AgentContext createContext(HarnessExecutionContext executionContext,
                                       HarnessRunBudget budget) {
        AgentContext base = agent.getContext();
        if (base == null) {
            throw new IllegalStateException("agent context is required");
        }
        AgentOptions options = base.getOptions() == null
                ? AgentOptions.builder().build() : base.getOptions();
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
        ToolExecutor businessExecutor = base.getToolExecutor();
        return base.toBuilder()
                .toolRegistry(new HarnessToolRegistry(base.getToolRegistry()))
                .toolExecutor(new HarnessToolExecutor(executionContext, businessExecutor))
                .toolInterceptor(new HarnessToolInterceptor(base.getToolInterceptor()))
                .options(optionsBuilder.build())
                .sessionId(executionContext.getSessionId())
                .systemPrompt(appendPrompt(base.getSystemPrompt(), HarnessPrompts.instructions()))
                .build();
    }

    private AgentSessionSnapshot decodeSessionSnapshot(HarnessAdapterState state) {
        if (state == null || state.getPayload() == null) {
            return null;
        }
        Object raw = state.getPayload().get(SESSION_SNAPSHOT);
        if (raw == null) {
            return null;
        }
        if (raw instanceof AgentSessionSnapshot) {
            return HarnessJson.copy((AgentSessionSnapshot) raw, AgentSessionSnapshot.class);
        }
        return JSON.parseObject(JSON.toJSONString(raw), AgentSessionSnapshot.class);
    }

    private HarnessAdapterState withSessionSnapshot(HarnessAdapterState state,
                                                     AgentSessionSnapshot snapshot) {
        Map<String, Object> payload = state == null || state.getPayload() == null
                ? new LinkedHashMap<String, Object>()
                : new LinkedHashMap<String, Object>(state.getPayload());
        payload.put(SESSION_SNAPSHOT, snapshot);
        return HarnessAdapterState.builder()
                .adapterType(ADAPTER_TYPE)
                .sessionId(snapshot == null ? state == null ? null : state.getSessionId() : snapshot.getSessionId())
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

    private String approvalDeliveryOutput(Object input) {
        return executionContextApprovalGranted(input)
                ? "HARNESS_APPROVAL_GRANTED: retry the approved tool call. delivery=" + stringify(input)
                : "HARNESS_APPROVAL_DENIED: do not retry the denied tool call. delivery=" + stringify(input);
    }

    private boolean executionContextApprovalGranted(Object input) {
        if (input instanceof Boolean) {
            return ((Boolean) input).booleanValue();
        }
        if (input instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) input;
            Object approved = map.get("approved");
            if (approved instanceof Boolean) {
                return ((Boolean) approved).booleanValue();
            }
            Object decision = map.get("decision");
            return decision != null && isApprovalWord(String.valueOf(decision));
        }
        return input != null && isApprovalWord(String.valueOf(input));
    }

    private boolean isApprovalWord(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase();
        return "true".equals(normalized) || "yes".equals(normalized)
                || "approve".equals(normalized) || "approved".equals(normalized)
                || "allow".equals(normalized) || "allowed".equals(normalized)
                || "同意".equals(normalized) || "批准".equals(normalized)
                || "允许".equals(normalized);
    }

    private final class Session implements HarnessExecutionAdapterSession {
        private final HarnessExecutionContext executionContext;
        private final AgentSession session;

        private Session(HarnessExecutionContext executionContext, AgentSession session) {
            this.executionContext = executionContext;
            this.session = session;
        }

        @Override
        public HarnessAdapterExecution run(AgentRequest request) throws Exception {
            AgentResult result = session.run(request);
            AgentExecutionStatus status = result == null || result.getExecutionStatus() == null
                    ? AgentExecutionStatus.FAILED : result.getExecutionStatus();
            return HarnessAdapterExecution.builder()
                    .status(status)
                    .outputText(result == null ? null : result.getOutputText())
                    .waitId(result == null ? null : result.getWaitId())
                    .operationId(result == null ? null : result.getOperationId())
                    .checkpointSummary(checkpointSummary(status, result))
                    .checkpointState(checkpointState(status, result))
                    .result(result)
                    .build();
        }

        @Override
        public HarnessAdapterState snapshot() {
            return withSessionSnapshot(HarnessAdapterState.builder()
                    .adapterType(ADAPTER_TYPE)
                    .sessionId(executionContext.getSessionId())
                    .payload(new LinkedHashMap<String, Object>())
                    .build(), session.snapshot());
        }
    }

    private String checkpointSummary(AgentExecutionStatus status, AgentResult result) {
        if (status == AgentExecutionStatus.WAITING) {
            return "Agent slice is waiting for a durable wakeup";
        }
        if (status == AgentExecutionStatus.CONTINUATION_REQUIRED) {
            return "Agent slice reached its boundary and can continue";
        }
        return result == null || result.getOutputText() == null
                ? "Agent slice completed" : "Agent slice completed: " + result.getOutputText();
    }

    private Map<String, Object> checkpointState(AgentExecutionStatus status, AgentResult result) {
        Map<String, Object> state = new LinkedHashMap<String, Object>();
        state.put("adapterType", ADAPTER_TYPE);
        state.put("agentStatus", status == null ? null : status.name());
        if (result != null) {
            state.put("steps", result.getSteps());
            state.put("runId", result.getRunId());
            state.put("sessionId", result.getSessionId());
        }
        return state;
    }
}
