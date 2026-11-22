package io.github.lnyocly.ai4j.harness;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Package-private shared validation, normalization, scope, and idempotency helpers
 * used across the Harness gateway, stores, and coordinators.
 */
final class HarnessSupport {

    static final String IDEMPOTENCY_TASK = "task";
    static final String IDEMPOTENCY_EXECUTION = "execution";

    private HarnessSupport() {
    }

    static String valueOrGenerated(String value, String prefix) {
        String normalized = trimToNull(value);
        return normalized == null ? prefix + UUID.randomUUID().toString().replace("-", "") : normalized;
    }

    static String requireText(String value, String label) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new HarnessValidationException(label + " is required");
        }
        return normalized;
    }

    static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    static String normalizeScope(String value) {
        return trimToNull(value);
    }

    static void assertCompatibleScope(String left, String right, String message) {
        String normalizedLeft = normalizeScope(left);
        String normalizedRight = normalizeScope(right);
        if (normalizedLeft != null && normalizedRight != null
                && !normalizedLeft.equals(normalizedRight)) {
            throw new HarnessConflictException(message + ": "
                    + normalizedLeft + " != " + normalizedRight);
        }
    }

    static void assertSameScope(String left, String right, String message) {
        String normalizedLeft = normalizeScope(left);
        String normalizedRight = normalizeScope(right);
        if (!safeEquals(normalizedLeft, normalizedRight)) {
            throw new HarnessConflictException(message + ": "
                    + String.valueOf(normalizedLeft) + " != "
                    + String.valueOf(normalizedRight));
        }
    }

    static boolean visibleScope(String entityScope, String requestedScope) {
        String scope = normalizeScope(requestedScope);
        return scope == null || (normalizeScope(entityScope) != null
                && scope.equals(normalizeScope(entityScope)));
    }

    static long now() {
        return System.currentTimeMillis();
    }

    static String actorKey(HarnessActor actor, HarnessActor fallback) {
        HarnessActor effective = normalizeActor(actor, fallback);
        return effective.getKind() + ":" + effective.getId();
    }

    static HarnessActor normalizeActor(HarnessActor actor, HarnessActor fallback) {
        HarnessActor source = actor == null ? fallback : actor;
        if (source == null || trimToNull(source.getKind()) == null || trimToNull(source.getId()) == null) {
            throw new HarnessValidationException("actor kind and id are required");
        }
        return source;
    }

    static HarnessActor copyActor(HarnessActor actor) {
        return actor == null ? null : HarnessActor.builder()
                .kind(actor.getKind())
                .id(actor.getId())
                .displayName(actor.getDisplayName())
                .build();
    }

    static Map<String, Object> copyMap(Map<String, Object> source) {
        return source == null ? new LinkedHashMap<String, Object>() : new LinkedHashMap<String, Object>(source);
    }

    static List<String> copyList(List<String> source) {
        return source == null ? new ArrayList<String>() : new ArrayList<String>(source);
    }

    static Map<String, Object> mapOf(Object... values) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        if (values == null) {
            return map;
        }
        for (int i = 0; i + 1 < values.length; i += 2) {
            map.put(String.valueOf(values[i]), values[i + 1]);
        }
        return map;
    }

    static boolean safeEquals(Object left, Object right) {
        return left == null ? right == null : left.equals(right);
    }

    static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    static HarnessProvenance provenance(HarnessActor actor,
                                        HarnessActor fallback,
                                        String executionId,
                                        String sessionId,
                                        String sourceType,
                                        String sourceId) {
        return HarnessProvenance.builder()
                .actor(copyActor(normalizeActor(actor, fallback)))
                .executionId(executionId)
                .sessionId(sessionId)
                .sourceType(sourceType)
                .sourceId(sourceId)
                .recordedAtEpochMs(now())
                .build();
    }

    static String idempotencyNamespace(String entityType, String scopeKey, String key) {
        return "v2|" + idempotencySegment(entityType)
                + "|" + idempotencySegment(normalizeScope(scopeKey))
                + "|" + idempotencySegment(key);
    }

    private static String idempotencySegment(String value) {
        if (value == null) {
            return "-";
        }
        return value.length() + ":" + value;
    }

    static TaskRecord requireTask(HarnessState state, String taskId) {
        TaskRecord task = state.getTasks().get(taskId);
        if (task == null) {
            throw new HarnessValidationException("task not found: " + taskId);
        }
        return task;
    }

    static ExecutionRecord requireExecution(HarnessState state, String executionId) {
        ExecutionRecord execution = state.getExecutions().get(executionId);
        if (execution == null) {
            throw new HarnessValidationException("execution not found: " + executionId);
        }
        return execution;
    }

    static <T> T write(HarnessStore store, final StateCommand<T> command) {
        if (command == null) {
            throw new IllegalArgumentException("Harness command is required");
        }
        final ValueHolder<T> holder = new ValueHolder<T>();
        store.update(new HarnessStateMutation() {
            @Override
            public HarnessState apply(HarnessState current) {
                current.ensureCollections();
                holder.value = command.apply(current);
                return current;
            }
        });
        return holder.value;
    }

    private static final class ValueHolder<T> {
        private T value;
    }

    static final class ConflictHolder {
        String message;
    }

    interface StateCommand<T> {
        T apply(HarnessState state);
    }
}
