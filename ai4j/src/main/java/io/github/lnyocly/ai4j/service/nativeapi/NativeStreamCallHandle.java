package io.github.lnyocly.ai4j.service.nativeapi;

import io.github.lnyocly.ai4j.model.ModelCallStatus;
import io.github.lnyocly.ai4j.protocol.RawSseEvent;
import io.github.lnyocly.ai4j.transport.CallHandle;
import io.github.lnyocly.ai4j.transport.SseChannel;
import okhttp3.Response;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Default {@link NativeStreamCall}: receives raw transport callbacks, forwards
 * them to the caller's {@link NativeSseListener} and tracks the terminal state.
 */
final class NativeStreamCallHandle implements NativeStreamCall, SseChannel {

    private final NativeSseListener listener;
    private final NativeErrorMapper errorMapper;
    private final CountDownLatch settled = new CountDownLatch(1);
    private final AtomicReference<ModelCallStatus> status =
            new AtomicReference<ModelCallStatus>(ModelCallStatus.RUNNING);
    private final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
    private volatile CallHandle handle;

    NativeStreamCallHandle(NativeSseListener listener, NativeErrorMapper errorMapper) {
        this.listener = listener;
        this.errorMapper = errorMapper;
    }

    /** Attach the transport handle; a cancel requested before binding is replayed. */
    void bind(CallHandle handle) {
        this.handle = handle;
        if (status.get() == ModelCallStatus.CANCELLED) {
            handle.cancel();
        }
    }

    @Override
    public void onOpen() {
        listener.onOpen();
    }

    @Override
    public void onEvent(RawSseEvent event) {
        listener.onEvent(event.getId(), event.getType(), event.getData());
    }

    @Override
    public void onClosed() {
        settle(ModelCallStatus.COMPLETED);
        listener.onClosed();
    }

    @Override
    public void onFailure(Throwable failureCause, Response response) {
        onFailure(failureCause, response, null);
    }

    @Override
    public void onFailure(Throwable failureCause, Response response, String responseBody) {
        Throwable nativeFailure = response == null
                ? failureCause
                : errorMapper.fromHttp(response.code(), responseBody,
                        NativeProtocolSupport.headersOf(response));
        if (nativeFailure == null) {
            nativeFailure = failureCause;
        }
        failure.compareAndSet(null, nativeFailure);
        settle(ModelCallStatus.FAILED);
        listener.onFailure(nativeFailure);
    }

    @Override
    public void onRetrying(Throwable failureCause, int attempt, int maxAttempts) {
        // The native entry performs a single attempt; retry policy is not part of this surface.
    }

    @Override
    public void cancel() {
        if (settle(ModelCallStatus.CANCELLED)) {
            CallHandle active = handle;
            if (active != null) {
                active.cancel();
            }
        }
    }

    @Override
    public boolean isCancelled() {
        return status.get() == ModelCallStatus.CANCELLED;
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
        return status.get();
    }

    @Override
    public Throwable failure() {
        return failure.get();
    }

    private boolean settle(ModelCallStatus terminal) {
        if (status.compareAndSet(ModelCallStatus.RUNNING, terminal)) {
            settled.countDown();
            return true;
        }
        return false;
    }
}
