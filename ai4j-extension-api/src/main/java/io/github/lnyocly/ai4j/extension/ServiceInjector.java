package io.github.lnyocly.ai4j.extension;

/**
 * Reactive consumer binder invoked when a {@link ServiceKey} becomes available, returning a
 * {@link Disposable} cleanup handle bound to that injected service instance.
 */
@FunctionalInterface
public interface ServiceInjector<T> {

    Disposable inject(T service);
}
