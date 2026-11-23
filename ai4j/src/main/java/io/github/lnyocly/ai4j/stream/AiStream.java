package io.github.lnyocly.ai4j.stream;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Non-blocking, Reactor-free stream handle with both push callbacks and pull
 * iteration.
 */
public interface AiStream<T> extends AutoCloseable {

    AiStream<T> onEvent(Consumer<AiStreamEvent> listener);

    AiStream<T> onTextDelta(Consumer<String> onDelta);

    AiStream<T> onThinkingDelta(Consumer<String> onThinking);

    AiStream<T> onComplete(Consumer<AiReply<T>> onDone);

    AiStream<T> onError(Consumer<Throwable> onError);

    void cancel();

    boolean isCancelled();

    AiReply<T> await();

    CompletableFuture<AiReply<T>> toFuture();

    Iterable<AiStreamEvent> iterable();

    @Override
    default void close() {
        cancel();
    }
}
