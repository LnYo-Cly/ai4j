package io.github.lnyocly.ai4j.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Per-finding owner disposition recorded inside a {@link ContentPinnedConsentRecord}.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class FindingDisposition {

    public enum Status {
        OPEN,
        RESOLVED,
        ACCEPTED_RISK,
        WAIVED,
        FALSE_POSITIVE,
        DEFERRED
    }

    private String findingId;
    @Builder.Default
    private Status status = Status.RESOLVED;
    private String rationale;
    private String evidenceId;

    public boolean isResolvedDisposition() {
        return status != null && status != Status.OPEN;
    }
}
