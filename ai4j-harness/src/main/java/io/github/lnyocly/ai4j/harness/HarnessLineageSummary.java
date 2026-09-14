package io.github.lnyocly.ai4j.harness;

import lombok.Value;
import java.util.List;

/** Generic, benchmark-neutral summary of one execution repair lineage. */
@Value
public class HarnessLineageSummary {
    List<ExecutionRecord> executions;
    List<AcceptanceRecord> acceptances;
    int repairCount;
    HarnessAcceptanceStatus finalAcceptanceStatus;
    boolean passed;
}
