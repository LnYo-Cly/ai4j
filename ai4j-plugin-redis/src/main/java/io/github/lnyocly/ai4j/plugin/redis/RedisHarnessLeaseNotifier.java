package io.github.lnyocly.ai4j.plugin.redis;

import io.github.lnyocly.ai4j.codec.JsonCodec;
import io.github.lnyocly.ai4j.harness.HarnessConflictException;
import io.github.lnyocly.ai4j.harness.LeaseRecord;
import io.github.lnyocly.ai4j.harness.WriterEpochFence;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fence- and writer-epoch-aware Redis lease notification coordinator supporting
 * durable publish, poll, and ack operations over {@link RedisClientAdapter}.
 */
public class RedisHarnessLeaseNotifier {

    public static final String DEFAULT_KEY_PREFIX = "ai4j:harness:lease:";

    private final RedisClientAdapter client;
    private final String keyPrefix;
    private final long ttlSeconds;

    public RedisHarnessLeaseNotifier(RedisClientAdapter client) {
        this(client, DEFAULT_KEY_PREFIX, 0L);
    }

    public RedisHarnessLeaseNotifier(RedisClientAdapter client, String keyPrefix, long ttlSeconds) {
        if (client == null) {
            throw new IllegalArgumentException("RedisClientAdapter must not be null");
        }
        this.client = client;
        this.keyPrefix = RedisConfig.normalizePrefix(keyPrefix, DEFAULT_KEY_PREFIX);
        this.ttlSeconds = Math.max(0L, ttlSeconds);
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public String getChannelName() {
        return keyPrefix + "channel";
    }

    public synchronized WriterEpochFence currentWriterEpoch() {
        String raw = client.get(epochKey());
        if (raw == null || raw.trim().isEmpty()) {
            return new WriterEpochFence(0L, null, 0L);
        }
        WriterEpochFence parsed = JsonCodec.fromJson(raw, WriterEpochFence.class);
        return parsed == null ? new WriterEpochFence(0L, null, 0L) : parsed;
    }

    public synchronized WriterEpochFence claimWriterEpoch(String nodeId, long expectedEpoch) {
        if (nodeId == null || nodeId.trim().isEmpty()) {
            throw new IllegalArgumentException("nodeId must not be blank");
        }
        if (expectedEpoch < 0L) {
            throw new IllegalArgumentException("expectedEpoch must be >= 0");
        }
        WriterEpochFence current = currentWriterEpoch();
        if (current.getEpoch() != expectedEpoch) {
            throw new HarnessConflictException("writer epoch conflict: expected " + expectedEpoch
                    + ", current is " + current.getEpoch()
                    + " (holder=" + current.getHolderNodeId() + ")");
        }
        WriterEpochFence next = WriterEpochFence.builder()
                .epoch(expectedEpoch + 1L)
                .holderNodeId(nodeId.trim())
                .acquiredAtEpochMs(System.currentTimeMillis())
                .build();
        writeEpoch(next);
        return next.copy();
    }

    public synchronized WriterEpochFence syncWriterEpoch(WriterEpochFence fence) {
        WriterEpochFence current = currentWriterEpoch();
        if (fence == null || fence.getEpoch() <= current.getEpoch()) {
            return current;
        }
        WriterEpochFence updated = fence.copy();
        writeEpoch(updated);
        return updated;
    }

    public synchronized long currentFencingToken(String executionId) {
        if (executionId == null || executionId.trim().isEmpty()) {
            return 0L;
        }
        String raw = client.get(fenceKey(executionId));
        if (raw == null || raw.trim().isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    public LeaseNotificationResult publish(LeaseRecord lease,
                                           WriterEpochFence fence,
                                           String eventType,
                                           String reason) {
        if (lease == null) {
            throw new IllegalArgumentException("lease must not be null");
        }
        LeaseNotification notification = LeaseNotification.builder()
                .executionId(lease.getExecutionId())
                .leaseId(lease.getLeaseId())
                .workerId(lease.getWorkerId())
                .nodeId(fence == null ? null : fence.getHolderNodeId())
                .eventType(eventType)
                .fencingToken(lease.getFencingToken())
                .writerEpoch(fence == null ? 0L : fence.getEpoch())
                .timestampEpochMs(System.currentTimeMillis())
                .reason(reason)
                .build();
        return publish(notification);
    }

    public synchronized LeaseNotificationResult publish(LeaseNotification source) {
        if (source == null) {
            throw new IllegalArgumentException("notification must not be null");
        }
        WriterEpochFence currentEpoch = currentWriterEpoch();
        long currentToken = currentFencingToken(source.getExecutionId());

        if (source.getWriterEpoch() < currentEpoch.getEpoch()) {
            return LeaseNotificationResult.builder()
                    .outcome(Outcome.STALE_EPOCH)
                    .currentWriterEpoch(currentEpoch.getEpoch())
                    .currentFencingToken(currentToken)
                    .notification(source.copy())
                    .build();
        }
        if (hasText(source.getExecutionId()) && source.getFencingToken() < currentToken) {
            return LeaseNotificationResult.builder()
                    .outcome(Outcome.STALE_FENCE)
                    .currentWriterEpoch(currentEpoch.getEpoch())
                    .currentFencingToken(currentToken)
                    .notification(source.copy())
                    .build();
        }

        if (source.getWriterEpoch() > currentEpoch.getEpoch()) {
            WriterEpochFence advanced = WriterEpochFence.builder()
                    .epoch(source.getWriterEpoch())
                    .holderNodeId(hasText(source.getNodeId()) ? source.getNodeId() : currentEpoch.getHolderNodeId())
                    .acquiredAtEpochMs(System.currentTimeMillis())
                    .build();
            writeEpoch(advanced);
            currentEpoch = advanced;
        }
        if (hasText(source.getExecutionId()) && source.getFencingToken() > currentToken) {
            writeFenceToken(source.getExecutionId(), source.getFencingToken());
            currentToken = source.getFencingToken();
        }

        LeaseNotification normalized = normalizeForPublish(source);
        String json = JsonCodec.toJson(normalized);
        client.hset(pendingHashKey(), normalized.getNotificationId(), json);
        client.rpush(orderListKey(), normalized.getNotificationId());
        if (ttlSeconds > 0L) {
            client.expire(pendingHashKey(), ttlSeconds);
            client.expire(orderListKey(), ttlSeconds);
        }
        client.publish(getChannelName(), json);

        return LeaseNotificationResult.builder()
                .outcome(Outcome.PUBLISHED)
                .currentWriterEpoch(currentEpoch.getEpoch())
                .currentFencingToken(currentToken)
                .notification(normalized)
                .build();
    }

    public List<LeaseNotification> poll() {
        return poll(Integer.MAX_VALUE);
    }

    public synchronized List<LeaseNotification> poll(int limit) {
        if (limit <= 0) {
            return Collections.emptyList();
        }
        Map<String, String> pendingMap = client.hgetAll(pendingHashKey());
        if (pendingMap == null || pendingMap.isEmpty()) {
            return Collections.emptyList();
        }

        long currentEpoch = currentWriterEpoch().getEpoch();
        List<String> order = client.lrange(orderListKey(), 0L, -1L);
        List<String> staleIds = new ArrayList<String>();
        List<LeaseNotification> active = new ArrayList<LeaseNotification>();

        if (order != null && !order.isEmpty()) {
            for (String id : order) {
                if (active.size() >= limit) {
                    break;
                }
                String raw = pendingMap.get(id);
                if (raw == null) {
                    continue;
                }
                LeaseNotification notification = JsonCodec.fromJson(raw, LeaseNotification.class);
                if (notification == null || isStale(notification, currentEpoch)) {
                    staleIds.add(id);
                    continue;
                }
                active.add(notification);
            }
        } else {
            for (Map.Entry<String, String> entry : pendingMap.entrySet()) {
                if (active.size() >= limit) {
                    break;
                }
                LeaseNotification notification = JsonCodec.fromJson(entry.getValue(), LeaseNotification.class);
                if (notification == null || isStale(notification, currentEpoch)) {
                    staleIds.add(entry.getKey());
                    continue;
                }
                active.add(notification);
            }
        }

        if (!staleIds.isEmpty()) {
            client.hdel(pendingHashKey(), staleIds.toArray(new String[0]));
        }
        return active;
    }

    public boolean ack(String notificationId) {
        return ack(notificationId, -1L, -1L);
    }

    public synchronized boolean ack(String notificationId, long expectedWriterEpoch, long expectedFencingToken) {
        if (!hasText(notificationId)) {
            return false;
        }
        String id = notificationId.trim();
        String raw = client.hget(pendingHashKey(), id);
        if (raw == null || raw.trim().isEmpty()) {
            return false;
        }
        LeaseNotification notification = JsonCodec.fromJson(raw, LeaseNotification.class);
        if (notification == null) {
            client.hdel(pendingHashKey(), id);
            return false;
        }

        long currentEpoch = currentWriterEpoch().getEpoch();
        long currentToken = currentFencingToken(notification.getExecutionId());
        if (notification.getWriterEpoch() < currentEpoch
                || (hasText(notification.getExecutionId()) && notification.getFencingToken() < currentToken)) {
            client.hdel(pendingHashKey(), id);
            return false;
        }
        if ((expectedWriterEpoch >= 0L && expectedWriterEpoch < currentEpoch)
                || (expectedFencingToken >= 0L && hasText(notification.getExecutionId())
                && expectedFencingToken < currentToken)) {
            return false;
        }

        long removed = client.hdel(pendingHashKey(), id);
        if (removed <= 0L) {
            return false;
        }
        if (ttlSeconds > 0L) {
            client.setex(ackedKey(id), ttlSeconds, "1");
        } else {
            client.set(ackedKey(id), "1");
        }
        return true;
    }

    private boolean isStale(LeaseNotification notification, long currentEpoch) {
        if (notification.getWriterEpoch() < currentEpoch) {
            return true;
        }
        if (hasText(notification.getExecutionId())) {
            long currentToken = currentFencingToken(notification.getExecutionId());
            return notification.getFencingToken() < currentToken;
        }
        return false;
    }

    private void writeEpoch(WriterEpochFence fence) {
        String json = JsonCodec.toJson(fence);
        if (ttlSeconds > 0L) {
            client.setex(epochKey(), ttlSeconds, json);
        } else {
            client.set(epochKey(), json);
        }
    }

    private void writeFenceToken(String executionId, long fencingToken) {
        String value = String.valueOf(fencingToken);
        if (ttlSeconds > 0L) {
            client.setex(fenceKey(executionId), ttlSeconds, value);
        } else {
            client.set(fenceKey(executionId), value);
        }
    }

    private LeaseNotification normalizeForPublish(LeaseNotification source) {
        LeaseNotification copy = source.copy();
        if (!hasText(copy.getNotificationId())) {
            copy.setNotificationId("lease_ntf_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        }
        if (copy.getTimestampEpochMs() <= 0L) {
            copy.setTimestampEpochMs(System.currentTimeMillis());
        }
        return copy;
    }

    private String epochKey() {
        return keyPrefix + "epoch";
    }

    private String fenceKey(String executionId) {
        return keyPrefix + "fence:" + executionId.trim();
    }

    private String pendingHashKey() {
        return keyPrefix + "pending";
    }

    private String orderListKey() {
        return keyPrefix + "order";
    }

    private String ackedKey(String notificationId) {
        return keyPrefix + "acked:" + notificationId;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public enum Outcome {
        PUBLISHED,
        STALE_EPOCH,
        STALE_FENCE
    }

    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LeaseNotification {
        private String notificationId;
        private String executionId;
        private String leaseId;
        private String sessionId;
        private String workerId;
        private String nodeId;
        private String eventType;
        private long fencingToken;
        private long writerEpoch;
        private long timestampEpochMs;
        private String reason;
        private Map<String, Object> metadata;

        public LeaseNotification copy() {
            Map<String, Object> metadataCopy = metadata == null ? null : new LinkedHashMap<String, Object>(metadata);
            return LeaseNotification.builder()
                    .notificationId(notificationId)
                    .executionId(executionId)
                    .leaseId(leaseId)
                    .sessionId(sessionId)
                    .workerId(workerId)
                    .nodeId(nodeId)
                    .eventType(eventType)
                    .fencingToken(fencingToken)
                    .writerEpoch(writerEpoch)
                    .timestampEpochMs(timestampEpochMs)
                    .reason(reason)
                    .metadata(metadataCopy)
                    .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LeaseNotificationResult {
        private Outcome outcome;
        private long currentWriterEpoch;
        private long currentFencingToken;
        private LeaseNotification notification;

        public boolean isAccepted() {
            return Outcome.PUBLISHED == outcome;
        }

        public boolean isFenced() {
            return Outcome.STALE_EPOCH == outcome || Outcome.STALE_FENCE == outcome;
        }
    }
}
