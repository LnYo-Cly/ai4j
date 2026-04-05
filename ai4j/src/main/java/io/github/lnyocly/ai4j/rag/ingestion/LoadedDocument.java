package io.github.lnyocly.ai4j.rag.ingestion;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.Map;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class LoadedDocument {

    private String content;

    private String sourceName;

    private String sourcePath;

    private String sourceUri;

    @Builder.Default
    private Map<String, Object> metadata = Collections.emptyMap();
}
