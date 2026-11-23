package io.github.lnyocly.ai4j.protocol;

import io.github.lnyocly.ai4j.client.ModelClient;
import io.github.lnyocly.ai4j.client.ModelClients;
import io.github.lnyocly.ai4j.model.Message;
import io.github.lnyocly.ai4j.model.ModelRequest;
import io.github.lnyocly.ai4j.model.NativeItem;
import io.github.lnyocly.ai4j.model.Protocol;
import io.github.lnyocly.ai4j.model.ReasoningItem;
import io.github.lnyocly.ai4j.transport.CallHandle;
import io.github.lnyocly.ai4j.transport.ModelTransport;
import io.github.lnyocly.ai4j.transport.SseChannel;
import io.github.lnyocly.ai4j.transport.TransportRequest;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Stage-2 protocol repair contract: B6 (no empty payload on the wire) and
 * endpoint capability-disable. Every assertion here failed on the pre-repair
 * kernel (dispatch 33bbcb832dce77dc9854316a).
 */
public class ProtocolRepairContractTest {

    @Test
    public void responsesDropsUnsignedReasoningInsteadOfEmptyObject() {
        ProtocolCodec codec = ProtocolDescriptors.of(Protocol.RESPONSES).getCodec();
        ModelRequest request = ModelRequest.builder()
                .model("test-model")
                .message(Message.user().text("hi").build())
                .item(ReasoningItem.of("a private thought"))
                .build();

        WireRequest wire = codec.encode(request, policy(Protocol.RESPONSES));

        List<?> input = (List<?>) wire.getBody().get("input");
        Assert.assertEquals("only the message may remain in input", 1, input.size());
        for (Object element : input) {
            Assert.assertFalse("an empty reasoning object must never be sent",
                    element instanceof Map && ((Map<?, ?>) element).isEmpty());
        }
        Assert.assertFalse("the drop must be reported", wire.getWarnings().isEmpty());
    }

    @Test
    public void responsesDropsEmptyNativeItemPayload() {
        ProtocolCodec codec = ProtocolDescriptors.of(Protocol.RESPONSES).getCodec();
        ModelRequest request = ModelRequest.builder()
                .model("test-model")
                .item(NativeItem.of(Protocol.RESPONSES, Collections.<String, Object>emptyMap()))
                .build();

        WireRequest wire = codec.encode(request, policy(Protocol.RESPONSES));

        List<?> input = (List<?>) wire.getBody().get("input");
        Assert.assertTrue("an empty native payload must not be sent as {}", input.isEmpty());
        Assert.assertFalse(wire.getWarnings().isEmpty());
    }

    @Test
    public void messagesDropsUnsignedReasoningItemInsteadOfEmptyMessage() {
        ProtocolCodec codec = ProtocolDescriptors.of(Protocol.MESSAGES).getCodec();
        ModelRequest request = ModelRequest.builder()
                .model("claude-test")
                .item(ReasoningItem.of("a private thought"))
                .build();

        WireRequest wire = codec.encode(request, policy(Protocol.MESSAGES));

        List<?> messages = (List<?>) wire.getBody().get("messages");
        Assert.assertTrue("an un-replayable reasoning item must not become an empty message",
                messages.isEmpty());
        Assert.assertFalse(wire.getWarnings().isEmpty());
    }

    @Test
    public void disableCapabilityRemovesBaselineCapability() {
        Endpoint endpoint = Endpoint.builder(Protocol.CHAT_COMPLETIONS)
                .name("test-endpoint")
                .baseUrl("https://example.test")
                .disableCapability(Capability.FUNCTION_TOOLS)
                .build();

        EndpointPolicy resolved = EndpointPolicy.resolve(
                endpoint, ProtocolDescriptors.of(Protocol.CHAT_COMPLETIONS));

        Assert.assertFalse("disableCapability must remove a baseline capability",
                resolved.getCapabilities().supports(Capability.FUNCTION_TOOLS));
        Assert.assertTrue("unrelated capabilities must be untouched",
                resolved.getCapabilities().supports(Capability.IMAGE_INPUT));
    }

    @Test
    public void disableCapabilityIsVisibleThroughTheClient() {
        Endpoint endpoint = Endpoint.builder(Protocol.CHAT_COMPLETIONS)
                .name("test-endpoint")
                .baseUrl("https://example.test")
                .disableCapability(Capability.IMAGE_INPUT)
                .build();
        ModelClient client = ModelClients.of(endpoint, unusedTransport());

        Assert.assertFalse(client.capabilities().supports(Capability.IMAGE_INPUT));
        try {
            client.require(Capability.IMAGE_INPUT);
            Assert.fail("a disabled capability must fail the require pre-flight");
        } catch (UnsupportedCapabilityException expected) {
            Assert.assertEquals(Protocol.CHAT_COMPLETIONS, expected.getProtocol());
        }
    }

    private static EndpointPolicy policy(Protocol protocol) {
        return EndpointPolicy.resolve(
                Endpoint.builder(protocol).baseUrl("https://example.test").build(),
                ProtocolDescriptors.of(protocol));
    }

    private static ModelTransport unusedTransport() {
        return new ModelTransport() {
            @Override
            public WireResponse execute(TransportRequest request) {
                throw new UnsupportedOperationException("no blocking call in this test");
            }

            @Override
            public CallHandle stream(TransportRequest request, SseChannel channel) {
                throw new UnsupportedOperationException("no streaming call in this test");
            }
        };
    }
}
