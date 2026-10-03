package io.github.lnyocly.ai4j.transport;

import io.github.lnyocly.ai4j.listener.AbstractManagedStreamListener;
import io.github.lnyocly.ai4j.listener.RetryEligibility;
import io.github.lnyocly.ai4j.protocol.RawSseEvent;
import okhttp3.Response;
import okhttp3.sse.EventSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Bridges OkHttp SSE callbacks into the shared
 * {@link AbstractManagedStreamListener} lifecycle and forwards raw events to a
 * {@link SseChannel}.
 *
 * <p>This reuses the legacy retry/timeout/cancel engine
 * ({@code StreamExecutionSupport}) instead of re-implementing it (design
 * 8.2/8.4): first-token timeout, idle timeout, retry-before-first-event and
 * cancel all keep the legacy semantics.</p>
 */
final class StreamBridgeListener extends AbstractManagedStreamListener implements RetryEligibility {

    private final SseChannel channel;
    private volatile String failureBody;

    StreamBridgeListener(SseChannel channel) {
        this.channel = channel;
    }

    @Override
    public void onOpen(@NotNull EventSource eventSource, @NotNull Response response) {
        super.onOpen(eventSource, response);
        channel.onOpen();
    }

    @Override
    public void onEvent(@NotNull EventSource eventSource, @Nullable String id,
                        @Nullable String type, @NotNull String data) {
        markActivity();
        channel.onEvent(RawSseEvent.of(id, type, data));
    }

    @Override
    public void onClosed(@NotNull EventSource eventSource) {
        channel.onClosed();
        finishAttempt();
    }

    @Override
    public void onFailure(@NotNull EventSource eventSource, @Nullable Throwable t,
                          @Nullable Response response) {
        if (response != null) {
            failureBody = readBodySafely(response);
        }
        super.onFailure(eventSource, t, response);
    }

    /** Body text captured while the failure response was still readable. */
    String failureBody() {
        return failureBody;
    }

    /**
     * Retry safety gate (design 8.3 F5): only a failure that received an
     * HTTP response with a retryable status may be resent. An absent response
     * leaves the transmission state undefined, so it is never resent.
     */
    @Override
    public boolean allowsRetry(Throwable failure) {
        Response response = getFailureResponse();
        if (response == null) {
            return false;
        }
        return Retryability.isRetryableStatus(response.code());
    }

    @Override
    protected void resetRetryState() {
        failureBody = null;
    }

    private static String readBodySafely(Response response) {
        try {
            return response.body() == null ? null : response.body().string();
        } catch (Exception ignored) {
            return null;
        }
    }

    @Override
    protected void error(Throwable t, Response response) {
        channel.onFailure(t, response);
    }

    @Override
    protected void retry(Throwable t, int attempt, int maxAttempts) {
        channel.onRetrying(t, attempt, maxAttempts);
    }
}
