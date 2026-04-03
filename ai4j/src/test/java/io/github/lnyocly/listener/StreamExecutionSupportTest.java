package io.github.lnyocly.listener;

import io.github.lnyocly.ai4j.listener.ManagedStreamListener;
import io.github.lnyocly.ai4j.listener.StreamExecutionOptions;
import io.github.lnyocly.ai4j.listener.StreamExecutionSupport;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class StreamExecutionSupportTest {

    @Test
    public void shouldRetryBeforeFirstEventAndStopAfterSuccess() throws Exception {
        RecordingManagedStreamListener listener = new RecordingManagedStreamListener();
        AtomicInteger attempts = new AtomicInteger();

        StreamExecutionSupport.execute(
                listener,
                StreamExecutionOptions.builder().maxRetries(2).retryBackoffMs(0L).build(),
                () -> {
                    if (attempts.incrementAndGet() == 1) {
                        listener.recordFailure(new RuntimeException("connect failed"));
                    } else {
                        listener.clearFailure();
                    }
                }
        );

        Assert.assertEquals(2, attempts.get());
        Assert.assertEquals(1, listener.retries.size());
        Assert.assertEquals("connect failed|2/3", listener.retries.get(0));
        Assert.assertEquals(1, listener.prepareForRetryCalls);
        Assert.assertNull(listener.getFailure());
    }

    @Test
    public void shouldNotRetryAfterFirstEventArrives() throws Exception {
        RecordingManagedStreamListener listener = new RecordingManagedStreamListener();
        listener.receivedEvent = true;
        AtomicInteger attempts = new AtomicInteger();

        StreamExecutionSupport.execute(
                listener,
                StreamExecutionOptions.builder().maxRetries(3).retryBackoffMs(0L).build(),
                () -> {
                    attempts.incrementAndGet();
                    listener.recordFailure(new RuntimeException("stream stalled"));
                }
        );

        Assert.assertEquals(1, attempts.get());
        Assert.assertEquals(0, listener.retries.size());
        Assert.assertEquals(0, listener.prepareForRetryCalls);
        Assert.assertNotNull(listener.getFailure());
    }

    @Test
    public void shouldApplySafeDefaultTimeoutsWhenOptionsAreMissing() throws Exception {
        RecordingManagedStreamListener listener = new RecordingManagedStreamListener();

        StreamExecutionSupport.execute(listener, null, new StreamExecutionSupport.StreamStarter() {
            @Override
            public void start() {
            }
        });

        Assert.assertNotNull(listener.awaitedOptions);
        Assert.assertEquals(StreamExecutionSupport.DEFAULT_FIRST_TOKEN_TIMEOUT_MS, listener.awaitedOptions.getFirstTokenTimeoutMs());
        Assert.assertEquals(StreamExecutionSupport.DEFAULT_IDLE_TIMEOUT_MS, listener.awaitedOptions.getIdleTimeoutMs());
        Assert.assertEquals(StreamExecutionSupport.DEFAULT_MAX_RETRIES, listener.awaitedOptions.getMaxRetries());
        Assert.assertEquals(StreamExecutionSupport.DEFAULT_RETRY_BACKOFF_MS, listener.awaitedOptions.getRetryBackoffMs());
    }

    @Test
    public void shouldHonorConfiguredDefaultTimeoutOverrides() throws Exception {
        RecordingManagedStreamListener listener = new RecordingManagedStreamListener();
        String firstTokenPrevious = System.getProperty(StreamExecutionSupport.DEFAULT_FIRST_TOKEN_TIMEOUT_PROPERTY);
        String idlePrevious = System.getProperty(StreamExecutionSupport.DEFAULT_IDLE_TIMEOUT_PROPERTY);
        String retriesPrevious = System.getProperty(StreamExecutionSupport.DEFAULT_MAX_RETRIES_PROPERTY);
        String backoffPrevious = System.getProperty(StreamExecutionSupport.DEFAULT_RETRY_BACKOFF_PROPERTY);
        try {
            System.setProperty(StreamExecutionSupport.DEFAULT_FIRST_TOKEN_TIMEOUT_PROPERTY, "1234");
            System.setProperty(StreamExecutionSupport.DEFAULT_IDLE_TIMEOUT_PROPERTY, "5678");
            System.setProperty(StreamExecutionSupport.DEFAULT_MAX_RETRIES_PROPERTY, "2");
            System.setProperty(StreamExecutionSupport.DEFAULT_RETRY_BACKOFF_PROPERTY, "250");

            StreamExecutionSupport.execute(listener, null, new StreamExecutionSupport.StreamStarter() {
                @Override
                public void start() {
                }
            });

            Assert.assertNotNull(listener.awaitedOptions);
            Assert.assertEquals(1234L, listener.awaitedOptions.getFirstTokenTimeoutMs());
            Assert.assertEquals(5678L, listener.awaitedOptions.getIdleTimeoutMs());
            Assert.assertEquals(2, listener.awaitedOptions.getMaxRetries());
            Assert.assertEquals(250L, listener.awaitedOptions.getRetryBackoffMs());
        } finally {
            restoreProperty(StreamExecutionSupport.DEFAULT_FIRST_TOKEN_TIMEOUT_PROPERTY, firstTokenPrevious);
            restoreProperty(StreamExecutionSupport.DEFAULT_IDLE_TIMEOUT_PROPERTY, idlePrevious);
            restoreProperty(StreamExecutionSupport.DEFAULT_MAX_RETRIES_PROPERTY, retriesPrevious);
            restoreProperty(StreamExecutionSupport.DEFAULT_RETRY_BACKOFF_PROPERTY, backoffPrevious);
        }
    }

    private static final class RecordingManagedStreamListener implements ManagedStreamListener {
        private Throwable failure;
        private boolean receivedEvent;
        private boolean cancelRequested;
        private int prepareForRetryCalls;
        private final List<String> retries = new ArrayList<String>();
        private StreamExecutionOptions awaitedOptions;

        @Override
        public void awaitCompletion(StreamExecutionOptions options) {
            this.awaitedOptions = options;
        }

        @Override
        public Throwable getFailure() {
            return failure;
        }

        @Override
        public void recordFailure(Throwable failure) {
            this.failure = failure;
        }

        @Override
        public void clearFailure() {
            this.failure = null;
        }

        @Override
        public void prepareForRetry() {
            prepareForRetryCalls += 1;
            clearFailure();
            receivedEvent = false;
        }

        @Override
        public boolean hasReceivedEvent() {
            return receivedEvent;
        }

        @Override
        public boolean isCancelRequested() {
            return cancelRequested;
        }

        @Override
        public void onRetrying(Throwable failure, int attempt, int maxAttempts) {
            String message = failure == null ? "unknown" : failure.getMessage();
            retries.add(message + "|" + attempt + "/" + maxAttempts);
        }
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
}
