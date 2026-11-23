package io.github.lnyocly.ai4j.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class FactRecord {

    private String factId;
    private String scopeKey;
    private String taskId;
    private String statement;
    private String source;
    private String confidence;
    private boolean valid;
    private String invalidatedBy;
    private long createdAtEpochMs;
    private long invalidatedAtEpochMs;
    @Builder.Default
    private long version = 1L;
    private String supersedesFactId;
    private String supersededByFactId;
    private String factTypeId;
    private int factTypeVersion;
    private String typedPayloadJson;
    private HarnessProvenance provenance;

    @Builder.Default
    private List<String> evidenceIds = new ArrayList<String>();

    @Builder.Default
    private Map<String, Object> metadata = new LinkedHashMap<String, Object>();

    public FactRecord copy() {
        return HarnessJson.copy(this, FactRecord.class);
    }
}
