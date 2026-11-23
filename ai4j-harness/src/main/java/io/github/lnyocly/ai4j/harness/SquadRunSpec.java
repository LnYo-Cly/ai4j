package io.github.lnyocly.ai4j.harness;

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
public class SquadRunSpec {

    private String squadRunId;
    private String scopeKey;
    private String taskId;
    private String leaderId;
    private String objective;
    @Builder.Default
    private int leaderTurnBudget = 4;
    @Builder.Default
    private boolean synthesizeOnBudgetExhausted = true;

    @Builder.Default
    private List<String> memberIds = new ArrayList<String>();
}
