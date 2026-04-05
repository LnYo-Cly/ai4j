package io.github.lnyocly.ai4j.coding;

import io.github.lnyocly.ai4j.coding.process.BashProcessInfo;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class CodingSessionSnapshot {

    private String sessionId;

    private String workspaceRoot;

    private int memoryItemCount;

    private String summary;

    private String checkpointGoal;

    private long checkpointGeneratedAtEpochMs;

    private boolean checkpointSplitTurn;

    private int processCount;

    private int activeProcessCount;

    private int restoredProcessCount;

    private int estimatedContextTokens;

    private String lastCompactMode;

    private int lastCompactBeforeItemCount;

    private int lastCompactAfterItemCount;

    private int lastCompactTokensBefore;

    private int lastCompactTokensAfter;

    private String lastCompactStrategy;

    private String lastCompactSummary;

    private int autoCompactFailureCount;

    private boolean autoCompactCircuitBreakerOpen;

    @Builder.Default
    private List<BashProcessInfo> processes = new ArrayList<BashProcessInfo>();
}
