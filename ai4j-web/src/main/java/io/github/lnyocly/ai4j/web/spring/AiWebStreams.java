package io.github.lnyocly.ai4j.web.spring;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.lnyocly.ai4j.listener.SseListener;
import io.github.lnyocly.ai4j.web.sse.AiSseEvent;
import io.github.lnyocly.ai4j.web.sse.AiSseSink;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Non-blocking Spring {@link SseEmitter} bridge coordinating async producers,
 * optional keepalive heartbeat comments, {@link SseListener} bridging, and
 * at-most-once upstream cancellation on client disconnect, timeout, or error.
 */
public final class AiWebStreams {

    private static final Logger log = LoggerFactory.getLogger(AiWebStreams.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final ScheduledThreadPoolExecutor HEARTBEAT_SCHEDULER = createHeartbeatScheduler();

    private AiWebStreams() {
    }

    @FunctionalInterface
    public interface StreamTask<T> {
        void accept(T target) throws Exception;
    }

    public static SseEmitter sseEmitter(Consumer<AiSseSink> producer) {
        return sseEmitter(AiWebStreamOptions.defaults(), producer);
    }

    public static SseEmitter sseEmitter(AiWebStreamOptions options, Consumer<AiSseSink> producer) {
        AiWebStreamOptions resolved = resolveOptions(options);
        return stream(newEmitter(resolved), resolved, producer);
    }

    public static SseEmitter sseEmitter(Consumer<AiSseSink> producer, Runnable cancelHandle) {
        return sseEmitter(AiWebStreamOptions.defaults(), producer, cancelHandle);
    }

    public static SseEmitter sseEmitter(AiWebStreamOptions options,
                                        Consumer<AiSseSink> producer,
                                        Runnable cancelHandle) {
        AiWebStreamOptions resolved = resolveOptions(options);
        SseEmitter emitter = newEmitter(resolved);
        AiSseSink sink = bind(emitter, resolved);
        if (cancelHandle != null) {
            sink.onCancel(cancelHandle);
        }
        invokeProducer(sink, producer);
        return emitter;
    }

    public static SseEmitter sseEmitter(AutoCloseable upstreamResource, Consumer<AiSseSink> producer) {
        return sseEmitter(AiWebStreamOptions.defaults(), upstreamResource, producer);
    }

    public static SseEmitter sseEmitter(AiWebStreamOptions options,
                                        AutoCloseable upstreamResource,
                                        Consumer<AiSseSink> producer) {
        return sseEmitter(options, producer, toCancelRunnable(upstreamResource));
    }

    public static SseEmitter sseEmitter(Executor executor,
                                        AiWebStreamOptions options,
                                        Consumer<AiSseSink> producer) {
        AiWebStreamOptions resolved = resolveOptions(options);
        return stream(executor, newEmitter(resolved), resolved, producer);
    }

    public static SseEmitter sseEmitter(Executor executor,
                                        AiWebStreamOptions options,
                                        Consumer<AiSseSink> producer,
                                        Runnable cancelHandle) {
        if (executor == null) {
            throw new IllegalArgumentException("executor must not be null");
        }
        AiWebStreamOptions resolved = resolveOptions(options);
        SseEmitter emitter = newEmitter(resolved);
        AiSseSink sink = bind(emitter, resolved);
        if (cancelHandle != null) {
            sink.onCancel(cancelHandle);
        }
        dispatchProducer(executor, sink, producer);
        return emitter;
    }

    public static SseEmitter sseEmitter(Executor executor,
                                        AiWebStreamOptions options,
                                        AutoCloseable upstreamResource,
                                        Consumer<AiSseSink> producer) {
        return sseEmitter(executor, options, producer, toCancelRunnable(upstreamResource));
    }

    public static SseEmitter stream(SseEmitter emitter,
                                    AiWebStreamOptions options,
                                    Consumer<AiSseSink> producer) {
        AiSseSink sink = bind(emitter, options);
        invokeProducer(sink, producer);
        return emitter;
    }

    public static SseEmitter stream(Executor executor,
                                    SseEmitter emitter,
                                    AiWebStreamOptions options,
                                    Consumer<AiSseSink> producer) {
        if (executor == null) {
            throw new IllegalArgumentException("executor must not be null");
        }
        AiSseSink sink = bind(emitter, options);
        dispatchProducer(executor, sink, producer);
        return emitter;
    }

    public static AiSseSink bind(SseEmitter emitter) {
        return bind(emitter, AiWebStreamOptions.defaults());
    }

    public static AiSseSink bind(SseEmitter emitter, AiWebStreamOptions options) {
        if (emitter == null) {
            throw new IllegalArgumentException("emitter must not be null");
        }
        AiWebStreamOptions resolved = resolveOptions(options);
        SseEmitterSink sink = new SseEmitterSink(emitter, resolved);
        sink.initialize();
        return sink;
    }

    public static SseListener toSseListener(AiSseSink sink) {
        return toSseListener(sink, AiWebStreams::defaultSseListenerEvent);
    }

    public static SseListener toSseListener(AiSseSink sink,
                                            Function<SseListener, AiSseEvent> eventMapper) {
        if (sink == null) {
            throw new IllegalArgumentException("sink must not be null");
        }
        final Function<SseListener, AiSseEvent> mapper =
                eventMapper != null ? eventMapper : AiWebStreams::defaultSseListenerEvent;
        return new BridgedSseListener(sink, mapper);
    }

    public static SseEmitter sseEmitterFromListener(Executor executor,
                                                    AiWebStreamOptions options,
                                                    StreamTask<SseListener> task) {
        return sseEmitterFromListener(executor, options, task, AiWebStreams::defaultSseListenerEvent);
    }

    public static SseEmitter sseEmitterFromListener(Executor executor,
                                                    AiWebStreamOptions options,
                                                    StreamTask<SseListener> task,
                                                    Function<SseListener, AiSseEvent> eventMapper) {
        if (executor == null) {
            throw new IllegalArgumentException("executor must not be null");
        }
        if (task == null) {
            throw new IllegalArgumentException("task must not be null");
        }
        return sseEmitter(executor, options, sink -> {
            SseListener listener = toSseListener(sink, eventMapper);
            try {
                task.accept(listener);
                if (!sink.isClosed()) {
                    sink.complete();
                }
            } catch (Exception ex) {
                sink.completeWithError(ex);
            }
        });
    }

    static SseEmitter.SseEventBuilder toSpringEventBuilder(AiSseEvent event) {
        SseEmitter.SseEventBuilder builder = SseEmitter.event();
        if (event.getComment() != null) {
            for (String line : AiSseEvent.splitLines(event.getComment())) {
                if (line.isEmpty()) {
                    builder.comment("");
                } else if (line.startsWith(" ")) {
                    builder.comment(line);
                } else {
                    builder.comment(" " + line);
                }
            }
        }
        if (event.getId() != null) {
            builder.id(event.getId());
        }
        if (event.getEvent() != null) {
            builder.name(event.getEvent());
        }
        if (event.getRetryMs() != null) {
            builder.reconnectTime(event.getRetryMs().longValue());
        }
        if (event.getData() != null) {
            for (String line : AiSseEvent.splitLines(event.getData())) {
                builder.data(line);
            }
        }
        return builder;
    }

    private static AiSseEvent defaultSseListenerEvent(SseListener listener) {
        String delta = listener.getCurrStr();
        if (delta == null || delta.isEmpty()) {
            return null;
        }
        return listener.isReasoning()
                ? AiSseEvent.ofEvent("reasoning", delta)
                : AiSseEvent.ofData(delta);
    }

    private static AiWebStreamOptions resolveOptions(AiWebStreamOptions options) {
        return options != null ? options : AiWebStreamOptions.defaults();
    }

    private static SseEmitter newEmitter(AiWebStreamOptions options) {
        Long timeoutMs = options.getTimeoutMs();
        return timeoutMs == null ? new SseEmitter() : new SseEmitter(timeoutMs);
    }

    private static void invokeProducer(AiSseSink sink, Consumer<AiSseSink> producer) {
        if (producer == null) {
            throw new IllegalArgumentException("producer must not be null");
        }
        try {
            producer.accept(sink);
        } catch (Throwable ex) {
            sink.completeWithError(ex);
        }
    }

    private static void dispatchProducer(Executor executor, AiSseSink sink, Consumer<AiSseSink> producer) {
        if (producer == null) {
            throw new IllegalArgumentException("producer must not be null");
        }
        try {
            executor.execute(() -> invokeProducer(sink, producer));
        } catch (Throwable ex) {
            sink.completeWithError(ex);
        }
    }

    private static Runnable toCancelRunnable(AutoCloseable resource) {
        if (resource == null) {
            return null;
        }
        return () -> {
            try {
                resource.close();
            } catch (Exception ex) {
                log.debug("Ignoring exception while closing upstream SSE resource", ex);
            }
        };
    }

    private static ScheduledThreadPoolExecutor createHeartbeatScheduler() {
        ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(
                1,
                new DaemonThreadFactory("ai4j-web-sse-heartbeat-")
        );
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }

    static ScheduledFuture<?> heartbeatFuture(AiSseSink sink) {
        if (sink instanceof SseEmitterSink) {
            return ((SseEmitterSink) sink).heartbeatFuture.get();
        }
        return null;
    }

    static boolean isHeartbeatQueued(ScheduledFuture<?> future) {
        return future instanceof Runnable && HEARTBEAT_SCHEDULER.getQueue().contains((Runnable) future);
    }

    private static AiSseEvent buildErrorEvent(Throwable error) {
        ObjectNode node = OBJECT_MAPPER.createObjectNode();
        String message = error != null && error.getMessage() != null && !error.getMessage().trim().isEmpty()
                ? error.getMessage()
                : (error != null ? error.getClass().getSimpleName() : "SSE stream failed");
        node.put("message", message);
        if (error != null) {
            node.put("type", error.getClass().getSimpleName());
        }
        try {
            return AiSseEvent.ofEvent("error", OBJECT_MAPPER.writeValueAsString(node));
        } catch (JsonProcessingException ex) {
            return AiSseEvent.ofEvent("error", "{\"message\":\"SSE stream failed\"}");
        }
    }

    private static void runSafely(Runnable action) {
        if (action == null) {
            return;
        }
        try {
            action.run();
        } catch (Throwable ex) {
            log.debug("Ignoring exception from SSE cancel callback", ex);
        }
    }

    private enum TerminalOutcome {
        OPEN,
        COMPLETED,
        CANCELLED
    }

    private static final class SseEmitterSink implements AiSseSink {

        private final SseEmitter emitter;
        private final AiWebStreamOptions options;
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private final Object sendLock = new Object();
        private final Object cancelLock = new Object();
        private final List<Runnable> cancelActions = new ArrayList<Runnable>();
        private final AtomicReference<ScheduledFuture<?>> heartbeatFuture = new AtomicReference<ScheduledFuture<?>>();
        private TerminalOutcome outcome = TerminalOutcome.OPEN;

        private SseEmitterSink(SseEmitter emitter, AiWebStreamOptions options) {
            this.emitter = emitter;
            this.options = options;
        }

        private void initialize() {
            emitter.onCompletion(() -> terminateWithCancel(null, false, false));
            emitter.onTimeout(() -> terminateWithCancel(new TimeoutException("SSE stream timed out"), false, true));
            emitter.onError(error -> terminateWithCancel(error, false, false));
            startHeartbeat();
        }

        private void startHeartbeat() {
            long intervalMs = options.getHeartbeatIntervalMs();
            if (intervalMs <= 0L || closed.get()) {
                return;
            }
            ScheduledFuture<?> future = HEARTBEAT_SCHEDULER.scheduleWithFixedDelay(
                    this::sendHeartbeatSafely,
                    intervalMs,
                    intervalMs,
                    TimeUnit.MILLISECONDS
            );
            if (!heartbeatFuture.compareAndSet(null, future) || closed.get()) {
                future.cancel(false);
            }
        }

        private void sendHeartbeatSafely() {
            try {
                sendBuilder(toSpringEventBuilder(AiSseEvent.commentOnly(options.getHeartbeatComment())), true);
            } catch (IOException ignored) {
                // Handled inside sendBuilder when heartbeat == true.
            }
        }

        @Override
        public void send(AiSseEvent event) throws IOException {
            if (event == null) {
                throw new IllegalArgumentException("event must not be null");
            }
            sendBuilder(toSpringEventBuilder(event), false);
        }

        @Override
        public void sendComment(String comment) throws IOException {
            if (comment == null) {
                throw new IllegalArgumentException("comment must not be null");
            }
            sendBuilder(toSpringEventBuilder(AiSseEvent.commentOnly(comment)), false);
        }

        private void sendBuilder(SseEmitter.SseEventBuilder builder, boolean heartbeat) throws IOException {
            if (closed.get()) {
                if (heartbeat) {
                    return;
                }
                throw new IOException("SSE sink is already closed");
            }
            Throwable sendFailure = null;
            synchronized (sendLock) {
                if (closed.get()) {
                    if (heartbeat) {
                        return;
                    }
                    throw new IOException("SSE sink is already closed");
                }
                try {
                    emitter.send(builder);
                } catch (IOException ex) {
                    sendFailure = ex;
                } catch (RuntimeException ex) {
                    sendFailure = ex;
                }
            }
            if (sendFailure != null) {
                terminateWithCancel(sendFailure, false, false);
                if (heartbeat) {
                    return;
                }
                if (sendFailure instanceof IOException) {
                    throw (IOException) sendFailure;
                }
                throw new IOException("Failed to emit SSE event", sendFailure);
            }
        }

        @Override
        public void complete() {
            synchronized (cancelLock) {
                if (!closed.compareAndSet(false, true)) {
                    return;
                }
                outcome = TerminalOutcome.COMPLETED;
                cancelActions.clear();
            }
            cancelHeartbeat();
            synchronized (sendLock) {
                try {
                    emitter.complete();
                } catch (RuntimeException ex) {
                    log.debug("Ignoring exception while completing SseEmitter", ex);
                }
            }
        }

        @Override
        public void completeWithError(Throwable error) {
            terminateWithCancel(error, true, false);
        }

        @Override
        public void onCancel(Runnable cancelAction) {
            if (cancelAction == null) {
                return;
            }
            boolean runImmediately;
            synchronized (cancelLock) {
                if (outcome == TerminalOutcome.OPEN) {
                    cancelActions.add(cancelAction);
                    runImmediately = false;
                } else {
                    runImmediately = (outcome == TerminalOutcome.CANCELLED);
                }
            }
            if (runImmediately) {
                runSafely(cancelAction);
            }
        }

        @Override
        public boolean isClosed() {
            return closed.get();
        }

        private void terminateWithCancel(Throwable error,
                                         boolean notifyEmitterWithError,
                                         boolean notifyEmitterComplete) {
            List<Runnable> actionsToRun;
            synchronized (cancelLock) {
                if (!closed.compareAndSet(false, true)) {
                    return;
                }
                outcome = TerminalOutcome.CANCELLED;
                actionsToRun = new ArrayList<Runnable>(cancelActions);
                cancelActions.clear();
            }
            cancelHeartbeat();
            if (notifyEmitterWithError) {
                synchronized (sendLock) {
                    if (options.isIncludeErrorEvent()) {
                        try {
                            emitter.send(toSpringEventBuilder(buildErrorEvent(error)));
                        } catch (IOException ex) {
                            log.debug("Failed to send SSE error event before emitter error completion", ex);
                        } catch (RuntimeException ex) {
                            log.debug("Failed to send SSE error event before emitter error completion", ex);
                        }
                    }
                    try {
                        emitter.completeWithError(error != null ? error : new RuntimeException("SSE stream failed"));
                    } catch (RuntimeException ex) {
                        log.debug("Ignoring exception while completing SseEmitter with error", ex);
                    }
                }
            } else if (notifyEmitterComplete) {
                synchronized (sendLock) {
                    try {
                        emitter.complete();
                    } catch (RuntimeException ex) {
                        log.debug("Ignoring exception while completing SseEmitter on timeout", ex);
                    }
                }
            }
            for (Runnable action : actionsToRun) {
                runSafely(action);
            }
        }

        private void cancelHeartbeat() {
            ScheduledFuture<?> future = heartbeatFuture.getAndSet(null);
            if (future != null) {
                future.cancel(false);
            }
        }
    }

    private static final class BridgedSseListener extends SseListener {

        private final AiSseSink sink;
        private final Function<SseListener, AiSseEvent> mapper;

        private BridgedSseListener(AiSseSink sink, Function<SseListener, AiSseEvent> mapper) {
            this.sink = sink;
            this.mapper = mapper;
            this.sink.onCancel(this::cancelStream);
        }

        @Override
        protected void send() {
            if (sink.isClosed()) {
                cancelStream();
                return;
            }
            AiSseEvent event = mapper.apply(this);
            if (event == null) {
                return;
            }
            try {
                sink.send(event);
            } catch (IOException ex) {
                cancelStream();
                sink.completeWithError(ex);
            }
        }

        @Override
        protected void error(Throwable t, Response response) {
            if (!isCancelRequested() && !sink.isClosed()) {
                sink.completeWithError(t != null ? t : new IllegalStateException("SSE stream failed"));
            }
        }
    }

    private static final class DaemonThreadFactory implements ThreadFactory {

        private final String prefix;
        private final AtomicInteger sequence = new AtomicInteger(1);

        private DaemonThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, prefix + sequence.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}
