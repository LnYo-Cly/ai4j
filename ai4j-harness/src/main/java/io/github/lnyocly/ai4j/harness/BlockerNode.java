package io.github.lnyocly.ai4j.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Node in a structured blocker explanation tree returned by {@code explain(entityRef)}.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class BlockerNode {

    private String code;
    private String entityType;
    private String entityId;
    private String status;
    private String message;
    private String remediationHint;

    @Builder.Default
    private Map<String, Object> details = new LinkedHashMap<String, Object>();

    @Builder.Default
    private List<BlockerNode> children = new ArrayList<BlockerNode>();
}
