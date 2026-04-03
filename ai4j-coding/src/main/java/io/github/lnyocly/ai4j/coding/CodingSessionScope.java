package io.github.lnyocly.ai4j.coding;

public final class CodingSessionScope {

    private static final ThreadLocal<CodingSession> CURRENT = new ThreadLocal<CodingSession>();

    private CodingSessionScope() {
    }

    public interface SessionCallable<T> {
        T call() throws Exception;
    }

    public interface SessionRunnable {
        void run() throws Exception;
    }

    public static CodingSession currentSession() {
        return CURRENT.get();
    }

    public static <T> T runWithSession(CodingSession session, SessionCallable<T> callable) throws Exception {
        if (callable == null) {
            throw new IllegalArgumentException("callable is required");
        }
        CodingSession previous = CURRENT.get();
        CURRENT.set(session);
        try {
            return callable.call();
        } finally {
            restore(previous);
        }
    }

    public static void runWithSession(CodingSession session, SessionRunnable runnable) throws Exception {
        if (runnable == null) {
            throw new IllegalArgumentException("runnable is required");
        }
        runWithSession(session, new SessionCallable<Void>() {
            @Override
            public Void call() throws Exception {
                runnable.run();
                return null;
            }
        });
    }

    private static void restore(CodingSession previous) {
        if (previous == null) {
            CURRENT.remove();
            return;
        }
        CURRENT.set(previous);
    }
}
