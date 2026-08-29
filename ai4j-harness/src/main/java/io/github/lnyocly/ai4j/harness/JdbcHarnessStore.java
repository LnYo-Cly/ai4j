package io.github.lnyocly.ai4j.harness;

import com.alibaba.fastjson2.JSON;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** JDBC store using a transaction and row lock per stable harness id. */
public final class JdbcHarnessStore implements HarnessStore {

    private static final int DEFAULT_JOURNAL_RETENTION_VERSIONS = 128;

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
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS ai4j_harness_state ("
                    + "harness_id VARCHAR(255) PRIMARY KEY, version BIGINT NOT NULL, "
                    + "state_json TEXT NOT NULL, updated_at BIGINT NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS ai4j_harness_journal ("
                    + "harness_id VARCHAR(255) NOT NULL, version BIGINT NOT NULL, "
                    + "state_json TEXT NOT NULL, recorded_at BIGINT NOT NULL, "
                    + "PRIMARY KEY (harness_id, version))");
            ensureStateRow(connection);
        } catch (SQLException error) {
            throw new HarnessStoreException("cannot initialize JDBC Harness schema", error);
        }
    }

    private void ensureStateRow(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO ai4j_harness_state (harness_id, version, state_json, updated_at) VALUES (?, 0, ?, 0)")) {
            statement.setString(1, harnessId);
            statement.setString(2, JSON.toJSONString(HarnessState.empty(harnessId)));
            statement.executeUpdate();
        } catch (SQLException error) {
            if (!isDuplicateKey(error)) {
                throw error;
            }
            // Another process initialized the same harness row first.
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
    public HarnessState update(HarnessStateMutation mutation) {
        if (mutation == null) {
            throw new IllegalArgumentException("Harness mutation is required");
        }
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            boolean committed = false;
            try {
                HarnessState current = selectForUpdate(connection);
                HarnessState next = mutation.apply(current.copy());
                if (next == null) {
                    throw new HarnessStoreException("Harness mutation returned null");
                }
                next.ensureCollections();
                next.setHarnessId(harnessId);
                next.setVersion(current.getVersion() + 1L);
                next.setUpdatedAtEpochMs(System.currentTimeMillis());
                String json = JSON.toJSONString(next);
                if (current.getVersion() == 0L && !exists(connection)) {
                    insertState(connection, next.getVersion(), json, next.getUpdatedAtEpochMs());
                } else {
                    updateState(connection, current.getVersion(), next.getVersion(), json, next.getUpdatedAtEpochMs());
                }
                insertJournal(connection, next.getVersion(), json, next.getUpdatedAtEpochMs());
                pruneJournal(connection, next.getVersion());
                HarnessState result = next.copy();
                connection.commit();
                committed = true;
                return result;
            } catch (HarnessStoreException error) {
                rollbackQuietly(connection, committed);
                throw error;
            } catch (Exception error) {
                rollbackQuietly(connection, committed);
                if (error instanceof HarnessStoreException) {
                    throw (HarnessStoreException) error;
                }
                throw new HarnessStoreException("JDBC Harness update failed", error);
            } finally {
                // A successful commit is already the durable result. A
                // connection-pool reset failure must not turn that result into
                // a false failed write.
                try {
                    connection.setAutoCommit(previousAutoCommit);
                } catch (SQLException ignored) {
                    // Closing or pooling the connection remains responsible for
                    // resetting its transaction state.
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

    private HarnessState decodeState(String json) {
        try {
            if (json == null || json.trim().isEmpty()) {
                throw new IllegalArgumentException("state JSON is empty");
            }
            HarnessState state = JSON.parseObject(json, HarnessState.class);
            if (state == null) {
                throw new IllegalArgumentException("state JSON decoded to null");
            }
            if (state.getHarnessId() != null && !state.getHarnessId().trim().isEmpty()
                    && !harnessId.equals(state.getHarnessId())) {
                throw new HarnessStoreException("Harness id mismatch: expected " + harnessId
                        + ", found " + state.getHarnessId());
            }
            state.setHarnessId(harnessId);
            state.ensureCollections();
            return state;
        } catch (HarnessStoreException error) {
            throw error;
        } catch (RuntimeException error) {
            throw new HarnessStoreException("cannot decode JDBC Harness state for " + harnessId, error);
        }
    }
}
