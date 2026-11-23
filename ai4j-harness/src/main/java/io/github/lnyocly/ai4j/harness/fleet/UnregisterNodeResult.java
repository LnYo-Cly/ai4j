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
public class UnregisterNodeResult {

    private FleetNodeRecord node;
    private long previousWriterEpoch;
    private long newWriterEpoch;

    @Builder.Default
    private List<String> revokedLeaseIds = new ArrayList<String>();

    @Builder.Default
    private List<OrphanHandoffExport> orphanExports = new ArrayList<OrphanHandoffExport>();
}
