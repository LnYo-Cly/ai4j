package io.github.lnyocly.ai4j.service.nativeapi;

import io.github.lnyocly.ai4j.model.ModelCallStatus;
import io.github.lnyocly.ai4j.protocol.RawSseEvent;
import io.github.lnyocly.ai4j.transport.CallHandle;
import io.github.lnyocly.ai4j.transport.SseChannel;
import okhttp3.Response;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Default {@link NativeStreamCall}: receives raw transport callbacks, forwards
 * them to the caller's {@link NativeSseListener} and tracks the terminal state.
 *
 * <p>Terminal state is decided once, atomically: the first of an in-band
 * protocol error, a protocol success terminal, a transport failure, or a
 * cancellation wins, and every later event/terminal callback is dropped. A
 * clean transport close without the protocol's required terminal is an
 * incomplete failure rather than a completion.</p>
 *
 * <p>Callback-failure policy: an exception thrown by {@code onEvent}/{@code
 * onOpen} is the call's real failure (the original cause is preserved, never
 * rewritten into a synthetic HTTP 200). An exception thrown by a terminal
 * {@code onClosed}/{@code onFailure} callback is contained, because the call has
 * already settled and the callback cannot resurrect it or hang {@code await()}.</p>
 */
final class NativeStreamCallHandle implements NativeStreamCall, SseChannel {

    private final NativeSseListener listener;
    private final NativeErrorMapper errorMapper;
    private final NativeStreamSemantics semantics;
    private final CountDownLatch settled = new CountDownLatch(1);
    private final Object lock = new Object();
    private volatile ModelCallStatus status = ModelCallStatus.RUNNING;
    private volatile Throwable failure;
    private volatile boolean completionSignalSeen;
    private volatile CallHandle handle;

    NativeStreamCallHandle(NativeSseListener listener, NativeErrorMapper errorMapper,
                           NativeStreamSemantics semantics) {
        this.listener = Objects.requireNonNull(listener, "listener is required");
        this.errorMapper = errorMapper;
        this.semantics = semantics;
    }

    /** Attach the transport handle; a call already terminal cancels it to release upstream. */
    void bind(CallHandle handle) {
        this.handle = handle;
        if (status != ModelCallStatus.RUNNING) {
            handle.cancel();
        }
    }

    @Override
    public void onOpen() {
        if (status != ModelCallStatus.RUNNING) {
            return;
        }
        listener.onOpen();
    }

    @Override
    public void onEvent(RawSseEvent event) {
        if (status != ModelCallStatus.RUNNING) {
            return;
        }
        String id = event.getId();
        String type = event.getType();
        String data = event.getData();
        NativeStreamEvent typed = semantics.decode(id, type, data);
        if (typed.isCompletionSignal()) {
            completionSignalSeen = true;
        }
        listener.onEvent(id, type, data);
        if (status != ModelCallStatus.RUNNING) {
            return;
        }
        NativeStreamError error = typed.getError();
        if (error != null) {
            settleTerminal(ModelCallStatus.FAILED, error.toException());
            return;
        }
        if (typed.isTerminal()) {
            settleTerminal(ModelCallStatus.COMPLETED, null);
        }
    }

    @Override
    public void onClosed() {
        if (status != ModelCallStatus.RUNNING) {
            return;
        }
        if (completionSignalSeen) {
            settleTerminal(ModelCallStatus.COMPLETED, null);
        } else {
            settleTerminal(ModelCallStatus.FAILED, semantics.incompleteFailure());
        }
    }

    @Override
    public void onFailure(Throwable failureCause, Response response) {
        onFailure(failureCause, response, null);
    }

    @Override
    public void onFailure(Throwable failureCause, Response response, String responseBody) {
        if (status != ModelCallStatus.RUNNING) {
            return;
        }
        Throwable nativeFailure = failureCause;
        if (response != null && response.code() >= 400) {
            Throwable mapped = errorMapper.fromHttp(response.code(), responseBody,
                    NativeProtocolSupport.headersOf(response));
            if (mapped != null) {
                nativeFailure = mapped;
            }
        }
        if (nativeFailure == null) {
            nativeFailure = semantics.incompleteFailure();
        }
        settleTerminal(ModelCallStatus.FAILED, nativeFailure);
    }

    @Override
    public void onRetrying(Throwable failureCause, int attempt, int maxAttempts) {
        if (status != ModelCallStatus.RUNNING) {
            return;
        }
        listener.onRetrying(attempt, maxAttempts);
    }

    @Override
    public void cancel() {
        settleTerminal(ModelCallStatus.CANCELLED, null);
    }

    @Override
    public boolean isCancelled() {
        return status == ModelCallStatus.CANCELLED;
    }

    @Override
    public void await() throws InterruptedException {
        settled.await();
        CallHandle active = handle;
        if (active != null) {
            active.await();
        }
    }

    @Override
    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        return settled.await(timeout, unit);
    }

    @Override
    public ModelCallStatus status() {
        return status;
    }

    @Override
    public Throwable failure() {
        return failure;
    }

    private boolean settleTerminal(ModelCallStatus terminal, Throwable terminalFailure) {
        synchronized (lock) {
            if (status != ModelCallStatus.RUNNING) {
                return false;
            }
            failure = terminalFailure;
            status = terminal;
            settled.countDown();
        }
        releaseUpstream();
        if (terminal == ModelCallStatus.COMPLETED) {
            invokeQuietly(new Runnable() {
                @Override
                public void run() {
                    listener.onClosed();
                }
            });
        } else if (terminal == ModelCallStatus.FAILED) {
            final Throwable delivered = terminalFailure;
            invokeQuietly(new Runnable() {
                @Override
                public void run() {
                    listener.onFailure(delivered);
                }
            });
        }
        return true;
    }

    private void releaseUpstream() {
        CallHandle active = handle;
        if (active != null) {
            try {
                active.cancel();
            } catch (Exception ignored) {
                // Best effort release; the terminal state is already decided.
            }
        }
    }

    private static void invokeQuietly(Runnable callback) {
        try {
            callback.run();
        } catch (Throwable ignored) {
            // Terminal callbacks cannot change the settled state or hang await().
        }
    }
}

