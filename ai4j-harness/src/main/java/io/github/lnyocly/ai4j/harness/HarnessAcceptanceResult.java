package io.github.lnyocly.ai4j.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.ArrayList;
import java.util.List;

/** Structured result; a model completion claim is never an acceptance result. */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class HarnessAcceptanceResult {
    private String checkId;
    private HarnessAcceptanceStatus status;
    private String summary;
    @Builder.Default private List<HarnessAcceptanceFinding> findings = new ArrayList<HarnessAcceptanceFinding>();

    public boolean isPassed() { return HarnessAcceptanceStatus.PASS == status; }
    public boolean isTerminalFailure() { return status == HarnessAcceptanceStatus.FAIL || status == HarnessAcceptanceStatus.ERROR; }
}
