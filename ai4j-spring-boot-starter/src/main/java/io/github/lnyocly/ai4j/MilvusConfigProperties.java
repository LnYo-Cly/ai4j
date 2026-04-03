package io.github.lnyocly.ai4j;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "ai.vector.milvus")
public class MilvusConfigProperties {

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

    private String delete = "/v2/vectordb/entities/delete";
}
