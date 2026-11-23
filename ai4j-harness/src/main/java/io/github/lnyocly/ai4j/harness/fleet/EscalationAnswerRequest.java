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
public class EscalationAnswerRequest {

    private String escalationId;
    private String answer;
    private String selectedOption;
    private String rationale;
    private HarnessActor actor;
}
