package io.github.lnyocly.ai4j.vector.store;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VectorUpsertRequest {

    private String dataset;

    @Builder.Default
    private List<VectorRecord> records = Collections.emptyList();
}
