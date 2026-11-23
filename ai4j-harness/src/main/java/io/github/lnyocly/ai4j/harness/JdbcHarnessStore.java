package io.github.lnyocly.ai4j.harness;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * JDBC store using a single-transaction row lock per stable harness id,
 * supporting both the legacy snapshot/journal contract and the 2.0 CQRS
 * append-only event ledger, entity projections, cursor pagination, and
 * split-brain writer epoch fencing.
 */
public final class JdbcHarnessStore implements HarnessEventStore {

    private static final int DEFAULT_JOURNAL_RETENTION_VERSIONS = 128;
    private static final int DEFAULT_EVENT_LIMIT = 100;
    private static final Object SCHEMA_INIT_LOCK = new Object();

    private static final String SQL_PAGE_ALL =
            "SELECT harness_id, entity_type, entity_id, entity_version, status, updated_at, payload_json "
                    + "FROM ai4j_harness_entities WHERE harness_id = ? "
                    + "ORDER BY entity_type ASC, entity_id ASC LIMIT ?";
    private static final String SQL_PAGE_ALL_CURSOR =
            "SELECT harness_id, entity_type, entity_id, entity_version, status, updated_at, payload_json "
                    + "FROM ai4j_harness_entities WHERE harness_id = ? "
                    + "AND (entity_type > ? OR (entity_type = ? AND entity_id > ?)) "
                    + "ORDER BY entity_type ASC, entity_id ASC LIMIT ?";
    private static final String SQL_PAGE_STATUS =
            "SELECT harness_id, entity_type, entity_id, entity_version, status, updated_at, payload_json "
                    + "FROM ai4j_harness_entities WHERE harness_id = ? AND status = ? "
                    + "ORDER BY entity_type ASC, entity_id ASC LIMIT ?";
    private static final String SQL_PAGE_STATUS_CURSOR =
            "SELECT harness_id, entity_type, entity_id, entity_version, status, updated_at, payload_json "
                    + "FROM ai4j_harness_entities WHERE harness_id = ? AND status = ? "
                    + "AND (entity_type > ? OR (entity_type = ? AND entity_id > ?)) "
                    + "ORDER BY entity_type ASC, entity_id ASC LIMIT ?";
    private static final String SQL_PAGE_TYPE =
            "SELECT harness_id, entity_type, entity_id, entity_version, status, updated_at, payload_json "
                    + "FROM ai4j_harness_entities WHERE harness_id = ? AND entity_type = ? "
                    + "ORDER BY entity_type ASC, entity_id ASC LIMIT ?";
    private static final String SQL_PAGE_TYPE_CURSOR =
            "SELECT harness_id, entity_type, entity_id, entity_version, status, updated_at, payload_json "
                    + "FROM ai4j_harness_entities WHERE harness_id = ? AND entity_type = ? "
                    + "AND (entity_type > ? OR (entity_type = ? AND entity_id > ?)) "
                    + "ORDER BY entity_type ASC, entity_id ASC LIMIT ?";
    private static final String SQL_PAGE_TYPE_STATUS =
            "SELECT harness_id, entity_type, entity_id, entity_version, status, updated_at, payload_json "
                    + "FROM ai4j_harness_entities WHERE harness_id = ? AND entity_type = ? AND status = ? "
                    + "ORDER BY entity_type ASC, entity_id ASC LIMIT ?";
    private static final String SQL_PAGE_TYPE_STATUS_CURSOR =
            "SELECT harness_id, entity_type, entity_id, entity_version, status, updated_at, payload_json "
                    + "FROM ai4j_harness_entities WHERE harness_id = ? AND entity_type = ? AND status = ? "
                    + "AND (entity_type > ? OR (entity_type = ? AND entity_id > ?)) "
                    + "ORDER BY entity_type ASC, entity_id ASC LIMIT ?";

    private static final String SQL_COUNT_ALL =
            "SELECT COUNT(*) FROM ai4j_harness_entities WHERE harness_id = ?";
    private static final String SQL_COUNT_STATUS =
            "SELECT COUNT(*) FROM ai4j_harness_entities WHERE harness_id = ? AND status = ?";
    private static final String SQL_COUNT_TYPE =
            "SELECT COUNT(*) FROM ai4j_harness_entities WHERE harness_id = ? AND entity_type = ?";
    private static final String SQL_COUNT_TYPE_STATUS =
            "SELECT COUNT(*) FROM ai4j_harness_entities WHERE harness_id = ? AND entity_type = ? AND status = ?";

    private final DataSource dataSource;
    private final String harnessId;
    private final int journalRetentionVersions;

    public JdbcHarnessStore(DataSource dataSource, String harnessId) {
        this(dataSource, harnessId, DEFAULT_JOURNAL_RETENTION_VERSIONS);
    }

    public JdbcHarnessStore(DataSource dataSource,
                            String harnessId,
                            int journalRetentionVersions) {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource is required");
        }
        this.dataSource = dataSource;
        this.harnessId = harnessId == null || harnessId.trim().isEmpty() ? "default" : harnessId.trim();
        this.journalRetentionVersions = journalRetentionVersions;
        initializeSchema();
    }

    public void initializeSchema() {
        try (Connection connection = dataSource.getConnection()) {
            synchronized (SCHEMA_INIT_LOCK) {
                createSchemaObjects(connection);
            }
            ensureStateRow(connection);
            ensureWriterEpochRow(connection);
            migrateLegacyStateIfNeeded(connection);
        } catch (SQLException error) {
            throw new HarnessStoreException("cannot initialize JDBC Harness schema", error);
        }
    }

    private void createSchemaObjects(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            try {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS ai4j_harness_state ("
                        + "harness_id VARCHAR(255) PRIMARY KEY, version BIGINT NOT NULL, "
                        + "state_json TEXT NOT NULL, updated_at BIGINT NOT NULL)");
            } catch (SQLException error) {
                if (!isSchemaObjectAlreadyExists(error)) {
                    throw error;
                }
            }
            try {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS ai4j_harness_journal ("
                        + "harness_id VARCHAR(255) NOT NULL, version BIGINT NOT NULL, "
                        + "state_json TEXT NOT NULL, recorded_at BIGINT NOT NULL, "
                        + "PRIMARY KEY (harness_id, version))");
            } catch (SQLException error) {
                if (!isSchemaObjectAlreadyExists(error)) {
                    throw error;
                }
            }
            try {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS ai4j_harness_events ("
                        + "harness_id VARCHAR(255) NOT NULL, "
                        + "seq BIGINT NOT NULL, "
                        + "event_id VARCHAR(255) NOT NULL, "
                        + "entity_type VARCHAR(128) NOT NULL, "
                        + "entity_id VARCHAR(255) NOT NULL, "
                        + "event_type VARCHAR(255) NOT NULL, "
                        + "expected_version BIGINT, "
                        + "entity_version BIGINT NOT NULL, "
                        + "writer_epoch BIGINT NOT NULL, "
                        + "actor VARCHAR(255), "
                        + "idempotency_key VARCHAR(255), "
                        + "timestamp_epoch_ms BIGINT NOT NULL, "
                        + "payload_json TEXT NOT NULL, "
                        + "PRIMARY KEY (harness_id, seq))");
            } catch (SQLException error) {
                if (!isSchemaObjectAlreadyExists(error)) {
                    throw error;
                }
            }
            try {
                statement.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS idx_ai4j_harness_events_idempotency "
                        + "ON ai4j_harness_events (harness_id, idempotency_key)");
            } catch (SQLException error) {
                if (!isSchemaObjectAlreadyExists(error)) {
                    throw error;
                }
            }
            try {
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_ai4j_harness_events_entity "
                        + "ON ai4j_harness_events (harness_id, entity_type, entity_id, seq)");
            } catch (SQLException error) {
                if (!isSchemaObjectAlreadyExists(error)) {
                    throw error;
                }
            }
            try {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS ai4j_harness_entities ("
                        + "harness_id VARCHAR(255) NOT NULL, "
                        + "entity_type VARCHAR(128) NOT NULL, "
                        + "entity_id VARCHAR(255) NOT NULL, "
                        + "entity_version BIGINT NOT NULL, "
                        + "status VARCHAR(128), "
                        + "updated_at BIGINT NOT NULL, "
                        + "payload_json TEXT NOT NULL, "
                        + "PRIMARY KEY (harness_id, entity_type, entity_id))");
            } catch (SQLException error) {
                if (!isSchemaObjectAlreadyExists(error)) {
                    throw error;
                }
            }
            try {
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_ai4j_harness_entities_page "
                        + "ON ai4j_harness_entities (harness_id, entity_type, status, entity_id)");
            } catch (SQLException error) {
                if (!isSchemaObjectAlreadyExists(error)) {
                    throw error;
                }
            }
            try {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS ai4j_harness_writer_epoch ("
                        + "harness_id VARCHAR(255) PRIMARY KEY, "
                        + "writer_epoch BIGINT NOT NULL, "
                        + "holder_node_id VARCHAR(255), "
                        + "acquired_at BIGINT NOT NULL)");
            } catch (SQLException error) {
                if (!isSchemaObjectAlreadyExists(error)) {
                    throw error;
                }
            }
        }
    }

    private boolean isSchemaObjectAlreadyExists(SQLException error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof SQLException) {
                String sqlState = ((SQLException) current).getSQLState();
                if ("42S01".equals(sqlState)
                        || "42S11".equals(sqlState)
                        || "42710".equals(sqlState)
                        || "42P07".equals(sqlState)) {
                    return true;
                }
            }
            String message = current.getMessage();
            if (message != null) {
                String lower = message.toLowerCase(java.util.Locale.ROOT);
                if (lower.contains("already exists")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private void ensureStateRow(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO ai4j_harness_state (harness_id, version, state_json, updated_at) VALUES (?, 0, ?, 0)")) {
            statement.setString(1, harnessId);
            statement.setString(2, HarnessJson.toJson(HarnessState.empty(harnessId)));
            statement.executeUpdate();
        } catch (SQLException error) {
            if (!isDuplicateKey(error)) {
                throw error;
            }
        }
    }

    private void ensureWriterEpochRow(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO ai4j_harness_writer_epoch (harness_id, writer_epoch, holder_node_id, acquired_at) "
                        + "VALUES (?, 0, NULL, 0)")) {
            statement.setString(1, harnessId);
            statement.executeUpdate();
        } catch (SQLException error) {
            if (!isDuplicateKey(error)) {
                throw error;
            }
        }
    }

    private void migrateLegacyStateIfNeeded(Connection connection) throws SQLException {
        if (hasAnyProjectedEntities(connection) || hasAnyLedgerEvents(connection)) {
            return;
        }
        HarnessState existing = null;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT version, state_json FROM ai4j_harness_state WHERE harness_id = ?")) {
            statement.setString(1, harnessId);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next() && result.getLong("version") > 0L) {
                    existing = decodeState(result.getString("state_json"));
                }
            }
        } catch (RuntimeException ignored) {
            // Corrupt legacy state should surface explicitly on load().
            return;
        }
        if (existing == null) {
            return;
        }
        Map<HarnessProjectionEngine.EntityKey, HarnessEntityRecord> entities =
                HarnessProjectionEngine.extractEntitiesFromState(harnessId, existing);
        for (HarnessEntityRecord entity : entities.values()) {
            upsertEntityIfAbsent(connection, entity);
        }
        long nextSeq = 1L;
        for (HarnessEventRecord raw : existing.getEvents()) {
            if (raw == null) {
                continue;
            }
            long seq = raw.getSeq() > 0L ? raw.getSeq() : nextSeq;
            nextSeq = Math.max(nextSeq, seq + 1L);
            HarnessEventRecord enriched = HarnessProjectionEngine.enrichEventForLedger(
                    harnessId, seq, raw, entities);
            insertEventIfAbsent(connection, enriched);
        }
    }

    private boolean hasAnyProjectedEntities(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM ai4j_harness_entities WHERE harness_id = ?")) {
            statement.setString(1, harnessId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getLong(1) > 0L;
            }
        }
    }

    private boolean hasAnyLedgerEvents(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM ai4j_harness_events WHERE harness_id = ?")) {
            statement.setString(1, harnessId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getLong(1) > 0L;
            }
        }
    }

    private boolean isDuplicateKey(SQLException error) {
        SQLException current = error;
        while (current != null) {
            String sqlState = current.getSQLState();
            if (sqlState != null && sqlState.startsWith("23")) {
                return true;
            }
            current = current.getNextException();
        }
        return false;
    }

    @Override
    public HarnessState load() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT version, state_json FROM ai4j_harness_state WHERE harness_id = ?")) {
            statement.setString(1, harnessId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return HarnessState.empty(harnessId);
                }
                HarnessState state = decodeState(result.getString("state_json"));
                state.setVersion(result.getLong("version"));
                return state.copy();
            }
        } catch (HarnessStoreException error) {
            throw error;
        } catch (SQLException error) {
            throw new HarnessStoreException("cannot load JDBC Harness state", error);
        }
    }

    @Override
    public HarnessState update(final HarnessStateMutation mutation) {
        if (mutation == null) {
            throw new IllegalArgumentException("Harness mutation is required");
        }
        return inTransaction(new TxOperation<HarnessState>() {
            @Override
            public HarnessState run(Connection connection) throws SQLException {
                HarnessState current = selectForUpdate(connection);
                HarnessState next = mutation.apply(current.copy());
                if (next == null) {
                    throw new HarnessStoreException("Harness mutation returned null");
                }
                next.ensureCollections();
                next.setHarnessId(harnessId);
                next.setVersion(current.getVersion() + 1L);
                next.setUpdatedAtEpochMs(System.currentTimeMillis());
                String json = HarnessJson.toJson(next);
                if (current.getVersion() == 0L && !exists(connection)) {
                    insertState(connection, next.getVersion(), json, next.getUpdatedAtEpochMs());
                } else {
                    updateState(connection, current.getVersion(), next.getVersion(), json, next.getUpdatedAtEpochMs());
                }
                insertJournal(connection, next.getVersion(), json, next.getUpdatedAtEpochMs());
                pruneJournal(connection, next.getVersion());
                syncEntitiesAndEvents(connection, current, next);
                return next.copy();
            }
        }, "JDBC Harness update failed");
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
        return inTransaction(new TxOperation<MutationReceipt>() {
            @Override
            public MutationReceipt run(Connection connection) throws SQLException {
                HarnessState currentState = selectForUpdate(connection);
                WriterEpochFence fence = loadWriterEpochForUpdate(connection);
                if (event.getWriterEpoch() < fence.getEpoch()) {
                    return MutationReceipt.builder()
                            .outcome(MutationReceipt.Outcome.EPOCH_FENCED)
                            .entityType(entityType)
                            .entityId(entityId)
                            .writerEpoch(fence.getEpoch())
                            .idempotencyKey(idempotencyKey)
                            .message("writer epoch " + event.getWriterEpoch()
                                    + " is fenced by current epoch " + fence.getEpoch())
                            .build();
                }
                if (idempotencyKey != null) {
                    HarnessEventRecord duplicate = findEventByIdempotencyKey(connection, idempotencyKey);
                    if (duplicate != null) {
                        return MutationReceipt.builder()
                                .outcome(MutationReceipt.Outcome.IDEMPOTENT_REPLAY)
                                .seq(duplicate.getSeq())
                                .entityType(duplicate.getEntityType())
                                .entityId(duplicate.getEntityId())
                                .entityVersion(duplicate.getEntityVersion())
                                .writerEpoch(duplicate.getWriterEpoch())
                                .idempotencyKey(idempotencyKey)
                                .message("idempotent replay for key: " + idempotencyKey)
                                .build();
                    }
                }
                HarnessEntityRecord currentEntity = selectEntity(connection, entityType, entityId);
                long currentEntityVersion = currentEntity == null ? 0L : currentEntity.getEntityVersion();
                if (event.getExpectedVersion() != null
                        && event.getExpectedVersion().longValue() != currentEntityVersion) {
                    return MutationReceipt.builder()
                            .outcome(MutationReceipt.Outcome.VERSION_CONFLICT)
                            .entityType(entityType)
                            .entityId(entityId)
                            .entityVersion(currentEntityVersion)
                            .writerEpoch(fence.getEpoch())
                            .idempotencyKey(idempotencyKey)
                            .message("expected version " + event.getExpectedVersion()
                                    + " but current version is " + currentEntityVersion)
                            .build();
                }

                long nextSeq = selectMaxEventSeq(connection) + 1L;
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

                insertEvent(connection, storedEvent);
                upsertEntity(connection, nextEntity);
                syncStateAfterFencedAppend(connection, currentState, storedEvent, nextEntity);

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
        }, "JDBC Harness appendEventAndProject failed");
    }

    @Override
    public CursorPage<HarnessEntityRecord> listEntitiesPage(String entityType,
                                                            String statusFilter,
                                                            String cursor,
                                                            int limit) {
        String normalizedType = HarnessSupport.trimToNull(entityType);
        String normalizedStatus = HarnessSupport.trimToNull(statusFilter);
        HarnessProjectionEngine.EntityCursor parsedCursor = HarnessProjectionEngine.decodeCursor(cursor);
        int effectiveLimit = HarnessProjectionEngine.effectivePageLimit(limit);
        int fetchLimit = effectiveLimit + 1;

        try (Connection connection = dataSource.getConnection()) {
            long totalCount = countEntities(connection, normalizedType, normalizedStatus);
            List<HarnessEntityRecord> rows = queryEntityPage(
                    connection, normalizedType, normalizedStatus, parsedCursor, fetchLimit);
            boolean hasMore = rows.size() > effectiveLimit;
            List<HarnessEntityRecord> items = hasMore
                    ? new ArrayList<HarnessEntityRecord>(rows.subList(0, effectiveLimit))
                    : rows;
            String nextCursor = null;
            if (hasMore && !items.isEmpty()) {
                HarnessEntityRecord last = items.get(items.size() - 1);
                nextCursor = HarnessProjectionEngine.encodeCursor(last.getEntityType(), last.getEntityId());
            }
            return CursorPage.<HarnessEntityRecord>builder()
                    .items(items)
                    .nextCursor(nextCursor)
                    .hasMore(hasMore)
                    .totalCount(totalCount)
                    .build();
        } catch (HarnessStoreException error) {
            throw error;
        } catch (SQLException error) {
            throw new HarnessStoreException("cannot query JDBC Harness entities page", error);
        }
    }

    @Override
    public List<HarnessEventRecord> listEventsSince(long afterSeq, int limit) {
        int effectiveLimit = limit <= 0 ? DEFAULT_EVENT_LIMIT : limit;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT harness_id, seq, event_id, entity_type, entity_id, event_type, "
                             + "expected_version, entity_version, writer_epoch, actor, "
                             + "idempotency_key, timestamp_epoch_ms, payload_json "
                             + "FROM ai4j_harness_events WHERE harness_id = ? AND seq > ? "
                             + "ORDER BY seq ASC LIMIT ?")) {
            statement.setString(1, harnessId);
            statement.setLong(2, afterSeq);
            statement.setInt(3, effectiveLimit);
            try (ResultSet result = statement.executeQuery()) {
                List<HarnessEventRecord> events = new ArrayList<HarnessEventRecord>();
                while (result.next()) {
                    events.add(readEventRow(result));
                }
                return events;
            }
        } catch (SQLException error) {
            throw new HarnessStoreException("cannot query JDBC Harness events", error);
        }
    }

    @Override
    public int rebuildEntitiesFromEvents() {
        return inTransaction(new TxOperation<Integer>() {
            @Override
            public Integer run(Connection connection) throws SQLException {
                selectForUpdate(connection);
                try (PreparedStatement delete = connection.prepareStatement(
                        "DELETE FROM ai4j_harness_entities WHERE harness_id = ?")) {
                    delete.setString(1, harnessId);
                    delete.executeUpdate();
                }
                List<HarnessEventRecord> events = new ArrayList<HarnessEventRecord>();
                try (PreparedStatement query = connection.prepareStatement(
                        "SELECT harness_id, seq, event_id, entity_type, entity_id, event_type, "
                                + "expected_version, entity_version, writer_epoch, actor, "
                                + "idempotency_key, timestamp_epoch_ms, payload_json "
                                + "FROM ai4j_harness_events WHERE harness_id = ? ORDER BY seq ASC")) {
                    query.setString(1, harnessId);
                    try (ResultSet result = query.executeQuery()) {
                        while (result.next()) {
                            events.add(readEventRow(result));
                        }
                    }
                }
                Map<HarnessProjectionEngine.EntityKey, HarnessEntityRecord> rebuilt =
                        HarnessProjectionEngine.foldEvents(harnessId, events);
                for (HarnessEntityRecord entity : rebuilt.values()) {
                    insertEntity(connection, entity);
                }
                return rebuilt.size();
            }
        }, "JDBC Harness rebuildEntitiesFromEvents failed");
    }

    @Override
    public WriterEpochFence claimWriterEpoch(String nodeId, final long expectedEpoch) {
        final String holder = HarnessSupport.requireText(nodeId, "node id");
        if (expectedEpoch < 0L) {
            throw new HarnessValidationException("expected writer epoch must be >= 0");
        }
        return inTransaction(new TxOperation<WriterEpochFence>() {
            @Override
            public WriterEpochFence run(Connection connection) throws SQLException {
                selectForUpdate(connection);
                WriterEpochFence current = loadWriterEpochForUpdate(connection);
                if (current.getEpoch() != expectedEpoch) {
                    throw new HarnessConflictException("writer epoch mismatch for " + harnessId
                            + ": expected " + expectedEpoch + ", current " + current.getEpoch());
                }
                long nextEpoch = expectedEpoch + 1L;
                long acquiredAt = System.currentTimeMillis();
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE ai4j_harness_writer_epoch SET writer_epoch = ?, holder_node_id = ?, acquired_at = ? "
                                + "WHERE harness_id = ? AND writer_epoch = ?")) {
                    update.setLong(1, nextEpoch);
                    update.setString(2, holder);
                    update.setLong(3, acquiredAt);
                    update.setString(4, harnessId);
                    update.setLong(5, expectedEpoch);
                    if (update.executeUpdate() != 1) {
                        throw new HarnessConflictException("writer epoch compare-and-set failed for " + harnessId);
                    }
                }
                return new WriterEpochFence(nextEpoch, holder, acquiredAt);
            }
        }, "JDBC Harness claimWriterEpoch failed");
    }

    @Override
    public WriterEpochFence currentWriterEpoch() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT writer_epoch, holder_node_id, acquired_at "
                             + "FROM ai4j_harness_writer_epoch WHERE harness_id = ?")) {
            statement.setString(1, harnessId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return new WriterEpochFence(0L, null, 0L);
                }
                return new WriterEpochFence(result.getLong("writer_epoch"),
                        result.getString("holder_node_id"),
                        result.getLong("acquired_at"));
            }
        } catch (SQLException error) {
            throw new HarnessStoreException("cannot load JDBC Harness writer epoch", error);
        }
    }

    private <T> T inTransaction(TxOperation<T> operation, String failureMessage) {
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            boolean committed = false;
            try {
                T result = operation.run(connection);
                connection.commit();
                committed = true;
                return result;
            } catch (HarnessStoreException error) {
                rollbackQuietly(connection, committed);
                throw error;
            } catch (RuntimeException error) {
                rollbackQuietly(connection, committed);
                throw error;
            } catch (SQLException error) {
                rollbackQuietly(connection, committed);
                throw new HarnessStoreException(failureMessage, error);
            } finally {
                try {
                    connection.setAutoCommit(previousAutoCommit);
                } catch (SQLException ignored) {
                    // Closing or pooling the connection remains responsible for resetting state.
                }
            }
        } catch (HarnessStoreException error) {
            throw error;
        } catch (SQLException error) {
            throw new HarnessStoreException("JDBC Harness transaction failed", error);
        }
    }

    private void rollbackQuietly(Connection connection, boolean committed) {
        if (committed) {
            return;
        }
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // Preserve the original validation or transaction failure.
        }
    }

    private HarnessState selectForUpdate(Connection connection) throws SQLException {
        ensureStateRow(connection);
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT version, state_json FROM ai4j_harness_state WHERE harness_id = ? FOR UPDATE")) {
            statement.setString(1, harnessId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return HarnessState.empty(harnessId);
                }
                HarnessState state = decodeState(result.getString("state_json"));
                state.setVersion(result.getLong("version"));
                return state;
            }
        }
    }

    private WriterEpochFence loadWriterEpochForUpdate(Connection connection) throws SQLException {
        ensureWriterEpochRow(connection);
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT writer_epoch, holder_node_id, acquired_at "
                        + "FROM ai4j_harness_writer_epoch WHERE harness_id = ? FOR UPDATE")) {
            statement.setString(1, harnessId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return new WriterEpochFence(0L, null, 0L);
                }
                return new WriterEpochFence(result.getLong("writer_epoch"),
                        result.getString("holder_node_id"),
                        result.getLong("acquired_at"));
            }
        }
    }

    private boolean exists(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM ai4j_harness_state WHERE harness_id = ?")) {
            statement.setString(1, harnessId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private void insertState(Connection connection, long version, String json, long updatedAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO ai4j_harness_state (harness_id, version, state_json, updated_at) VALUES (?, ?, ?, ?)")) {
            statement.setString(1, harnessId);
            statement.setLong(2, version);
            statement.setString(3, json);
            statement.setLong(4, updatedAt);
            statement.executeUpdate();
        }
    }

    private void updateState(Connection connection, long expectedVersion, long nextVersion,
                             String json, long updatedAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE ai4j_harness_state SET version = ?, state_json = ?, updated_at = ? "
                        + "WHERE harness_id = ? AND version = ?")) {
            statement.setLong(1, nextVersion);
            statement.setString(2, json);
            statement.setLong(3, updatedAt);
            statement.setString(4, harnessId);
            statement.setLong(5, expectedVersion);
            if (statement.executeUpdate() != 1) {
                throw new HarnessStoreException("JDBC Harness compare-and-set failed");
            }
        }
    }

    private void insertJournal(Connection connection, long version, String json, long recordedAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO ai4j_harness_journal (harness_id, version, state_json, recorded_at) VALUES (?, ?, ?, ?)")) {
            statement.setString(1, harnessId);
            statement.setLong(2, version);
            statement.setString(3, json);
            statement.setLong(4, recordedAt);
            statement.executeUpdate();
        }
    }

    private void pruneJournal(Connection connection, long latestVersion) throws SQLException {
        if (journalRetentionVersions <= 0) {
            return;
        }
        long firstRetainedVersion = Math.max(1L,
                latestVersion - journalRetentionVersions + 1L);
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM ai4j_harness_journal WHERE harness_id = ? AND version < ?")) {
            statement.setString(1, harnessId);
            statement.setLong(2, firstRetainedVersion);
            statement.executeUpdate();
        }
    }

    private void syncEntitiesAndEvents(Connection connection,
                                       HarnessState previous,
                                       HarnessState next) throws SQLException {
        Map<HarnessProjectionEngine.EntityKey, HarnessEntityRecord> before =
                HarnessProjectionEngine.extractEntitiesFromState(harnessId, previous);
        Map<HarnessProjectionEngine.EntityKey, HarnessEntityRecord> after =
                HarnessProjectionEngine.extractEntitiesFromState(harnessId, next);
        Map<HarnessProjectionEngine.EntityKey, Long> existingVersions = selectEntityVersions(connection);

        for (Map.Entry<HarnessProjectionEngine.EntityKey, HarnessEntityRecord> entry : after.entrySet()) {
            HarnessEntityRecord prevEntity = before.get(entry.getKey());
            HarnessEntityRecord nextEntity = entry.getValue();
            if (prevEntity == null
                    || !HarnessSupport.safeEquals(prevEntity.getStatus(), nextEntity.getStatus())
                    || !HarnessSupport.safeEquals(prevEntity.getPayloadJson(), nextEntity.getPayloadJson())) {
                Long storedVer = existingVersions.get(entry.getKey());
                long resolvedVersion = storedVer == null
                        ? nextEntity.getEntityVersion()
                        : Math.max(nextEntity.getEntityVersion(), storedVer.longValue() + 1L);
                nextEntity.setEntityVersion(resolvedVersion);
                upsertEntity(connection, nextEntity);
            }
        }
        for (HarnessProjectionEngine.EntityKey key : before.keySet()) {
            if (!after.containsKey(key)) {
                deleteEntity(connection, key.entityType, key.entityId);
            }
        }

        int prevEvents = previous.getEvents() == null ? 0 : previous.getEvents().size();
        List<HarnessEventRecord> nextEvents = next.getEvents();
        if (nextEvents != null && nextEvents.size() > prevEvents) {
            long maxSeq = selectMaxEventSeq(connection);
            for (int i = prevEvents; i < nextEvents.size(); i++) {
                HarnessEventRecord raw = nextEvents.get(i);
                if (raw == null) {
                    continue;
                }
                long seq = Math.max(maxSeq + 1L, raw.getSeq());
                maxSeq = seq;
                HarnessEventRecord enriched = HarnessProjectionEngine.enrichEventForLedger(
                        harnessId, seq, raw, after);
                insertEvent(connection, enriched);
            }
        }
    }

    private void syncStateAfterFencedAppend(Connection connection,
                                            HarnessState currentState,
                                            HarnessEventRecord storedEvent,
                                            HarnessEntityRecord nextEntity) throws SQLException {
        HarnessState updated = currentState.copy();
        updated.ensureCollections();
        boolean appliedToState = HarnessProjectionEngine.applyEntityToState(updated, nextEntity);
        if (!appliedToState && updated.getEvents().isEmpty() && currentState.getVersion() == 0L) {
            return;
        }
        updated.getEvents().add(storedEvent.copy());
        updated.setHarnessId(harnessId);
        updated.setVersion(currentState.getVersion() + 1L);
        updated.setUpdatedAtEpochMs(storedEvent.getTimestampEpochMs());
        String json = HarnessJson.toJson(updated);
        updateState(connection, currentState.getVersion(), updated.getVersion(), json, updated.getUpdatedAtEpochMs());
        insertJournal(connection, updated.getVersion(), json, updated.getUpdatedAtEpochMs());
        pruneJournal(connection, updated.getVersion());
    }

    private Map<HarnessProjectionEngine.EntityKey, Long> selectEntityVersions(Connection connection) throws SQLException {
        Map<HarnessProjectionEngine.EntityKey, Long> versions =
                new LinkedHashMap<HarnessProjectionEngine.EntityKey, Long>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT entity_type, entity_id, entity_version FROM ai4j_harness_entities WHERE harness_id = ?")) {
            statement.setString(1, harnessId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    versions.put(new HarnessProjectionEngine.EntityKey(
                                    result.getString("entity_type"), result.getString("entity_id")),
                            Long.valueOf(result.getLong("entity_version")));
                }
            }
        }
        return versions;
    }

    private HarnessEntityRecord selectEntity(Connection connection,
                                             String entityType,
                                             String entityId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT harness_id, entity_type, entity_id, entity_version, status, updated_at, payload_json "
                        + "FROM ai4j_harness_entities WHERE harness_id = ? AND entity_type = ? AND entity_id = ?")) {
            statement.setString(1, harnessId);
            statement.setString(2, entityType);
            statement.setString(3, entityId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                return readEntityRow(result);
            }
        }
    }

    private void upsertEntityIfAbsent(Connection connection, HarnessEntityRecord entity) throws SQLException {
        if (selectEntity(connection, entity.getEntityType(), entity.getEntityId()) == null) {
            insertEntity(connection, entity);
        }
    }

    private void upsertEntity(Connection connection, HarnessEntityRecord entity) throws SQLException {
        int updated;
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE ai4j_harness_entities SET entity_version = ?, status = ?, updated_at = ?, payload_json = ? "
                        + "WHERE harness_id = ? AND entity_type = ? AND entity_id = ?")) {
            statement.setLong(1, entity.getEntityVersion());
            if (entity.getStatus() == null) {
                statement.setNull(2, Types.VARCHAR);
            } else {
                statement.setString(2, entity.getStatus());
            }
            statement.setLong(3, entity.getUpdatedAtEpochMs());
            statement.setString(4, entity.getPayloadJson() == null ? "{}" : entity.getPayloadJson());
            statement.setString(5, harnessId);
            statement.setString(6, entity.getEntityType());
            statement.setString(7, entity.getEntityId());
            updated = statement.executeUpdate();
        }
        if (updated == 0) {
            insertEntity(connection, entity);
        }
    }

    private void insertEntity(Connection connection, HarnessEntityRecord entity) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO ai4j_harness_entities "
                        + "(harness_id, entity_type, entity_id, entity_version, status, updated_at, payload_json) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, harnessId);
            statement.setString(2, entity.getEntityType());
            statement.setString(3, entity.getEntityId());
            statement.setLong(4, entity.getEntityVersion());
            if (entity.getStatus() == null) {
                statement.setNull(5, Types.VARCHAR);
            } else {
                statement.setString(5, entity.getStatus());
            }
            statement.setLong(6, entity.getUpdatedAtEpochMs());
            statement.setString(7, entity.getPayloadJson() == null ? "{}" : entity.getPayloadJson());
            statement.executeUpdate();
        }
    }

    private void deleteEntity(Connection connection, String entityType, String entityId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM ai4j_harness_entities WHERE harness_id = ? AND entity_type = ? AND entity_id = ?")) {
            statement.setString(1, harnessId);
            statement.setString(2, entityType);
            statement.setString(3, entityId);
            statement.executeUpdate();
        }
    }

    private long selectMaxEventSeq(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT MAX(seq) FROM ai4j_harness_events WHERE harness_id = ?")) {
            statement.setString(1, harnessId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getLong(1) : 0L;
            }
        }
    }

    private HarnessEventRecord findEventByIdempotencyKey(Connection connection,
                                                         String idempotencyKey) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT harness_id, seq, event_id, entity_type, entity_id, event_type, "
                        + "expected_version, entity_version, writer_epoch, actor, "
                        + "idempotency_key, timestamp_epoch_ms, payload_json "
                        + "FROM ai4j_harness_events WHERE harness_id = ? AND idempotency_key = ?")) {
            statement.setString(1, harnessId);
            statement.setString(2, idempotencyKey);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                return readEventRow(result);
            }
        }
    }

    private void insertEventIfAbsent(Connection connection, HarnessEventRecord event) throws SQLException {
        try (PreparedStatement check = connection.prepareStatement(
                "SELECT 1 FROM ai4j_harness_events WHERE harness_id = ? AND seq = ?")) {
            check.setString(1, harnessId);
            check.setLong(2, event.getSeq());
            try (ResultSet result = check.executeQuery()) {
                if (result.next()) {
                    return;
                }
            }
        }
        insertEvent(connection, event);
    }

    private void insertEvent(Connection connection, HarnessEventRecord event) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO ai4j_harness_events "
                        + "(harness_id, seq, event_id, entity_type, entity_id, event_type, "
                        + "expected_version, entity_version, writer_epoch, actor, "
                        + "idempotency_key, timestamp_epoch_ms, payload_json) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, harnessId);
            statement.setLong(2, event.getSeq());
            statement.setString(3, event.getEventId());
            statement.setString(4, event.getEntityType());
            statement.setString(5, event.getEntityId());
            statement.setString(6, event.getEventType());
            if (event.getExpectedVersion() == null) {
                statement.setNull(7, Types.BIGINT);
            } else {
                statement.setLong(7, event.getExpectedVersion().longValue());
            }
            statement.setLong(8, event.getEntityVersion());
            statement.setLong(9, event.getWriterEpoch());
            if (event.getActor() == null) {
                statement.setNull(10, Types.VARCHAR);
            } else {
                statement.setString(10, event.getActor());
            }
            String idem = HarnessSupport.trimToNull(event.getIdempotencyKey());
            if (idem == null) {
                statement.setNull(11, Types.VARCHAR);
            } else {
                statement.setString(11, idem);
            }
            statement.setLong(12, event.getTimestampEpochMs());
            String payloadJson = event.getPayloadJson();
            statement.setString(13, payloadJson == null ? "{}" : payloadJson);
            statement.executeUpdate();
        }
    }

    private long countEntities(Connection connection,
                               String entityType,
                               String status) throws SQLException {
        String sql;
        if (entityType != null && status != null) {
            sql = SQL_COUNT_TYPE_STATUS;
        } else if (entityType != null) {
            sql = SQL_COUNT_TYPE;
        } else if (status != null) {
            sql = SQL_COUNT_STATUS;
        } else {
            sql = SQL_COUNT_ALL;
        }
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int idx = 1;
            statement.setString(idx++, harnessId);
            if (entityType != null) {
                statement.setString(idx++, entityType);
            }
            if (status != null) {
                statement.setString(idx, status);
            }
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getLong(1) : 0L;
            }
        }
    }

    private List<HarnessEntityRecord> queryEntityPage(Connection connection,
                                                      String entityType,
                                                      String status,
                                                      HarnessProjectionEngine.EntityCursor cursor,
                                                      int fetchLimit) throws SQLException {
        String sql = selectPageSql(entityType != null, status != null, cursor != null);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int idx = 1;
            statement.setString(idx++, harnessId);
            if (entityType != null) {
                statement.setString(idx++, entityType);
            }
            if (status != null) {
                statement.setString(idx++, status);
            }
            if (cursor != null) {
                statement.setString(idx++, cursor.entityType);
                statement.setString(idx++, cursor.entityType);
                statement.setString(idx++, cursor.entityId);
            }
            statement.setInt(idx, fetchLimit);
            try (ResultSet result = statement.executeQuery()) {
                List<HarnessEntityRecord> items = new ArrayList<HarnessEntityRecord>();
                while (result.next()) {
                    items.add(readEntityRow(result));
                }
                return items;
            }
        }
    }

    private String selectPageSql(boolean hasType, boolean hasStatus, boolean hasCursor) {
        if (hasType && hasStatus) {
            return hasCursor ? SQL_PAGE_TYPE_STATUS_CURSOR : SQL_PAGE_TYPE_STATUS;
        }
        if (hasType) {
            return hasCursor ? SQL_PAGE_TYPE_CURSOR : SQL_PAGE_TYPE;
        }
        if (hasStatus) {
            return hasCursor ? SQL_PAGE_STATUS_CURSOR : SQL_PAGE_STATUS;
        }
        return hasCursor ? SQL_PAGE_ALL_CURSOR : SQL_PAGE_ALL;
    }

    private HarnessEventRecord readEventRow(ResultSet result) throws SQLException {
        long rawExpected = result.getLong("expected_version");
        Long expectedVersion = result.wasNull() ? null : Long.valueOf(rawExpected);
        return HarnessEventRecord.builder()
                .harnessId(result.getString("harness_id"))
                .seq(result.getLong("seq"))
                .eventId(result.getString("event_id"))
                .entityType(result.getString("entity_type"))
                .entityId(result.getString("entity_id"))
                .eventType(result.getString("event_type"))
                .expectedVersion(expectedVersion)
                .entityVersion(result.getLong("entity_version"))
                .writerEpoch(result.getLong("writer_epoch"))
                .actor(result.getString("actor"))
                .idempotencyKey(result.getString("idempotency_key"))
                .timestampEpochMs(result.getLong("timestamp_epoch_ms"))
                .payloadJson(result.getString("payload_json"))
                .build();
    }

    private HarnessEntityRecord readEntityRow(ResultSet result) throws SQLException {
        return HarnessEntityRecord.builder()
                .harnessId(result.getString("harness_id"))
                .entityType(result.getString("entity_type"))
                .entityId(result.getString("entity_id"))
                .entityVersion(result.getLong("entity_version"))
                .status(result.getString("status"))
                .updatedAtEpochMs(result.getLong("updated_at"))
                .payloadJson(result.getString("payload_json"))
                .build();
    }

    private HarnessState decodeState(String json) {
        try {
            if (json == null || json.trim().isEmpty()) {
                throw new IllegalArgumentException("state JSON is empty");
            }
            HarnessState state = HarnessJson.fromJson(json, HarnessState.class);
            if (state == null) {
                throw new IllegalArgumentException("state JSON decoded to null");
            }
            if (state.getHarnessId() != null && !state.getHarnessId().trim().isEmpty()
                    && !harnessId.equals(state.getHarnessId())) {
                throw new HarnessConflictException("Harness id mismatch: expected " + harnessId
                        + ", found " + state.getHarnessId());
            }
            state.setHarnessId(harnessId);
            state.ensureCollections();
            return state;
        } catch (HarnessConflictException error) {
            throw new HarnessStoreException(error.getMessage(), error);
        } catch (RuntimeException error) {
            throw new HarnessStoreException("cannot decode JDBC Harness state for " + harnessId, error);
        }
    }

    private interface TxOperation<T> {
        T run(Connection connection) throws SQLException;
    }
}
