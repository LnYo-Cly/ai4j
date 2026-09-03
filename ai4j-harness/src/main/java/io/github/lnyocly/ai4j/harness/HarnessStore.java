package io.github.lnyocly.ai4j.harness;

/** Durable authoritative state store. Implementations must provide atomic update semantics. */
public interface HarnessStore extends AutoCloseable {

    HarnessState load();

    HarnessState update(HarnessStateMutation mutation);

    @Override
    default void close() {
    }
}
