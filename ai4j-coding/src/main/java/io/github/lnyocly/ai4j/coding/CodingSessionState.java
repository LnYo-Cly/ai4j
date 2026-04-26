package io.github.lnyocly.ai4j.coding;

import io.github.lnyocly.ai4j.agent.memory.MemorySnapshot;
import io.github.lnyocly.ai4j.coding.process.StoredProcessSnapshot;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodingSessionState {

    private String sessionId;

    private String workspaceRoot;

    private MemorySnapshot memorySnapshot;

    private int processCount;

    private CodingSessionCheckpoint checkpoint;

    private CodingSessionCompactResult latestCompactResult;

    private int autoCompactFailureCount;

    private boolean autoCompactCircuitBreakerOpen;

    @Builder.Default
    private List<StoredProcessSnapshot> processSnapshots = new ArrayList<StoredProcessSnapshot>();
}
