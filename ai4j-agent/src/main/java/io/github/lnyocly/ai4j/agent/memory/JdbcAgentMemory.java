package io.github.lnyocly.ai4j.agent.memory;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.agent.util.AgentInputItem;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class JdbcAgentMemory implements AgentMemory {

    private static final String ENTRY_TYPE_ITEM = "item";
    private static final String ENTRY_TYPE_SUMMARY = "summary";

    private final DataSource dataSource;
    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final String sessionId;
    private final String tableName;

    private MemoryCompressor compressor;

    public JdbcAgentMemory(JdbcAgentMemoryConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config is required");
        }
        this.dataSource = config.getDataSource();
        this.jdbcUrl = trimToNull(config.getJdbcUrl());
        this.username = trimToNull(config.getUsername());
        this.password = config.getPassword();
        this.sessionId = requiredText(config.getSessionId(), "sessionId");
        this.tableName = validIdentifier(config.getTableName());
        this.compressor = config.getCompressor();
        if (this.dataSource == null && this.jdbcUrl == null) {
            throw new IllegalArgumentException("dataSource or jdbcUrl is required");
        }
        if (config.isInitializeSchema()) {
            initializeSchema();
        }
    }

    public JdbcAgentMemory(String jdbcUrl, String sessionId) {
        this(JdbcAgentMemoryConfig.builder()
                .jdbcUrl(jdbcUrl)
                .sessionId(sessionId)
                .build());
    }

    public JdbcAgentMemory(String jdbcUrl, String username, String password, String sessionId) {
        this(JdbcAgentMemoryConfig.builder()
                .jdbcUrl(jdbcUrl)
                .username(username)
                .password(password)
                .sessionId(sessionId)
                .build());
    }

    public JdbcAgentMemory(DataSource dataSource, String sessionId) {
        this(JdbcAgentMemoryConfig.builder()
                .dataSource(dataSource)
                .sessionId(sessionId)
                .build());
    }

    public void setCompressor(MemoryCompressor compressor) {
        this.compressor = compressor;
        synchronized (this) {
            replaceSnapshot(applyCompressor(loadSnapshot()));
        }
    }

    public synchronized void setSummary(String summary) {
        MemorySnapshot snapshot = loadSnapshot();
        snapshot.setSummary(summary);
        replaceSnapshot(applyCompressor(snapshot));
    }

    public synchronized MemorySnapshot snapshot() {
        MemorySnapshot snapshot = loadSnapshot();
        return MemorySnapshot.from(snapshot.getItems(), snapshot.getSummary());
    }

    public synchronized void restore(MemorySnapshot snapshot) {
        MemorySnapshot target = snapshot == null
                ? MemorySnapshot.from(Collections.<Object>emptyList(), null)
                : MemorySnapshot.from(snapshot.getItems(), snapshot.getSummary());
        replaceSnapshot(applyCompressor(target));
    }

    @Override
    public synchronized void addUserInput(Object input) {
        if (input == null) {
            return;
        }
        MemorySnapshot snapshot = loadSnapshot();
        List<Object> items = copyItems(snapshot.getItems());
        if (input instanceof String) {
            items.add(AgentInputItem.userMessage((String) input));
        } else {
            items.add(input);
        }
        replaceSnapshot(applyCompressor(MemorySnapshot.from(items, snapshot.getSummary())));
    }

    @Override
    public synchronized void addOutputItems(List<Object> outputItems) {
        if (outputItems == null || outputItems.isEmpty()) {
            return;
        }
        MemorySnapshot snapshot = loadSnapshot();
        List<Object> items = copyItems(snapshot.getItems());
        items.addAll(outputItems);
        replaceSnapshot(applyCompressor(MemorySnapshot.from(items, snapshot.getSummary())));
    }

    @Override
    public synchronized void addToolOutput(String callId, String output) {
        if (callId == null) {
            return;
        }
        MemorySnapshot snapshot = loadSnapshot();
        List<Object> items = copyItems(snapshot.getItems());
        items.add(AgentInputItem.functionCallOutput(callId, output));
        replaceSnapshot(applyCompressor(MemorySnapshot.from(items, snapshot.getSummary())));
    }

    @Override
    public synchronized List<Object> getItems() {
        MemorySnapshot snapshot = loadSnapshot();
        List<Object> items = copyItems(snapshot.getItems());
        if (snapshot.getSummary() == null || snapshot.getSummary().trim().isEmpty()) {
            return items;
        }
        List<Object> merged = new ArrayList<Object>(items.size() + 1);
        merged.add(AgentInputItem.systemMessage(snapshot.getSummary()));
        merged.addAll(items);
        return merged;
    }

    @Override
    public synchronized String getSummary() {
        return loadSnapshot().getSummary();
    }

    @Override
    public synchronized void clear() {
        replaceSnapshot(MemorySnapshot.from(Collections.<Object>emptyList(), null));
    }

    private void initializeSchema() {
        String sql = "create table if not exists " + tableName + " (" +
                "session_id varchar(191) not null, " +
                "entry_type varchar(16) not null, " +
                "entry_index integer not null, " +
                "entry_json text, " +
                "updated_at bigint not null, " +
                "primary key (session_id, entry_type, entry_index)" +
                ")";
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize agent memory schema", e);
        }
    }

    private MemorySnapshot loadSnapshot() {
        String summarySql = "select entry_json from " + tableName +
                " where session_id = ? and entry_type = ? and entry_index = 0";
        String itemsSql = "select entry_json from " + tableName +
                " where session_id = ? and entry_type = ? order by entry_index asc";
        try (Connection connection = openConnection()) {
            String summary = null;
            try (PreparedStatement summaryStatement = connection.prepareStatement(summarySql)) {
                summaryStatement.setString(1, sessionId);
                summaryStatement.setString(2, ENTRY_TYPE_SUMMARY);
                try (ResultSet resultSet = summaryStatement.executeQuery()) {
                    if (resultSet.next()) {
                        Object value = JSON.parse(resultSet.getString("entry_json"));
                        summary = value == null ? null : String.valueOf(value);
                    }
                }
            }

            List<Object> items = new ArrayList<Object>();
            try (PreparedStatement itemsStatement = connection.prepareStatement(itemsSql)) {
                itemsStatement.setString(1, sessionId);
                itemsStatement.setString(2, ENTRY_TYPE_ITEM);
                try (ResultSet resultSet = itemsStatement.executeQuery()) {
                    while (resultSet.next()) {
                        items.add(JSON.parse(resultSet.getString("entry_json")));
                    }
                }
            }
            return MemorySnapshot.from(items, summary);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load agent memory", e);
        }
    }

    private void replaceSnapshot(MemorySnapshot snapshot) {
        String deleteSql = "delete from " + tableName + " where session_id = ?";
        String insertSql = "insert into " + tableName +
                " (session_id, entry_type, entry_index, entry_json, updated_at) values (?, ?, ?, ?, ?)";
        try (Connection connection = openConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement deleteStatement = connection.prepareStatement(deleteSql)) {
                    deleteStatement.setString(1, sessionId);
                    deleteStatement.executeUpdate();
                }
                long now = System.currentTimeMillis();
                if (snapshot != null && snapshot.getSummary() != null) {
                    try (PreparedStatement insertStatement = connection.prepareStatement(insertSql)) {
                        insertStatement.setString(1, sessionId);
                        insertStatement.setString(2, ENTRY_TYPE_SUMMARY);
                        insertStatement.setInt(3, 0);
                        insertStatement.setString(4, JSON.toJSONString(snapshot.getSummary()));
                        insertStatement.setLong(5, now);
                        insertStatement.executeUpdate();
                    }
                }
                if (snapshot != null && snapshot.getItems() != null && !snapshot.getItems().isEmpty()) {
                    try (PreparedStatement insertStatement = connection.prepareStatement(insertSql)) {
                        for (int i = 0; i < snapshot.getItems().size(); i++) {
                            insertStatement.setString(1, sessionId);
                            insertStatement.setString(2, ENTRY_TYPE_ITEM);
                            insertStatement.setInt(3, i);
                            insertStatement.setString(4, JSON.toJSONString(snapshot.getItems().get(i)));
                            insertStatement.setLong(5, now);
                            insertStatement.addBatch();
                        }
                        insertStatement.executeBatch();
                    }
                }
                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to persist agent memory", e);
        }
    }

    private MemorySnapshot applyCompressor(MemorySnapshot snapshot) {
        if (compressor == null) {
            return MemorySnapshot.from(snapshot == null ? null : snapshot.getItems(), snapshot == null ? null : snapshot.getSummary());
        }
        return compressor.compress(snapshot == null
                ? MemorySnapshot.from(Collections.<Object>emptyList(), null)
                : MemorySnapshot.from(snapshot.getItems(), snapshot.getSummary()));
    }

    private List<Object> copyItems(List<Object> items) {
        return items == null ? new ArrayList<Object>() : new ArrayList<Object>(items);
    }

    private Connection openConnection() throws Exception {
        if (dataSource != null) {
            return dataSource.getConnection();
        }
        if (username == null) {
            return DriverManager.getConnection(jdbcUrl);
        }
        return DriverManager.getConnection(jdbcUrl, username, password);
    }

    private String validIdentifier(String value) {
        String identifier = requiredText(value, "tableName");
        if (!identifier.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?")) {
            throw new IllegalArgumentException("Invalid sql identifier: " + value);
        }
        return identifier;
    }

    private String requiredText(String value, String fieldName) {
        String text = trimToNull(value);
        if (text == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return text;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
