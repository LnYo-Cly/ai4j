package io.github.lnyocly.ai4j.agent.dynamicworkflow;

/**
 * Host-owned bridge used by a dynamic workflow script's {@code agent(...)}
 * primitive. Implementations decide whether this means a subagent, a fresh
 * Agent session, a coding-agent worker, or a test double.
 */
public interface DynamicWorkflowAgentBridge {

    String runAgent(DynamicWorkflowAgentCallRequest request) throws Exception;
}
