package io.github.lnyocly.ai4j.harness;

/**
 * Durable authoritative state store. Implementations must provide atomic update
 * semantics, return detached snapshots from load(), and advance HarnessState.version
 * on every accepted update. Completion uses this version to reject stale gate results.
 */
public interface HarnessStore extends AutoCloseable {

    HarnessState load();

    HarnessState update(HarnessStateMutation mutation);

    @Override
    default void close() {
    }
}
