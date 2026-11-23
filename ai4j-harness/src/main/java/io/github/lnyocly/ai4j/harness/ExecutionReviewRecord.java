package io.github.lnyocly.ai4j.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Independent technical review of a submission. Unlike {@link ContentPinnedConsentRecord},
 * an ExecutionReview may be produced by an independent QA agent or system reviewer and
 * never satisfies owner consent on its own.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionReviewRecord {

    private String reviewId;
    private String submissionId;
    private String taskId;
    private String executionId;
    private String scopeKey;
    private String submissionDigest;
    private HarnessActor reviewer;
    private ReviewVerdict verdict;
    private String findingsSummary;
    private String rationale;
    private long createdAtEpochMs;

    @Builder.Default
    private List<ReviewFinding> findings = new ArrayList<ReviewFinding>();

    public ExecutionReviewRecord copy() {
        return HarnessJson.copy(this, ExecutionReviewRecord.class);
    }
}
