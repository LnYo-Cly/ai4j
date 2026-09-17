package io.github.lnyocly.ai4j.harness;

import lombok.AllArgsConstructor;
import lombok.Data;

/** Detached result of one acceptance run, including the durable ids it created. */
@Data @AllArgsConstructor
public final class HarnessAcceptanceEvaluation {
    private HarnessAcceptanceResult result;
    private AcceptanceRecord acceptance;
    private EvidenceRecord evidence;
}
