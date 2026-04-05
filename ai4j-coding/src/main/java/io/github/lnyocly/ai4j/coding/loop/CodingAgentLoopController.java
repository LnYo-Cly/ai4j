package io.github.lnyocly.ai4j.coding.loop;

import io.github.lnyocly.ai4j.agent.event.AgentEvent;
import io.github.lnyocly.ai4j.agent.event.AgentListener;
import io.github.lnyocly.ai4j.agent.tool.AgentToolResult;
import io.github.lnyocly.ai4j.coding.CodingAgentRequest;
import io.github.lnyocly.ai4j.coding.CodingAgentResult;
import io.github.lnyocly.ai4j.coding.CodingSession;
import io.github.lnyocly.ai4j.coding.CodingSessionCompactResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class CodingAgentLoopController {

    private static final String TOOL_ERROR_PREFIX = "TOOL_ERROR:";
    private static final String APPROVAL_REJECTED_PREFIX = "[approval-rejected]";
    private static final Pattern QUESTION_PATTERN = Pattern.compile(
            "(\\?|？|\\b(could you|can you|would you like|do you want|which|what|please confirm|need your input|need approval)\\b|请确认|请提供|你希望|是否需要)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern COMPLETION_PATTERN = Pattern.compile(
            "^(done|completed|finished|implemented|updated|fixed|resolved|summary:|here('| i)s|已完成|完成了|已经|已实现|已修复|已更新|总结)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern CONTINUE_PATTERN = Pattern.compile(
            "\\b(next|continue|continuing|proceed|then I will|I'll next|still need to|remaining work)\\b|继续|下一步|接下来|仍需",
            Pattern.CASE_INSENSITIVE
    );

    public CodingAgentResult run(CodingSession session, CodingAgentRequest request) throws Exception {
        return execute(session, request, null, false);
    }

    public CodingAgentResult runStream(CodingSession session,
                                       CodingAgentRequest request,
                                       AgentListener listener) throws Exception {
        return execute(session, request, listener, true);
    }

    private CodingAgentResult execute(CodingSession session,
                                      CodingAgentRequest request,
                                      AgentListener listener,
                                      boolean stream) throws Exception {
        if (session == null) {
            throw new IllegalArgumentException("session is required");
        }
        CodingLoopPolicy policy = CodingLoopPolicy.from(session.getOptions());
        List<io.github.lnyocly.ai4j.agent.tool.AgentToolCall> aggregatedCalls = new ArrayList<io.github.lnyocly.ai4j.agent.tool.AgentToolCall>();
        List<AgentToolResult> aggregatedResults = new ArrayList<AgentToolResult>();
        CodingAgentResult lastResult = null;
        int totalSteps = 0;
        int turns = 0;
        int autoFollowUps = 0;
        String continuationPrompt = null;

        while (true) {
            throwIfInterrupted();
            if (hasPositiveLimit(policy.getMaxTotalTurns()) && turns >= policy.getMaxTotalTurns()) {
                CodingLoopDecision forcedStop = stopDecision(turns, CodingStopReason.MAX_TOTAL_TURNS_REACHED,
                        "Stopped after reaching the total turn limit.");
                session.recordLoopDecision(forcedStop);
                return aggregate(session, lastResult, aggregatedCalls, aggregatedResults, totalSteps, turns, autoFollowUps, forcedStop);
            }

            AgentListener effectiveListener = listener == null ? null : new StepOffsetAgentListener(listener, totalSteps);
            CodingAgentResult turnResult = stream
                    ? session.runSingleTurnStream(turnRequest(request, continuationPrompt), effectiveListener, continuationPrompt)
                    : session.runSingleTurn(turnRequest(request, continuationPrompt), continuationPrompt);
            turns += 1;
            totalSteps += turnResult == null ? 0 : turnResult.getSteps();
            if (turnResult != null && turnResult.getToolCalls() != null) {
                aggregatedCalls.addAll(turnResult.getToolCalls());
            }
            if (turnResult != null && turnResult.getToolResults() != null) {
                aggregatedResults.addAll(turnResult.getToolResults());
            }
            lastResult = turnResult;

            CodingLoopDecision decision = decide(
                    policy,
                    turnResult,
                    session.getLastAutoCompactResult(),
                    session.getLastAutoCompactError(),
                    turns,
                    autoFollowUps
            );
            session.recordLoopDecision(decision);

            if (!decision.isContinueLoop()) {
                return aggregate(session, lastResult, aggregatedCalls, aggregatedResults, totalSteps, turns, autoFollowUps, decision);
            }

            autoFollowUps += 1;
            continuationPrompt = decision.getContinuationPrompt();
            request = null;
        }
    }

    private CodingAgentRequest turnRequest(CodingAgentRequest request, String continuationPrompt) {
        if (continuationPrompt == null || continuationPrompt.trim().isEmpty()) {
            return request;
        }
        return CodingAgentRequest.builder().build();
    }

    private CodingLoopDecision decide(CodingLoopPolicy policy,
                                      CodingAgentResult result,
                                      CodingSessionCompactResult compactResult,
                                      Exception compactError,
                                      int turnNumber,
                                      int autoFollowUpsSoFar) {
        String outputText = result == null || result.getOutputText() == null ? "" : result.getOutputText().trim();
        boolean compactApplied = compactResult != null;
        boolean approvalBlocked = hasApprovalBlockedResult(result);
        boolean toolError = hasToolError(result);
        boolean explicitQuestion = policy.isStopOnExplicitQuestion() && looksLikeQuestion(outputText);
        boolean candidateContinue = shouldContinue(policy, result, compactApplied, outputText);

        if (approvalBlocked && policy.isStopOnApprovalBlock()) {
            return stopDecision(turnNumber, CodingStopReason.BLOCKED_BY_APPROVAL, "Stopped because tool approval was rejected.")
                    .toBuilder()
                    .compactApplied(compactApplied)
                    .build();
        }
        if (explicitQuestion) {
            return stopDecision(turnNumber, CodingStopReason.NEEDS_USER_INPUT, "Stopped because the assistant asked for user input.")
                    .toBuilder()
                    .compactApplied(compactApplied)
                    .build();
        }
        if (toolError && !looksLikeCompleted(outputText)) {
            return stopDecision(turnNumber, CodingStopReason.BLOCKED_BY_TOOL_ERROR, "Stopped because a tool failed and the task could not continue safely.")
                    .toBuilder()
                    .compactApplied(compactApplied)
                    .build();
        }
        if (compactError != null && candidateContinue) {
            return stopDecision(turnNumber, CodingStopReason.ERROR, "Stopped because automatic compaction failed before continuation.")
                    .toBuilder()
                    .compactApplied(compactApplied)
                    .build();
        }
        if (!candidateContinue || !policy.isAutoContinueEnabled()) {
            return stopDecision(turnNumber, CodingStopReason.COMPLETED, "Stopped after the assistant completed the current task turn.")
                    .toBuilder()
                    .compactApplied(compactApplied)
                    .build();
        }
        if (hasPositiveLimit(policy.getMaxAutoFollowUps()) && autoFollowUpsSoFar >= policy.getMaxAutoFollowUps()) {
            return stopDecision(turnNumber, CodingStopReason.MAX_AUTO_FOLLOWUPS_REACHED,
                    "Stopped after reaching the auto-follow-up limit.")
                    .toBuilder()
                    .compactApplied(compactApplied)
                    .build();
        }
        if (hasPositiveLimit(policy.getMaxTotalTurns()) && turnNumber >= policy.getMaxTotalTurns()) {
            return stopDecision(turnNumber, CodingStopReason.MAX_TOTAL_TURNS_REACHED,
                    "Stopped after reaching the total turn limit.")
                    .toBuilder()
                    .compactApplied(compactApplied)
                    .build();
        }
        String continueReason = resolveContinueReason(result, compactApplied, outputText);
        CodingLoopDecision seedDecision = CodingLoopDecision.builder()
                .turnNumber(turnNumber)
                .continueLoop(true)
                .continueReason(continueReason)
                .summary(buildContinueSummary(turnNumber, continueReason, compactApplied))
                .compactApplied(compactApplied)
                .build();
        return seedDecision.toBuilder()
                .continuationPrompt(CodingContinuationPrompt.build(seedDecision, result, compactResult, turnNumber + 1))
                .build();
    }

    private boolean shouldContinue(CodingLoopPolicy policy,
                                   CodingAgentResult result,
                                   boolean compactApplied,
                                   String outputText) {
        if (!policy.isAutoContinueEnabled()) {
            return false;
        }
        if (looksLikeCompleted(outputText)) {
            return false;
        }
        if (result != null && result.getToolCalls() != null && !result.getToolCalls().isEmpty()) {
            return outputText.isEmpty() || looksTransitional(outputText);
        }
        if (compactApplied && policy.isContinueAfterCompact()) {
            return outputText.isEmpty() || looksTransitional(outputText);
        }
        return outputText.isEmpty() || looksTransitional(outputText);
    }

    private String resolveContinueReason(CodingAgentResult result, boolean compactApplied, String outputText) {
        if (compactApplied) {
            return CodingLoopDecision.CONTINUE_AFTER_COMPACTION;
        }
        if (result != null && result.getToolCalls() != null && !result.getToolCalls().isEmpty()) {
            return CodingLoopDecision.CONTINUE_AFTER_TOOL_WORK;
        }
        if (outputText.isEmpty() || looksTransitional(outputText)) {
            return CodingLoopDecision.CONTINUE_AUTONOMOUS_WORK;
        }
        return CodingLoopDecision.CONTINUE_AFTER_TOOL_WORK;
    }

    private String buildContinueSummary(int turnNumber, String continueReason, boolean compactApplied) {
        StringBuilder summary = new StringBuilder();
        summary.append("Auto-continue after turn ").append(turnNumber);
        if (continueReason != null && !continueReason.trim().isEmpty()) {
            summary.append(" (").append(continueReason).append(")");
        }
        if (compactApplied) {
            summary.append(" with compacted context");
        }
        summary.append(".");
        return summary.toString();
    }

    private CodingLoopDecision stopDecision(int turnNumber, CodingStopReason stopReason, String summary) {
        return CodingLoopDecision.builder()
                .turnNumber(turnNumber)
                .continueLoop(false)
                .stopReason(stopReason)
                .summary(summary)
                .build();
    }

    private CodingAgentResult aggregate(CodingSession session,
                                        CodingAgentResult lastResult,
                                        List<io.github.lnyocly.ai4j.agent.tool.AgentToolCall> aggregatedCalls,
                                        List<AgentToolResult> aggregatedResults,
                                        int totalSteps,
                                        int turns,
                                        int autoFollowUps,
                                        CodingLoopDecision decision) {
        return CodingAgentResult.builder()
                .sessionId(session == null ? null : session.getSessionId())
                .outputText(lastResult == null ? null : lastResult.getOutputText())
                .rawResponse(lastResult == null ? null : lastResult.getRawResponse())
                .toolCalls(aggregatedCalls.isEmpty() ? Collections.<io.github.lnyocly.ai4j.agent.tool.AgentToolCall>emptyList() : aggregatedCalls)
                .toolResults(aggregatedResults.isEmpty() ? Collections.<AgentToolResult>emptyList() : aggregatedResults)
                .steps(totalSteps)
                .turns(turns)
                .stopReason(decision == null ? null : decision.getStopReason())
                .autoContinued(autoFollowUps > 0)
                .autoFollowUpCount(autoFollowUps)
                .lastCompactApplied(decision != null && decision.isCompactApplied())
                .build();
    }

    private boolean hasApprovalBlockedResult(CodingAgentResult result) {
        if (result == null || result.getToolResults() == null) {
            return false;
        }
        for (AgentToolResult toolResult : result.getToolResults()) {
            if (toolResult != null
                    && toolResult.getOutput() != null
                    && toolResult.getOutput().startsWith(APPROVAL_REJECTED_PREFIX)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasToolError(CodingAgentResult result) {
        if (result == null || result.getToolResults() == null) {
            return false;
        }
        for (AgentToolResult toolResult : result.getToolResults()) {
            if (toolResult != null
                    && toolResult.getOutput() != null
                    && toolResult.getOutput().startsWith(TOOL_ERROR_PREFIX)) {
                return true;
            }
        }
        return false;
    }

    private boolean looksLikeQuestion(String text) {
        return text != null && !text.trim().isEmpty() && QUESTION_PATTERN.matcher(text).find();
    }

    private boolean looksLikeCompleted(String text) {
        return text != null && !text.trim().isEmpty() && COMPLETION_PATTERN.matcher(text.trim()).find();
    }

    private boolean looksTransitional(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }
        String normalized = text.trim().toLowerCase(Locale.ROOT);
        return CONTINUE_PATTERN.matcher(normalized).find();
    }

    private boolean hasPositiveLimit(int limit) {
        return limit > 0;
    }

    private void throwIfInterrupted() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("Coding agent loop interrupted");
        }
    }

    private static final class StepOffsetAgentListener implements AgentListener {

        private final AgentListener delegate;
        private final int stepOffset;

        private StepOffsetAgentListener(AgentListener delegate, int stepOffset) {
            this.delegate = delegate;
            this.stepOffset = Math.max(0, stepOffset);
        }

        @Override
        public void onEvent(AgentEvent event) {
            if (delegate == null) {
                return;
            }
            if (event == null) {
                delegate.onEvent(null);
                return;
            }
            Integer originalStep = event.getStep();
            int adjustedStep = originalStep == null ? stepOffset : originalStep + stepOffset;
            delegate.onEvent(AgentEvent.builder()
                    .type(event.getType())
                    .step(adjustedStep)
                    .message(event.getMessage())
                    .payload(event.getPayload())
                    .build());
        }
    }
}
