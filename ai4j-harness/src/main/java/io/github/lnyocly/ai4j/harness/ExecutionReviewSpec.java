package io.github.lnyocly.ai4j.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Input specification for submitting an independent technical {@link ExecutionReviewRecord}.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionReviewSpec {

    private String reviewId;
    private String submissionId;
    private String submissionDigest;
    private ReviewVerdict verdict;
    private String findingsSummary;
    private String rationale;

    @Builder.Default
    private List<ReviewFinding> findings = new ArrayList<ReviewFinding>();
}
