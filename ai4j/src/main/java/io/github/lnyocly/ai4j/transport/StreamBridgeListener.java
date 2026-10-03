package io.github.lnyocly.ai4j.transport;

import io.github.lnyocly.ai4j.listener.AbstractManagedStreamListener;
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
final class StreamBridgeListener extends AbstractManagedStreamListener {

    private final SseChannel channel;

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
        super.onFailure(eventSource, t, response);
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
