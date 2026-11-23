package io.github.lnyocly.ai4j.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Human/owner-only final sign-off pinned to a specific {@code submissionDigest}
 * and explicit per-finding {@link FindingDisposition} coverage.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ContentPinnedConsentRecord {

    private String consentId;
    private String submissionId;
    private String taskId;
    private String executionId;
    private String scopeKey;
    private String submissionDigest;
    private HarnessActor consenter;
    private boolean approved;
    private String rationale;
    private long consentedAtEpochMs;
    @Builder.Default
    private long version = 1L;

    @Builder.Default
    private List<FindingDisposition> dispositions = new ArrayList<FindingDisposition>();

    public ContentPinnedConsentRecord copy() {
        return HarnessJson.copy(this, ContentPinnedConsentRecord.class);
    }
}
