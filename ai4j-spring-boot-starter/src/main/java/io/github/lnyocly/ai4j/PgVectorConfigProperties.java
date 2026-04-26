package io.github.lnyocly.ai4j;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "ai.vector.pgvector")
public class PgVectorConfigProperties {

    private boolean enabled = false;

    private String jdbcUrl = "jdbc:postgresql://localhost:5432/postgres";

    private String username = "";

    private String password = "";

    private String tableName = "ai4j_vectors";

    private String idColumn = "id";

    private String datasetColumn = "dataset";

    private String vectorColumn = "embedding";

    private String contentColumn = "content";

    private String metadataColumn = "metadata";

    private String distanceOperator = "<=>";
}
