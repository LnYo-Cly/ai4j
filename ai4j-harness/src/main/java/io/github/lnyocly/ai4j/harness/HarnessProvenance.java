package io.github.lnyocly.ai4j.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class HarnessProvenance {

    private HarnessActor actor;
    private String executionId;
    private String sessionId;
    private String sourceType;
    private String sourceId;
    private long recordedAtEpochMs;
}
