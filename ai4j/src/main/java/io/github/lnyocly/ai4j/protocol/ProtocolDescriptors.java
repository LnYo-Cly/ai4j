package io.github.lnyocly.ai4j.protocol;

import io.github.lnyocly.ai4j.model.Protocol;
import io.github.lnyocly.ai4j.protocol.chat.ChatCompletionsCodec;
import io.github.lnyocly.ai4j.protocol.messages.MessagesCodec;
import io.github.lnyocly.ai4j.protocol.responses.ResponsesCodec;

/**
 * The three protocol descriptors known to the kernel.
 *
 * <p>Descriptors are stateless singletons built once from the
 * {@link CapabilityMatrix} baseline. Onboarding a standard-shape vendor is an
 * {@link Endpoint} declaration, not a descriptor change, so this class needs no
 * vendor branch (design A1).</p>
 */
public final class ProtocolDescriptors {

    private static final ProtocolDescriptor CHAT_COMPLETIONS = ProtocolDescriptor
            .builder(Protocol.CHAT_COMPLETIONS, new ChatCompletionsCodec(), "/chat/completions")
            .capabilities(CapabilityMatrix.forChatCompletions())
            .unsupported(Capability.HOSTED_TOOLS)
            .unsupported(Capability.TOOL_SEARCH_DEFERRED)
            .unsupported(Capability.SYSTEM_INSTRUCTIONS)
            .unsupported(Capability.RESPONSE_CHAINING)
            .build();

    private static final ProtocolDescriptor RESPONSES = ProtocolDescriptor
            .builder(Protocol.RESPONSES, new ResponsesCodec(), "/responses")
            .capabilities(CapabilityMatrix.forResponses())
            .unsupported(Capability.TOOL_SEARCH_DEFERRED)
            .build();

    private static final ProtocolDescriptor MESSAGES = ProtocolDescriptor
            .builder(Protocol.MESSAGES, new MessagesCodec(), "/v1/messages")
            .capabilities(CapabilityMatrix.forMessages())
            .unsupported(Capability.PARALLEL_TOOL_CALLS)
            .unsupported(Capability.RESPONSE_CHAINING)
            .anthropicVersionHeader(true)
            .build();

    private ProtocolDescriptors() {
    }

    /** Descriptor for a protocol; every {@link Protocol} value has one. */
    public static ProtocolDescriptor of(Protocol protocol) {
        switch (protocol) {
            case CHAT_COMPLETIONS:
                return CHAT_COMPLETIONS;
            case RESPONSES:
                return RESPONSES;
            case MESSAGES:
                return MESSAGES;
            default:
                throw new IllegalArgumentException("no descriptor for protocol " + protocol);
        }
    }
}
