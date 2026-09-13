package io.github.lnyocly.ai4j.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ReviewRecord {

    private String reviewId;
    private String submissionId;
    private String taskId;
    private HarnessActor reviewer;
    private ReviewVerdict verdict;
    private String findings;
    private String rationale;
    private long createdAtEpochMs;

    public ReviewRecord copy() {
        return HarnessJson.copy(this, ReviewRecord.class);
    }
}
