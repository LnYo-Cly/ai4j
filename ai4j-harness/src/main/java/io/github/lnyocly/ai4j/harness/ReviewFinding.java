package io.github.lnyocly.ai4j.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Structured technical finding emitted by an {@link ExecutionReviewRecord}.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ReviewFinding {

    public enum Severity {
        INFO,
        WARN,
        ERROR,
        CRITICAL
    }

    private String findingId;
    @Builder.Default
    private Severity severity = Severity.WARN;
    private String summary;
    private String location;
    private String category;
    @Builder.Default
    private boolean blocking = true;
}
