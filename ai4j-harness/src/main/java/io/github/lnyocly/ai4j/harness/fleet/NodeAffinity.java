package io.github.lnyocly.ai4j.harness.fleet;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Node-affinity routing rule pinning or constraining execution placement to
 * a specific {@code nodeId} and/or capability {@code tags}.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class NodeAffinity {

    private String targetNodeId;

    @Builder.Default
    private List<String> requiredTags = new ArrayList<String>();

    public boolean matches(FleetNodeRecord node) {
        if (node == null || node.getStatus() != FleetNodeStatus.ACTIVE) {
            return false;
        }
        if (targetNodeId != null && !targetNodeId.trim().isEmpty()
                && !targetNodeId.trim().equals(node.getNodeId())) {
            return false;
        }
        return node.supportsTags(requiredTags);
    }
}
