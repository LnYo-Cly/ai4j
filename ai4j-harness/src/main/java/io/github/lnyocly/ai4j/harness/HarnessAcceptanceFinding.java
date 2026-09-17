package io.github.lnyocly.ai4j.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One actionable, durable finding returned by an acceptance check. */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class HarnessAcceptanceFinding {
    private String requirementId;
    private String message;
    private String artifactLocation;
    private String repairHint;
}
