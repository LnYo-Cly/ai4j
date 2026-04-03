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
public class RagChunk {

    private String chunkId;

    private String documentId;

    private String content;

    private Integer chunkIndex;

    private Integer pageNumber;

    private String sectionTitle;

    private Map<String, Object> metadata;
}
