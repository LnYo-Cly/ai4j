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
public class HarnessRelationSpec {

    private RelationType type;
    private String scopeKey;
    private EntityKind fromKind;
    private String fromId;
    private EntityKind toKind;
    private String toId;

    @Builder.Default
    private Map<String, Object> metadata = new LinkedHashMap<String, Object>();
}
