package io.github.lnyocly.ai4j.vector.store;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VectorSearchRequest {

    private String dataset;

    private List<Float> vector;

    @Builder.Default
    private Integer topK = 10;

    private Map<String, Object> filter;

    @Builder.Default
    private Boolean includeMetadata = Boolean.TRUE;

    @Builder.Default
    private Boolean includeVector = Boolean.FALSE;
}
