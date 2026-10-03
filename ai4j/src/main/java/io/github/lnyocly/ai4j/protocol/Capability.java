package io.github.lnyocly.ai4j.protocol;

/**
 * Declared capability keys used for pre-flight checks (design 4.6/6.6).
 *
 * <p>A capability is a declared fact about the resolved protocol + profile +
 * endpoint combination. The kernel reports what is declared; it never infers a
 * capability from a tool name.</p>
 */
public enum Capability {

    /** Function tool declarations. */
    FUNCTION_TOOLS,

    /** Provider-hosted/server tools. */
    HOSTED_TOOLS,

    /** Provider-side deferred tool retrieval. */
    TOOL_SEARCH_DEFERRED,

    /** Native parallel tool call flag. */
    PARALLEL_TOOL_CALLS,

    /** Strict JSON schema flag. */
    STRICT_SCHEMA,

    /** Forced tool choice (required / named tool). */
    TOOL_CHOICE_FORCED,

    /** Top-level system/instructions placement. */
    SYSTEM_INSTRUCTIONS,

    /** Image content blocks. */
    IMAGE_INPUT,

    /** Document content blocks. */
    DOCUMENT_INPUT,

    /** Reasoning/thinking signatures that can be replayed. */
    REASONING_SIGNATURE,

    /** Server-side response chaining (previous response id). */
    RESPONSE_CHAINING,

    /** Raw SSE envelope retention. */
    SSE_RAW_EVENT
}
