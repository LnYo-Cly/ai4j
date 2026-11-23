package io.github.lnyocly.ai4j.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Dual-queue supervisory projection separating items currently awaiting a human/owner
 * ruling from answered items waiting for execution follow-up.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class SupervisoryAgenda {

    private String actorId;
    private String scopeKey;
    private long generatedAtEpochMs;

    @Builder.Default
    private List<AgendaItem> awaitingRuling = new ArrayList<AgendaItem>();

    @Builder.Default
    private List<AgendaItem> answeredPendingFollowUp = new ArrayList<AgendaItem>();
}
