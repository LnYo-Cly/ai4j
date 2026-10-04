package io.github.lnyocly.ai4j.client;

import io.github.lnyocly.ai4j.listener.AbstractManagedStreamListener;
import io.github.lnyocly.ai4j.model.CallExecutionOptions;
import io.github.lnyocly.ai4j.model.Message;
import io.github.lnyocly.ai4j.model.ModelCallStatus;
import io.github.lnyocly.ai4j.model.ModelEvent;
import io.github.lnyocly.ai4j.model.ModelEventListener;
import io.github.lnyocly.ai4j.model.ModelEventType;
import io.github.lnyocly.ai4j.model.ModelRequest;
import io.github.lnyocly.ai4j.model.Protocol;
import io.github.lnyocly.ai4j.service.Configuration;
import okhttp3.Call;
import okhttp3.Request;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * B7 narrow repair contract: cancel must reach the upstream OkHttp call even
 * before the SSE response headers arrive, must own the EventSource returned by
 * the factory, and must be permanent against late callbacks and retry
 * preparation.
 *
 * <p>Positive controls live in {@code ModelStreamContractTest} (cancel after
 * headers) and {@code StreamingRepairContractTest} (B1-B6).</p>
 */
public class StreamingCancelPendingContractTest {

    private MockWebServer server;
    private Configuration configuration;

    @Before
    public void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        configuration = new Configuration();
    }

    @After
    public void tearDown() throws Exception {
        configuration.getOkHttpClient().dispatcher().cancelAll();
        configuration.getOkHttpClient().connectionPool().evictAll();
        configuration.getOkHttpClient().dispatcher().executorService().shutdownNow();
        server.shutdown();
    }

    @Test
    public void cancelDuringPendingHeadersCancelsUpstreamCall() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setHeadersDelay(3, TimeUnit.SECONDS)
                .setBody("data: [DONE]\n\n"));

        AtomicInteger doneEvents = new AtomicInteger();
        ModelCall call = ModelClients.of(
                        TestFixtures.endpoint(Protocol.CHAT_COMPLETIONS, server.url("/").toString()),
                        configuration)
                .createStream(request(0), countingListener(doneEvents));

        Assert.assertNotNull("the stream request must reach the server",
                server.takeRequest(2, TimeUnit.SECONDS));
        call.cancel();
        Assert.assertTrue(call.isCancelled());

        Assert.assertTrue("cancel must settle the model call as CANCELLED",
                awaitStatus(call, ModelCallStatus.CANCELLED, 2_000));
        Assert.assertTrue("cancel must cancel the underlying OkHttp call while headers are pending",
                awaitUpstreamCancelled(configuration, 2_000));
        awaitBounded(call, 2_000);
        Assert.assertEquals("a cancelled call must not deliver a DONE terminal event",
                0, doneEvents.get());
    }

    @Test
    public void lateFactoryReturnAfterCancelIsCancelled() throws Exception {
        final CountDownLatch factoryEntered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        BlockingFactoryConfiguration blocking =
                new BlockingFactoryConfiguration(factoryEntered, release);
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setHeadersDelay(3, TimeUnit.SECONDS)
                .setBody("data: [DONE]\n\n"));

        AtomicInteger doneEvents = new AtomicInteger();
        ModelCall call = ModelClients.of(
                        TestFixtures.endpoint(Protocol.CHAT_COMPLETIONS, server.url("/").toString()),
                        blocking)
                .createStream(request(0), countingListener(doneEvents));

        Assert.assertTrue("the EventSource factory must be entered",
                factoryEntered.await(2, TimeUnit.SECONDS));
        call.cancel();
        Assert.assertTrue(call.isCancelled());
        release.countDown();

        RecordingEventSource created = awaitCreated(blocking, 2_000);
        Assert.assertNotNull("the factory must return an EventSource after release", created);
        Assert.assertTrue("an EventSource returned after cancel must be cancelled immediately",
                awaitCancelled(created, 2_000));
        Assert.assertTrue("cancel must cancel the underlying OkHttp call",
                awaitUpstreamCancelled(blocking, 2_000));
        Assert.assertTrue("cancel must settle the model call as CANCELLED",
                awaitStatus(call, ModelCallStatus.CANCELLED, 2_000));
        awaitBounded(call, 2_000);
        Assert.assertEquals("a cancelled call must not deliver a DONE terminal event",
                0, doneEvents.get());
    }

    @Test
    public void cancelRequestIsPermanentAgainstLateOpenAndRetryPreparation() {
        ProbeListener listener = new ProbeListener();
        listener.cancelStream();
        Assert.assertTrue("cancelStream must record the cancel request",
                listener.isCancelRequested());

        listener.prepareForRetry();
        Assert.assertTrue("prepareForRetry must not clear a requested cancel",
                listener.isCancelRequested());

        FakeEventSource late = new FakeEventSource(fakeRequest());
        listener.onOpen(late, null);
        Assert.assertTrue("a late onOpen must not clear a requested cancel",
                listener.isCancelRequested());
        Assert.assertTrue("a late onOpen must not leave the EventSource live",
                late.cancelled.get());
    }

    private static ModelRequest request(int maxRetries) {
        return ModelRequest.builder()
                .protocol(Protocol.CHAT_COMPLETIONS)
                .model("test-model")
                .message(Message.user().text("hello").build())
                .execution(CallExecutionOptions.builder()
                        .firstTokenTimeoutMs(10_000)
                        .idleTimeoutMs(10_000)
                        .maxRetries(maxRetries)
                        .build())
                .build();
    }

    private static ModelEventListener countingListener(final AtomicInteger doneEvents) {
        return new ModelEventListener() {
            @Override
            public void onEvent(ModelEvent event) {
                if (event.getType() == ModelEventType.DONE) {
                    doneEvents.incrementAndGet();
                }
            }
        };
    }

    private static boolean awaitStatus(ModelCall call, ModelCallStatus expected, long timeoutMs)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (System.nanoTime() < deadline) {
            if (call.status() == expected) {
                return true;
            }
            Thread.sleep(20);
        }
        return call.status() == expected;
    }

    private static boolean awaitUpstreamCancelled(Configuration config, long timeoutMs)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (System.nanoTime() < deadline) {
            if (config.getOkHttpClient().dispatcher().runningCallsCount() == 0) {
                return true;
            }
            for (Call running : config.getOkHttpClient().dispatcher().runningCalls()) {
                if (running.isCanceled()) {
                    return true;
                }
            }
            Thread.sleep(20);
        }
        return config.getOkHttpClient().dispatcher().runningCallsCount() == 0;
    }

    private static RecordingEventSource awaitCreated(BlockingFactoryConfiguration config,
                                                     long timeoutMs) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (System.nanoTime() < deadline) {
            RecordingEventSource created = config.created.get();
            if (created != null) {
                return created;
            }
            Thread.sleep(20);
        }
        return config.created.get();
    }

    private static boolean awaitCancelled(RecordingEventSource eventSource, long timeoutMs)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (System.nanoTime() < deadline) {
            if (eventSource.cancelled.get()) {
                return true;
            }
            Thread.sleep(20);
        }
        return eventSource.cancelled.get();
    }

    private static void awaitBounded(final ModelCall call, long timeoutMs) throws Exception {
        final CountDownLatch done = new CountDownLatch(1);
        Thread waiter = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    call.await();
                } catch (Throwable ignored) {
                    // Cancelled calls may surface as an exception; only the wait bound matters here.
                } finally {
                    done.countDown();
                }
            }
        }, "cancel-await");
        waiter.setDaemon(true);
        waiter.start();
        Assert.assertTrue("call.await() must return within " + timeoutMs + "ms after cancel",
                done.await(timeoutMs, TimeUnit.MILLISECONDS));
    }

    private static Request fakeRequest() {
        return new Request.Builder().url("http://localhost/").build();
    }

    /** Minimal concrete listener: the abstract lifecycle is fully implemented upstream. */
    private static final class ProbeListener extends AbstractManagedStreamListener {
    }

    /** Records whether the transport cancelled the EventSource it owns. */
    private static final class RecordingEventSource implements EventSource {
        private final EventSource delegate;
        final AtomicBoolean cancelled = new AtomicBoolean();

        RecordingEventSource(EventSource delegate) {
            this.delegate = delegate;
        }

        @Override
        public void cancel() {
            cancelled.set(true);
            delegate.cancel();
        }

        @Override
        public Request request() {
            return delegate.request();
        }
    }

    /** Standalone EventSource double for the listener-level cancel contract. */
    private static final class FakeEventSource implements EventSource {
        private final Request request;
        final AtomicBoolean cancelled = new AtomicBoolean();

        FakeEventSource(Request request) {
            this.request = request;
        }

        @Override
        public void cancel() {
            cancelled.set(true);
        }

        @Override
        public Request request() {
            return request;
        }
    }

    /** Configuration whose factory blocks before delegating, to model a late factory return. */
    private static final class BlockingFactoryConfiguration extends Configuration {
        private final CountDownLatch entered;
        private final CountDownLatch release;
        final AtomicReference<RecordingEventSource> created =
                new AtomicReference<RecordingEventSource>();
        private final EventSource.Factory real;

        BlockingFactoryConfiguration(CountDownLatch entered, CountDownLatch release) {
            this.entered = entered;
            this.release = release;
            this.real = EventSources.createFactory(getOkHttpClient());
        }

        @Override
        public EventSource.Factory createRequestFactory() {
            return new EventSource.Factory() {
                @Override
                public EventSource newEventSource(Request request, EventSourceListener listener) {
                    entered.countDown();
                    try {
                        release.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    RecordingEventSource recording =
                            new RecordingEventSource(real.newEventSource(request, listener));
                    created.set(recording);
                    return recording;
                }
            };
        }
    }
}
