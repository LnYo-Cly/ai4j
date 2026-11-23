package io.github.lnyocly.ai4j.harness;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable-in-practice durable event record for the Harness CQRS event ledger.
 * Preserves legacy 1.x aliases ({@code sequence}, {@code type}, {@code actorId},
 * {@code recordedAtEpochMs}, {@code payload}) alongside 2.0 ledger fields.
 */
public class HarnessEventRecord {

    @JsonAlias("sequence")
    private long seq;

    private String eventId;

    private String harnessId;

    private String entityType;

    private String entityId;

    @JsonAlias("type")
    private String eventType;

    private Long expectedVersion;

    private long entityVersion;

    private long writerEpoch;

    @JsonAlias("actorId")
    private String actor;

    private String idempotencyKey;

    @JsonAlias("recordedAtEpochMs")
    private long timestampEpochMs;

    private String payloadJson;

    private Map<String, Object> payload;

    public HarnessEventRecord() {
    }

    public HarnessEventRecord(long seq,
                              String eventId,
                              String harnessId,
                              String entityType,
                              String entityId,
                              String eventType,
                              Long expectedVersion,
                              long entityVersion,
                              long writerEpoch,
                              String actor,
                              String idempotencyKey,
                              long timestampEpochMs,
                              String payloadJson) {
        this(seq, eventId, harnessId, entityType, entityId, eventType, expectedVersion,
                entityVersion, writerEpoch, actor, idempotencyKey, timestampEpochMs, payloadJson, null);
    }

    public HarnessEventRecord(long seq,
                              String eventId,
                              String harnessId,
                              String entityType,
                              String entityId,
                              String eventType,
                              Long expectedVersion,
                              long entityVersion,
                              long writerEpoch,
                              String actor,
                              String idempotencyKey,
                              long timestampEpochMs,
                              String payloadJson,
                              Map<String, Object> payload) {
        this.seq = seq;
        this.eventId = eventId;
        this.harnessId = harnessId;
        this.entityType = entityType;
        this.entityId = entityId;
        this.eventType = eventType;
        this.expectedVersion = expectedVersion;
        this.entityVersion = entityVersion;
        this.writerEpoch = writerEpoch;
        this.actor = actor;
        this.idempotencyKey = idempotencyKey;
        this.timestampEpochMs = timestampEpochMs;
        this.payload = payload == null ? null : new LinkedHashMap<String, Object>(payload);
        if (payloadJson != null) {
            this.payloadJson = payloadJson;
        } else if (this.payload != null && !this.payload.isEmpty()) {
            this.payloadJson = HarnessJson.toJson(this.payload);
        }
    }

    /** Legacy 1.x constructor retained for compatibility. */
    public HarnessEventRecord(long sequence,
                              String type,
                              String entityId,
                              String actorId,
                              long recordedAtEpochMs,
                              Map<String, Object> payload) {
        this(sequence, null, null, null, entityId, type, null, 0L, 0L,
                actorId, null, recordedAtEpochMs, null, payload);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .seq(seq)
                .eventId(eventId)
                .harnessId(harnessId)
                .entityType(entityType)
                .entityId(entityId)
                .eventType(eventType)
                .expectedVersion(expectedVersion)
                .entityVersion(entityVersion)
                .writerEpoch(writerEpoch)
                .actor(actor)
                .idempotencyKey(idempotencyKey)
                .timestampEpochMs(timestampEpochMs)
                .payloadJson(payloadJson)
                .payload(payload == null ? null : new LinkedHashMap<String, Object>(payload));
    }

    public long getSeq() {
        return seq;
    }

    public void setSeq(long seq) {
        this.seq = seq;
    }

    public long getSequence() {
        return seq;
    }

    public void setSequence(long sequence) {
        this.seq = sequence;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getHarnessId() {
        return harnessId;
    }

    public void setHarnessId(String harnessId) {
        this.harnessId = harnessId;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getType() {
        return eventType;
    }

    public void setType(String type) {
        this.eventType = type;
    }

    public Long getExpectedVersion() {
        return expectedVersion;
    }

    public void setExpectedVersion(Long expectedVersion) {
        this.expectedVersion = expectedVersion;
    }

    public long getEntityVersion() {
        return entityVersion;
    }

    public void setEntityVersion(long entityVersion) {
        this.entityVersion = entityVersion;
    }

    public long getWriterEpoch() {
        return writerEpoch;
    }

    public void setWriterEpoch(long writerEpoch) {
        this.writerEpoch = writerEpoch;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public String getActorId() {
        return actor;
    }

    public void setActorId(String actorId) {
        this.actor = actorId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public long getTimestampEpochMs() {
        return timestampEpochMs;
    }

    public void setTimestampEpochMs(long timestampEpochMs) {
        this.timestampEpochMs = timestampEpochMs;
    }

    public long getRecordedAtEpochMs() {
        return timestampEpochMs;
    }

    public void setRecordedAtEpochMs(long recordedAtEpochMs) {
        this.timestampEpochMs = recordedAtEpochMs;
    }

    public String getPayloadJson() {
        if (payloadJson == null && payload != null && !payload.isEmpty()) {
            payloadJson = HarnessJson.toJson(payload);
        }
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
        this.payload = null;
    }

    public Map<String, Object> getPayload() {
        if (payload == null) {
            payload = parsePayloadMap(payloadJson);
        }
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload == null ? new LinkedHashMap<String, Object>() : new LinkedHashMap<String, Object>(payload);
        this.payloadJson = this.payload.isEmpty() ? null : HarnessJson.toJson(this.payload);
    }

    public HarnessEventRecord copy() {
        HarnessEventRecord copy = HarnessJson.copy(this, HarnessEventRecord.class);
        if (copy != null) {
            copy.getPayload();
        }
        return copy;
    }

    private static Map<String, Object> parsePayloadMap(String rawJson) {
        if (rawJson == null || rawJson.trim().isEmpty()) {
            return new LinkedHashMap<String, Object>();
        }
        try {
            Map<String, Object> parsed = HarnessJson.parseMap(rawJson);
            return parsed == null ? new LinkedHashMap<String, Object>() : parsed;
        } catch (RuntimeException ignored) {
            return new LinkedHashMap<String, Object>();
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof HarnessEventRecord)) {
            return false;
        }
        HarnessEventRecord that = (HarnessEventRecord) other;
        return seq == that.seq
                && entityVersion == that.entityVersion
                && writerEpoch == that.writerEpoch
                && timestampEpochMs == that.timestampEpochMs
                && Objects.equals(eventId, that.eventId)
                && Objects.equals(harnessId, that.harnessId)
                && Objects.equals(entityType, that.entityType)
                && Objects.equals(entityId, that.entityId)
                && Objects.equals(eventType, that.eventType)
                && Objects.equals(expectedVersion, that.expectedVersion)
                && Objects.equals(actor, that.actor)
                && Objects.equals(idempotencyKey, that.idempotencyKey)
                && Objects.equals(getPayloadJson(), that.getPayloadJson())
                && Objects.equals(getPayload(), that.getPayload());
    }

    @Override
    public int hashCode() {
        return Objects.hash(seq, eventId, harnessId, entityType, entityId, eventType,
                expectedVersion, entityVersion, writerEpoch, actor, idempotencyKey,
                timestampEpochMs, getPayloadJson());
    }

    @Override
    public String toString() {
        return "HarnessEventRecord{"
                + "seq=" + seq
                + ", eventId='" + eventId + '\''
                + ", harnessId='" + harnessId + '\''
                + ", entityType='" + entityType + '\''
                + ", entityId='" + entityId + '\''
                + ", eventType='" + eventType + '\''
                + ", expectedVersion=" + expectedVersion
                + ", entityVersion=" + entityVersion
                + ", writerEpoch=" + writerEpoch
                + ", actor='" + actor + '\''
                + ", idempotencyKey='" + idempotencyKey + '\''
                + ", timestampEpochMs=" + timestampEpochMs
                + '}';
    }

    public static final class Builder {
        private long seq;
        private String eventId;
        private String harnessId;
        private String entityType;
        private String entityId;
        private String eventType;
        private Long expectedVersion;
        private long entityVersion;
        private long writerEpoch;
        private String actor;
        private String idempotencyKey;
        private long timestampEpochMs;
        private String payloadJson;
        private Map<String, Object> payload;

        private Builder() {
        }

        public Builder seq(long seq) {
            this.seq = seq;
            return this;
        }

        public Builder sequence(long sequence) {
            this.seq = sequence;
            return this;
        }

        public Builder eventId(String eventId) {
            this.eventId = eventId;
            return this;
        }

        public Builder harnessId(String harnessId) {
            this.harnessId = harnessId;
            return this;
        }

        public Builder entityType(String entityType) {
            this.entityType = entityType;
            return this;
        }

        public Builder entityId(String entityId) {
            this.entityId = entityId;
            return this;
        }

        public Builder eventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        public Builder type(String type) {
            this.eventType = type;
            return this;
        }

        public Builder expectedVersion(Long expectedVersion) {
            this.expectedVersion = expectedVersion;
            return this;
        }

        public Builder entityVersion(long entityVersion) {
            this.entityVersion = entityVersion;
            return this;
        }

        public Builder writerEpoch(long writerEpoch) {
            this.writerEpoch = writerEpoch;
            return this;
        }

        public Builder actor(String actor) {
            this.actor = actor;
            return this;
        }

        public Builder actorId(String actorId) {
            this.actor = actorId;
            return this;
        }

        public Builder idempotencyKey(String idempotencyKey) {
            this.idempotencyKey = idempotencyKey;
            return this;
        }

        public Builder timestampEpochMs(long timestampEpochMs) {
            this.timestampEpochMs = timestampEpochMs;
            return this;
        }

        public Builder recordedAtEpochMs(long recordedAtEpochMs) {
            this.timestampEpochMs = recordedAtEpochMs;
            return this;
        }

        public Builder payloadJson(String payloadJson) {
            this.payloadJson = payloadJson;
            return this;
        }

        public Builder payload(Map<String, Object> payload) {
            this.payload = payload == null ? null : new LinkedHashMap<String, Object>(payload);
            return this;
        }

        public HarnessEventRecord build() {
            return new HarnessEventRecord(seq, eventId, harnessId, entityType, entityId, eventType,
                    expectedVersion, entityVersion, writerEpoch, actor, idempotencyKey,
                    timestampEpochMs, payloadJson, payload);
        }
    }
}
