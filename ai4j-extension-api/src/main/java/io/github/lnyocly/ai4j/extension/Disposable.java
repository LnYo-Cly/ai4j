package io.github.lnyocly.ai4j.extension;

import java.util.Collection;

/**
 * Reversible cleanup handle for extension effects, service provisions, and reactive injections.
 */
@FunctionalInterface
public interface Disposable extends AutoCloseable {

    void dispose();

    @Override
    default void close() {
        dispose();
    }

    static Disposable noop() {
        return CompositeDisposable.NOOP;
    }

    static Disposable composite(Collection<? extends Disposable> disposables) {
        return CompositeDisposable.of(disposables);
    }

    static Disposable composite(Disposable... disposables) {
        return CompositeDisposable.of(disposables);
    }
}
