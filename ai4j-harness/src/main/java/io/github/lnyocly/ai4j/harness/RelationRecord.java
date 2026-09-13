package io.github.lnyocly.ai4j.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class RelationRecord {

    private String relationId;
    private String scopeKey;
    private RelationType type;
    private EntityKind fromKind;
    private String fromId;
    private EntityKind toKind;
    private String toId;
    private String createdBy;
    private long createdAtEpochMs;

    @Builder.Default
    private Map<String, Object> metadata = new LinkedHashMap<String, Object>();

    public RelationRecord copy() {
        return HarnessJson.copy(this, RelationRecord.class);
    }
}
