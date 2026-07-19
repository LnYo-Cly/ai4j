package io.github.lnyocly.ai4j.vector.store;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VectorStoreCapabilities {

    private boolean dataset;

    private boolean metadataFilter;

    private boolean metadataLookup;

    private boolean deleteByFilter;

    private boolean returnStoredVector;

    public VectorStoreCapabilities(boolean dataset,
                                   boolean metadataFilter,
                                   boolean deleteByFilter,
                                   boolean returnStoredVector) {
        this(dataset, metadataFilter, false, deleteByFilter, returnStoredVector);
    }
}
