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
public class VectorSearchResult {

    private String id;

    private Float score;

    private String content;

    private List<Float> vector;

    private Map<String, Object> metadata;
}
