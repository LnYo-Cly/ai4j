package io.github.lnyocly.ai4j.agent.subagent;

public final class HandoffContext {

    private static final ThreadLocal<Integer> DEPTH = new ThreadLocal<>();

    private HandoffContext() {
    }

    public static int currentDepth() {
        Integer depth = DEPTH.get();
        return depth == null ? 0 : depth;
    }

    public static <T> T runWithDepth(int depth, HandoffCallable<T> callable) throws Exception {
        Integer previous = DEPTH.get();
        DEPTH.set(depth);
        try {
            return callable.call();
        } finally {
            if (previous == null) {
                DEPTH.remove();
            } else {
                DEPTH.set(previous);
            }
        }
    }

    public interface HandoffCallable<T> {
        T call() throws Exception;
    }
}
