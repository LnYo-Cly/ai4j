package io.github.lnyocly.ai4j.stream;

import io.github.lnyocly.ai4j.client.ModelCall;
import io.github.lnyocly.ai4j.client.ModelCallException;
import io.github.lnyocly.ai4j.client.ModelClient;
import io.github.lnyocly.ai4j.client.ModelClients;
import io.github.lnyocly.ai4j.exception.Ai4jException;
import io.github.lnyocly.ai4j.model.Block;
import io.github.lnyocly.ai4j.model.EventDeltaKind;
import io.github.lnyocly.ai4j.model.Message;
import io.github.lnyocly.ai4j.model.ModelCallStatus;
import io.github.lnyocly.ai4j.model.ModelEvent;
import io.github.lnyocly.ai4j.model.ModelEventListener;
import io.github.lnyocly.ai4j.model.ModelEventType;
import io.github.lnyocly.ai4j.model.ModelRequest;
import io.github.lnyocly.ai4j.model.ModelResult;
import io.github.lnyocly.ai4j.model.Protocol;
import io.github.lnyocly.ai4j.model.ReasoningBlock;
import io.github.lnyocly.ai4j.model.TextBlock;
import io.github.lnyocly.ai4j.model.ToolCallBlock;
import io.github.lnyocly.ai4j.protocol.Capabilities;
import io.github.lnyocly.ai4j.protocol.Capability;
import io.github.lnyocly.ai4j.protocol.Endpoint;
import io.github.lnyocly.ai4j.service.Configuration;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Tests for {@link AiStream}, {@link DefaultAiStream}, {@link AiStreamEvent},
 * {@link AiReply}, and {@link DispatcherProvider}.
 */
public class AiStreamTest {

    private MockWebServer server;

    @Before
    public void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @After
    public void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    public void emitsSymmetricThreePhaseEventsWithImmutablePartialSnapshotsAndLateReplay() throws Exception {
        DefaultAiStream<String> stream = DefaultAiStream.create();
        DefaultAiStream.BlockTracker tracker = new DefaultAiStream.BlockTracker(stream);

        final List<AiStreamEvent> events = new ArrayList<AiStreamEvent>();
        final StringBuilder textDeltas = new StringBuilder();
        final StringBuilder thinkingDeltas = new StringBuilder();
        stream.onEvent(new Consumer<AiStreamEvent>() {
            @Override
            public void accept(AiStreamEvent event) {
                events.add(event);
            }
        });
        stream.onTextDelta(new Consumer<String>() {
            @Override
            public void accept(String s) {
                textDeltas.append(s);
            }
        });
        stream.onThinkingDelta(new Consumer<String>() {
            @Override
            public void accept(String s) {
                thinkingDeltas.append(s);
            }
        });

        stream.emitStart();
        tracker.accept(ModelEvent.builder(Protocol.GEMINI, ModelEventType.ITEM_DELTA)
                .delta(EventDeltaKind.REASONING, "plan ")
                .signature("sig_t")
                .build());
        tracker.accept(ModelEvent.builder(Protocol.GEMINI, ModelEventType.ITEM_DELTA)
                .delta(EventDeltaKind.REASONING, "more")
                .signature("sig_t")
                .build());
        tracker.accept(ModelEvent.builder(Protocol.GEMINI, ModelEventType.ITEM_DELTA)
                .delta(EventDeltaKind.TEXT, "Hel")
                .build());
        tracker.accept(ModelEvent.builder(Protocol.GEMINI, ModelEventType.ITEM_DELTA)
                .delta(EventDeltaKind.TEXT, "lo")
                .build());
        tracker.accept(ModelEvent.builder(Protocol.GEMINI, ModelEventType.ITEM_DELTA)
                .delta(EventDeltaKind.TOOL_ARGS, "{\"a\":")
                .itemId("c1")
                .toolName("calc")
                .signature("sig_c")
                .build());
        tracker.accept(ModelEvent.builder(Protocol.GEMINI, ModelEventType.ITEM_DELTA)
                .delta(EventDeltaKind.TOOL_ARGS, "1}")
                .itemId("c1")
                .toolName("calc")
                .build());
        tracker.closeActiveBlock();

        AiReply<String> reply = AiReply.<String>builder()
                .data("Hello")
                .text("Hello")
                .thinking("plan more")
                .message(tracker.snapshotPartial())
                .stopReason("STOP")
                .build();
        stream.complete(reply);

        Assert.assertEquals("Hello", textDeltas.toString());
        Assert.assertEquals("plan more", thinkingDeltas.toString());
        Assert.assertSame(reply, stream.await());
        Assert.assertSame(reply, stream.toFuture().get(1, TimeUnit.SECONDS));

        // Verify symmetric lifecycle + non-retroactively-mutated partial snapshots + no signature doubling
        List<AiStreamEventType> types = new ArrayList<AiStreamEventType>();
        AiStreamEvent firstThinkingDelta = null;
        AiStreamEvent thinkingEnd = null;
        AiStreamEvent toolEnd = null;
        for (AiStreamEvent e : events) {
            types.add(e.getType());
            if (e.getType() == AiStreamEventType.THINKING_DELTA && firstThinkingDelta == null) {
                firstThinkingDelta = e;
            } else if (e.getType() == AiStreamEventType.THINKING_END) {
                thinkingEnd = e;
            } else if (e.getType() == AiStreamEventType.TOOLCALL_END) {
                toolEnd = e;
            }
        }
        Assert.assertEquals(AiStreamEventType.START, types.get(0));
        Assert.assertTrue(types.contains(AiStreamEventType.THINKING_START));
        Assert.assertTrue(types.contains(AiStreamEventType.THINKING_END));
        Assert.assertTrue(types.contains(AiStreamEventType.TEXT_START));
        Assert.assertTrue(types.contains(AiStreamEventType.TEXT_END));
        Assert.assertTrue(types.contains(AiStreamEventType.TOOLCALL_START));
        Assert.assertTrue(types.contains(AiStreamEventType.TOOLCALL_END));
        Assert.assertEquals(AiStreamEventType.DONE, types.get(types.size() - 1));

        // Earlier snapshot stayed at "plan ", while THINKING_END is index 0 with non-doubled sig_t
        Assert.assertEquals("plan ", ((ReasoningBlock) firstThinkingDelta.getPartial().getBlocks().get(0)).getText());
        Assert.assertEquals(Integer.valueOf(0), thinkingEnd.getContentIndex());
        Assert.assertEquals("sig_t",
                ((ReasoningBlock) thinkingEnd.getPartial().getBlocks().get(0)).getSignature());
        Assert.assertEquals(Integer.valueOf(2), toolEnd.getContentIndex());
        Assert.assertEquals("sig_c",
                ((ToolCallBlock) toolEnd.getToolCall()).getValue().getThoughtSignature());

        // Pull iteration sees exact same sequence
        List<AiStreamEventType> pulled = new ArrayList<AiStreamEventType>();
        for (AiStreamEvent e : stream.iterable()) {
            pulled.add(e.getType());
        }
        Assert.assertEquals(types, pulled);

        // Late subscriber receives ordered replay + onComplete
        final AtomicReference<AiReply<String>> lateReply = new AtomicReference<AiReply<String>>();
        final List<AiStreamEventType> lateTypes = new ArrayList<AiStreamEventType>();
        stream.onEvent(new Consumer<AiStreamEvent>() {
            @Override
            public void accept(AiStreamEvent event) {
                lateTypes.add(event.getType());
            }
        }).onComplete(new Consumer<AiReply<String>>() {
            @Override
            public void accept(AiReply<String> r) {
                lateReply.set(r);
            }
        });
        Assert.assertEquals(types, lateTypes);
        Assert.assertSame(reply, lateReply.get());
    }

    @Test
    public void cancelPropagatesToModelCallAndUnblocksPullAndAwait() {
        final AtomicBoolean upstreamCancelled = new AtomicBoolean(false);
        final CountDownLatch callReleased = new CountDownLatch(1);
        ModelClient client = new ModelClient() {
            @Override
            public Endpoint endpoint() {
                return Endpoint.builder(Protocol.GEMINI).baseUrl("https://example.test").build();
            }

            @Override
            public Capabilities capabilities() {
                return Capabilities.none(Protocol.GEMINI);
            }

            @Override
            public void require(Capability capability) {
            }

            @Override
            public ModelResult create(ModelRequest request) throws ModelCallException {
                return ModelResult.builder().build();
            }

            @Override
            public ModelCall createStream(ModelRequest request, ModelEventListener listener) {
                return new ModelCall() {
                    @Override
                    public void cancel() {
                        upstreamCancelled.set(true);
                        callReleased.countDown();
                    }

                    @Override
                    public boolean isCancelled() {
                        return upstreamCancelled.get();
                    }

                    @Override
                    public ModelResult await() throws ModelCallException {
                        try {
                            callReleased.await(2, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return ModelResult.builder().build();
                    }

                    @Override
                    public ModelCallStatus status() {
                        return upstreamCancelled.get() ? ModelCallStatus.CANCELLED : ModelCallStatus.RUNNING;
                    }
                };
            }
        };

        AiStream<String> stream = DefaultAiStream.fromModelClient(
                client,
                ModelRequest.builder().model("gemini-2.5-flash").message(Message.user().text("hi").build()).build(),
                new Function<ModelResult, String>() {
                    @Override
                    public String apply(ModelResult result) {
                        return "";
                    }
                },
                new Executor() {
                    @Override
                    public void execute(Runnable command) {
                        Thread t = new Thread(command, "test-cancel-stream");
                        t.setDaemon(true);
                        t.start();
                    }
                });

        stream.cancel();
        Assert.assertTrue(stream.isCancelled());
        Assert.assertTrue(upstreamCancelled.get());
        Assert.assertTrue(stream.toFuture().isCancelled());

        try {
            stream.await();
            Assert.fail("Cancelled stream await must throw Ai4jException");
        } catch (Ai4jException expected) {
            Assert.assertTrue(expected.getMessage().contains("cancelled"));
        }

        // Pull iterator terminates cleanly after cancel
        int count = 0;
        for (AiStreamEvent ignored : stream.iterable()) {
            count++;
        }
        Assert.assertEquals(1, count); // START event before cancel
    }

    @Test
    public void bridgesLiveMockWebServerGeminiStreamAndDispatcherCreatesDaemonThreads() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody("data: {\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"text\":\"Hi there\"}]}}]}\n\n"
                        + "data: {\"candidates\":[{\"finishReason\":\"STOP\"}]}\n\n"));

        ModelClient client = ModelClients.of(
                Endpoint.builder(Protocol.GEMINI).baseUrl(server.url("/").toString()).build(),
                new Configuration());

        AiStream<String> stream = DefaultAiStream.fromModelClient(
                client,
                ModelRequest.builder().model("gemini-2.5-flash").message(Message.user().text("hi").build()).build(),
                new Function<ModelResult, String>() {
                    @Override
                    public String apply(ModelResult result) {
                        return ((Message) result.getItems().get(0)).textContent();
                    }
                });

        AiReply<String> reply = stream.await();
        Assert.assertEquals("Hi there", reply.getData());
        Assert.assertEquals("STOP", reply.getStopReason());

        // Error delivery invokes onError and fails await()
        DefaultAiStream<String> failing = DefaultAiStream.create();
        final AtomicReference<Throwable> seenErr = new AtomicReference<Throwable>();
        failing.onError(new Consumer<Throwable>() {
            @Override
            public void accept(Throwable throwable) {
                seenErr.set(throwable);
            }
        });
        IllegalStateException boom = new IllegalStateException("stream-boom");
        failing.fail(boom);
        Assert.assertSame(boom, seenErr.get());
        try {
            failing.await();
            Assert.fail("Expected exception");
        } catch (IllegalStateException expected) {
            Assert.assertSame(boom, expected);
        }

        // DispatcherProvider returns non-null executor usable immediately
        final CountDownLatch ran = new CountDownLatch(1);
        final AtomicBoolean daemon = new AtomicBoolean(false);
        DispatcherProvider.executor().execute(new Runnable() {
            @Override
            public void run() {
                daemon.set(Thread.currentThread().isDaemon());
                ran.countDown();
            }
        });
        Assert.assertTrue(ran.await(3, TimeUnit.SECONDS));
        Assert.assertTrue(daemon.get());
    }
}
