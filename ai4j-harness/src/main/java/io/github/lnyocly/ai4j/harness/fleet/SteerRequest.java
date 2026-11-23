package io.github.lnyocly.ai4j.harness.fleet;

import io.github.lnyocly.ai4j.harness.HarnessActor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class SteerRequest {

    private String executionId;
    private String expectedTaskId;
    private long expectedVersion;
    private String instructions;
    private HarnessActor actor;
}
