package io.github.lnyocly.ai4j.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagCitation {

    private String citationId;

    private String sourceName;

    private String sourcePath;

    private String sourceUri;

    private Integer pageNumber;

    private String sectionTitle;

    private String snippet;
}
