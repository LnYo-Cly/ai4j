package io.github.lnyocly.ai4j.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagHit {

    private String id;

    private Float score;

    private Integer rank;

    private String retrieverSource;

    private Float retrievalScore;

    private Float fusionScore;

    private Float rerankScore;

    private String content;

    private Map<String, Object> metadata;

    private String documentId;

    private String sourceName;

    private String sourcePath;

    private String sourceUri;

    private Integer pageNumber;

    private String sectionTitle;

    private Integer chunkIndex;

    private List<RagScoreDetail> scoreDetails;
}
