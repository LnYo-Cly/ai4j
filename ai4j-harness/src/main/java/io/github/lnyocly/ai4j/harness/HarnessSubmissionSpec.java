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
public class HarnessSubmissionSpec {

    private String completionClaim;
    private String verificationNotes;

    @Builder.Default
    private List<String> deliverables = new ArrayList<String>();

    @Builder.Default
    private List<String> evidenceIds = new ArrayList<String>();

    @Builder.Default
    private List<String> knownGaps = new ArrayList<String>();

    @Builder.Default
    private List<String> residualRisks = new ArrayList<String>();
}
