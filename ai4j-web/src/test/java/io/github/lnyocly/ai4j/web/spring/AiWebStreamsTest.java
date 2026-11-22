package io.github.lnyocly.ai4j.web.spring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lnyocly.ai4j.listener.SseListener;
import io.github.lnyocly.ai4j.web.sse.AiSseEvent;
import io.github.lnyocly.ai4j.web.sse.AiSseSink;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class AiWebStreamsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void shouldEmitAsyncEventsInOrderAndCompleteWithoutRunningCancelOnNormalCompletion() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        RecordingSseEmitter emitter = new RecordingSseEmitter(30_000L);
        AtomicInteger cancelCount = new AtomicInteger(0);
        CountDownLatch startedLatch = new CountDownLatch(1);
        CountDownLatch releaseLatch = new CountDownLatch(1);

        try {
            AiWebStreams.stream(executor, emitter, AiWebStreamOptions.defaults(), sink -> {
                sink.onCancel(cancelCount::incrementAndGet);
                startedLatch.countDown();
                try {
                    Assert.assertTrue(releaseLatch.await(3, TimeUnit.SECONDS));
                    sink.send(AiSseEvent.ofEvent("1", "delta", "hello"));
                    sink.send(AiSseEvent.ofData("line1\nline2"));
                    sink.sendComment("keepalive");
                    sink.complete();
                } catch (Exception ex) {
                    sink.completeWithError(ex);
                }
            });

            Assert.assertTrue(startedLatch.await(3, TimeUnit.SECONDS));
            releaseLatch.countDown();
            Assert.assertTrue(emitter.awaitCompletion(3, TimeUnit.SECONDS));

            // Simulate container completion callback arriving after sink.complete().
            emitter.triggerCompletion();

            Assert.assertTrue(emitter.isCompleted());
            Assert.assertNull(emitter.getCompletedError());
            Assert.assertEquals(0, cancelCount.get());
            Assert.assertEquals(3, emitter.wireFrames.size());
            Assert.assertEquals("id:1\nevent:delta\ndata:hello\n\n", emitter.wireFrames.get(0));
            Assert.assertEquals("data:line1\ndata:line2\n\n", emitter.wireFrames.get(1));
            Assert.assertEquals(": keepalive\n\n", emitter.wireFrames.get(2));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void shouldCancelUpstreamHandleExactlyOnceOnTimeoutAndSubsequentCallbacks() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        RecordingSseEmitter emitter = new RecordingSseEmitter(5_000L);
        AtomicInteger cancelCount = new AtomicInteger(0);
        CountDownLatch producerSubscribed = new CountDownLatch(1);
        CountDownLatch producerCancelled = new CountDownLatch(1);
        AtomicReference<AiSseSink> capturedSink = new AtomicReference<AiSseSink>();

        try {
            AiWebStreams.stream(executor, emitter, AiWebStreamOptions.defaults(), sink -> {
                capturedSink.set(sink);
                sink.onCancel(() -> {
                    cancelCount.incrementAndGet();
                    producerCancelled.countDown();
                });
                producerSubscribed.countDown();
            });

            Assert.assertTrue(producerSubscribed.await(3, TimeUnit.SECONDS));
            emitter.triggerTimeout();
            emitter.triggerCompletion();
            emitter.triggerError(new IOException("late container error"));
            capturedSink.get().complete();
            capturedSink.get().completeWithError(new RuntimeException("late producer error"));

            Assert.assertTrue(producerCancelled.await(3, TimeUnit.SECONDS));
            Assert.assertEquals(1, cancelCount.get());
            Assert.assertTrue(capturedSink.get().isClosed());
            Assert.assertTrue(emitter.isCompleted());

            // Late onCancel registered after cancellation must fire once immediately.
            AtomicInteger lateCancelCount = new AtomicInteger(0);
            capturedSink.get().onCancel(lateCancelCount::incrementAndGet);
            Assert.assertEquals(1, lateCancelCount.get());

            try {
                capturedSink.get().send(AiSseEvent.ofData("after-close"));
                Assert.fail("Expected IOException when sending to closed sink");
            } catch (IOException expected) {
                Assert.assertTrue(expected.getMessage().contains("closed"));
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void shouldCancelUpstreamAutoCloseableOnceOnSendIoException() throws Exception {
        RecordingSseEmitter emitter = new RecordingSseEmitter(1_000L);
        emitter.setSendFailure(new IOException("Broken pipe"));
        AtomicInteger closeCount = new AtomicInteger(0);
        AutoCloseable upstream = closeCount::incrementAndGet;

        AiSseSink sink = AiWebStreams.bind(emitter, AiWebStreamOptions.defaults());
        sink.onCancel(() -> {
            try {
                upstream.close();
            } catch (Exception ignored) {
            }
        });

        try {
            sink.send(AiSseEvent.ofData("chunk"));
            Assert.fail("Expected IOException from broken pipe");
        } catch (IOException expected) {
            Assert.assertEquals("Broken pipe", expected.getMessage());
        }

        emitter.triggerError(new IOException("Reset"));
        emitter.triggerCompletion();
        sink.completeWithError(new RuntimeException("downstream"));

        Assert.assertEquals(1, closeCount.get());
        Assert.assertTrue(sink.isClosed());
    }

    @Test
    public void shouldSupportRunnableAndAutoCloseableCancelOverloadsOnRealSseEmitter() throws Exception {
        AtomicInteger runnableCancels = new AtomicInteger(0);
        AiWebStreamOptions options = AiWebStreamOptions.builder()
                .timeoutMs(12_345L)
                .build();
        SseEmitter runnableEmitter = AiWebStreams.sseEmitter(
                options,
                sink -> {
                    try {
                        sink.send(AiSseEvent.ofData("ok"));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                },
                runnableCancels::incrementAndGet
        );
        Assert.assertEquals(Long.valueOf(12_345L), runnableEmitter.getTimeout());
        runnableEmitter.complete();
        Assert.assertEquals(0, runnableCancels.get());

        AtomicInteger closeableCancels = new AtomicInteger(0);
        AtomicReference<AiSseSink> captured = new AtomicReference<AiSseSink>();
        SseEmitter closeableEmitter = AiWebStreams.sseEmitter(
                (AutoCloseable) closeableCancels::incrementAndGet,
                sink -> captured.set(sink)
        );
        Assert.assertNotNull(closeableEmitter);
        captured.get().completeWithError(new IllegalStateException("aborted"));
        Assert.assertEquals(1, closeableCancels.get());
    }

    @Test
    public void shouldScheduleHeartbeatPingAndStopCleanlyOnClose() throws Exception {
        RecordingSseEmitter emitter = new RecordingSseEmitter(30_000L);
        AiWebStreamOptions options = AiWebStreamOptions.builder()
                .heartbeatIntervalMs(20L)
                .heartbeatComment("ping")
                .build();

        AiSseSink sink = AiWebStreams.bind(emitter, options);
        ScheduledFuture<?> future = AiWebStreams.heartbeatFuture(sink);
        Assert.assertNotNull(future);
        Assert.assertFalse(future.isCancelled());

        String first = emitter.frameQueue.poll(3, TimeUnit.SECONDS);
        String second = emitter.frameQueue.poll(3, TimeUnit.SECONDS);

        Assert.assertEquals(": ping\n\n", first);
        Assert.assertEquals(": ping\n\n", second);

        sink.complete();
        Assert.assertTrue(sink.isClosed());
        Assert.assertTrue(emitter.isCompleted());
        Assert.assertTrue(future.isCancelled());
        Assert.assertTrue(future.isDone());
        Assert.assertFalse(AiWebStreams.isHeartbeatQueued(future));

        // Verify timeout also cancels and purges the heartbeat task deterministically.
        RecordingSseEmitter timeoutEmitter = new RecordingSseEmitter(30_000L);
        AiSseSink timeoutSink = AiWebStreams.bind(timeoutEmitter, options);
        ScheduledFuture<?> timeoutFuture = AiWebStreams.heartbeatFuture(timeoutSink);
        Assert.assertNotNull(timeoutFuture);
        timeoutEmitter.triggerTimeout();
        Assert.assertTrue(timeoutSink.isClosed());
        Assert.assertTrue(timeoutFuture.isCancelled());
        Assert.assertTrue(timeoutFuture.isDone());
        Assert.assertFalse(AiWebStreams.isHeartbeatQueued(timeoutFuture));
    }

    @Test
    public void shouldBridgeSseEmitterFromListenerAndHandleExecutorFailures() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            AiWebStreamOptions options = AiWebStreamOptions.builder()
                    .timeoutMs(8_000L)
                    .build();
            SseEmitter okEmitter = AiWebStreams.sseEmitterFromListener(
                    executor,
                    options,
                    listener -> {
                        listener.onEvent(null, null, null,
                                "{\"choices\":[{\"delta\":{\"content\":\"hi\"},\"finish_reason\":\"stop\"}]}");
                    },
                    listener -> AiSseEvent.ofData("mapped:" + listener.getCurrStr())
            );
            Assert.assertEquals(Long.valueOf(8_000L), okEmitter.getTimeout());

            CountDownLatch errorHandled = new CountDownLatch(1);
            AtomicInteger cancels = new AtomicInteger(0);
            Executor failingExecutor = command -> {
                throw new IllegalStateException("pool full");
            };
            SseEmitter rejectedEmitter = AiWebStreams.sseEmitter(
                    failingExecutor,
                    options,
                    sink -> {
                    },
                    () -> {
                        cancels.incrementAndGet();
                        errorHandled.countDown();
                    }
            );
            Assert.assertNotNull(rejectedEmitter);
            Assert.assertTrue(errorHandled.await(3, TimeUnit.SECONDS));
            Assert.assertEquals(1, cancels.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void shouldValidateOptionsAndSseEventValueSemantics() {
        AiWebStreamOptions defaults = AiWebStreamOptions.defaults();
        AiWebStreamOptions modified = defaults.toBuilder()
                .timeoutMs(1000L)
                .heartbeatIntervalMs(50L)
                .heartbeatComment(" ")
                .includeErrorEvent(true)
                .build();
        Assert.assertEquals(AiWebStreamOptions.DEFAULT_HEARTBEAT_COMMENT, modified.getHeartbeatComment());
        Assert.assertTrue(modified.isIncludeErrorEvent());
        Assert.assertNotEquals(defaults, modified);
        Assert.assertEquals(modified, modified.toBuilder().build());
        Assert.assertEquals(modified.hashCode(), modified.toBuilder().build().hashCode());
        Assert.assertTrue(modified.toString().contains("heartbeatIntervalMs=50"));

        try {
            AiWebStreamOptions.builder().timeoutMs(-1L).build();
            Assert.fail("Expected IllegalArgumentException for negative timeoutMs");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("timeoutMs"));
        }

        try {
            AiWebStreamOptions.builder().heartbeatIntervalMs(-5L).build();
            Assert.fail("Expected IllegalArgumentException for negative heartbeatIntervalMs");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("heartbeatIntervalMs"));
        }

        AiSseEvent base = AiSseEvent.ofEvent("evt", "body");
        AiSseEvent copy = base.toBuilder().id("1").retryMs(500L).build();
        Assert.assertEquals(copy, copy.toBuilder().build());
        Assert.assertEquals(copy.hashCode(), copy.toBuilder().build().hashCode());
        Assert.assertNotEquals(base, copy);
        Assert.assertTrue(copy.toString().contains("retryMs=500"));
    }

    @Test
    public void shouldEmitErrorEventBeforeCompleteWithErrorWhenEnabled() throws Exception {
        RecordingSseEmitter emitter = new RecordingSseEmitter(5_000L);
        AiWebStreamOptions options = AiWebStreamOptions.builder()
                .includeErrorEvent(true)
                .build();

        AiSseSink sink = AiWebStreams.bind(emitter, options);
        sink.completeWithError(new IllegalStateException("upstream timeout"));

        Assert.assertTrue(sink.isClosed());
        Assert.assertTrue(emitter.isCompleted());
        Assert.assertNotNull(emitter.getCompletedError());
        Assert.assertEquals("upstream timeout", emitter.getCompletedError().getMessage());
        Assert.assertEquals(1, emitter.wireFrames.size());
        Assert.assertTrue(emitter.wireFrames.get(0).startsWith("event:error\ndata:"));

        String dataLine = emitter.wireFrames.get(0)
                .replace("event:error\ndata:", "")
                .trim();
        JsonNode errorJson = MAPPER.readTree(dataLine);
        Assert.assertEquals("upstream timeout", errorJson.get("message").asText());
        Assert.assertEquals("IllegalStateException", errorJson.get("type").asText());
    }

    @Test
    public void shouldBridgeAi4jSseListenerDeltasCompletionAndCancellation() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        RecordingSseEmitter emitter = new RecordingSseEmitter(5_000L);
        CountDownLatch listenerReady = new CountDownLatch(1);
        CountDownLatch allowClose = new CountDownLatch(1);
        AtomicReference<SseListener> capturedListener = new AtomicReference<SseListener>();

        try {
            AiSseSink sink = AiWebStreams.bind(emitter, AiWebStreamOptions.defaults());
            executor.execute(() -> {
                SseListener listener = AiWebStreams.toSseListener(sink);
                capturedListener.set(listener);
                listenerReady.countDown();
                listener.onEvent(null, null, null,
                        "{\"choices\":[{\"delta\":{\"reasoning_content\":\"think\"},\"finish_reason\":null}]}");
                listener.onEvent(null, null, null,
                        "{\"choices\":[{\"delta\":{\"content\":\"answer\"},\"finish_reason\":null}]}");
                listener.onEvent(null, null, null, "[DONE]");
                try {
                    allowClose.await(3, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                sink.complete();
            });

            Assert.assertTrue(listenerReady.await(3, TimeUnit.SECONDS));
            Assert.assertEquals("event:reasoning\ndata:think\n\n", emitter.frameQueue.poll(3, TimeUnit.SECONDS));
            Assert.assertEquals("data:answer\n\n", emitter.frameQueue.poll(3, TimeUnit.SECONDS));

            allowClose.countDown();
            Assert.assertTrue(emitter.awaitCompletion(3, TimeUnit.SECONDS));
            Assert.assertFalse(capturedListener.get().isCancelRequested());

            // Verify cancellation propagation from sink to SseListener.
            RecordingSseEmitter cancelEmitter = new RecordingSseEmitter(5_000L);
            AiSseSink cancelSink = AiWebStreams.bind(cancelEmitter, AiWebStreamOptions.defaults());
            SseListener cancelListener = AiWebStreams.toSseListener(cancelSink);
            cancelEmitter.triggerTimeout();
            Assert.assertTrue(cancelListener.isCancelRequested());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void shouldFormatAiSseEventWireFramesSafelyAndRejectHeaderInjection() {
        AiSseEvent full = AiSseEvent.builder()
                .comment("header")
                .id("evt-1")
                .event("message")
                .retryMs(1500L)
                .data("first\nsecond\r\n leading")
                .build();

        String wire = full.toWireFormat();
        Assert.assertEquals(
                ": header\n"
                        + "id: evt-1\n"
                        + "event: message\n"
                        + "retry: 1500\n"
                        + "data: first\n"
                        + "data: second\n"
                        + "data:  leading\n\n",
                wire
        );

        try {
            AiSseEvent.builder().id("bad\nid").data("x").build();
            Assert.fail("Expected IllegalArgumentException for newline in SSE id");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("SSE id"));
        }

        try {
            AiSseEvent.ofEvent("bad\nevent", "x");
            Assert.fail("Expected IllegalArgumentException for newline in SSE event");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("SSE event"));
        }
    }

    @Test
    public void shouldContainZeroServletOrFastjson2ReferencesInCompiledConstantPools() throws Exception {
        Path classesDir = Files.exists(Paths.get("target", "classes"))
                ? Paths.get("target", "classes")
                : Paths.get("ai4j-web", "target", "classes");
        Assert.assertTrue("Expected compiled classes directory at " + classesDir.toAbsolutePath(), Files.isDirectory(classesDir));

        List<String> forbiddenPrefixes = Arrays.asList(
                "javax/servlet/",
                "javax.servlet.",
                "jakarta/servlet/",
                "jakarta.servlet.",
                "com/alibaba/fastjson2/",
                "com.alibaba.fastjson2."
        );

        List<Path> classFiles = new ArrayList<Path>();
        try (Stream<Path> walk = Files.walk(classesDir)) {
            walk.filter(path -> path.toString().endsWith(".class")).forEach(classFiles::add);
        }
        Assert.assertFalse("Expected compiled .class files in " + classesDir, classFiles.isEmpty());

        for (Path classFile : classFiles) {
            List<String> utf8Constants = readConstantPoolUtf8Entries(classFile);
            for (String constant : utf8Constants) {
                for (String forbidden : forbiddenPrefixes) {
                    Assert.assertFalse(
                            "Forbidden reference '" + forbidden + "' found in " + classFile + ": " + constant,
                            constant.contains(forbidden)
                    );
                }
            }
        }
    }

    private static List<String> readConstantPoolUtf8Entries(Path classFile) throws IOException {
        List<String> entries = new ArrayList<String>();
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(classFile)))) {
            int magic = in.readInt();
            Assert.assertEquals("Invalid class file magic in " + classFile, 0xCAFEBABE, magic);
            in.readUnsignedShort(); // minor
            in.readUnsignedShort(); // major
            int constantPoolCount = in.readUnsignedShort();
            for (int i = 1; i < constantPoolCount; i++) {
                int tag = in.readUnsignedByte();
                switch (tag) {
                    case 1: // CONSTANT_Utf8
                        int length = in.readUnsignedShort();
                        byte[] bytes = new byte[length];
                        in.readFully(bytes);
                        entries.add(new String(bytes, StandardCharsets.UTF_8));
                        break;
                    case 3: // Integer
                    case 4: // Float
                    case 9: // Fieldref
                    case 10: // Methodref
                    case 11: // InterfaceMethodref
                    case 12: // NameAndType
                    case 17: // Dynamic
                    case 18: // InvokeDynamic
                        skipBytesFully(in, 4);
                        break;
                    case 5: // Long
                    case 6: // Double
                        skipBytesFully(in, 8);
                        i++; // occupies two slots
                        break;
                    case 7: // Class
                    case 8: // String
                    case 16: // MethodType
                    case 19: // Module
                    case 20: // Package
                        skipBytesFully(in, 2);
                        break;
                    case 15: // MethodHandle
                        skipBytesFully(in, 3);
                        break;
                    default:
                        throw new IOException("Unsupported constant pool tag " + tag + " in " + classFile);
                }
            }
        }
        return entries;
    }

    private static void skipBytesFully(DataInputStream in, int count) throws IOException {
        int remaining = count;
        while (remaining > 0) {
            int skipped = in.skipBytes(remaining);
            if (skipped <= 0) {
                if (in.read() < 0) {
                    throw new IOException("Unexpected EOF while skipping constant pool bytes");
                }
                remaining--;
            } else {
                remaining -= skipped;
            }
        }
    }

    private static final class RecordingSseEmitter extends SseEmitter {

        private final List<String> wireFrames = new CopyOnWriteArrayList<String>();
        private final BlockingQueue<String> frameQueue = new LinkedBlockingQueue<String>();
        private final AtomicBoolean completed = new AtomicBoolean(false);
        private final AtomicReference<Throwable> completedError = new AtomicReference<Throwable>();
        private final CountDownLatch completionLatch = new CountDownLatch(1);
        private volatile IOException sendFailure;
        private volatile Runnable completionCallback;
        private volatile Runnable timeoutCallback;
        private volatile Consumer<Throwable> errorCallback;

        private RecordingSseEmitter(Long timeout) {
            super(timeout);
        }

        private void setSendFailure(IOException sendFailure) {
            this.sendFailure = sendFailure;
        }

        @Override
        public void send(Object object) throws IOException {
            send(object, null);
        }

        @Override
        public synchronized void send(Object object, MediaType mediaType) throws IOException {
            if (sendFailure != null) {
                throw sendFailure;
            }
            String text = String.valueOf(object);
            wireFrames.add(text);
            frameQueue.offer(text);
        }

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            if (sendFailure != null) {
                throw sendFailure;
            }
            Set<ResponseBodyEmitter.DataWithMediaType> parts = builder.build();
            StringBuilder frame = new StringBuilder();
            for (ResponseBodyEmitter.DataWithMediaType part : parts) {
                if (part.getData() != null) {
                    frame.append(part.getData());
                }
            }
            String rendered = frame.toString();
            wireFrames.add(rendered);
            frameQueue.offer(rendered);
        }

        @Override
        public synchronized void complete() {
            completed.set(true);
            completionLatch.countDown();
            super.complete();
        }

        @Override
        public synchronized void completeWithError(Throwable ex) {
            completed.set(true);
            completedError.set(ex);
            completionLatch.countDown();
            super.completeWithError(ex);
        }

        @Override
        public synchronized void onCompletion(Runnable callback) {
            this.completionCallback = callback;
            super.onCompletion(callback);
        }

        @Override
        public synchronized void onTimeout(Runnable callback) {
            this.timeoutCallback = callback;
            super.onTimeout(callback);
        }

        @Override
        public synchronized void onError(Consumer<Throwable> callback) {
            this.errorCallback = callback;
            super.onError(callback);
        }

        private void triggerTimeout() {
            if (timeoutCallback != null) {
                timeoutCallback.run();
            }
        }

        private void triggerCompletion() {
            if (completionCallback != null) {
                completionCallback.run();
            }
        }

        private void triggerError(Throwable error) {
            if (errorCallback != null) {
                errorCallback.accept(error);
            }
        }

        private boolean awaitCompletion(long timeout, TimeUnit unit) throws InterruptedException {
            return completionLatch.await(timeout, unit);
        }

        private boolean isCompleted() {
            return completed.get();
        }

        private Throwable getCompletedError() {
            return completedError.get();
        }
    }
}
