package io.github.lnyocly.ai4j.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Arrays;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MilvusConfig {

    private boolean enabled = false;

    private String host = "http://localhost:19530";

    private String token = "";

    private String dbName = "";

    private String partitionName = "";

    private String idField = "id";

    private String vectorField = "vector";

    private String contentField = "content";

    private List<String> outputFields = Arrays.asList(
            "id",
            "content",
            "documentId",
            "sourceName",
            "sourcePath",
            "sourceUri",
            "pageNumber",
            "sectionTitle",
            "chunkIndex"
    );

    private String upsert = "/v2/vectordb/entities/upsert";

    private String search = "/v2/vectordb/entities/search";

    private String query = "/v2/vectordb/entities/query";

    private String delete = "/v2/vectordb/entities/delete";

    public MilvusConfig(boolean enabled,
                        String host,
                        String token,
                        String dbName,
                        String partitionName,
                        String idField,
                        String vectorField,
                        String contentField,
                        List<String> outputFields,
                        String upsert,
                        String search,
                        String delete) {
        this(enabled, host, token, dbName, partitionName, idField, vectorField, contentField, outputFields,
                upsert, search, "/v2/vectordb/entities/query", delete);
    }

}
