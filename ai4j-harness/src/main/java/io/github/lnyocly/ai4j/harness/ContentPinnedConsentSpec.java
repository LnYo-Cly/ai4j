package io.github.lnyocly.ai4j.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Input specification for signing a human/owner {@link ContentPinnedConsentRecord}.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ContentPinnedConsentSpec {

    private String consentId;
    private String submissionId;
    private String submissionDigest;
    @Builder.Default
    private boolean approved = true;
    private String rationale;

    @Builder.Default
    private List<FindingDisposition> dispositions = new ArrayList<FindingDisposition>();
}
