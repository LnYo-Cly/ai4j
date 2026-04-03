package io.github.lnyocly.ai4j.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QdrantConfig {

    private boolean enabled = false;

    private String host = "http://localhost:6333";

    private String apiKey = "";

    private String vectorName = "";

    private String upsert = "/collections/%s/points";

    private String query = "/collections/%s/points/query";

    private String delete = "/collections/%s/points/delete";
}
