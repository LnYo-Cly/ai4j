package io.github.lnyocly.ai4j.flowgram.springboot.support;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class JdbcFlowGramTaskStore implements FlowGramTaskStore {

    private final DataSource dataSource;
    private final String tableName;

    public JdbcFlowGramTaskStore(DataSource dataSource, String tableName, boolean initializeSchema) {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource is required");
        }
        this.dataSource = dataSource;
        this.tableName = validIdentifier(tableName);
        if (initializeSchema) {
            initializeSchema();
        }
    }

    @Override
    public void save(FlowGramStoredTask task) {
        if (task == null || isBlank(task.getTaskId())) {
            return;
        }
        upsert(copy(task));
    }

    @Override
    public FlowGramStoredTask find(String taskId) {
        if (isBlank(taskId)) {
            return null;
        }
        String sql = "select task_id, creator_id, tenant_id, created_at, expires_at, status, terminated, error, result_snapshot " +
                "from " + tableName + " where task_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, taskId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return FlowGramStoredTask.builder()
                        .taskId(resultSet.getString("task_id"))
                        .creatorId(resultSet.getString("creator_id"))
                        .tenantId(resultSet.getString("tenant_id"))
                        .createdAt(longValue(resultSet, "created_at"))
                        .expiresAt(longValue(resultSet, "expires_at"))
                        .status(resultSet.getString("status"))
                        .terminated(booleanValue(resultSet, "terminated"))
                        .error(resultSet.getString("error"))
                        .resultSnapshot(parseSnapshot(resultSet.getString("result_snapshot")))
                        .build();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load FlowGram task: " + taskId, e);
        }
    }

    @Override
    public void updateState(String taskId, String status, Boolean terminated, String error, Map<String, Object> resultSnapshot) {
        if (isBlank(taskId)) {
            return;
        }
        FlowGramStoredTask existing = find(taskId);
        FlowGramStoredTask target = existing == null
                ? FlowGramStoredTask.builder().taskId(taskId).build()
                : existing.toBuilder().build();
        if (status != null) {
            target.setStatus(status);
        }
        if (terminated != null) {
            target.setTerminated(terminated);
        }
        if (error != null || target.getError() != null) {
            target.setError(error);
        }
        if (resultSnapshot != null) {
            target.setResultSnapshot(copyMap(resultSnapshot));
        }
        upsert(target);
    }

    private void initializeSchema() {
        String sql = "create table if not exists " + tableName + " (" +
                "task_id varchar(191) not null, " +
                "creator_id varchar(191), " +
                "tenant_id varchar(191), " +
                "created_at bigint, " +
                "expires_at bigint, " +
                "status varchar(64), " +
                "terminated boolean, " +
                "error text, " +
                "result_snapshot text, " +
                "updated_at bigint not null, " +
                "primary key (task_id)" +
                ")";
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize FlowGram task store schema", e);
        }
    }

    private void upsert(FlowGramStoredTask task) {
        String deleteSql = "delete from " + tableName + " where task_id = ?";
        String insertSql = "insert into " + tableName +
                " (task_id, creator_id, tenant_id, created_at, expires_at, status, terminated, error, result_snapshot, updated_at) " +
                "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement deleteStatement = connection.prepareStatement(deleteSql)) {
                    deleteStatement.setString(1, task.getTaskId());
                    deleteStatement.executeUpdate();
                }
                try (PreparedStatement insertStatement = connection.prepareStatement(insertSql)) {
                    insertStatement.setString(1, task.getTaskId());
                    insertStatement.setString(2, task.getCreatorId());
                    insertStatement.setString(3, task.getTenantId());
                    setLong(insertStatement, 4, task.getCreatedAt());
                    setLong(insertStatement, 5, task.getExpiresAt());
                    insertStatement.setString(6, task.getStatus());
                    if (task.getTerminated() == null) {
                        insertStatement.setObject(7, null);
                    } else {
                        insertStatement.setBoolean(7, task.getTerminated());
                    }
                    insertStatement.setString(8, task.getError());
                    Map<String, Object> snapshot = task.getResultSnapshot() == null
                            ? Collections.<String, Object>emptyMap()
                            : task.getResultSnapshot();
                    insertStatement.setString(9, JSON.toJSONString(snapshot));
                    insertStatement.setLong(10, System.currentTimeMillis());
                    insertStatement.executeUpdate();
                }
                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to persist FlowGram task: " + task.getTaskId(), e);
        }
    }

    private FlowGramStoredTask copy(FlowGramStoredTask task) {
        return task.toBuilder()
                .resultSnapshot(copyMap(task.getResultSnapshot()))
                .build();
    }

    private Map<String, Object> copyMap(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<String, Object>();
        if (source == null) {
            return copy;
        }
        copy.putAll(source);
        return copy;
    }

    private Map<String, Object> parseSnapshot(String json) {
        if (isBlank(json)) {
            return new LinkedHashMap<String, Object>();
        }
        return JSON.parseObject(json, new TypeReference<LinkedHashMap<String, Object>>() {
        });
    }

    private Long longValue(ResultSet resultSet, String column) throws Exception {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private Boolean booleanValue(ResultSet resultSet, String column) throws Exception {
        boolean value = resultSet.getBoolean(column);
        return resultSet.wasNull() ? null : value;
    }

    private void setLong(PreparedStatement statement, int index, Long value) throws Exception {
        if (value == null) {
            statement.setObject(index, null);
        } else {
            statement.setLong(index, value);
        }
    }

    private String validIdentifier(String value) {
        if (isBlank(value) || !value.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?")) {
            throw new IllegalArgumentException("Invalid sql identifier: " + value);
        }
        return value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
