package io.github.lnyocly.ai4j.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagDocument {

    private String documentId;

    private String sourceName;

    private String sourcePath;

    private String sourceUri;

    private String title;

    private String tenant;

    private String biz;

    private String version;

    private Map<String, Object> metadata;
}
