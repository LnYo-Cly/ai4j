package io.github.lnyocly.ai4j.model;

/**
 * The single-turn conversation protocols supported by the model kernel.
 *
 * <p>Names intentionally carry no {@code LLM_} prefix (design C2). The kernel
 * covers exactly these three conversational protocols; embedding, rerank,
 * image/audio/video and realtime surfaces stay on the legacy chain.</p>
 */
public enum Protocol {

    /** OpenAI-compatible chat completions wire protocol. */
    CHAT_COMPLETIONS,

    /** OpenAI Responses wire protocol. */
    RESPONSES,

    /** Anthropic Messages wire protocol. */
    MESSAGES
}
