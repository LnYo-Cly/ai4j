package io.github.lnyocly.ai4j.agent.codeact;

/**
 * Internal control-flow signal used to stop a CodeAct program at a pending
 * asynchronous tool call. The owning CodeExecutor converts it to a
 * {@link CodeExecutionResult}; it must never escape to an application caller.
 */
final class CodeActPendingToolException extends RuntimeException {

    CodeActPendingToolException(String callId) {
        super("CodeAct tool execution is waiting: " + callId);
    }
}
