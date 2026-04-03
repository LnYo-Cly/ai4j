package io.github.lnyocly.ai4j.cli.runtime;

import io.github.lnyocly.ai4j.agent.event.AgentEvent;
import io.github.lnyocly.ai4j.agent.event.AgentEventType;
import io.github.lnyocly.ai4j.agent.event.AgentListener;
import io.github.lnyocly.ai4j.agent.model.ChatModelClient;
import io.github.lnyocly.ai4j.agent.model.ResponsesModelClient;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.AgentToolResult;
import io.github.lnyocly.ai4j.cli.command.CodeCommandOptions;
import io.github.lnyocly.ai4j.cli.session.CodingSessionManager;
import io.github.lnyocly.ai4j.coding.CodingAgentResult;
import io.github.lnyocly.ai4j.coding.CodingAgentRequest;
import io.github.lnyocly.ai4j.coding.CodingSessionCompactResult;
import io.github.lnyocly.ai4j.coding.loop.CodingLoopDecision;
import io.github.lnyocly.ai4j.coding.loop.CodingStopReason;
import io.github.lnyocly.ai4j.coding.session.ManagedCodingSession;
import io.github.lnyocly.ai4j.coding.session.SessionEvent;
import io.github.lnyocly.ai4j.coding.session.SessionEventType;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class HeadlessCodingSessionRuntime {

    public static final String TURN_INTERRUPTED_MESSAGE = "Conversation interrupted by user.";

    private final CodeCommandOptions options;
    private final CodingSessionManager sessionManager;

    public HeadlessCodingSessionRuntime(CodeCommandOptions options, CodingSessionManager sessionManager) {
        this.options = options;
        this.sessionManager = sessionManager;
    }

    public PromptResult runPrompt(ManagedCodingSession session,
                                  String input,
                                  PromptControl control,
                                  HeadlessTurnObserver observer) throws Exception {
        if (session == null || session.getSession() == null) {
            throw new IllegalArgumentException("managed session is required");
        }
        String turnId = newTurnId();
        PromptControl activeControl = control == null ? new PromptControl() : control;
        HeadlessTurnObserver effectiveObserver = observer == null ? new HeadlessTurnObserver.Adapter() : observer;

        appendEvent(session, SessionEventType.USER_MESSAGE, turnId, null, clip(input, 200), payloadOf(
                "input", clipPreserveNewlines(input, options != null && options.isVerbose() ? 4000 : 1200)
        ));
        effectiveObserver.onTurnStarted(session, turnId, input);

        HeadlessAgentListener listener = new HeadlessAgentListener(session, turnId, activeControl, effectiveObserver);
        try {
            activeControl.attach(Thread.currentThread());
            CodingAgentResult result = session.getSession().runStream(CodingAgentRequest.builder().input(input).build(), listener);
            if (activeControl.isCancelled()) {
                appendCancelledEvent(session, turnId);
                persistSession(session);
                effectiveObserver.onTurnCompleted(session, turnId, listener.getFinalOutput(), true);
                return PromptResult.cancelled(turnId, listener.getFinalOutput());
            }
            String finalOutput = listener.flushFinalOutput();
            appendLoopDecisionEvents(session, turnId, result, effectiveObserver);
            appendAutoCompactEvent(session, turnId);
            persistSession(session);
            effectiveObserver.onTurnCompleted(session, turnId, finalOutput, false);
            return PromptResult.completed(turnId, finalOutput, result == null ? null : result.getStopReason());
        } catch (Exception ex) {
            if (activeControl.isCancelled() || Thread.currentThread().isInterrupted()) {
                appendCancelledEvent(session, turnId);
                persistSession(session);
                effectiveObserver.onTurnCompleted(session, turnId, listener.getFinalOutput(), true);
                return PromptResult.cancelled(turnId, listener.getFinalOutput());
            }
            String message = safeMessage(ex);
            appendEvent(session, SessionEventType.ERROR, turnId, null, message, payloadOf("error", message));
            effectiveObserver.onTurnError(session, turnId, null, message);
            throw ex;
        } finally {
            listener.close();
            activeControl.detach();
        }
    }

    private void appendCancelledEvent(ManagedCodingSession session, String turnId) {
        appendEvent(session, SessionEventType.ERROR, turnId, null, TURN_INTERRUPTED_MESSAGE, payloadOf(
                "error", TURN_INTERRUPTED_MESSAGE
        ));
    }

    private void appendAutoCompactEvent(ManagedCodingSession session, String turnId) {
        if (session == null || session.getSession() == null) {
            return;
        }
        List<CodingSessionCompactResult> results = session.getSession().drainAutoCompactResults();
        for (CodingSessionCompactResult result : results) {
            if (result == null) {
                continue;
            }
            appendEvent(session, SessionEventType.COMPACT, turnId, null,
                    (result.isAutomatic() ? "auto" : "manual")
                            + " compact " + result.getEstimatedTokensBefore() + "->" + result.getEstimatedTokensAfter() + " tokens",
                    payloadOf(
                            "automatic", result.isAutomatic(),
                            "strategy", result.getStrategy(),
                            "beforeItemCount", result.getBeforeItemCount(),
                            "afterItemCount", result.getAfterItemCount(),
                            "estimatedTokensBefore", result.getEstimatedTokensBefore(),
                            "estimatedTokensAfter", result.getEstimatedTokensAfter(),
                            "compactedToolResultCount", result.getCompactedToolResultCount(),
                            "deltaItemCount", result.getDeltaItemCount(),
                            "checkpointReused", result.isCheckpointReused(),
                            "fallbackSummary", result.isFallbackSummary(),
                            "splitTurn", result.isSplitTurn(),
                            "summary", clip(result.getSummary(), options != null && options.isVerbose() ? 4000 : 1200),
                            "checkpointGoal", result.getCheckpoint() == null ? null : result.getCheckpoint().getGoal()
                    ));
        }
        List<Exception> compactErrors = session.getSession().drainAutoCompactErrors();
        for (Exception compactError : compactErrors) {
            if (compactError == null) {
                continue;
            }
            String message = safeMessage(compactError);
            appendEvent(session, SessionEventType.ERROR, turnId, null, message, payloadOf(
                    "error", message,
                    "source", "auto-compact"
            ));
        }
    }

    private void appendLoopDecisionEvents(ManagedCodingSession session,
                                          String turnId,
                                          CodingAgentResult result,
                                          HeadlessTurnObserver observer) {
        if (session == null || session.getSession() == null) {
            return;
        }
        List<CodingLoopDecision> decisions = session.getSession().drainLoopDecisions();
        for (CodingLoopDecision decision : decisions) {
            if (decision == null) {
                continue;
            }
            SessionEventType eventType = decision.isContinueLoop()
                    ? SessionEventType.AUTO_CONTINUE
                    : decision.isBlocked() ? SessionEventType.BLOCKED : SessionEventType.AUTO_STOP;
            SessionEvent event = SessionEvent.builder()
                    .sessionId(session.getSessionId())
                    .type(eventType)
                    .turnId(turnId)
                    .summary(firstNonBlank(decision.getSummary(), formatStopReason(result == null ? null : result.getStopReason())))
                    .payload(payloadOf(
                            "turnNumber", decision.getTurnNumber(),
                            "continueReason", decision.getContinueReason(),
                            "stopReason", decision.getStopReason() == null ? null : decision.getStopReason().name().toLowerCase(),
                            "compactApplied", decision.isCompactApplied()
                    ))
                    .build();
            appendEvent(session, event);
            observer.onSessionEvent(session, event);
        }
    }

    private void persistSession(ManagedCodingSession session) {
        if (session == null || sessionManager == null || options == null || !options.isAutoSaveSession()) {
            return;
        }
        try {
            sessionManager.save(session);
        } catch (IOException ignored) {
        }
    }

    private void appendEvent(ManagedCodingSession session,
                             SessionEventType type,
                             String turnId,
                             Integer step,
                             String summary,
                             Map<String, Object> payload) {
        if (session == null || type == null || sessionManager == null) {
            return;
        }
        try {
            sessionManager.appendEvent(session.getSessionId(), SessionEvent.builder()
                    .sessionId(session.getSessionId())
                    .type(type)
                    .turnId(turnId)
                    .step(step)
                    .summary(summary)
                    .payload(payload)
                    .build());
        } catch (IOException ignored) {
        }
    }

    private void appendEvent(ManagedCodingSession session, SessionEvent event) {
        if (event == null) {
            return;
        }
        appendEvent(session, event.getType(), event.getTurnId(), event.getStep(), event.getSummary(), event.getPayload());
    }

    private String newTurnId() {
        return UUID.randomUUID().toString();
    }

    private Map<String, Object> payloadOf(Object... values) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        if (values == null) {
            return payload;
        }
        for (int i = 0; i + 1 < values.length; i += 2) {
            Object key = values[i];
            if (key != null) {
                payload.put(String.valueOf(key), values[i + 1]);
            }
        }
        return payload;
    }

    private String clip(String value, int maxChars) {
        if (value == null) {
            return null;
        }
        String normalized = value.replace('\r', ' ').replace('\n', ' ').trim();
        if (normalized.length() <= maxChars) {
            return normalized;
        }
        return normalized.substring(0, maxChars);
    }

    private String clipPreserveNewlines(String value, int maxChars) {
        if (value == null) {
            return null;
        }
        if (value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars);
    }

    private String safeMessage(Throwable throwable) {
        String message = null;
        Throwable current = throwable;
        Throwable last = throwable;
        while (current != null) {
            if (!isBlank(current.getMessage())) {
                message = current.getMessage().trim();
            }
            last = current;
            current = current.getCause();
        }
        return isBlank(message)
                ? (last == null ? "unknown error" : last.getClass().getSimpleName())
                : message;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static final class PromptControl {

        private volatile boolean cancelled;
        private volatile Thread worker;

        void attach(Thread worker) {
            this.worker = worker;
        }

        void detach() {
            this.worker = null;
        }

        public void cancel() {
            cancelled = true;
            Thread currentWorker = worker;
            if (currentWorker != null) {
                currentWorker.interrupt();
                ChatModelClient.cancelActiveStream(currentWorker);
                ResponsesModelClient.cancelActiveStream(currentWorker);
            }
        }

        public boolean isCancelled() {
            return cancelled;
        }
    }

    public static final class PromptResult {

        private final String turnId;
        private final String finalOutput;
        private final String stopReason;
        private final CodingStopReason codingStopReason;

        private PromptResult(String turnId, String finalOutput, String stopReason, CodingStopReason codingStopReason) {
            this.turnId = turnId;
            this.finalOutput = finalOutput;
            this.stopReason = stopReason;
            this.codingStopReason = codingStopReason;
        }

        public static PromptResult completed(String turnId, String finalOutput, CodingStopReason codingStopReason) {
            return new PromptResult(turnId, finalOutput, mapPromptStopReason(codingStopReason), codingStopReason);
        }

        public static PromptResult cancelled(String turnId, String finalOutput) {
            return new PromptResult(turnId, finalOutput, "cancelled", CodingStopReason.INTERRUPTED);
        }

        public String getTurnId() {
            return turnId;
        }

        public String getFinalOutput() {
            return finalOutput;
        }

        public String getStopReason() {
            return stopReason;
        }

        public CodingStopReason getCodingStopReason() {
            return codingStopReason;
        }

        private static String mapPromptStopReason(CodingStopReason codingStopReason) {
            if (codingStopReason == CodingStopReason.BLOCKED_BY_APPROVAL
                    || codingStopReason == CodingStopReason.BLOCKED_BY_TOOL_ERROR) {
                return "blocked";
            }
            return "end_turn";
        }
    }

    private final class HeadlessAgentListener implements AgentListener {

        private final ManagedCodingSession session;
        private final String turnId;
        private final PromptControl control;
        private final HeadlessTurnObserver observer;
        private final Map<String, AgentToolCall> toolCalls = new LinkedHashMap<String, AgentToolCall>();
        private final StringBuilder pendingReasoning = new StringBuilder();
        private final StringBuilder pendingText = new StringBuilder();
        private String finalOutput;
        private boolean closed;

        private HeadlessAgentListener(ManagedCodingSession session,
                                      String turnId,
                                      PromptControl control,
                                      HeadlessTurnObserver observer) {
            this.session = session;
            this.turnId = turnId;
            this.control = control;
            this.observer = observer;
        }

        @Override
        public void onEvent(AgentEvent event) {
            if (closed || control.isCancelled() || event == null || event.getType() == null) {
                return;
            }
            AgentEventType type = event.getType();
            if (type == AgentEventType.MODEL_REASONING) {
                handleReasoning(event);
                return;
            }
            if (type == AgentEventType.MODEL_RESPONSE) {
                handleAssistant(event);
                return;
            }
            if (type == AgentEventType.TOOL_CALL) {
                flushPendingAssistantText(event.getStep());
                handleToolCall(event);
                return;
            }
            if (type == AgentEventType.TOOL_RESULT) {
                handleToolResult(event);
                return;
            }
            if (AgentHandoffSessionEventSupport.supports(event)) {
                handleHandoffEvent(event);
                return;
            }
            if (AgentTeamSessionEventSupport.supports(event)) {
                handleTeamEvent(event);
                return;
            }
            if (AgentTeamMessageSessionEventSupport.supports(event)) {
                handleTeamMessageEvent(event);
                return;
            }
            if (type == AgentEventType.FINAL_OUTPUT) {
                finalOutput = event.getMessage();
                return;
            }
            if (type == AgentEventType.ERROR) {
                flushPendingAssistantText(event.getStep());
                observer.onTurnError(session, turnId, event.getStep(), event.getMessage());
            }
        }

        private void handleReasoning(AgentEvent event) {
            if (event.getMessage() == null || event.getMessage().isEmpty()) {
                return;
            }
            pendingReasoning.append(event.getMessage());
            observer.onReasoningDelta(session, turnId, event.getStep(), event.getMessage());
        }

        private void handleAssistant(AgentEvent event) {
            if (event.getMessage() == null || event.getMessage().isEmpty()) {
                return;
            }
            if (pendingReasoning.length() > 0) {
                flushPendingReasoning(event.getStep());
            }
            pendingText.append(event.getMessage());
            observer.onAssistantDelta(session, turnId, event.getStep(), event.getMessage());
        }

        private void handleToolCall(AgentEvent event) {
            AgentToolCall call = event.getPayload() instanceof AgentToolCall ? (AgentToolCall) event.getPayload() : null;
            if (call == null) {
                return;
            }
            String toolCallKey = resolveToolCallKey(call);
            if (toolCallKey != null && toolCalls.containsKey(toolCallKey)) {
                return;
            }
            if (toolCallKey != null) {
                toolCalls.put(toolCallKey, call);
            }
            appendEvent(session, SessionEventType.TOOL_CALL, turnId, event.getStep(),
                    call.getName() + " " + clip(call.getArguments(), 120),
                    payloadOf(
                            "tool", call.getName(),
                            "callId", call.getCallId(),
                            "arguments", clipPreserveNewlines(call.getArguments(), options != null && options.isVerbose() ? 4000 : 1200)
                    ));
            observer.onToolCall(session, turnId, event.getStep(), call);
        }

        private void handleToolResult(AgentEvent event) {
            AgentToolResult result = event.getPayload() instanceof AgentToolResult ? (AgentToolResult) event.getPayload() : null;
            if (result == null) {
                return;
            }
            AgentToolCall call = toolCalls.remove(resolveToolResultKey(result));
            boolean failed = isApprovalRejectedToolResult(result);
            appendEvent(session, SessionEventType.TOOL_RESULT, turnId, event.getStep(),
                    result.getName() + " " + clip(result.getOutput(), 120),
                    payloadOf(
                            "tool", result.getName(),
                            "callId", result.getCallId(),
                            "arguments", call == null ? null : clipPreserveNewlines(call.getArguments(), options != null && options.isVerbose() ? 4000 : 1200),
                            "output", clipPreserveNewlines(result.getOutput(), options != null && options.isVerbose() ? 4000 : 1200)
                    ));
            observer.onToolResult(session, turnId, event.getStep(), call, result, failed);
        }

        private void handleHandoffEvent(AgentEvent event) {
            SessionEvent sessionEvent = AgentHandoffSessionEventSupport.toSessionEvent(session.getSessionId(), turnId, event);
            if (sessionEvent == null) {
                return;
            }
            appendEvent(session, sessionEvent);
            observer.onSessionEvent(session, sessionEvent);
        }

        private void handleTeamEvent(AgentEvent event) {
            SessionEvent sessionEvent = AgentTeamSessionEventSupport.toSessionEvent(session.getSessionId(), turnId, event);
            if (sessionEvent == null) {
                return;
            }
            appendEvent(session, sessionEvent);
            observer.onSessionEvent(session, sessionEvent);
        }

        private void handleTeamMessageEvent(AgentEvent event) {
            SessionEvent sessionEvent = AgentTeamMessageSessionEventSupport.toSessionEvent(session.getSessionId(), turnId, event);
            if (sessionEvent == null) {
                return;
            }
            appendEvent(session, sessionEvent);
            observer.onSessionEvent(session, sessionEvent);
        }

        private void flushPendingAssistantText(Integer step) {
            flushPendingReasoning(step);
            flushPendingText(step);
        }

        private void flushPendingReasoning(Integer step) {
            if (pendingReasoning.length() == 0) {
                return;
            }
            appendEvent(session, SessionEventType.ASSISTANT_MESSAGE, turnId, step, clip(pendingReasoning.toString(), 200), payloadOf(
                    "kind", "reasoning",
                    "output", clipPreserveNewlines(pendingReasoning.toString(), options != null && options.isVerbose() ? 4000 : 1200)
            ));
            pendingReasoning.setLength(0);
        }

        private void flushPendingText(Integer step) {
            String text = pendingText.length() == 0 ? finalOutput : pendingText.toString();
            if (isBlank(text)) {
                return;
            }
            appendEvent(session, SessionEventType.ASSISTANT_MESSAGE, turnId, step, clip(text, 200), payloadOf(
                    "kind", "assistant",
                    "output", clipPreserveNewlines(text, options != null && options.isVerbose() ? 4000 : 1200)
            ));
            pendingText.setLength(0);
        }

        private String flushFinalOutput() {
            flushPendingAssistantText(null);
            return finalOutput;
        }

        private String getFinalOutput() {
            return finalOutput;
        }

        private void close() {
            closed = true;
        }

        private String resolveToolCallKey(AgentToolCall call) {
            return call == null ? null : firstNonBlank(call.getCallId(), call.getName());
        }

        private String resolveToolResultKey(AgentToolResult result) {
            return result == null ? null : firstNonBlank(result.getCallId(), result.getName());
        }

        private boolean isApprovalRejectedToolResult(AgentToolResult result) {
            return result != null
                    && result.getOutput() != null
                    && result.getOutput().startsWith(CliToolApprovalDecorator.APPROVAL_REJECTED_PREFIX);
        }

        private String firstNonBlank(String... values) {
            if (values == null) {
                return null;
            }
            for (String value : values) {
                if (!isBlank(value)) {
                    return value;
                }
            }
            return null;
        }
    }

    private String formatStopReason(CodingStopReason stopReason) {
        if (stopReason == null) {
            return null;
        }
        String normalized = stopReason.name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1) + ".";
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }
}
