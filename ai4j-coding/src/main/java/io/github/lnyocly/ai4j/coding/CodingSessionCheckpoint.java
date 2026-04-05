package io.github.lnyocly.ai4j.coding;

import io.github.lnyocly.ai4j.coding.process.StoredProcessSnapshot;
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
public class CodingSessionCheckpoint {

    private String goal;

    @Builder.Default
    private List<String> constraints = new ArrayList<String>();

    @Builder.Default
    private List<String> doneItems = new ArrayList<String>();

    @Builder.Default
    private List<String> inProgressItems = new ArrayList<String>();

    @Builder.Default
    private List<String> blockedItems = new ArrayList<String>();

    @Builder.Default
    private List<String> keyDecisions = new ArrayList<String>();

    @Builder.Default
    private List<String> nextSteps = new ArrayList<String>();

    @Builder.Default
    private List<String> criticalContext = new ArrayList<String>();

    @Builder.Default
    private List<StoredProcessSnapshot> processSnapshots = new ArrayList<StoredProcessSnapshot>();

    private long generatedAtEpochMs;

    private int sourceItemCount;

    private boolean splitTurn;
}
