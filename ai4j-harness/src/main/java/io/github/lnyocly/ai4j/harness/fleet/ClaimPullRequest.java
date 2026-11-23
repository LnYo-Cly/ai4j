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
public class ClaimPullRequest {

    private String nodeId;
    private String credentialToken;
    private String workerId;
    private String agentId;
    @Builder.Default
    private int maxCount = 1;
    @Builder.Default
    private long leaseDurationMillis = 60_000L;
    @Builder.Default
    private boolean claimImmediately = true;

    @Builder.Default
    private List<String> scopeKeys = new ArrayList<String>();

    @Builder.Default
    private List<String> tags = new ArrayList<String>();

    @Builder.Default
    private List<String> allowedAgents = new ArrayList<String>();
}
