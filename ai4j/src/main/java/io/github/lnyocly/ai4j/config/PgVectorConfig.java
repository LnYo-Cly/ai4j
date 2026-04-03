package io.github.lnyocly.ai4j.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PgVectorConfig {

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
