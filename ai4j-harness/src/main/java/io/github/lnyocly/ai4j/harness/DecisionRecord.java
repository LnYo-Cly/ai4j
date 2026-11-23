package io.github.lnyocly.ai4j.harness;

import com.fasterxml.jackson.annotation.JsonAlias;
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

    @JsonAlias("chosen")
    private String chosenOption;

    @Builder.Default
    private List<String> rejected = new ArrayList<String>();

    @Builder.Default
    private List<DecisionClaim> claims = new ArrayList<DecisionClaim>();

    @Builder.Default
    private List<DecisionFulfillment> fulfillments = new ArrayList<DecisionFulfillment>();

    private String rationale;
    private DecisionStatus status;

    @Builder.Default
    private ReckonStatus reckonStatus = ReckonStatus.UNRECKONED;
    private String reckonSummary;
    private HarnessActor reckonedBy;
    private long reckonedAtEpochMs;

    private HarnessActor proposer;
    private HarnessActor arbiter;
    private HarnessActor amendedBy;
    private long createdAtEpochMs;
    private long resolvedAtEpochMs;
    private long amendedAtEpochMs;

    @Builder.Default
    private long version = 1L;

    @Builder.Default
    private List<String> factIds = new ArrayList<String>();

    @Builder.Default
    private List<String> evidenceIds = new ArrayList<String>();

    public String getChosen() {
        return chosenOption;
    }

    public void setChosen(String chosen) {
        this.chosenOption = chosen;
    }

    public DecisionRecord copy() {
        return HarnessJson.copy(this, DecisionRecord.class);
    }

    public static class DecisionRecordBuilder {
        public DecisionRecordBuilder chosen(String chosen) {
            this.chosenOption = chosen;
            return this;
        }
    }
}
