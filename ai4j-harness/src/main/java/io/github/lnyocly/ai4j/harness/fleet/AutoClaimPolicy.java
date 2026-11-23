package io.github.lnyocly.ai4j.harness.fleet;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Execution pull-claim policy governing an {@link AutoClaimWorker}.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class AutoClaimPolicy {

    @Builder.Default
    private List<String> allowedAgents = new ArrayList<String>();

    @Builder.Default
    private List<String> allowedScopeKeys = new ArrayList<String>();

    @Builder.Default
    private List<String> tags = new ArrayList<String>();

    @Builder.Default
    private int maxConcurrentExecutions = 4;

    @Builder.Default
    private int batchSize = 4;

    @Builder.Default
    private long leaseDurationMillis = 60_000L;
}
