package io.github.lnyocly.ai4j.rag.ingestion;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.File;
import java.util.Collections;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngestionSource {

    private String name;

    private String path;

    private String uri;

    private File file;

    private String content;

    @Builder.Default
    private Map<String, Object> metadata = Collections.emptyMap();

    public static IngestionSource text(String content) {
        return IngestionSource.builder().content(content).build();
    }

    public static IngestionSource file(File file) {
        return IngestionSource.builder().file(file).build();
    }
}
