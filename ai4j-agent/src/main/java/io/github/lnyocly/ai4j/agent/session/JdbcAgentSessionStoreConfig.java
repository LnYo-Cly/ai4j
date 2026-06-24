package io.github.lnyocly.ai4j.agent.session;

import io.github.lnyocly.ai4j.agent.memory.JdbcAgentMemoryConfig;

import javax.sql.DataSource;

/**
 * Configuration for {@link JdbcAgentSessionStore}. Mirrors {@link JdbcAgentMemoryConfig}: supply
 * either a {@link DataSource} or a {@code jdbcUrl} (plus optional credentials). Schema init is on
 * by default so the store is usable out of the box against a fresh database.
 */
public final class JdbcAgentSessionStoreConfig {

    private DataSource dataSource;
    private String jdbcUrl;
    private String username;
    private String password;
    private String tableName = "ai4j_agent_session";
    private boolean initializeSchema = true;

    public static JdbcAgentSessionStoreConfig builder() {
        return new JdbcAgentSessionStoreConfig();
    }

    public JdbcAgentSessionStoreConfig dataSource(DataSource dataSource) {
        this.dataSource = dataSource;
        return this;
    }

    public JdbcAgentSessionStoreConfig jdbcUrl(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
        return this;
    }

    public JdbcAgentSessionStoreConfig username(String username) {
        this.username = username;
        return this;
    }

    public JdbcAgentSessionStoreConfig password(String password) {
        this.password = password;
        return this;
    }

    public JdbcAgentSessionStoreConfig tableName(String tableName) {
        if (tableName != null && !tableName.trim().isEmpty()) {
            this.tableName = tableName.trim();
        }
        return this;
    }

    public JdbcAgentSessionStoreConfig initializeSchema(boolean initializeSchema) {
        this.initializeSchema = initializeSchema;
        return this;
    }

    public JdbcAgentSessionStoreConfig build() {
        return this;
    }

    public DataSource getDataSource() { return dataSource; }
    public String getJdbcUrl() { return jdbcUrl; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public String getTableName() { return tableName; }
    public boolean isInitializeSchema() { return initializeSchema; }
}
