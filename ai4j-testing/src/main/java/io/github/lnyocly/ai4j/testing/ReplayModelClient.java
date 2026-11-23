package io.github.lnyocly.ai4j.testing;

import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.client.ModelCall;
import io.github.lnyocly.ai4j.client.ModelCallException;
import io.github.lnyocly.ai4j.client.ModelClient;
import io.github.lnyocly.ai4j.codec.JsonCodec;
import io.github.lnyocly.ai4j.model.Block;
import io.github.lnyocly.ai4j.model.EventDeltaKind;
import io.github.lnyocly.ai4j.model.Message;
import io.github.lnyocly.ai4j.model.ModelCallStatus;
import io.github.lnyocly.ai4j.model.ModelEvent;
import io.github.lnyocly.ai4j.model.ModelEventListener;
import io.github.lnyocly.ai4j.model.ModelEventType;
import io.github.lnyocly.ai4j.model.ModelItem;
import io.github.lnyocly.ai4j.model.ModelRequest;
import io.github.lnyocly.ai4j.model.ModelResult;
import io.github.lnyocly.ai4j.model.Protocol;
import io.github.lnyocly.ai4j.model.ReasoningBlock;
import io.github.lnyocly.ai4j.model.ReasoningItem;
import io.github.lnyocly.ai4j.model.ToolCallBlock;
import io.github.lnyocly.ai4j.model.ToolCallItem;
import io.github.lnyocly.ai4j.model.ToolCallValue;
import io.github.lnyocly.ai4j.protocol.Capabilities;
import io.github.lnyocly.ai4j.protocol.Capability;
import io.github.lnyocly.ai4j.protocol.Endpoint;
import io.github.lnyocly.ai4j.protocol.EndpointPolicy;
import io.github.lnyocly.ai4j.protocol.JsonWire;
import io.github.lnyocly.ai4j.protocol.ProtocolDescriptor;
import io.github.lnyocly.ai4j.protocol.ProtocolDescriptors;
import io.github.lnyocly.ai4j.protocol.RawSseEvent;
import io.github.lnyocly.ai4j.protocol.UnsupportedCapabilityException;
import io.github.lnyocly.ai4j.protocol.WireRequest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Plays a {@link ModelFixture} back through the real agent runtime: each model invocation
 * returns the fixture's next recorded response, and each invocation's prompt is checked against
 * the exchange's optional {@code expectedPromptContains} pins before the response is served.
 *
 * <pre>{@code
 * ReplayModelClient model = ReplayModelClient.load(
 *         ReplayModelClientTest.class.getResourceAsStream("/fixtures/weather.json"));
 *
 * Agent agent = Agents.react()
 *         .modelClient(model)
 *         .toolRegistry(registry)
 *         .toolExecutor(tools)
 *         .build();
 * agent.newSession().run("weather in shanghai?");
 * }</pre>
 *
 * <p>A pin mismatch fails the invocation with {@link IllegalStateException} naming the exchange
 * index and the missing substring — the golden contract is "the runtime sent what the fixture
 * pinned", so a drifted prompt fails where the model call happens, not in a later assertion.</p>
 *
 * <p>Inherits script exhaustion semantics from {@link ScriptedModelClient}: invocations past the
 * last exchange return {@link ModelResults#empty()} unless {@code failWhenExhausted(true)} was set.</p>
 */
public class ReplayModelClient extends ScriptedModelClient implements ModelClient {

    private final ModelFixture fixture;
    private final Endpoint endpoint;
    private final Capabilities capabilities;
    private final List<ModelRequest> kernelRequests = new ArrayList<ModelRequest>();
    private int position;
    private int kernelPosition;
    private boolean failWhenKernelExhausted;

    public ReplayModelClient(ModelFixture fixture) {
        this(fixture, null);
    }

    public ReplayModelClient(ModelFixture fixture, Endpoint endpoint) {
        if (fixture == null) {
            throw new IllegalArgumentException("fixture is required");
        }
        this.fixture = fixture;
        Protocol proto = endpoint != null ? endpoint.getProtocol() : fixture.resolveProtocol();
        ProtocolDescriptor descriptor = ProtocolDescriptors.of(proto);
        this.endpoint = endpoint != null
                ? endpoint
                : Endpoint.builder(proto).baseUrl("https://replay.local").build();
        this.capabilities = EndpointPolicy.resolve(this.endpoint, descriptor).getCapabilities();
        for (ModelFixture.Exchange exchange : fixture.getExchanges()) {
            enqueue(exchange.getResponse());
        }
    }

    /** Loads a fixture file and returns a client primed with its exchanges. */
    public static ReplayModelClient load(Path file) throws IOException {
        return new ReplayModelClient(ModelFixture.load(file));
    }

    /** Loads a fixture from a stream (e.g. classpath resource); the stream is consumed but not closed. */
    public static ReplayModelClient load(InputStream in) throws IOException {
        return new ReplayModelClient(ModelFixture.load(in));
    }

    /** Parses fixture JSON text and returns a primed client. */
    public static ReplayModelClient parse(String json) {
        return new ReplayModelClient(ModelFixture.parse(json));
    }

    public ModelFixture getFixture() {
        return fixture;
    }

    /** Returns this instance typed as kernel {@link ModelClient}. */
    public ModelClient asModelClient() {
        return this;
    }

    @Override
    public synchronized ReplayModelClient failWhenExhausted(boolean value) {
        this.failWhenKernelExhausted = value;
        super.failWhenExhausted(value);
        return this;
    }

    /** Returns all kernel {@link ModelRequest}s received so far, in invocation order. */
    public synchronized List<ModelRequest> getKernelRequests() {
        return Collections.unmodifiableList(new ArrayList<ModelRequest>(kernelRequests));
    }

    /** Returns the most recent kernel {@link ModelRequest}, or {@code null}. */
    public synchronized ModelRequest getLastKernelRequest() {
        return kernelRequests.isEmpty() ? null : kernelRequests.get(kernelRequests.size() - 1);
    }

    @Override
    public Endpoint endpoint() {
        return endpoint;
    }

    @Override
    public Capabilities capabilities() {
        return capabilities;
    }

    @Override
    public void require(Capability capability) {
        if (capability != null && !capabilities.supports(capability)) {
            throw new UnsupportedCapabilityException(endpoint.getProtocol(), capability,
                    "Capability " + capability + " is not supported by replay endpoint "
                            + endpoint.getProtocol());
        }
    }

    @Override
    public synchronized ModelResult create(ModelRequest request) throws ModelCallException {
        kernelRequests.add(request);
        int index = kernelPosition++;
        if (index >= fixture.getExchanges().size()) {
            if (failWhenKernelExhausted) {
                throw new IllegalStateException("Replay fixture '" + fixture.getName()
                        + "' exhausted after " + index + " kernel invocation(s)");
            }
            return ModelResult.builder().build();
        }
        ModelFixture.Exchange exchange = fixture.getExchanges().get(index);
        assertKernelPrompt(exchange, index, request);
        return exchange.getModelResult();
    }

    @Override
    public synchronized ModelCall createStream(ModelRequest request, ModelEventListener listener) {
        kernelRequests.add(request);
        int index = kernelPosition++;
        if (index >= fixture.getExchanges().size()) {
            if (failWhenKernelExhausted) {
                throw new IllegalStateException("Replay fixture '" + fixture.getName()
                        + "' exhausted after " + index + " kernel invocation(s)");
            }
            ModelResult empty = ModelResult.builder().build();
            if (listener != null) {
                listener.onEvent(ModelEvent.builder(endpoint.getProtocol(), ModelEventType.DONE).build());
            }
            return new ReplayModelCall(empty);
        }
        ModelFixture.Exchange exchange = fixture.getExchanges().get(index);
        assertKernelPrompt(exchange, index, request);
        Protocol effectiveProto = exchange.getProtocol() != null ? exchange.getProtocol() : endpoint.getProtocol();
        ModelResult result = exchange.getModelResult();
        if (!exchange.getWireStreamEvents().isEmpty()) {
            result = replayWireStream(effectiveProto, exchange.getWireStreamEvents(), result, listener);
        } else {
            emitSyntheticStream(effectiveProto, result, listener);
        }
        return new ReplayModelCall(result);
    }

    @Override
    public AgentModelResult create(AgentPrompt prompt) {
        assertPrompt(prompt);
        return super.create(prompt);
    }

    @Override
    public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
        assertPrompt(prompt);
        return super.createStream(prompt, listener);
    }

    private void assertPrompt(AgentPrompt prompt) {
        int index = position++;
        if (index >= fixture.getExchanges().size()) {
            return;
        }
        ModelFixture.Exchange exchange = fixture.getExchanges().get(index);
        if (exchange.getExpectedPromptContains().isEmpty()) {
            return;
        }
        String rendered = JsonCodec.toJson(prompt);
        for (String expected : exchange.getExpectedPromptContains()) {
            if (!rendered.contains(expected)) {
                throw new IllegalStateException("Replay fixture '" + fixture.getName()
                        + "' exchange[" + index + "] expected prompt to contain '" + expected
                        + "' but it was absent");
            }
        }
    }

    private void assertKernelPrompt(ModelFixture.Exchange exchange, int index, ModelRequest request) {
        if (exchange.getExpectedPromptContains().isEmpty()) {
            return;
        }
        Protocol effectiveProto = exchange.getProtocol() != null ? exchange.getProtocol() : endpoint.getProtocol();
        String wireJson = encodeWireRequestJson(effectiveProto, request);
        String canonicalJson = request == null ? "" : JsonCodec.toJson(request);
        for (String expected : exchange.getExpectedPromptContains()) {
            if (!wireJson.contains(expected) && !canonicalJson.contains(expected)) {
                throw new IllegalStateException("Replay fixture '" + fixture.getName()
                        + "' exchange[" + index + "] expected request/wire payload to contain '" + expected
                        + "' but it was absent (protocol=" + effectiveProto + ")");
            }
        }
    }

    private String encodeWireRequestJson(Protocol proto, ModelRequest request) {
        if (request == null) {
            return "";
        }
        ProtocolDescriptor descriptor = ProtocolDescriptors.of(proto);
        Endpoint encEndpoint = endpoint.getProtocol() == proto
                ? endpoint
                : Endpoint.builder(proto).baseUrl("https://replay.local").build();
        EndpointPolicy policy = EndpointPolicy.resolve(encEndpoint, descriptor);
        WireRequest wire = descriptor.getCodec().encode(request, policy);
        return wire.getBody() == null ? "" : JsonWire.write(wire.getBody());
    }

    private ModelResult replayWireStream(Protocol proto, List<String> rawEvents,
                                         ModelResult fallbackResult, ModelEventListener listener) {
        ProtocolDescriptor descriptor = ProtocolDescriptors.of(proto);
        if (listener != null) {
            listener.onEvent(ModelEvent.builder(proto, ModelEventType.RUN_START).build());
        }
        boolean sawDone = false;
        StringBuilder textBuf = new StringBuilder();
        StringBuilder reasoningBuf = new StringBuilder();
        String reasoningSig = null;
        for (String data : rawEvents) {
            ModelEvent event = descriptor.getCodec().decodeEvent(RawSseEvent.of(data));
            if (event == null) {
                continue;
            }
            if (event.getDeltaKind() == EventDeltaKind.TEXT && event.getDeltaText() != null) {
                textBuf.append(event.getDeltaText());
            } else if (event.getDeltaKind() == EventDeltaKind.REASONING) {
                if (event.getDeltaText() != null) {
                    reasoningBuf.append(event.getDeltaText());
                }
                if (event.getSignature() != null) {
                    reasoningSig = event.getSignature();
                }
            }
            if (listener != null) {
                listener.onEvent(event);
            }
            if (event.getType() == ModelEventType.DONE) {
                sawDone = true;
            }
        }
        if (!sawDone && listener != null) {
            listener.onEvent(ModelEvent.builder(proto, ModelEventType.DONE).build());
        }
        if (fallbackResult != null) {
            return fallbackResult;
        }
        ModelResult.Builder builder = ModelResult.builder();
        if (reasoningBuf.length() > 0 || reasoningSig != null) {
            builder.item(ReasoningItem.of(reasoningBuf.toString(), null, reasoningSig, proto, null, null, null));
        }
        if (textBuf.length() > 0) {
            builder.item(Message.assistant().text(textBuf.toString()).build());
        }
        return builder.build();
    }

    private static void emitSyntheticStream(Protocol proto, ModelResult result, ModelEventListener listener) {
        if (listener == null || result == null) {
            return;
        }
        listener.onEvent(ModelEvent.builder(proto, ModelEventType.MESSAGE_START).build());
        for (ModelItem item : result.getItems()) {
            if (item instanceof ReasoningItem) {
                ReasoningItem r = (ReasoningItem) item;
                if ((r.getText() != null && !r.getText().isEmpty()) || r.getSignature() != null) {
                    listener.onEvent(ModelEvent.builder(proto, ModelEventType.ITEM_DELTA)
                            .delta(EventDeltaKind.REASONING, r.getText() == null ? "" : r.getText())
                            .signature(r.getSignature())
                            .build());
                }
                listener.onEvent(ModelEvent.builder(proto, ModelEventType.ITEM_DONE)
                        .item(r)
                        .build());
            } else if (item instanceof ToolCallItem) {
                ToolCallItem tc = (ToolCallItem) item;
                ToolCallValue v = tc.getValue();
                listener.onEvent(ModelEvent.builder(proto, ModelEventType.ITEM_DELTA)
                        .delta(EventDeltaKind.TOOL_ARGS, v.getArgumentsJson() == null ? "" : v.getArgumentsJson())
                        .itemId(v.getCallId())
                        .toolName(v.getName())
                        .signature(v.getThoughtSignature())
                        .build());
                listener.onEvent(ModelEvent.builder(proto, ModelEventType.ITEM_DONE)
                        .item(tc)
                        .build());
            } else if (item instanceof Message) {
                Message msg = (Message) item;
                for (Block block : msg.getBlocks()) {
                    if (block instanceof ReasoningBlock) {
                        ReasoningBlock rb = (ReasoningBlock) block;
                        listener.onEvent(ModelEvent.builder(proto, ModelEventType.ITEM_DELTA)
                                .delta(EventDeltaKind.REASONING, rb.getText() == null ? "" : rb.getText())
                                .signature(rb.getSignature())
                                .build());
                    } else if (block instanceof ToolCallBlock) {
                        ToolCallValue v = ((ToolCallBlock) block).getValue();
                        listener.onEvent(ModelEvent.builder(proto, ModelEventType.ITEM_DELTA)
                                .delta(EventDeltaKind.TOOL_ARGS,
                                        v.getArgumentsJson() == null ? "" : v.getArgumentsJson())
                                .itemId(v.getCallId())
                                .toolName(v.getName())
                                .signature(v.getThoughtSignature())
                                .build());
                    }
                }
                String text = msg.textContent();
                if (text != null && !text.isEmpty()) {
                    listener.onEvent(ModelEvent.builder(proto, ModelEventType.ITEM_DELTA)
                            .delta(EventDeltaKind.TEXT, text)
                            .build());
                }
                listener.onEvent(ModelEvent.builder(proto, ModelEventType.ITEM_DONE)
                        .item(msg)
                        .build());
            }
        }
        if (result.getUsage() != null) {
            listener.onEvent(ModelEvent.builder(proto, ModelEventType.USAGE)
                    .usage(result.getUsage())
                    .build());
        }
        listener.onEvent(ModelEvent.builder(proto, ModelEventType.DONE)
                .stopReason(result.getStopReason())
                .build());
    }

    private static final class ReplayModelCall implements ModelCall {

        private final ModelResult result;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        private ReplayModelCall(ModelResult result) {
            this.result = result;
        }

        @Override
        public void cancel() {
            cancelled.set(true);
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get();
        }

        @Override
        public ModelResult await() throws ModelCallException {
            return result;
        }

        @Override
        public ModelCallStatus status() {
            return cancelled.get() ? ModelCallStatus.CANCELLED : ModelCallStatus.COMPLETED;
        }
    }
}
