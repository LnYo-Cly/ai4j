package io.github.lnyocly.ai4j.harness.fleet;

import io.github.lnyocly.ai4j.harness.HarnessActor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class TakeoverOrphanRequest {

    private String exportId;
    private String targetNodeId;
    private String credentialToken;
    private String workerId;
    @Builder.Default
    private long leaseDurationMillis = 60_000L;
    private HarnessActor actor;
}
