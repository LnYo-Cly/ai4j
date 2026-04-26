package io.github.lnyocly.ai4j.agent.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.sql.DataSource;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class JdbcAgentMemoryConfig {

    private DataSource dataSource;

    private String jdbcUrl;

    private String username;

    private String password;

    private String sessionId;

    @Builder.Default
    private String tableName = "ai4j_agent_memory";

    @Builder.Default
    private boolean initializeSchema = true;

    private MemoryCompressor compressor;
}
