package io.github.lnyocly.ai4j.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JinaConfig {

    private String apiHost = "https://api.jina.ai/";

    private String apiKey = "";

    private String rerankUrl = "v1/rerank";
}
