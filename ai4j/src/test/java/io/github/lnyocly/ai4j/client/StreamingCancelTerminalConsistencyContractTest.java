package io.github.lnyocly.ai4j.client;

import io.github.lnyocly.ai4j.model.Message;
import io.github.lnyocly.ai4j.model.ModelCallStatus;
import io.github.lnyocly.ai4j.model.ModelEvent;
import io.github.lnyocly.ai4j.model.ModelEventListener;
import io.github.lnyocly.ai4j.model.ModelEventType;
import io.github.lnyocly.ai4j.model.ModelRequest;
import io.github.lnyocly.ai4j.model.ModelResult;
import io.github.lnyocly.ai4j.model.Protocol;
import io.github.lnyocly.ai4j.protocol.Endpoint;
import io.github.lnyocly.ai4j.protocol.WireResponse;
import io.github.lnyocly.ai4j.transport.CallHandle;
import io.github.lnyocly.ai4j.transport.ModelTransport;
import io.github.lnyocly.ai4j.transport.SseChannel;
import io.github.lnyocly.ai4j.transport.TransportRequest;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Cancel/terminal consistency contract for the shared kernel stream.
 *
 * <p>A terminal transition that loses the settlement CAS to a concurrent
 * {@code cancel()} must not publish anything: the call stays
 * {@link ModelCallStatus#CANCELLED}, {@code await()} returns without throwing,
 * and no {@code ERROR} event is delivered. The publish-after-guard window is
 * driven deterministically with latches, never sleeps.</p>
 */
public class StreamingCancelTerminalConsistencyContractTest {

    @Test
    public void losingFailureSettlementAfterCancelDoesNotPublishFailureOrError() throws Exception {
        final CountDownLatch guardPassed = new CountDownLatch(1);
        final CountDownLatch releaseFailure = new CountDownLatch(1);
        final AtomicReference<SseChannel> channelRef = new AtomicReference<SseChannel>();

        ModelTransport transport = new ModelTransport() {
            @Override
            public WireResponse execute(TransportRequest request) {
                throw new UnsupportedOperationException("blocking path is not used");
            }

            @Override
            public CallHandle stream(TransportRequest request, SseChannel channel) {
                channelRef.set(channel);
                return new CallHandle() {
                    @Override
                    public void cancel() {
                    }

                    @Override
                    public boolean isCancelled() {
                        return false;
                    }

                    @Override
                    public void await() {
                    }
                };
            }
        };

        final List<ModelEvent> events = new CopyOnWriteArrayList<ModelEvent>();
        ModelCall call = ModelClients.of(
                        TestFixtures.endpoint(Protocol.CHAT_COMPLETIONS, "http://localhost/"), transport)
                .createStream(request(), recording(events));

        final Throwable blockingFailure = new Throwable() {
            @Override
            public String getMessage() {
                guardPassed.countDown();
                try {
                    releaseFailure.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return "blocked failure";
            }
        };

        Thread failing = new Thread(new Runnable() {
            @Override
            public void run() {
                channelRef.get().onFailure(blockingFailure, null);
            }
        }, "terminal-failure");
        failing.setDaemon(true);
        failing.start();

        Assert.assertTrue("the failure path must pass its guard before cancel wins",
                guardPassed.await(2, TimeUnit.SECONDS));
        call.cancel();
        Assert.assertEquals(ModelCallStatus.CANCELLED, call.status());
        Assert.assertTrue(call.isCancelled());
        releaseFailure.countDown();
        failing.join(2_000);
        Assert.assertFalse("the failure path must finish after release", failing.isAlive());

        Assert.assertEquals("cancel stays terminal", ModelCallStatus.CANCELLED, call.status());
        Assert.assertTrue("a losing FAILED settlement must not dispatch an ERROR event",
                noErrorEvent(events));
        try {
            ModelResult result = call.await();
            Assert.assertNotNull(result);
        } catch (ModelCallException unexpected) {
            Assert.fail("a cancelled call must not throw from await(): " + unexpected.getMessage());
        }
    }

    private static boolean noErrorEvent(List<ModelEvent> events) {
        for (ModelEvent event : events) {
            if (event.getType() == ModelEventType.ERROR) {
                return false;
            }
        }
        return true;
    }

    private static ModelRequest request() {
        return ModelRequest.builder()
                .protocol(Protocol.CHAT_COMPLETIONS)
                .model("test-model")
                .message(Message.user().text("hello").build())
                .execution(TestFixtures.fastTimeouts())
                .build();
    }

    private static ModelEventListener recording(final List<ModelEvent> events) {
        return new ModelEventListener() {
            @Override
            public void onEvent(ModelEvent event) {
                events.add(event);
            }
        };
    }
}
