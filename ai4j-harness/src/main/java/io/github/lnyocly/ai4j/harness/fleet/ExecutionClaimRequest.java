package io.github.lnyocly.ai4j.harness.fleet;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionClaimRequest {

    private String nodeId;
    private String credentialToken;
    private String executionId;
    private String workerId;
    @Builder.Default
    private long leaseDurationMillis = 60_000L;
}
