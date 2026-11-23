package io.github.lnyocly.ai4j.stream;

import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Provides a non-blocking task dispatcher that automatically uses JDK 21+
 * virtual threads when available at runtime, while maintaining strict Java 8
 * bytecode compatibility via a daemon thread pool fallback.
 */
public final class DispatcherProvider {

    private static final Method VIRTUAL_THREAD_EXECUTOR_FACTORY = findVirtualThreadExecutorFactory();
    private static final ExecutorService DEFAULT_EXECUTOR = createExecutor("ai4j-dispatcher");

    private DispatcherProvider() {
    }

    /** True when running on a JVM that exposes {@code Executors.newVirtualThreadPerTaskExecutor()}. */
    public static boolean isVirtualThreadsSupported() {
        return VIRTUAL_THREAD_EXECUTOR_FACTORY != null;
    }

    /** Shared daemon/virtual dispatcher executor. */
    public static ExecutorService executor() {
        return DEFAULT_EXECUTOR;
    }

    /** Create a dedicated executor using virtual threads when available, else a named daemon pool. */
    public static ExecutorService newExecutor(final String namePrefix) {
        if (VIRTUAL_THREAD_EXECUTOR_FACTORY != null) {
            try {
                return (ExecutorService) VIRTUAL_THREAD_EXECUTOR_FACTORY.invoke(null);
            } catch (Exception ignored) {
                // Fall back cleanly if security manager or runtime restricts reflective invocation.
            }
        }
        final String prefix = (namePrefix == null || namePrefix.trim().isEmpty())
                ? "ai4j-dispatcher"
                : namePrefix.trim();
        final AtomicInteger counter = new AtomicInteger(1);
        return Executors.newCachedThreadPool(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, prefix + "-" + counter.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        });
    }

    private static ExecutorService createExecutor(String namePrefix) {
        return newExecutor(namePrefix);
    }

    private static Method findVirtualThreadExecutorFactory() {
        try {
            return Executors.class.getMethod("newVirtualThreadPerTaskExecutor");
        } catch (NoSuchMethodException ignored) {
            return null;
        } catch (SecurityException ignored) {
            return null;
        }
    }
}
