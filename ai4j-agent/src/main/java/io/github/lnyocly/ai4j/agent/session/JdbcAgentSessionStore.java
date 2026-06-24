package io.github.lnyocly.ai4j.agent.session;

import com.alibaba.fastjson2.JSON;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Durable, JDBC-backed {@link AgentSessionStore}. Stores each {@link AgentSessionSnapshot} as one
 * JSON row keyed by session id, so sessions survive process restarts and long tasks can resume.
 *
 * <p>Mirrors the connection handling of {@code JdbcAgentMemory}: a {@link DataSource} or a
 * {@code jdbcUrl} (optional username/password). Schema initialization is on by default.</p>
 */
public class JdbcAgentSessionStore implements AgentSessionStore {

    private final DataSource dataSource;
    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final String tableName;

    public JdbcAgentSessionStore(JdbcAgentSessionStoreConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config is required");
        }
        this.dataSource = config.getDataSource();
        this.jdbcUrl = trimToNull(config.getJdbcUrl());
        this.username = trimToNull(config.getUsername());
        this.password = config.getPassword();
        this.tableName = validIdentifier(config.getTableName(), "ai4j_agent_session");
        if (this.dataSource == null && this.jdbcUrl == null) {
            throw new IllegalArgumentException("dataSource or jdbcUrl is required");
        }
        if (config.isInitializeSchema()) {
            initializeSchema();
        }
    }

    public JdbcAgentSessionStore(String jdbcUrl) {
        this(JdbcAgentSessionStoreConfig.builder().jdbcUrl(jdbcUrl).build());
    }

    @Override
    public void save(AgentSessionSnapshot snapshot) {
        if (snapshot == null || snapshot.getSessionId() == null) {
            return;
        }
        String sessionId = snapshot.getSessionId();
        String json = JSON.toJSONString(snapshot);
        Connection connection = null;
        try {
            connection = openConnection();
            // upsert: delete then insert (portable across H2/MySQL/Postgres/SQLite)
            try (PreparedStatement delete = connection.prepareStatement(
                    "delete from " + tableName + " where session_id = ?")) {
                delete.setString(1, sessionId);
                delete.executeUpdate();
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "insert into " + tableName + " (session_id, snapshot_json, updated_at) values (?, ?, ?)")) {
                insert.setString(1, sessionId);
                insert.setString(2, json);
                insert.setLong(3, System.currentTimeMillis());
                insert.executeUpdate();
            }
        } catch (Exception e) {
            throw new RuntimeException("failed to save session snapshot " + sessionId, e);
        } finally {
            close(connection);
        }
    }

    @Override
    public AgentSessionSnapshot load(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        Connection connection = null;
        try {
            connection = openConnection();
            try (PreparedStatement select = connection.prepareStatement(
                    "select snapshot_json from " + tableName + " where session_id = ?")) {
                select.setString(1, sessionId);
                try (ResultSet rs = select.executeQuery()) {
                    if (!rs.next()) {
                        return null;
                    }
                    String json = rs.getString("snapshot_json");
                    if (json == null || json.trim().isEmpty()) {
                        return null;
                    }
                    return JSON.parseObject(json, AgentSessionSnapshot.class);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("failed to load session snapshot " + sessionId, e);
        } finally {
            close(connection);
        }
    }

    @Override
    public boolean delete(String sessionId) {
        if (sessionId == null) {
            return false;
        }
        Connection connection = null;
        try {
            connection = openConnection();
            try (PreparedStatement stmt = connection.prepareStatement(
                    "delete from " + tableName + " where session_id = ?")) {
                stmt.setString(1, sessionId);
                return stmt.executeUpdate() > 0;
            }
        } catch (Exception e) {
            throw new RuntimeException("failed to delete session snapshot " + sessionId, e);
        } finally {
            close(connection);
        }
    }

    @Override
    public List<String> listSessionIds() {
        List<String> ids = new ArrayList<String>();
        Connection connection = null;
        try {
            connection = openConnection();
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("select session_id from " + tableName)) {
                while (rs.next()) {
                    ids.add(rs.getString("session_id"));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("failed to list session ids", e);
        } finally {
            close(connection);
        }
        return ids;
    }

    private void initializeSchema() {
        Connection connection = null;
        try {
            connection = openConnection();
            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate("create table if not exists " + tableName + " ("
                        + "session_id varchar(255) not null primary key, "
                        + "snapshot_json clob, "
                        + "updated_at bigint)");
            }
        } catch (Exception e) {
            throw new RuntimeException("failed to initialize session store schema", e);
        } finally {
            close(connection);
        }
    }

    private Connection openConnection() throws Exception {
        if (dataSource != null) {
            return dataSource.getConnection();
        }
        if (username != null) {
            return DriverManager.getConnection(jdbcUrl, username, password);
        }
        return DriverManager.getConnection(jdbcUrl);
    }

    private static void close(Connection connection) {
        if (connection != null) {
            try {
                connection.close();
            } catch (Exception ignored) {
                // best-effort
            }
        }
    }

    private static String validIdentifier(String value, String fallback) {
        String text = trimToNull(value);
        if (text == null) {
            return fallback;
        }
        if (!text.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("invalid table identifier: " + text);
        }
        return text;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
