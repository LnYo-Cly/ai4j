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
public class DecisionRecord {

    private String decisionId;
    private String scopeKey;
    private String taskId;
    private String question;
    private String chosenOption;
    private String rationale;
    private DecisionStatus status;
    private HarnessActor proposer;
    private HarnessActor arbiter;
    private long createdAtEpochMs;
    private long resolvedAtEpochMs;

    @Builder.Default
    private List<String> factIds = new ArrayList<String>();

    @Builder.Default
    private List<String> evidenceIds = new ArrayList<String>();

    public DecisionRecord copy() {
        return HarnessJson.copy(this, DecisionRecord.class);
    }
}
