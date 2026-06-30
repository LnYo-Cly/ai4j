package io.github.lnyocly.ai4j.agent.interceptor;

import io.github.lnyocly.ai4j.agent.AgentContext;

/**
 * Control-flow hook that runs before the user's input is committed to the conversation — the
 * Claude-Code "UserPromptSubmit" interception capability. Lets the host block a harmful prompt or
 * rewrite/normalize it before the model ever sees it (prompt guardrails, injection defense, PII
 * redaction, prefix injection).
 *
 * <p>Register via {@code AgentBuilder.promptInterceptor(...)}. Sibling to {@link ToolInterceptor}
 * (which covers tool calls).</p>
 */
public interface PromptInterceptor {

    /**
     * Called with the raw user input before it enters memory / the model. Return a decision;
     * never return null (return {@link PromptDecision#allow()} instead).
     */
    PromptDecision beforePrompt(String input, AgentContext context);
}
