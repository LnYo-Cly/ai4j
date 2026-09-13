package io.github.lnyocly.ai4j.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Updated adapter state and replacement result for a delivered wait. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HarnessAdapterDelivery {

    private HarnessAdapterState state;

    private boolean replacedPendingResult;
}
