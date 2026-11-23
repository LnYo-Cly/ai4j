package io.github.lnyocly.ai4j.extension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Package-private LIFO composite {@link Disposable} implementation with at-most-once execution
 * and suppressed-exception preservation.
 */
final class CompositeDisposable implements Disposable {

    static final Disposable NOOP = new Disposable() {
        @Override
        public void dispose() {
        }
    };

    private final List<Disposable> disposables;
    private final AtomicBoolean disposed = new AtomicBoolean(false);

    private CompositeDisposable(List<Disposable> disposables) {
        this.disposables = disposables;
    }

    static Disposable of(Collection<? extends Disposable> items) {
        if (items == null || items.isEmpty()) {
            return NOOP;
        }
        List<Disposable> copy = new ArrayList<Disposable>(items.size());
        for (Disposable item : items) {
            if (item != null && item != NOOP) {
                copy.add(item);
            }
        }
        if (copy.isEmpty()) {
            return NOOP;
        }
        return new CompositeDisposable(Collections.unmodifiableList(copy));
    }

    static Disposable of(Disposable... items) {
        if (items == null || items.length == 0) {
            return NOOP;
        }
        return of(Arrays.asList(items));
    }

    @Override
    public void dispose() {
        if (!disposed.compareAndSet(false, true)) {
            return;
        }
        List<Throwable> failures = null;
        for (int i = disposables.size() - 1; i >= 0; i--) {
            Disposable item = disposables.get(i);
            try {
                item.dispose();
            } catch (Throwable failure) {
                if (failures == null) {
                    failures = new ArrayList<Throwable>(2);
                }
                failures.add(failure);
            }
        }
        if (failures != null) {
            throwAggregated(failures);
        }
    }

    private static void throwAggregated(List<Throwable> failures) {
        Throwable first = failures.get(0);
        if (first instanceof Error) {
            Error error = (Error) first;
            attachSuppressed(error, failures);
            throw error;
        }
        ExtensionException wrapped;
        if (first instanceof ExtensionException) {
            wrapped = (ExtensionException) first;
        } else {
            String message = first.getMessage();
            String detail = (message == null || message.trim().isEmpty())
                    ? first.getClass().getSimpleName()
                    : message.trim();
            wrapped = new ExtensionException(
                    "failed to dispose " + failures.size() + " resource(s): " + detail,
                    first
            );
        }
        attachSuppressed(wrapped, failures);
        throw wrapped;
    }

    private static void attachSuppressed(Throwable target, List<Throwable> failures) {
        for (int i = 1; i < failures.size(); i++) {
            Throwable suppressed = failures.get(i);
            if (suppressed != target) {
                target.addSuppressed(suppressed);
            }
        }
    }
}
