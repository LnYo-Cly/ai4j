package io.github.lnyocly.ai4j.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Budget-bounded multi-agent Squad orchestration record tracking leader turns
 * against {@code leaderTurnBudget} and forcing synthesis convergence.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class SquadRunRecord {

    private String squadRunId;
    private String scopeKey;
    private String taskId;
    private String leaderId;
    private String objective;
    private int leaderTurnBudget;
    private int leaderTurnsUsed;
    private int delegationCount;
    private SquadRunStatus status;
    private String synthesisReport;
    private long createdAtEpochMs;
    private long finishedAtEpochMs;
    @Builder.Default
    private long version = 1L;

    @Builder.Default
    private List<String> memberIds = new ArrayList<String>();

    @Builder.Default
    private List<SquadTurnRecord> turns = new ArrayList<SquadTurnRecord>();

    public SquadRunRecord copy() {
        return HarnessJson.copy(this, SquadRunRecord.class);
    }
}
