package io.github.lnyocly.ai4j.harness.fleet;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class EdgeJoinRequest {

    private String nodeId;
    @Builder.Default
    private FleetTopology topology = FleetTopology.FLEET_EDGE;
    private String displayName;
    private String credentialToken;
    @Builder.Default
    private int maxConcurrentExecutions = 4;

    @Builder.Default
    private List<String> tags = new ArrayList<String>();

    @Builder.Default
    private List<String> allowedAgents = new ArrayList<String>();

    @Builder.Default
    private List<String> allowedScopeKeys = new ArrayList<String>();
}
