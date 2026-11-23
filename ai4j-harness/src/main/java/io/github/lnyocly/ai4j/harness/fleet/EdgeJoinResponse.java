package io.github.lnyocly.ai4j.harness.fleet;

import io.github.lnyocly.ai4j.harness.HarnessEntityRecord;
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
public class EdgeJoinResponse {

    private FleetNodeRecord node;
    private String credentialToken;
    private long writerEpoch;
    private long headEventSeq;

    @Builder.Default
    private List<HarnessEntityRecord> centerEntities = new ArrayList<HarnessEntityRecord>();
}
