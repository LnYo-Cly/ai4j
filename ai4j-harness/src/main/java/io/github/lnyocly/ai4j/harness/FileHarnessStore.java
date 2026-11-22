package io.github.lnyocly.ai4j.harness;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * File-backed Harness store with an authoritative JSON snapshot, recovery
 * journal, append-only CQRS event ledger ({@code events.jsonl}), materialized
 * entity projection snapshot ({@code entities.json}), and writer-epoch fence
 * ({@code writer-epoch.json}).
 */
public final class FileHarnessStore implements HarnessEventStore {

    private static final int DEFAULT_EVENT_LIMIT = 100;
    private static final ConcurrentMap<Path, ReentrantLock> JVM_LOCKS = new ConcurrentHashMap<Path, ReentrantLock>();

    private final Path directory;
    private final Path stateFile;
    private final Path journalFile;
    private final Path eventsFile;
    private final Path entitiesFile;
    private final Path writerEpochFile;
    private final Path lockFile;
    private final String harnessId;
    private final long journalCompactionBytes;
    private final ReentrantLock jvmLock;

    public FileHarnessStore(FileHarnessConfig config) {
        if (config == null || config.getDirectory() == null) {
            throw new IllegalArgumentException("file harness directory is required");
        }
        this.directory = config.getDirectory().toAbsolutePath().normalize();
        this.harnessId = config.getHarnessId() == null || config.getHarnessId().trim().isEmpty()
                ? "default" : config.getHarnessId().trim();
        this.journalCompactionBytes = config.getJournalCompactionBytes();
        this.stateFile = directory.resolve("state.json");
        this.journalFile = directory.resolve("journal.jsonl");
        this.eventsFile = directory.resolve("events.jsonl");
        this.entitiesFile = directory.resolve("entities.json");
        this.writerEpochFile = directory.resolve("writer-epoch.json");
        this.lockFile = directory.resolve(".lock");
        this.jvmLock = JVM_LOCKS.computeIfAbsent(directory, ignored -> new ReentrantLock());
        try {
            Files.createDirectories(directory);
        } catch (IOException error) {
            throw new HarnessStoreException("cannot create Harness directory: " + directory, error);
        }
    }

    @Override
    public HarnessState load() {
        jvmLock.lock();
        try {
            return withFileLock(new LockedOperation<HarnessState>() {
                @Override
                public HarnessState run() throws IOException {
                    HarnessState recovered = readRecovered();
                    if (!Files.exists(stateFile) || recovered.getVersion() > readSnapshotVersion()) {
                        writeSnapshot(recovered);
                    }
                    ensureLegacyCqrsMigrated(recovered);
                    return recovered.copy();
                }
            });
        } finally {
            jvmLock.unlock();
        }
    }

    @Override
    public HarnessState update(final HarnessStateMutation mutation) {
        if (mutation == null) {
            throw new IllegalArgumentException("Harness mutation is required");
        }
        jvmLock.lock();
        try {
            return withFileLock(new LockedOperation<HarnessState>() {
                @Override
                public HarnessState run() throws IOException {
                    HarnessState current = readRecovered();
                    ensureLegacyCqrsMigrated(current);
                    HarnessState next = mutation.apply(current.copy());
                    if (next == null) {
                        throw new HarnessStoreException("Harness mutation returned null");
                    }
                    next.ensureCollections();
                    next.setHarnessId(harnessId);
                    next.setVersion(current.getVersion() + 1L);
                    next.setUpdatedAtEpochMs(System.currentTimeMillis());
                    appendJournal(next);
                    writeSnapshot(next);
                    syncCqrsOnUpdate(current, next);
                    compactJournalIfNeeded();
                    return next.copy();
                }
            });
        } finally {
            jvmLock.unlock();
        }
    }

    @Override
    public MutationReceipt appendEventAndProject(HarnessEventRecord event) {
        return appendEventAndProject(event, null, null);
    }

    @Override
    public MutationReceipt appendEventAndProject(final HarnessEventRecord event,
                                                 final String entityStatus,
                                                 final String entityPayloadJson) {
        if (event == null) {
            throw new HarnessValidationException("event record is required");
        }
        final String entityType = HarnessSupport.requireText(event.getEntityType(), "entity type");
        final String entityId = HarnessSupport.requireText(event.getEntityId(), "entity id");
        final String eventType = HarnessSupport.requireText(event.getEventType(), "event type");
        final String idempotencyKey = HarnessSupport.trimToNull(event.getIdempotencyKey());
        jvmLock.lock();
        try {
            return withFileLock(new LockedOperation<MutationReceipt>() {
                @Override
                public MutationReceipt run() throws IOException {
                    ensureLegacyCqrsMigratedIfPresent();
                    WriterEpochFence currentFence = readWriterEpoch();
                    if (event.getWriterEpoch() < currentFence.getEpoch()) {
                        return MutationReceipt.builder()
                                .outcome(MutationReceipt.Outcome.EPOCH_FENCED)
                                .entityType(entityType)
                                .entityId(entityId)
                                .writerEpoch(currentFence.getEpoch())
                                .idempotencyKey(idempotencyKey)
                                .message("writer epoch " + event.getWriterEpoch()
                                        + " is fenced by current epoch " + currentFence.getEpoch())
                                .build();
                    }

                    List<HarnessEventRecord> existingEvents = readAllEvents();
                    long maxSeq = 0L;
                    for (HarnessEventRecord existing : existingEvents) {
                        if (existing == null) {
                            continue;
                        }
                        if (existing.getSeq() > maxSeq) {
                            maxSeq = existing.getSeq();
                        }
                        if (idempotencyKey != null && idempotencyKey.equals(existing.getIdempotencyKey())) {
                            return MutationReceipt.builder()
                                    .outcome(MutationReceipt.Outcome.IDEMPOTENT_REPLAY)
                                    .seq(existing.getSeq())
                                    .entityType(existing.getEntityType())
                                    .entityId(existing.getEntityId())
                                    .entityVersion(existing.getEntityVersion())
                                    .writerEpoch(existing.getWriterEpoch())
                                    .idempotencyKey(idempotencyKey)
                                    .message("idempotent replay for key: " + idempotencyKey)
                                    .build();
                        }
                    }

                    Map<HarnessProjectionEngine.EntityKey, HarnessEntityRecord> entities =
                            readEntitiesMap(existingEvents);
                    HarnessProjectionEngine.EntityKey key =
                            new HarnessProjectionEngine.EntityKey(entityType, entityId);
                    HarnessEntityRecord currentEntity = entities.get(key);
                    long currentEntityVersion = currentEntity == null ? 0L : currentEntity.getEntityVersion();
                    if (event.getExpectedVersion() != null
                            && event.getExpectedVersion().longValue() != currentEntityVersion) {
                        return MutationReceipt.builder()
                                .outcome(MutationReceipt.Outcome.VERSION_CONFLICT)
                                .entityType(entityType)
                                .entityId(entityId)
                                .entityVersion(currentEntityVersion)
                                .writerEpoch(currentFence.getEpoch())
                                .idempotencyKey(idempotencyKey)
                                .message("expected version " + event.getExpectedVersion()
                                        + " but current version is " + currentEntityVersion)
                                .build();
                    }

                    long nextSeq = maxSeq + 1L;
                    long nextEntityVersion = currentEntityVersion + 1L;
                    long ts = event.getTimestampEpochMs() > 0L
                            ? event.getTimestampEpochMs() : System.currentTimeMillis();
                    String eventId = HarnessSupport.trimToNull(event.getEventId());
                    if (eventId == null) {
                        eventId = "evt_" + UUID.randomUUID().toString().replace("-", "");
                    }
                    String payloadJson = event.getPayloadJson();
                    if (payloadJson == null || payloadJson.trim().isEmpty()) {
                        payloadJson = entityPayloadJson != null ? entityPayloadJson : "{}";
                    }

                    HarnessEventRecord storedEvent = HarnessEventRecord.builder()
                            .seq(nextSeq)
                            .eventId(eventId)
                            .harnessId(harnessId)
                            .entityType(entityType)
                            .entityId(entityId)
                            .eventType(eventType)
                            .expectedVersion(event.getExpectedVersion())
                            .entityVersion(nextEntityVersion)
                            .writerEpoch(event.getWriterEpoch())
                            .actor(HarnessSupport.trimToNull(event.getActor()))
                            .idempotencyKey(idempotencyKey)
                            .timestampEpochMs(ts)
                            .payloadJson(payloadJson)
                            .build();

                    HarnessEntityRecord nextEntity = HarnessProjectionEngine.projectEvent(
                            harnessId, currentEntity, storedEvent, entityStatus, entityPayloadJson);

                    appendEventLine(storedEvent);
                    entities.put(key, nextEntity);
                    writeEntitiesMap(entities);
                    syncStateSnapshotAfterFencedAppend(storedEvent, nextEntity);

                    return MutationReceipt.builder()
                            .outcome(MutationReceipt.Outcome.APPLIED)
                            .seq(nextSeq)
                            .entityType(entityType)
                            .entityId(entityId)
                            .entityVersion(nextEntity.getEntityVersion())
                            .writerEpoch(storedEvent.getWriterEpoch())
                            .idempotencyKey(idempotencyKey)
                            .message("applied")
                            .build();
                }
            });
        } finally {
            jvmLock.unlock();
        }
    }

    @Override
    public CursorPage<HarnessEntityRecord> listEntitiesPage(final String entityType,
                                                            final String statusFilter,
                                                            final String cursor,
                                                            final int limit) {
        HarnessProjectionEngine.decodeCursor(cursor);
        jvmLock.lock();
        try {
            return withFileLock(new LockedOperation<CursorPage<HarnessEntityRecord>>() {
                @Override
                public CursorPage<HarnessEntityRecord> run() throws IOException {
                    ensureLegacyCqrsMigratedIfPresent();
                    Map<HarnessProjectionEngine.EntityKey, HarnessEntityRecord> entities =
                            readEntitiesMap(null);
                    return HarnessProjectionEngine.paginateEntities(
                            entities.values(), entityType, statusFilter, cursor, limit);
                }
            });
        } finally {
            jvmLock.unlock();
        }
    }

    @Override
    public List<HarnessEventRecord> listEventsSince(final long afterSeq, final int limit) {
        final int effectiveLimit = limit <= 0 ? DEFAULT_EVENT_LIMIT : limit;
        jvmLock.lock();
        try {
            return withFileLock(new LockedOperation<List<HarnessEventRecord>>() {
                @Override
                public List<HarnessEventRecord> run() throws IOException {
                    ensureLegacyCqrsMigratedIfPresent();
                    List<HarnessEventRecord> all = readAllEvents();
                    List<HarnessEventRecord> filtered = new ArrayList<HarnessEventRecord>();
                    for (HarnessEventRecord event : all) {
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
            });
        } finally {
            jvmLock.unlock();
        }
    }

    @Override
    public int rebuildEntitiesFromEvents() {
        jvmLock.lock();
        try {
            return withFileLock(new LockedOperation<Integer>() {
                @Override
                public Integer run() throws IOException {
                    List<HarnessEventRecord> events = readAllEvents();
                    Map<HarnessProjectionEngine.EntityKey, HarnessEntityRecord> rebuilt =
                            HarnessProjectionEngine.foldEvents(harnessId, events);
                    writeEntitiesMap(rebuilt);
                    return rebuilt.size();
                }
            });
        } finally {
            jvmLock.unlock();
        }
    }

    @Override
    public WriterEpochFence claimWriterEpoch(String nodeId, final long expectedEpoch) {
        final String holder = HarnessSupport.requireText(nodeId, "node id");
        if (expectedEpoch < 0L) {
            throw new HarnessValidationException("expected writer epoch must be >= 0");
        }
        jvmLock.lock();
        try {
            return withFileLock(new LockedOperation<WriterEpochFence>() {
                @Override
                public WriterEpochFence run() throws IOException {
                    WriterEpochFence current = readWriterEpoch();
                    if (current.getEpoch() != expectedEpoch) {
                        throw new HarnessConflictException("writer epoch mismatch for " + harnessId
                                + ": expected " + expectedEpoch + ", current " + current.getEpoch());
                    }
                    WriterEpochFence next = new WriterEpochFence(
                            expectedEpoch + 1L, holder, System.currentTimeMillis());
                    writeWriterEpoch(next);
                    return next.copy();
                }
            });
        } finally {
            jvmLock.unlock();
        }
    }

    @Override
    public WriterEpochFence currentWriterEpoch() {
        jvmLock.lock();
        try {
            return withFileLock(new LockedOperation<WriterEpochFence>() {
                @Override
                public WriterEpochFence run() throws IOException {
                    return readWriterEpoch().copy();
                }
            });
        } finally {
            jvmLock.unlock();
        }
    }

    private HarnessState readRecovered() throws IOException {
        HarnessState snapshot;
        HarnessStoreException snapshotFailure = null;
        if (!Files.exists(stateFile)) {
            snapshot = HarnessState.empty(harnessId);
        } else {
            try {
                snapshot = readSnapshot();
            } catch (HarnessStoreException error) {
                snapshotFailure = error;
                snapshot = HarnessState.empty(harnessId);
            }
        }
        HarnessState latest = snapshot;
        boolean recoveredJournalState = false;
        if (Files.exists(journalFile)) {
            List<String> lines = Files.readAllLines(journalFile, StandardCharsets.UTF_8);
            int lastNonBlank = -1;
            for (int i = 0; i < lines.size(); i++) {
                if (!lines.get(i).trim().isEmpty()) {
                    lastNonBlank = i;
                }
            }
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.trim().isEmpty()) {
                    continue;
                }
                try {
                    Map<String, Object> envelope = HarnessJson.parseMap(line);
                    if (envelope == null || envelope.get("state") == null) {
                        throw new IllegalArgumentException("journal entry has no state");
                    }
                    Object rawVersion = envelope.get("version");
                    if (!(rawVersion instanceof Number)) {
                        throw new IllegalArgumentException("journal entry has no version");
                    }
                    long version = ((Number) rawVersion).longValue();
                    Object rawState = envelope.get("state");
                    HarnessState candidate = rawState instanceof String
                            ? HarnessJson.fromJson((String) rawState, HarnessState.class)
                            : HarnessJson.convert(rawState, HarnessState.class);
                    if (candidate == null || candidate.getVersion() != version) {
                        throw new IllegalArgumentException("journal entry version does not match its state");
                    }
                    recoveredJournalState = true;
                    if (version > latest.getVersion()) {
                        latest = candidate;
                    }
                } catch (RuntimeException error) {
                    if (i == lastNonBlank) {
                        continue;
                    }
                    throw new HarnessStoreException("cannot recover Harness journal line " + (i + 1), error);
                }
            }
        }
        if (snapshotFailure != null && !recoveredJournalState) {
            throw new HarnessStoreException("Harness snapshot is corrupt and no recoverable journal state exists",
                    snapshotFailure);
        }
        return validateHarness(latest);
    }

    private HarnessState readSnapshot() throws IOException {
        if (!Files.exists(stateFile)) {
            return HarnessState.empty(harnessId);
        }
        String json = new String(Files.readAllBytes(stateFile), StandardCharsets.UTF_8);
        HarnessState state;
        try {
            state = HarnessJson.fromJson(json, HarnessState.class);
            if (state == null) {
                throw new IllegalArgumentException("snapshot is empty");
            }
        } catch (RuntimeException error) {
            throw new HarnessStoreException("cannot decode Harness snapshot: " + stateFile, error);
        }
        return validateHarness(state);
    }

    private long readSnapshotVersion() throws IOException {
        if (!Files.exists(stateFile)) {
            return -1L;
        }
        try {
            HarnessState state = HarnessJson.fromJson(
                    new String(Files.readAllBytes(stateFile), StandardCharsets.UTF_8), HarnessState.class);
            return state == null ? -1L : state.getVersion();
        } catch (RuntimeException error) {
            return -1L;
        }
    }

    private HarnessState validateHarness(HarnessState state) {
        if (state == null) {
            return HarnessState.empty(harnessId);
        }
        if (state.getHarnessId() == null || state.getHarnessId().trim().isEmpty()) {
            state.setHarnessId(harnessId);
        } else if (!harnessId.equals(state.getHarnessId())) {
            throw new HarnessStoreException("Harness id mismatch: expected " + harnessId
                    + ", found " + state.getHarnessId());
        }
        state.ensureCollections();
        return state;
    }

    private void appendJournal(HarnessState state) throws IOException {
        Map<String, Object> envelope = new LinkedHashMap<String, Object>();
        envelope.put("version", state.getVersion());
        envelope.put("state", HarnessJson.toMap(state));
        try (BufferedWriter writer = Files.newBufferedWriter(journalFile, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE)) {
            writer.write(HarnessJson.toJson(envelope));
            writer.newLine();
        }
    }

    private void writeSnapshot(HarnessState state) throws IOException {
        writeAtomically(stateFile, HarnessJson.toJson(state).getBytes(StandardCharsets.UTF_8));
    }

    private void ensureLegacyCqrsMigratedIfPresent() throws IOException {
        if (Files.exists(eventsFile) || Files.exists(entitiesFile)) {
            return;
        }
        if (!Files.exists(stateFile) && !Files.exists(journalFile)) {
            return;
        }
        HarnessState recovered;
        try {
            recovered = readRecovered();
        } catch (HarnessStoreException ignored) {
            return;
        }
        ensureLegacyCqrsMigrated(recovered);
    }

    private void ensureLegacyCqrsMigrated(HarnessState recovered) throws IOException {
        if (recovered == null || Files.exists(eventsFile) || Files.exists(entitiesFile)) {
            return;
        }
        Map<HarnessProjectionEngine.EntityKey, HarnessEntityRecord> entities =
                HarnessProjectionEngine.extractEntitiesFromState(harnessId, recovered);
        if (entities.isEmpty() && recovered.getEvents().isEmpty()) {
            return;
        }
        writeEntitiesMap(entities);
        long nextSeq = 1L;
        for (HarnessEventRecord raw : recovered.getEvents()) {
            if (raw == null) {
                continue;
            }
            long seq = raw.getSeq() > 0L ? raw.getSeq() : nextSeq;
            nextSeq = Math.max(nextSeq, seq + 1L);
            HarnessEventRecord enriched = HarnessProjectionEngine.enrichEventForLedger(
                    harnessId, seq, raw, entities);
            appendEventLine(enriched);
        }
    }

    private void syncCqrsOnUpdate(HarnessState previous, HarnessState next) throws IOException {
        Map<HarnessProjectionEngine.EntityKey, HarnessEntityRecord> before =
                HarnessProjectionEngine.extractEntitiesFromState(harnessId, previous);
        Map<HarnessProjectionEngine.EntityKey, HarnessEntityRecord> after =
                HarnessProjectionEngine.extractEntitiesFromState(harnessId, next);

        List<HarnessEventRecord> existingEvents = null;
        Map<HarnessProjectionEngine.EntityKey, HarnessEntityRecord> existingEntities = readEntitiesMap(null);
        boolean entitiesChanged = false;
        for (Map.Entry<HarnessProjectionEngine.EntityKey, HarnessEntityRecord> entry : after.entrySet()) {
            HarnessEntityRecord prevEntity = before.get(entry.getKey());
            HarnessEntityRecord nextEntity = entry.getValue();
            if (prevEntity == null
                    || !HarnessSupport.safeEquals(prevEntity.getStatus(), nextEntity.getStatus())
                    || !HarnessSupport.safeEquals(prevEntity.getPayloadJson(), nextEntity.getPayloadJson())) {
                HarnessEntityRecord stored = existingEntities.get(entry.getKey());
                long resolvedVersion = stored == null
                        ? nextEntity.getEntityVersion()
                        : Math.max(nextEntity.getEntityVersion(), stored.getEntityVersion() + 1L);
                nextEntity.setEntityVersion(resolvedVersion);
                existingEntities.put(entry.getKey(), nextEntity);
                entitiesChanged = true;
            }
        }
        for (HarnessProjectionEngine.EntityKey key : before.keySet()) {
            if (!after.containsKey(key) && existingEntities.remove(key) != null) {
                entitiesChanged = true;
            }
        }
        if (entitiesChanged || (!after.isEmpty() && !Files.exists(entitiesFile))) {
            writeEntitiesMap(existingEntities);
        }

        int prevEvents = previous.getEvents() == null ? 0 : previous.getEvents().size();
        List<HarnessEventRecord> nextEvents = next.getEvents();
        if (nextEvents != null && nextEvents.size() > prevEvents) {
            existingEvents = readAllEvents();
            long maxSeq = 0L;
            for (HarnessEventRecord e : existingEvents) {
                if (e != null && e.getSeq() > maxSeq) {
                    maxSeq = e.getSeq();
                }
            }
            for (int i = prevEvents; i < nextEvents.size(); i++) {
                HarnessEventRecord raw = nextEvents.get(i);
                if (raw == null) {
                    continue;
                }
                long seq = Math.max(maxSeq + 1L, raw.getSeq());
                maxSeq = seq;
                HarnessEventRecord enriched = HarnessProjectionEngine.enrichEventForLedger(
                        harnessId, seq, raw, after);
                appendEventLine(enriched);
            }
        }
    }

    private void syncStateSnapshotAfterFencedAppend(HarnessEventRecord storedEvent,
                                                    HarnessEntityRecord nextEntity) throws IOException {
        HarnessState state;
        if (Files.exists(stateFile)) {
            try {
                state = readRecovered();
            } catch (HarnessStoreException ignored) {
                return;
            }
        } else {
            state = HarnessState.empty(harnessId);
        }
        state.ensureCollections();
        boolean applied = HarnessProjectionEngine.applyEntityToState(state, nextEntity);
        if (!applied && !Files.exists(stateFile)) {
            return;
        }
        state.getEvents().add(storedEvent.copy());
        state.setHarnessId(harnessId);
        state.setVersion(state.getVersion() + 1L);
        state.setUpdatedAtEpochMs(storedEvent.getTimestampEpochMs());
        appendJournal(state);
        writeSnapshot(state);
        compactJournalIfNeeded();
    }

    private List<HarnessEventRecord> readAllEvents() throws IOException {
        List<HarnessEventRecord> result = new ArrayList<HarnessEventRecord>();
        if (!Files.exists(eventsFile)) {
            return result;
        }
        List<String> lines = Files.readAllLines(eventsFile, StandardCharsets.UTF_8);
        int lastNonBlank = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (!lines.get(i).trim().isEmpty()) {
                lastNonBlank = i;
            }
        }
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.trim().isEmpty()) {
                continue;
            }
            try {
                HarnessEventRecord record = HarnessJson.fromJson(line, HarnessEventRecord.class);
                if (record == null) {
                    throw new IllegalArgumentException("empty event line");
                }
                result.add(record);
            } catch (RuntimeException error) {
                if (i == lastNonBlank) {
                    // Only the trailing line may be torn by an abrupt crash mid-append.
                    continue;
                }
                throw new HarnessStoreException("cannot decode Harness event line " + (i + 1), error);
            }
        }
        return result;
    }

    private void appendEventLine(HarnessEventRecord event) throws IOException {
        HarnessEventRecord toStore = HarnessEventRecord.builder()
                .seq(event.getSeq())
                .eventId(event.getEventId())
                .harnessId(harnessId)
                .entityType(event.getEntityType())
                .entityId(event.getEntityId())
                .eventType(event.getEventType())
                .expectedVersion(event.getExpectedVersion())
                .entityVersion(event.getEntityVersion())
                .writerEpoch(event.getWriterEpoch())
                .actor(event.getActor())
                .idempotencyKey(HarnessSupport.trimToNull(event.getIdempotencyKey()))
                .timestampEpochMs(event.getTimestampEpochMs())
                .payloadJson(event.getPayloadJson() == null ? "{}" : event.getPayloadJson())
                .build();
        try (BufferedWriter writer = Files.newBufferedWriter(eventsFile, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE)) {
            writer.write(HarnessJson.toJson(toStore));
            writer.newLine();
        }
    }

    private Map<HarnessProjectionEngine.EntityKey, HarnessEntityRecord> readEntitiesMap(
            List<HarnessEventRecord> fallbackEvents) throws IOException {
        if (!Files.exists(entitiesFile)) {
            if (Files.exists(eventsFile)) {
                List<HarnessEventRecord> events = fallbackEvents != null ? fallbackEvents : readAllEvents();
                return HarnessProjectionEngine.foldEvents(harnessId, events);
            }
            return new LinkedHashMap<HarnessProjectionEngine.EntityKey, HarnessEntityRecord>();
        }
        String json = new String(Files.readAllBytes(entitiesFile), StandardCharsets.UTF_8);
        EntitiesSnapshot snapshot = HarnessJson.fromJson(json, EntitiesSnapshot.class);
        if (snapshot != null && snapshot.harnessId != null
                && !snapshot.harnessId.trim().isEmpty()
                && !harnessId.equals(snapshot.harnessId)) {
            throw new HarnessStoreException("Harness id mismatch in entities snapshot: expected "
                    + harnessId + ", found " + snapshot.harnessId);
        }
        Map<HarnessProjectionEngine.EntityKey, HarnessEntityRecord> result =
                new LinkedHashMap<HarnessProjectionEngine.EntityKey, HarnessEntityRecord>();
        if (snapshot != null && snapshot.entities != null) {
            for (HarnessEntityRecord entity : snapshot.entities) {
                if (entity != null && entity.getEntityType() != null && entity.getEntityId() != null) {
                    result.put(new HarnessProjectionEngine.EntityKey(
                            entity.getEntityType(), entity.getEntityId()), entity);
                }
            }
        }
        return result;
    }

    private void writeEntitiesMap(Map<HarnessProjectionEngine.EntityKey, HarnessEntityRecord> entities)
            throws IOException {
        EntitiesSnapshot snapshot = new EntitiesSnapshot();
        snapshot.harnessId = harnessId;
        snapshot.entities = entities == null
                ? new ArrayList<HarnessEntityRecord>()
                : new ArrayList<HarnessEntityRecord>(entities.values());
        writeAtomically(entitiesFile, HarnessJson.toJson(snapshot).getBytes(StandardCharsets.UTF_8));
    }

    private WriterEpochFence readWriterEpoch() throws IOException {
        if (!Files.exists(writerEpochFile)) {
            return new WriterEpochFence(0L, null, 0L);
        }
        String json = new String(Files.readAllBytes(writerEpochFile), StandardCharsets.UTF_8);
        WriterEpochFence fence = HarnessJson.fromJson(json, WriterEpochFence.class);
        return fence == null ? new WriterEpochFence(0L, null, 0L) : fence;
    }

    private void writeWriterEpoch(WriterEpochFence fence) throws IOException {
        writeAtomically(writerEpochFile, HarnessJson.toJson(fence).getBytes(StandardCharsets.UTF_8));
    }

    private void writeAtomically(Path target, byte[] bytes) throws IOException {
        Path fileName = target.getFileName();
        String baseName = fileName == null ? "state" : fileName.toString();
        Path temporary = directory.resolve(baseName + ".tmp-" + Thread.currentThread().getId());
        Files.write(temporary, bytes,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void compactJournalIfNeeded() {
        if (journalCompactionBytes <= 0L) {
            return;
        }
        try {
            if (!Files.exists(journalFile) || Files.size(journalFile) <= journalCompactionBytes) {
                return;
            }
            writeAtomically(journalFile, new byte[0]);
        } catch (IOException ignored) {
            // The snapshot is already the committed state. Leaving the old
            // journal in place is safe and the next update can retry trim.
        }
    }

    private <T> T withFileLock(LockedOperation<T> operation) {
        try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock ignored = channel.lock()) {
            return operation.run();
        } catch (HarnessStoreException error) {
            throw error;
        } catch (IOException error) {
            throw new HarnessStoreException("file Harness store operation failed", error);
        } catch (RuntimeException error) {
            throw new HarnessStoreException("file Harness store operation failed", error);
        }
    }

    private static final class EntitiesSnapshot {
        private String harnessId;
        private List<HarnessEntityRecord> entities = new ArrayList<HarnessEntityRecord>();
    }

    private interface LockedOperation<T> {
        T run() throws IOException;
    }
}
