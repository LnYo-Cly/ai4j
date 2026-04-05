package io.github.lnyocly.ai4j.cli.runtime;

import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.AgentToolResult;
import io.github.lnyocly.ai4j.coding.session.ManagedCodingSession;
import io.github.lnyocly.ai4j.coding.session.SessionEvent;

public interface HeadlessTurnObserver {

    void onTurnStarted(ManagedCodingSession session, String turnId, String input);

    void onReasoningDelta(ManagedCodingSession session, String turnId, Integer step, String delta);

    void onAssistantDelta(ManagedCodingSession session, String turnId, Integer step, String delta);

    void onToolCall(ManagedCodingSession session, String turnId, Integer step, AgentToolCall call);

    void onToolResult(ManagedCodingSession session,
                      String turnId,
                      Integer step,
                      AgentToolCall call,
                      AgentToolResult result,
                      boolean failed);

    void onTurnCompleted(ManagedCodingSession session, String turnId, String finalOutput, boolean cancelled);

    void onTurnError(ManagedCodingSession session, String turnId, Integer step, String message);

    void onSessionEvent(ManagedCodingSession session, SessionEvent event);

    class Adapter implements HeadlessTurnObserver {

        @Override
        public void onTurnStarted(ManagedCodingSession session, String turnId, String input) {
        }

        @Override
        public void onReasoningDelta(ManagedCodingSession session, String turnId, Integer step, String delta) {
        }

        @Override
        public void onAssistantDelta(ManagedCodingSession session, String turnId, Integer step, String delta) {
        }

        @Override
        public void onToolCall(ManagedCodingSession session, String turnId, Integer step, AgentToolCall call) {
        }

        @Override
        public void onToolResult(ManagedCodingSession session,
                                 String turnId,
                                 Integer step,
                                 AgentToolCall call,
                                 AgentToolResult result,
                                 boolean failed) {
        }

        @Override
        public void onTurnCompleted(ManagedCodingSession session, String turnId, String finalOutput, boolean cancelled) {
        }

        @Override
        public void onTurnError(ManagedCodingSession session, String turnId, Integer step, String message) {
        }

        @Override
        public void onSessionEvent(ManagedCodingSession session, SessionEvent event) {
        }
    }
}
