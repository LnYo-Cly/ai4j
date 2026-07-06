package io.github.lnyocly.ai4j.vector.store;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VectorExistsRequest {

    private String dataset;

    private Map<String, Object> filter;
}
