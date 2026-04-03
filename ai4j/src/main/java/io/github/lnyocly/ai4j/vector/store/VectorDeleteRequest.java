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
public class VectorDeleteRequest {

    private String dataset;

    private List<String> ids;

    @Builder.Default
    private boolean deleteAll = false;

    private Map<String, Object> filter;
}
