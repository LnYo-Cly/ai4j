package io.github.lnyocly.ai4j.harness;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cohesive event journal collaborator managing durable event appends,
 * idempotent fenced mutations, event-stream tailing, and entity rebuilds.
 */
public final class HarnessEventJournal {

    private static final int DEFAULT_EVENT_LIMIT = 100;

    private final HarnessStore store;
    private final HarnessActor defaultActor;
    private volatile WriterEpochFence fallbackEpochFence = new WriterEpochFence(0L, null, 0L);

    public HarnessEventJournal(HarnessStore store, HarnessActor defaultActor) {
        if (store == null) {
            throw new IllegalArgumentException("Harness store is required");
        }
        this.store = store;
        this.defaultActor = HarnessSupport.normalizeActor(defaultActor, HarnessActor.agent("harness-agent"));
    }

    public HarnessEventRecord recordEvent(String type,
                                          String entityId,
                                          Map<String, Object> payload,
                                          HarnessActor actor) {
        final String eventType = HarnessSupport.requireText(type, "event type");
        final String eventEntityId = HarnessSupport.trimToNull(entityId);
        final HarnessActor effectiveActor = HarnessSupport.normalizeActor(actor, defaultActor);
        return HarnessSupport.write(store, new HarnessSupport.StateCommand<HarnessEventRecord>() {
            @Override
            public HarnessEventRecord apply(HarnessState state) {
                return addEvent(state, eventType, eventEntityId, effectiveActor, payload).copy();
            }
        });
    }

    public List<HarnessEventRecord> eventsSince(long afterSeq, int limit) {
        if (store instanceof HarnessEventStore) {
            return ((HarnessEventStore) store).listEventsSince(afterSeq, limit);
        }
        int effectiveLimit = limit <= 0 ? DEFAULT_EVENT_LIMIT : limit;
        HarnessState state = store.load();
        if (state == null || state.getEvents() == null) {
            return Collections.emptyList();
        }
        List<HarnessEventRecord> filtered = new ArrayList<HarnessEventRecord>();
        for (HarnessEventRecord event : state.getEvents()) {
            if (event != null && event.getSeq() > afterSeq) {
                filtered.add(event.copy());
            }
        }
        Collections.sort(filtered, new Comparator<HarnessEventRecord>() {
            @Override
            public int compare(HarnessEventRecord left, HarnessEventRecord right) {
                return Long.compare(left.getSeq(), right.getSeq());
            }
        });
        if (filtered.size() > effectiveLimit) {
            return new ArrayList<HarnessEventRecord>(filtered.subList(0, effectiveLimit));
        }
        return filtered;
    }

    public MutationReceipt appendEventAndProject(HarnessEventRecord event) {
        return appendEventAndProject(event, null, null);
    }

    public MutationReceipt appendEventAndProject(HarnessEventRecord event,
                                                 String entityStatus,
                                                 String entityPayloadJson) {
        if (event == null) {
            throw new HarnessValidationException("event record is required");
        }
        HarnessSupport.requireText(event.getEntityType(), "entity type");
        HarnessSupport.requireText(event.getEntityId(), "entity id");
        HarnessSupport.requireText(event.getEventType(), "event type");
        if (store instanceof HarnessEventStore) {
            return ((HarnessEventStore) store).appendEventAndProject(event, entityStatus, entityPayloadJson);
        }
        return appendFallback(event, entityStatus, entityPayloadJson);
    }

    public MutationReceipt executeFencedMutation(String entityType,
                                                 String entityId,
                                                 String eventType,
                                                 Long expectedVersion,
                                                 long writerEpoch,
                                                 String idempotencyKey,
                                                 Map<String, Object> payload,
                                                 HarnessActor actor) {
        HarnessActor effectiveActor = HarnessSupport.normalizeActor(actor, defaultActor);
        String payloadJson = payload == null ? "{}" : HarnessJson.toJson(payload);
        HarnessEventRecord event = HarnessEventRecord.builder()
                .eventId("evt_" + UUID.randomUUID().toString().replace("-", ""))
                .entityType(HarnessSupport.requireText(entityType, "entity type"))
                .entityId(HarnessSupport.requireText(entityId, "entity id"))
                .eventType(HarnessSupport.requireText(eventType, "event type"))
                .expectedVersion(expectedVersion)
                .writerEpoch(writerEpoch)
                .actor(HarnessSupport.actorKey(effectiveActor, defaultActor))
                .idempotencyKey(HarnessSupport.trimToNull(idempotencyKey))
                .timestampEpochMs(HarnessSupport.now())
                .payloadJson(payloadJson)
                .build();
        return appendEventAndProject(event, null, null);
    }

    public int rebuildEntitiesFromEvents() {
        if (store instanceof HarnessEventStore) {
            return ((HarnessEventStore) store).rebuildEntitiesFromEvents();
        }
        HarnessState state = store.load();
        if (state == null || state.getEvents() == null) {
            return 0;
        }
        String harnessId = HarnessSupport.trimToNull(state.getHarnessId()) == null
                ? "default" : state.getHarnessId().trim();
        return HarnessProjectionEngine.foldEvents(harnessId, state.getEvents()).size();
    }

    synchronized WriterEpochFence claimFallbackEpoch(String nodeId, long expectedEpoch) {
        WriterEpochFence current = fallbackEpochFence;
        if (current.getEpoch() != expectedEpoch) {
            throw new HarnessConflictException("writer epoch mismatch: expected "
                    + expectedEpoch + ", current " + current.getEpoch());
        }
        WriterEpochFence next = new WriterEpochFence(expectedEpoch + 1L, nodeId, HarnessSupport.now());
        fallbackEpochFence = next;
        return next.copy();
    }

    WriterEpochFence currentFallbackEpoch() {
        return fallbackEpochFence.copy();
    }

    HarnessEventRecord addEvent(HarnessState state,
                                String type,
                                String entityId,
                                HarnessActor actor,
                                Map<String, Object> payload) {
        List<HarnessEventRecord> events = state.getEvents();
        long sequence = 1L;
        if (events != null && !events.isEmpty()) {
            sequence = events.get(events.size() - 1).getSequence() + 1L;
        }
        if (events == null) {
            events = new ArrayList<HarnessEventRecord>();
            state.setEvents(events);
        }
        HarnessEventRecord event = HarnessEventRecord.builder()
                .sequence(sequence)
                .eventId("evt_" + sequence + "_" + UUID.randomUUID().toString().replace("-", ""))
                .harnessId(state.getHarnessId())
                .entityType(HarnessProjectionEngine.inferEntityType(type))
                .entityId(entityId)
                .type(type)
                .actorId(HarnessSupport.actorKey(actor, defaultActor))
                .recordedAtEpochMs(HarnessSupport.now())
                .payload(HarnessSupport.copyMap(payload))
                .build();
        events.add(event);
        return event;
    }

    private MutationReceipt appendFallback(final HarnessEventRecord event,
                                           final String entityStatus,
                                           final String entityPayloadJson) {
        final MutationReceipt[] holder = new MutationReceipt[1];
        store.update(new HarnessStateMutation() {
            @Override
            public HarnessState apply(HarnessState state) {
                state.ensureCollections();
                WriterEpochFence currentFence = fallbackEpochFence;
                if (event.getWriterEpoch() < currentFence.getEpoch()) {
                    holder[0] = MutationReceipt.builder()
                            .outcome(MutationReceipt.Outcome.EPOCH_FENCED)
                            .entityType(event.getEntityType())
                            .entityId(event.getEntityId())
                            .writerEpoch(currentFence.getEpoch())
                            .idempotencyKey(HarnessSupport.trimToNull(event.getIdempotencyKey()))
                            .message("writer epoch " + event.getWriterEpoch()
                                    + " is fenced by current epoch " + currentFence.getEpoch())
                            .build();
                    return state;
                }
                String idem = HarnessSupport.trimToNull(event.getIdempotencyKey());
                if (idem != null) {
                    for (HarnessEventRecord existing : state.getEvents()) {
                        if (existing != null && idem.equals(existing.getIdempotencyKey())) {
                            holder[0] = MutationReceipt.builder()
                                    .outcome(MutationReceipt.Outcome.IDEMPOTENT_REPLAY)
                                    .seq(existing.getSeq())
                                    .entityType(existing.getEntityType())
                                    .entityId(existing.getEntityId())
                                    .entityVersion(existing.getEntityVersion())
                                    .writerEpoch(existing.getWriterEpoch())
                                    .idempotencyKey(idem)
                                    .message("idempotent replay for key: " + idem)
                                    .build();
                            return state;
                        }
                    }
                }
                String hid = HarnessSupport.trimToNull(state.getHarnessId()) == null
                        ? "default" : state.getHarnessId().trim();
                Map<HarnessProjectionEngine.EntityKey, HarnessEntityRecord> projected =
                        HarnessProjectionEngine.extractEntitiesFromState(hid, state);
                HarnessProjectionEngine.EntityKey key =
                        new HarnessProjectionEngine.EntityKey(event.getEntityType(), event.getEntityId());
                HarnessEntityRecord currentEntity = projected.get(key);
                long currentVersion = currentEntity == null ? 0L : currentEntity.getEntityVersion();
                if (event.getExpectedVersion() != null
                        && event.getExpectedVersion().longValue() != currentVersion) {
                    holder[0] = MutationReceipt.builder()
                            .outcome(MutationReceipt.Outcome.VERSION_CONFLICT)
                            .entityType(event.getEntityType())
                            .entityId(event.getEntityId())
                            .entityVersion(currentVersion)
                            .writerEpoch(currentFence.getEpoch())
                            .idempotencyKey(idem)
                            .message("expected version " + event.getExpectedVersion()
                                    + " but current version is " + currentVersion)
                            .build();
                    return state;
                }
                long nextSeq = state.getEvents().isEmpty()
                        ? 1L : state.getEvents().get(state.getEvents().size() - 1).getSeq() + 1L;
                long nextVersion = currentVersion + 1L;
                long ts = event.getTimestampEpochMs() > 0L ? event.getTimestampEpochMs() : HarnessSupport.now();
                String eventId = HarnessSupport.trimToNull(event.getEventId());
                if (eventId == null) {
                    eventId = "evt_" + UUID.randomUUID().toString().replace("-", "");
                }
                HarnessEventRecord stored = event.toBuilder()
                        .seq(nextSeq)
                        .eventId(eventId)
                        .harnessId(hid)
                        .entityVersion(nextVersion)
                        .idempotencyKey(idem)
                        .timestampEpochMs(ts)
                        .build();
                HarnessEntityRecord nextEntity = HarnessProjectionEngine.projectEvent(
                        hid, currentEntity, stored, entityStatus, entityPayloadJson);
                HarnessProjectionEngine.applyEntityToState(state, nextEntity);
                state.getEvents().add(stored);
                holder[0] = MutationReceipt.builder()
                        .outcome(MutationReceipt.Outcome.APPLIED)
                        .seq(nextSeq)
                        .entityType(stored.getEntityType())
                        .entityId(stored.getEntityId())
                        .entityVersion(nextVersion)
                        .writerEpoch(stored.getWriterEpoch())
                        .idempotencyKey(idem)
                        .message("applied")
                        .build();
                return state;
            }
        });
        return holder[0];
    }
}
