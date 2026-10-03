package io.github.lnyocly.ai4j.protocol;

import io.github.lnyocly.ai4j.model.Protocol;

import java.util.EnumSet;
import java.util.Set;

/**
 * Protocol baseline capability declarations (design 6.6).
 *
 * <p>This is the first layer of the three-layer resolution
 * {@code protocol baseline ∩ provider profile ∩ endpoint override}. A capability
 * marked absent here is not defined by the protocol specification; it is
 * reported as a loss or refused rather than faked.</p>
 *
 * <p>Verified source anchors: Responses flat function-tool shape and
 * {@code OPENAI_ALLOWED_FIELDS} in {@code OpenAiResponsesService}; Anthropic
 * {@code defer_loading}/{@code tool_reference} passthrough in
 * {@code AnthropicToolSearchPassthroughTest}; no Anthropic parallel-tool flag in
 * {@code AnthropicChatCompletion}.</p>
 */
public final class CapabilityMatrix {

    private CapabilityMatrix() {
    }

    /** Capabilities defined by the CHAT_COMPLETIONS wire protocol. */
    public static Set<Capability> forChatCompletions() {
        EnumSet<Capability> set = EnumSet.noneOf(Capability.class);
        set.add(Capability.FUNCTION_TOOLS);
        set.add(Capability.PARALLEL_TOOL_CALLS);
        set.add(Capability.STRICT_SCHEMA);
        set.add(Capability.TOOL_CHOICE_FORCED);
        set.add(Capability.IMAGE_INPUT);
        set.add(Capability.SSE_RAW_EVENT);
        return set;
    }

    /** Capabilities defined by the RESPONSES wire protocol. */
    public static Set<Capability> forResponses() {
        EnumSet<Capability> set = EnumSet.noneOf(Capability.class);
        set.add(Capability.FUNCTION_TOOLS);
        set.add(Capability.HOSTED_TOOLS);
        set.add(Capability.PARALLEL_TOOL_CALLS);
        set.add(Capability.STRICT_SCHEMA);
        set.add(Capability.TOOL_CHOICE_FORCED);
        set.add(Capability.SYSTEM_INSTRUCTIONS);
        set.add(Capability.IMAGE_INPUT);
        set.add(Capability.DOCUMENT_INPUT);
        set.add(Capability.REASONING_SIGNATURE);
        set.add(Capability.RESPONSE_CHAINING);
        set.add(Capability.SSE_RAW_EVENT);
        return set;
    }

    /** Capabilities defined by the MESSAGES wire protocol. */
    public static Set<Capability> forMessages() {
        EnumSet<Capability> set = EnumSet.noneOf(Capability.class);
        set.add(Capability.FUNCTION_TOOLS);
        set.add(Capability.HOSTED_TOOLS);
        set.add(Capability.TOOL_SEARCH_DEFERRED);
        set.add(Capability.STRICT_SCHEMA);
        set.add(Capability.TOOL_CHOICE_FORCED);
        set.add(Capability.SYSTEM_INSTRUCTIONS);
        set.add(Capability.IMAGE_INPUT);
        set.add(Capability.DOCUMENT_INPUT);
        set.add(Capability.REASONING_SIGNATURE);
        set.add(Capability.SSE_RAW_EVENT);
        return set;
    }

    /** Baseline set for a protocol. */
    public static Set<Capability> forProtocol(Protocol protocol) {
        switch (protocol) {
            case CHAT_COMPLETIONS:
                return forChatCompletions();
            case RESPONSES:
                return forResponses();
            case MESSAGES:
                return forMessages();
            default:
                return EnumSet.noneOf(Capability.class);
        }
    }
}
