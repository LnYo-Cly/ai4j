package io.github.lnyocly.ai4j.memory;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatMessage;
import io.github.lnyocly.ai4j.platform.openai.tool.ToolCall;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class JdbcChatMemory implements ChatMemory {

    private final DataSource dataSource;
    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final String sessionId;
    private final String tableName;

    private ChatMemoryPolicy policy;

    public JdbcChatMemory(JdbcChatMemoryConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config is required");
        }
        this.dataSource = config.getDataSource();
        this.jdbcUrl = trimToNull(config.getJdbcUrl());
        this.username = trimToNull(config.getUsername());
        this.password = config.getPassword();
        this.sessionId = requiredText(config.getSessionId(), "sessionId");
        this.tableName = validIdentifier(config.getTableName());
        this.policy = config.getPolicy() == null ? new UnboundedChatMemoryPolicy() : config.getPolicy();
        if (this.dataSource == null && this.jdbcUrl == null) {
            throw new IllegalArgumentException("dataSource or jdbcUrl is required");
        }
        if (config.isInitializeSchema()) {
            initializeSchema();
        }
    }

    public JdbcChatMemory(String jdbcUrl, String sessionId) {
        this(JdbcChatMemoryConfig.builder()
                .jdbcUrl(jdbcUrl)
                .sessionId(sessionId)
                .build());
    }

    public JdbcChatMemory(String jdbcUrl, String username, String password, String sessionId) {
        this(JdbcChatMemoryConfig.builder()
                .jdbcUrl(jdbcUrl)
                .username(username)
                .password(password)
                .sessionId(sessionId)
                .build());
    }

    public JdbcChatMemory(DataSource dataSource, String sessionId) {
        this(JdbcChatMemoryConfig.builder()
                .dataSource(dataSource)
                .sessionId(sessionId)
                .build());
    }

    public void setPolicy(ChatMemoryPolicy policy) {
        this.policy = policy == null ? new UnboundedChatMemoryPolicy() : policy;
        synchronized (this) {
            replaceItems(applyPolicy(loadItems()));
        }
    }

    @Override
    public void addSystem(String text) {
        add(ChatMemoryItem.system(text));
    }

    @Override
    public void addUser(String text) {
        add(ChatMemoryItem.user(text));
    }

    @Override
    public void addUser(String text, String... imageUrls) {
        add(ChatMemoryItem.user(text, imageUrls));
    }

    @Override
    public void addAssistant(String text) {
        add(ChatMemoryItem.assistant(text));
    }

    @Override
    public void addAssistant(String text, List<ToolCall> toolCalls) {
        add(ChatMemoryItem.assistant(text, toolCalls));
    }

    @Override
    public void addAssistantToolCalls(List<ToolCall> toolCalls) {
        add(ChatMemoryItem.assistantToolCalls(toolCalls));
    }

    @Override
    public void addToolOutput(String toolCallId, String output) {
        add(ChatMemoryItem.tool(toolCallId, output));
    }

    @Override
    public synchronized void add(ChatMemoryItem item) {
        if (item == null || item.isEmpty()) {
            return;
        }
        List<ChatMemoryItem> items = loadItems();
        items.add(ChatMemoryItem.copyOf(item));
        replaceItems(applyPolicy(items));
    }

    @Override
    public synchronized void addAll(List<ChatMemoryItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        List<ChatMemoryItem> merged = loadItems();
        for (ChatMemoryItem item : items) {
            if (item != null && !item.isEmpty()) {
                merged.add(ChatMemoryItem.copyOf(item));
            }
        }
        replaceItems(applyPolicy(merged));
    }

    @Override
    public synchronized List<ChatMemoryItem> getItems() {
        return copyItems(loadItems());
    }

    @Override
    public synchronized List<ChatMessage> toChatMessages() {
        List<ChatMemoryItem> items = loadItems();
        List<ChatMessage> messages = new ArrayList<ChatMessage>(items.size());
        for (ChatMemoryItem item : items) {
            messages.add(item.toChatMessage());
        }
        return messages;
    }

    @Override
    public synchronized List<Object> toResponsesInput() {
        List<ChatMemoryItem> items = loadItems();
        List<Object> input = new ArrayList<Object>(items.size());
        for (ChatMemoryItem item : items) {
            input.add(item.toResponsesInput());
        }
        return input;
    }

    @Override
    public synchronized ChatMemorySnapshot snapshot() {
        return ChatMemorySnapshot.from(loadItems());
    }

    @Override
    public synchronized void restore(ChatMemorySnapshot snapshot) {
        List<ChatMemoryItem> items = new ArrayList<ChatMemoryItem>();
        if (snapshot != null && snapshot.getItems() != null) {
            for (ChatMemoryItem item : snapshot.getItems()) {
                if (item != null && !item.isEmpty()) {
                    items.add(ChatMemoryItem.copyOf(item));
                }
            }
        }
        replaceItems(applyPolicy(items));
    }

    @Override
    public synchronized void clear() {
        replaceItems(Collections.<ChatMemoryItem>emptyList());
    }

    private void initializeSchema() {
        String sql = "create table if not exists " + tableName + " (" +
                "session_id varchar(191) not null, " +
                "item_index integer not null, " +
                "item_json text not null, " +
                "created_at bigint not null, " +
                "primary key (session_id, item_index)" +
                ")";
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize chat memory schema", e);
        }
    }

    private List<ChatMemoryItem> loadItems() {
        String sql = "select item_json from " + tableName + " where session_id = ? order by item_index asc";
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, sessionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<ChatMemoryItem> items = new ArrayList<ChatMemoryItem>();
                while (resultSet.next()) {
                    ChatMemoryItem item = JSON.parseObject(resultSet.getString("item_json"), ChatMemoryItem.class);
                    if (item != null && !item.isEmpty()) {
                        items.add(item);
                    }
                }
                return items;
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load chat memory", e);
        }
    }

    private void replaceItems(List<ChatMemoryItem> items) {
        String deleteSql = "delete from " + tableName + " where session_id = ?";
        String insertSql = "insert into " + tableName + " (session_id, item_index, item_json, created_at) values (?, ?, ?, ?)";
        try (Connection connection = openConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement deleteStatement = connection.prepareStatement(deleteSql)) {
                    deleteStatement.setString(1, sessionId);
                    deleteStatement.executeUpdate();
                }
                if (items != null && !items.isEmpty()) {
                    try (PreparedStatement insertStatement = connection.prepareStatement(insertSql)) {
                        long now = System.currentTimeMillis();
                        for (int i = 0; i < items.size(); i++) {
                            insertStatement.setString(1, sessionId);
                            insertStatement.setInt(2, i);
                            insertStatement.setString(3, JSON.toJSONString(items.get(i)));
                            insertStatement.setLong(4, now);
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
            throw new IllegalStateException("Failed to persist chat memory", e);
        }
    }

    private List<ChatMemoryItem> applyPolicy(List<ChatMemoryItem> items) {
        List<ChatMemoryItem> copied = copyItems(items);
        List<ChatMemoryItem> applied = (policy == null ? new UnboundedChatMemoryPolicy() : policy).apply(copied);
        return copyItems(applied);
    }

    private List<ChatMemoryItem> copyItems(List<ChatMemoryItem> items) {
        List<ChatMemoryItem> copied = new ArrayList<ChatMemoryItem>();
        if (items == null) {
            return copied;
        }
        for (ChatMemoryItem item : items) {
            if (item != null) {
                copied.add(ChatMemoryItem.copyOf(item));
            }
        }
        return copied;
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
