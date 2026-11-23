package io.github.lnyocly.ai4j.ambient.channel;

import io.github.lnyocly.ai4j.harness.HarnessActor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Callback payload submitted by a human endpoint when responding to an {@link EscalationOptionCard}.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class EscalationCallback {

    private String callbackId;
    private String cardId;
    private String waitId;
    private String endpointId;
    private String responderId;
    private HarnessActor responderActor;
    private String selectedOptionId;
    private String comment;
    private long respondedAtEpochMs;
}
